package com.alexmaxham.volta.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayUse(val date: LocalDate, val used: Int, val charged: Int, val hasData: Boolean)

/** A battery cycle: from one observed 100% to the next (or to now). */
data class Cycle(
    val start: Long,
    val end: Long,
    /** True for the first window, when tracking began before a full charge was seen. */
    val partial: Boolean,
    val ongoing: Boolean,
    val used: Int,
    val charged: Int,
)

data class WeekComparison(val thisWeek: Int, val lastWeek: Int, val daysWithData: Int) {
    /** Change versus the previous seven days, or null when last week has no data. */
    val change: Double? get() = if (lastWeek > 0) (thisWeek - lastWeek).toDouble() / lastWeek else null
}

object History {
    /** Points used and gained per local day, oldest first, for the [days] days ending on [today]. */
    fun daily(readings: List<Reading>, today: LocalDate, days: Int, zone: ZoneId): List<DayUse> {
        val sorted = readings.sortedBy { it.time }
        val used = HashMap<LocalDate, Int>()
        val charged = HashMap<LocalDate, Int>()
        val seen = HashSet<LocalDate>()
        for (r in sorted) seen += dateOf(r.time, zone)
        for ((a, b) in sorted.zipWithNext()) {
            val d = dateOf(b.time, zone)
            val delta = b.level - a.level
            if (delta < 0) used.merge(d, -delta, Int::plus) else if (delta > 0) charged.merge(d, delta, Int::plus)
        }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayUse(d, used[d] ?: 0, charged[d] ?: 0, d in seen)
        }
    }

    fun week(readings: List<Reading>, today: LocalDate, zone: ZoneId): WeekComparison {
        val days = daily(readings, today, 14, zone)
        val last = days.take(7)
        val now = days.drop(7)
        return WeekComparison(now.sumOf { it.used }, last.sumOf { it.used }, now.count { it.hasData })
    }

    /** Cycles from one observed 100% to the next, newest first. */
    fun cycles(readings: List<Reading>, now: Long): List<Cycle> {
        val sorted = readings.sortedBy { it.time }
        if (sorted.isEmpty()) return emptyList()
        // A cycle starts when the battery leaves 100%: the last reading at 100 before a drop.
        val starts = mutableListOf<Long>()
        for ((a, b) in sorted.zipWithNext()) if (a.level >= 100 && b.level < 100) starts += a.time
        val bounds = mutableListOf<Long>()
        val partial = starts.isEmpty() || starts.first() > sorted.first().time
        if (partial) bounds += sorted.first().time
        bounds += starts
        val out = mutableListOf<Cycle>()
        for ((i, s) in bounds.withIndex()) {
            val e = bounds.getOrNull(i + 1) ?: now
            val inside = sorted.filter { it.time in s..e }
            var used = 0
            var charged = 0
            for ((a, b) in inside.zipWithNext()) {
                val d = b.level - a.level
                if (d < 0) used -= d else charged += d
            }
            out += Cycle(s, e, partial && i == 0, i == bounds.lastIndex, used, charged)
        }
        return out.reversed()
    }

    fun dateOf(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
