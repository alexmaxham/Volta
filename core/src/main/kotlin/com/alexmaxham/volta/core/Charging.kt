package com.alexmaxham.volta.core

data class ChargingSession(
    val start: Long,
    val end: Long,
    val startLevel: Int,
    val endLevel: Int,
    val charger: Charger,
    /** True when the phone was still plugged in at the last reading. */
    val ongoing: Boolean,
    /** Time from the start of the session to the first reading at or above 50, 80 and 100%. */
    val to50: Long?,
    val to80: Long?,
    val to100: Long?,
    val peakPowerW: Float?,
    val avgPowerW: Float?,
    val peakTempC: Float?,
    val readings: List<Reading>,
) {
    val duration: Long get() = end - start
    val gained: Int get() = endLevel - startLevel
}

object Charging {
    /** Charging sessions found in [readings], newest first. Gaps over 30 minutes split sessions. */
    fun sessions(readings: List<Reading>): List<ChargingSession> {
        val sorted = readings.sortedBy { it.time }
        val out = mutableListOf<ChargingSession>()
        var current = mutableListOf<Reading>()
        var lastPlugged: Reading? = null

        fun flush(ongoing: Boolean) {
            if (current.size >= 2 || (current.size == 1 && ongoing)) out += build(current, ongoing)
            current = mutableListOf()
        }

        for (r in sorted) {
            if (r.plugged) {
                if (lastPlugged != null && r.time - lastPlugged.time > Limits.SESSION_GAP && current.isNotEmpty()) flush(false)
                current += r
                lastPlugged = r
            } else {
                if (current.isNotEmpty()) {
                    // The first unplugged reading closes the session at its level.
                    if (r.time - current.last().time <= Limits.SESSION_GAP && r.level >= current.last().level) current += r
                    flush(false)
                }
                lastPlugged = null
            }
        }
        if (current.isNotEmpty()) flush(current.last().plugged)
        return out.filter { it.gained > 0 || it.ongoing }.reversed()
    }

    private fun build(rs: List<Reading>, ongoing: Boolean): ChargingSession {
        val first = rs.first()
        fun reach(target: Int): Long? =
            if (first.level >= target) null else rs.firstOrNull { it.level >= target }?.let { it.time - first.time }
        val powers = rs.mapNotNull { it.powerW }.filter { it > 0f }
        return ChargingSession(
            start = first.time,
            end = rs.last().time,
            startLevel = first.level,
            endLevel = rs.maxOf { it.level },
            charger = rs.map { it.charger }.firstOrNull { it != Charger.NONE && it != Charger.UNKNOWN } ?: Charger.UNKNOWN,
            ongoing = ongoing,
            to50 = reach(50),
            to80 = reach(80),
            to100 = reach(100),
            peakPowerW = powers.maxOrNull(),
            avgPowerW = if (powers.isEmpty()) null else powers.average().toFloat(),
            peakTempC = rs.mapNotNull { it.tempC }.maxOrNull(),
            readings = rs,
        )
    }
}
