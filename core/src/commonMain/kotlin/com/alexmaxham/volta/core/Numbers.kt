package com.alexmaxham.volta.core

/** [value] with [decimals] places and a dot, the same in every language, for CSV. */
internal expect fun fixed(value: Double, decimals: Int): String

internal fun fixed(value: Float, decimals: Int): String = fixed(value.toDouble(), decimals)

/** [value] with [decimals] places in the phone's language, with thousands separators when [grouping]. */
internal expect fun localized(value: Double, decimals: Int, grouping: Boolean = false): String
