package com.alexmaxham.volta.core

/** One full-capacity estimate taken from a charging session. */
data class CapacityReading(val time: Long, val fullMah: Float, val gained: Int)

data class Health(
    val estimateMah: Float,
    val designMah: Int?,
    val samples: Int,
) {
    /** Estimated capacity as a share of the design capacity, or null without one. */
    val percent: Int? get() = designMah?.takeIf { it > 0 }?.let { (estimateMah / it * 100).toInt() }
}

object Capacity {
    const val MIN_GAIN = 30
    const val MIN_TIME = 20 * Time.MINUTE

    /**
     * Estimates full capacity from a charging session: the charge that went in divided by the
     * share of the battery it filled. Uses the phone's charge counter when it reports one, and
     * falls back to adding up current over time. Needs a continuous gain of 30+ points over 20+ minutes.
     */
    fun fromSession(session: ChargingSession): CapacityReading? {
        val rs = session.readings.filter { it.plugged }.sortedBy { it.time }
        if (rs.size < 2) return null
        if (rs.zipWithNext().any { (a, b) -> b.time - a.time > Limits.SESSION_GAP }) return null
        val first = rs.first()
        val last = rs.last()
        val gained = last.level - first.level
        if (gained < MIN_GAIN || last.time - first.time < MIN_TIME) return null

        val counterA = first.chargeCounterMah
        val counterB = last.chargeCounterMah
        val mah = if (counterA != null && counterB != null && counterB > counterA) {
            counterB - counterA
        } else {
            val pairs = rs.zipWithNext()
            val withCurrent = pairs.count { (a, b) -> a.currentMa != null && b.currentMa != null }
            if (withCurrent < pairs.size * 0.8) return null
            pairs.sumOf { (a, b) ->
                val ca = a.currentMa ?: return@sumOf 0.0
                val cb = b.currentMa ?: return@sumOf 0.0
                (ca + cb) / 2.0 * (b.time - a.time) / Time.HOUR
            }.toFloat()
        }
        val full = mah / (gained / 100f)
        // Discard values no phone battery could have.
        if (full < 500f || full > 30_000f) return null
        return CapacityReading(last.time, full, gained)
    }

    /** Health from the most recent estimates (median of up to the last 10). */
    fun health(readings: List<CapacityReading>, designMah: Int?): Health? {
        if (readings.isEmpty()) return null
        val recent = readings.sortedByDescending { it.time }.take(10).map { it.fullMah }.sorted()
        val median = if (recent.size % 2 == 1) recent[recent.size / 2] else (recent[recent.size / 2 - 1] + recent[recent.size / 2]) / 2f
        return Health(median, designMah, recent.size)
    }
}
