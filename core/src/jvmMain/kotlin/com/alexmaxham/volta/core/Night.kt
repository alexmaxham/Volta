package com.alexmaxham.volta.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class NightWindow(val start: Long, val end: Long)

data class NightReport(
    val window: NightWindow,
    val startLevel: Int?,
    val endLevel: Int?,
    /** Points lost, or null when charging was seen or the boundaries weren't observed. */
    val drop: Int?,
    val ratePerHour: Double?,
    val chargingSeen: Boolean,
    val lowest: Int?,
    val deepSleep: DeepSleep,
    val screen: ScreenTime,
    /** Share of the night covered by readings no more than [Limits.GAP] apart. */
    val coverage: Double,
    val inProgress: Boolean,
    val readingCount: Int,
) {
    val hasData: Boolean get() = readingCount >= 2
}

object Night {
    /**
     * The night window that ends on [morning]: from [start] the evening before to [end] that
     * morning. When [start] is earlier in the day than [end] both fall on [morning].
     */
    fun window(morning: LocalDate, start: LocalTime, end: LocalTime, zone: ZoneId): NightWindow {
        val endAt = morning.atTime(end).atZone(zone).toInstant().toEpochMilli()
        val startDay = if (start < end) morning else morning.minusDays(1)
        val startAt = startDay.atTime(start).atZone(zone).toInstant().toEpochMilli()
        return NightWindow(startAt, endAt)
    }

    fun report(
        window: NightWindow,
        readings: List<Reading>,
        screenEvents: List<ScreenEvent>,
        now: Long,
    ): NightReport {
        val end = minOf(window.end, now)
        val sorted = readings.sortedBy { it.time }
        val inside = sorted.filter { it.time in window.start..end }
        val before = sorted.lastOrNull { it.time < window.start && window.start - it.time <= Limits.GAP }
        val after = if (window.end <= now) sorted.firstOrNull { it.time > window.end && it.time - window.end <= Limits.GAP } else null
        val edges = listOfNotNull(before) + inside + listOfNotNull(after)

        val startLevel = levelAt(edges, window.start)
        val endLevel = levelAt(edges, end)
        val chargingSeen = inside.any { it.plugged } || edges.zipWithNext().any { (a, b) -> b.level > a.level }
        val drop = if (startLevel != null && endLevel != null && !chargingSeen) startLevel - endLevel else null
        val hours = (end - window.start).toDouble() / Time.HOUR
        var covered = 0L
        for ((a, b) in edges.zipWithNext()) if (b.time - a.time <= Limits.GAP) covered += Interval(a.time, b.time).overlap(window.start, end)

        return NightReport(
            window = window,
            startLevel = startLevel,
            endLevel = endLevel,
            drop = drop,
            ratePerHour = if (drop != null && hours > 0.5) drop / hours else null,
            chargingSeen = chargingSeen,
            lowest = inside.minOfOrNull { it.level },
            deepSleep = SleepClock.measure(edges, window.start, end),
            screen = ScreenTimeline.build(screenEvents, window.start, end),
            coverage = if (end > window.start) covered.toDouble() / (end - window.start) else 0.0,
            inProgress = now < window.end,
            readingCount = inside.size,
        )
    }

    /** Level at [t], interpolated between the nearest readings on either side. */
    fun levelAt(readings: List<Reading>, t: Long): Int? {
        val a = readings.lastOrNull { it.time <= t }
        val b = readings.firstOrNull { it.time >= t }
        return when {
            a != null && b != null && b.time != a.time -> {
                val f = (t - a.time).toDouble() / (b.time - a.time)
                Math.round(a.level + (b.level - a.level) * f).toInt()
            }
            a != null && (b == null || b.time == a.time) && t - a.time <= Limits.GAP -> a.level
            b != null && b.time - t <= Limits.GAP -> b.level
            else -> null
        }
    }
}
