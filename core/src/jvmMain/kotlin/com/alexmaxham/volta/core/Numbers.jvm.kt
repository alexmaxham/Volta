package com.alexmaxham.volta.core

import java.util.Locale

internal actual fun fixed(value: Double, decimals: Int): String = "%.${decimals}f".format(Locale.US, value)

internal actual fun localized(value: Double, decimals: Int, grouping: Boolean): String =
    (if (grouping) "%,.${decimals}f" else "%.${decimals}f").format(Locale.getDefault(), value)
