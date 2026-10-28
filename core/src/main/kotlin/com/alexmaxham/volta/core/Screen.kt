package com.alexmaxham.volta.core

/** Screen-on and screen-off time inside a window, rebuilt from Android's screen events. */
data class ScreenTime(
    val on: Long,
    val off: Long,
    val unclassified: Long,
    val wakes: Int,
    val onIntervals: List<Interval>,
) {
    val total: Long get() = on + off + unclassified
}

object ScreenTimeline {
    /**
     * Splits [from, to) into screen-on and screen-off time.
     *
     * Before the first event the state is the opposite of that event. With no events at all the
     * window takes [stateAtEnd] when it is known, otherwise the whole window is unclassified.
     */
    fun build(events: List<ScreenEvent>, from: Long, to: Long, stateAtEnd: Boolean? = null): ScreenTime {
        if (to <= from) return ScreenTime(0, 0, 0, 0, emptyList())
        val sorted = events.filter { it.time < to }.sortedBy { it.time }
        val inWindow = sorted.filter { it.time >= from }
        val before = sorted.lastOrNull { it.time < from }

        var state: Boolean? = when {
            before != null -> before.on
            inWindow.isNotEmpty() -> !inWindow.first().on
            else -> stateAtEnd
        }
        var cursor = from
        var on = 0L
        var off = 0L
        var unknown = 0L
        var wakes = 0
        val intervals = mutableListOf<Interval>()

        fun close(until: Long) {
            val span = until - cursor
            if (span <= 0) return
            when (state) {
                true -> {
                    on += span
                    intervals += Interval(cursor, until)
                }
                false -> off += span
                null -> unknown += span
            }
        }

        for (e in inWindow) {
            if (e.on == state) continue
            close(e.time)
            if (e.on) wakes++
            state = e.on
            cursor = e.time
        }
        close(to)
        return ScreenTime(on, off, unknown, wakes, mergeAdjacent(intervals))
    }

    /** Fraction of [from, to) the screen was on, given on-intervals. */
    fun onFraction(onIntervals: List<Interval>, from: Long, to: Long): Double {
        if (to <= from) return 0.0
        val covered = onIntervals.sumOf { it.overlap(from, to) }
        return covered.toDouble() / (to - from)
    }

    private fun mergeAdjacent(list: List<Interval>): List<Interval> {
        val out = mutableListOf<Interval>()
        for (i in list) {
            val last = out.lastOrNull()
            if (last != null && i.start <= last.end) out[out.size - 1] = Interval(last.start, maxOf(last.end, i.end))
            else out += i
        }
        return out
    }
}
