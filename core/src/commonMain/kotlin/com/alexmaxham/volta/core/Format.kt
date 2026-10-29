package com.alexmaxham.volta.core

import kotlin.math.roundToInt

object Format {
    /** "3 h 12 m", "41 min", "<1 min". */
    fun duration(millis: Long): String {
        val minutes = (millis / Time.MINUTE).toInt()
        if (minutes < 1) return "<1 min"
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return if (h >= 48) "${h / 24} d ${h % 24} h" else "$h h ${m.toString().padStart(2, '0')} m"
    }

    /** "1:42:18" stopwatch style. */
    fun clock(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        return "${s / 3600}:${two((s / 60) % 60)}:${two(s % 60)}"
    }

    fun temp(celsius: Float, fahrenheit: Boolean): String =
        if (fahrenheit) localized((celsius * 9f / 5f + 32f).toDouble(), 1) + " °F"
        else localized(celsius.toDouble(), 1) + " °C"

    fun watts(w: Float): String = localized(w.toDouble(), 1) + " W"

    fun rate(perHour: Double): String = localized(perHour, 1) + " pts/h"

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    fun mah(mah: Float): String = localized(mah.roundToInt().toDouble(), 0, grouping = true) + " mAh"

    private fun two(n: Long): String = n.toString().padStart(2, '0')
}
