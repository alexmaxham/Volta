package com.alexmaxham.volta.core

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.currentLocale

// Half-up rounding, as Java's formatter does, so CSVs match the Android app's.
private fun format(value: Double, decimals: Int, grouping: Boolean, locale: NSLocale): String {
    val f = NSNumberFormatter()
    f.locale = locale
    f.numberStyle = NSNumberFormatterDecimalStyle
    f.usesGroupingSeparator = grouping
    f.minimumFractionDigits = decimals.toULong()
    f.maximumFractionDigits = decimals.toULong()
    f.roundingMode = NSNumberFormatterRoundHalfUp
    return f.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}

internal actual fun fixed(value: Double, decimals: Int): String =
    format(value, decimals, false, NSLocale(localeIdentifier = "en_US_POSIX"))

internal actual fun localized(value: Double, decimals: Int, grouping: Boolean): String =
    format(value, decimals, grouping, NSLocale.currentLocale)
