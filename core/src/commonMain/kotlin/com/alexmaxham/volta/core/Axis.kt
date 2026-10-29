package com.alexmaxham.volta.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** A chart scale with round tick values from [min] to [max]. */
data class Scale(val min: Float, val max: Float, val step: Float) {
    val ticks: List<Float> get() = (0..intervals).map { min + it * step }
    val intervals: Int get() = ((max - min) / step).roundToInt()

    /** Position of [v] between 0 (at [min]) and 1 (at [max]). */
    fun fraction(v: Float): Float = if (max > min) (v - min) / (max - min) else 0f
}

object Axis {
    /** A step of 1, 2, 2.5 or 5 times a power of ten close to [raw]. */
    fun niceStep(raw: Double): Double {
        if (raw <= 0.0) return 1.0
        val mag = 10.0.pow(floor(log10(raw)))
        val f = raw / mag
        val nice = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 2.5 -> 2.5
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * mag
    }

    /** A scale covering [lo, hi] in about [target] intervals of a round step. */
    fun scale(lo: Float, hi: Float, target: Int = 4, fromZero: Boolean = false): Scale {
        var a = if (fromZero) 0.0 else lo.toDouble()
        var b = hi.toDouble()
        if (b <= a) {
            b = a + 1.0
            if (!fromZero) a -= 1.0
        }
        val step = niceStep((b - a) / target)
        val min = floor(a / step) * step
        val max = ceil(b / step) * step
        return Scale(min.toFloat(), max.toFloat(), step.toFloat())
    }

    /**
     * Two scales with the same number of intervals, so their gridlines line up: the scale with
     * fewer intervals is extended upward.
     */
    fun paired(left: Scale, right: Scale): Pair<Scale, Scale> {
        val n = maxOf(left.intervals, right.intervals)
        fun grow(s: Scale) = s.copy(max = s.min + n * s.step)
        return grow(left) to grow(right)
    }
}
