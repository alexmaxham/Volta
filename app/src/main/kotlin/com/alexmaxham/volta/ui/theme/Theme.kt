package com.alexmaxham.volta.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alexmaxham.volta.R
import com.alexmaxham.volta.data.ThemeMode

/**
 * Android Headlines' type: Roboto for headlines and big numbers, and Work Sans for titles,
 * labels and small print, as on its menus and bylines.
 */
val Display = FontFamily.SansSerif

/** Work Sans, for titles and labels. */
val Label = FontFamily(Font(R.font.work_sans_medium, FontWeight.Medium), Font(R.font.work_sans_semibold, FontWeight.SemiBold))

private val typography = Typography().let { t ->
    t.copy(
        displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-0.04).em),
        displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-0.03).em),
        displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em),
        headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
        headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
        headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Label, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Label, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Label, fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontFamily = Label, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = Label, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = Label, fontWeight = FontWeight.Medium),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun VoltaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val target = Palettes.colors(isDark(mode))
    MaterialTheme(colorScheme = target.animated(), typography = typography, shapes = shapes, content = content)
}

/** Cross-fades the main roles when the palette changes. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun a(c: Color): Color = animateColorAsState(c, tween(350), label = "scheme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary), tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), surface = a(surface), onSurface = a(onSurface), onBackground = a(onBackground),
        onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLow = a(surfaceContainerLow), surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh), surfaceContainerHighest = a(surfaceContainerHighest),
        outlineVariant = a(outlineVariant),
    )
}
