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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alexmaxham.volta.R
import com.alexmaxham.volta.data.Scheme
import com.alexmaxham.volta.data.ThemeMode

/** Roboto Flex, widened and heavy at display size, for big numbers. */
val Display = FontFamily(Font(R.font.volta_display, FontWeight.SemiBold))

/** Roboto Flex at headline size, for screen titles. */
val Headline = FontFamily(Font(R.font.volta_headline, FontWeight.SemiBold))

private val typography = Typography().let { t ->
    t.copy(
        displayLarge = TextStyle(fontFamily = Display, fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-0.04).em),
        displayMedium = TextStyle(fontFamily = Display, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-0.03).em),
        displaySmall = TextStyle(fontFamily = Display, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em),
        headlineLarge = TextStyle(fontFamily = Headline, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
        headlineMedium = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
        headlineSmall = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
fun VoltaTheme(scheme: Scheme, mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val target = Palettes.colors(context, scheme, isDark(mode))
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
