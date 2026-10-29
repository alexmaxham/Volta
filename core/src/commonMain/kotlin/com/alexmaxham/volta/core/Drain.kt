package com.alexmaxham.volta.core

/** Percentage points lost and the time it took. */
data class DrainRate(val points: Int, val duration: Long) {
    /** Points per hour, or null when there's too little time to say. */
    val perHour: Double? get() = if (duration >= 10 * Time.MINUTE) points * Time.HOUR.toDouble() / duration else null
}

/** What the battery did over a window. */
data class Usage(
    val from: Long,
    val to: Long,
    /** Points lost between readings, counting only drops. */
    val used: Int,
    /** Points gained while charging. */
    val charged: Int,
    val screenOn: DrainRate,
    val screenOff: DrainRate,
    val lowest: Int?,
    val peakTempC: Float?,
    /** Share of the window covered by readings no more than [Limits.GAP] apart. */
    val coverage: Double,
)

sealed interface Estimate {
    /** Not enough recent continuous use to say. Progress is 0..1. */
    data class Learning(val progress: Float) : Estimate
    data class Remaining(val millis: Long, val ratePerHour: Double) : Estimate
    data class UntilFull(val millis: Long, val ratePerHour: Double) : Estimate
    data object Full : Estimate
}

object Drain {
    const val MIN_LEARN_TIME = 45 * Time.MINUTE
    const val MIN_LEARN_DROP = 3
    const val MAX_REMAINING = 3 * Time.DAY

    /**
     * Battery use over [from, to). Intervals between readings count toward the screen-on or
     * screen-off rate only when the screen was on (or off) for at least 90% of the interval;
     * gaps, mixed and charging intervals are left out of the rates.
     */
    fun usage(readings: List<Reading>, screenOn: List<Interval>, from: Long, to: Long): Usage {
        val sorted = readings.sortedBy { it.time }
        var used = 0
        var charged = 0
        var onPts = 0
        var onTime = 0L
        var offPts = 0
        var offTime = 0L
        var covered = 0L
        for ((a, b) in sorted.zipWithNext()) {
            if (b.time <= from || a.time >= to) continue
            val dt = b.time - a.time
            if (dt <= 0) continue
            val delta = b.level - a.level
            if (delta < 0) used -= delta else if (delta > 0) charged += delta
            if (dt > Limits.GAP) continue
            covered += Interval(a.time, b.time).overlap(from, to)
            if (a.plugged || b.plugged || delta > 0) continue
            val onShare = ScreenTimeline.onFraction(screenOn, a.time, b.time)
            when {
                onShare >= 0.9 -> { onPts -= delta; onTime += dt }
                onShare <= 0.1 -> { offPts -= delta; offTime += dt }
            }
        }
        val inWindow = sorted.filter { it.time in from..to }
        return Usage(
            from = from,
            to = to,
            used = used,
            charged = charged,
            screenOn = DrainRate(onPts, onTime),
            screenOff = DrainRate(offPts, offTime),
            lowest = inWindow.minOfOrNull { it.level },
            peakTempC = inWindow.mapNotNull { it.tempC }.maxOrNull(),
            coverage = if (to > from) covered.toDouble() / (to - from) else 0.0,
        )
    }

    /**
     * Time left at the recent pace. Learning needs at least 45 minutes and a 3-point drop of
     * continuous unplugged use; a recording gap restarts it.
     */
    fun estimate(readings: List<Reading>): Estimate {
        val sorted = readings.sortedBy { it.time }
        val last = sorted.lastOrNull() ?: return Estimate.Learning(0f)
        if (last.plugged) return chargeEstimate(sorted, last)

        var start = last
        for (i in sorted.indices.reversed().drop(1)) {
            val r = sorted[i]
            val next = sorted[i + 1]
            if (r.plugged || next.time - r.time > Limits.GAP || r.level < next.level) break
            start = r
        }
        val drop = start.level - last.level
        val span = last.time - start.time
        if (span < MIN_LEARN_TIME || drop < MIN_LEARN_DROP) {
            val progress = minOf(span.toFloat() / MIN_LEARN_TIME, drop.toFloat() / MIN_LEARN_DROP).coerceIn(0f, 0.99f)
            return Estimate.Learning(progress)
        }
        val rate = drop * Time.HOUR.toDouble() / span
        val millis = (last.level / rate * Time.HOUR).toLong()
        return Estimate.Remaining(minOf(millis, MAX_REMAINING), rate)
    }

    private fun chargeEstimate(sorted: List<Reading>, last: Reading): Estimate {
        if (last.level >= 100) return Estimate.Full
        var start = last
        for (i in sorted.indices.reversed().drop(1)) {
            val r = sorted[i]
            val next = sorted[i + 1]
            if (!r.plugged || next.time - r.time > Limits.SESSION_GAP || r.level > next.level) break
            start = r
        }
        val gain = last.level - start.level
        val span = last.time - start.time
        if (span < 5 * Time.MINUTE || gain < 1) return Estimate.Learning((span.toFloat() / (5 * Time.MINUTE)).coerceIn(0f, 0.99f))
        val rate = gain * Time.HOUR.toDouble() / span
        // Charging slows near the top; stretch the last 20 points.
        val fast = (80 - last.level).coerceAtLeast(0)
        val slow = 100 - last.level - fast
        val hours = fast / rate + slow / (rate * 0.55)
        return Estimate.UntilFull((hours * Time.HOUR).toLong(), rate)
    }
}
