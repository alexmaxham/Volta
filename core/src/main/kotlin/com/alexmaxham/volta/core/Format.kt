package com.alexmaxham.volta.core

import java.util.Locale
import kotlin.math.roundToInt

object Format {
    /** "3 h 12 m", "41 min", "<1 min". */
    fun duration(millis: Long): String {
        val minutes = (millis / Time.MINUTE).toInt()
        if (minutes < 1) return "<1 min"
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return if (h >= 48) "${h / 24} d ${h % 24} h" else "$h h ${"%02d".format(m)} m"
    }

    /** "1:42:18" stopwatch style. */
    fun clock(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }

    fun temp(celsius: Float, fahrenheit: Boolean): String =
        if (fahrenheit) "%.1f °F".format(Locale.getDefault(), celsius * 9f / 5f + 32f)
        else "%.1f °C".format(Locale.getDefault(), celsius)

    fun watts(w: Float): String = "%.1f W".format(Locale.getDefault(), w)

    fun rate(perHour: Double): String = "%.1f pts/h".format(Locale.getDefault(), perHour)

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    fun mah(mah: Float): String = "%,d mAh".format(Locale.getDefault(), mah.roundToInt())
}
