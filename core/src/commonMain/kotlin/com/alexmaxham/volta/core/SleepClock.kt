package com.alexmaxham.volta.core

/**
 * Deep sleep (system suspend) measured from the gap between Android's elapsed-realtime clock,
 * which keeps counting while suspended, and the uptime clock, which stops.
 */
data class DeepSleep(
    /** Time suspended across measured intervals. */
    val asleep: Long,
    /** Wall time covered by usable clock pairs. */
    val covered: Long,
    /** Length of the window that was asked about. */
    val window: Long,
) {
    val coverage: Double get() = if (window > 0) covered.toDouble() / window else 0.0
    val fraction: Double get() = if (covered > 0) asleep.toDouble() / covered else 0.0
}

object SleepClock {
    fun measure(readings: List<Reading>, from: Long, to: Long): DeepSleep {
        val clocked = readings.filter { it.elapsed != null && it.uptime != null }.sortedBy { it.time }
        var asleep = 0L
        var covered = 0L
        for ((a, b) in clocked.zipWithNext()) {
            if (b.time <= from || a.time >= to) continue
            val dElapsed = b.elapsed!! - a.elapsed!!
            val dUptime = b.uptime!! - a.uptime!!
            // A reboot resets both clocks; the wall clock can also be changed by the user.
            if (dElapsed <= 0 || dUptime < 0 || dUptime > dElapsed) continue
            val dWall = b.time - a.time
            if (dWall <= 0 || kotlin.math.abs(dWall - dElapsed) > 2 * Time.MINUTE) continue
            val share = Interval(a.time, b.time).overlap(from, to).toDouble() / dWall
            asleep += ((dElapsed - dUptime) * share).toLong()
            covered += (dWall * share).toLong()
        }
        return DeepSleep(asleep, covered, (to - from).coerceAtLeast(0))
    }
}
