package com.alexmaxham.volta.core

/** How a reading was collected. */
enum class Source { APP, BACKGROUND, REVIEW }

enum class Charger { NONE, AC, USB, WIRELESS, DOCK, UNKNOWN }

/**
 * One battery sample.
 *
 * [currentMa] is battery-side current, positive while the battery gains charge.
 * [elapsed] and [uptime] are Android's `elapsedRealtime` and `uptimeMillis` at the time of the
 * reading; their difference over an interval is time spent in deep sleep (system suspend).
 */
data class Reading(
    val time: Long,
    val level: Int,
    val plugged: Boolean,
    val charger: Charger = if (plugged) Charger.UNKNOWN else Charger.NONE,
    val tempC: Float? = null,
    val voltageV: Float? = null,
    val currentMa: Int? = null,
    val chargeCounterMah: Float? = null,
    val elapsed: Long? = null,
    val uptime: Long? = null,
    val source: Source = Source.APP,
) {
    /** Net battery power in watts, positive while charging. Null when the phone doesn't report both values. */
    val powerW: Float?
        get() = if (voltageV != null && currentMa != null && voltageV > 0f) voltageV * currentMa / 1000f else null
}

/** A screen turning on (`on = true`) or off at [time]. */
data class ScreenEvent(val time: Long, val on: Boolean)

/** An app moving to the foreground (`resumed = true`) or leaving it. */
data class AppEvent(val time: Long, val pkg: String, val resumed: Boolean)

data class Interval(val start: Long, val end: Long) {
    val length: Long get() = (end - start).coerceAtLeast(0)
    fun overlap(from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)
}

object Time {
    const val SECOND = 1_000L
    const val MINUTE = 60 * SECOND
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
}

object Limits {
    /** Readings further apart than this are treated as a recording gap. */
    const val GAP = 45 * Time.MINUTE

    /** Charging sessions split when readings are further apart than this. */
    const val SESSION_GAP = 30 * Time.MINUTE

    /** Readings are kept for this long. */
    const val RETENTION = 30 * Time.DAY
}
