package com.alexmaxham.volta.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The tones the palette defines, for one brightness. */
private data class Tones(
    val bg: Long, val s1: Long, val s2: Long,
    val pri: Long, val onPri: Long, val pc: Long, val onPc: Long,
    val sc: Long, val onSc: Long,
    val ter: Long, val tc: Long, val onTc: Long,
    val tx: Long, val mu: Long, val ol: Long,
)

/**
 * Android Headlines' colors: its red, black and charcoal cards with white type in dark, and the
 * same red on white with light grey cards in light. Tertiary is the cool grey of its bylines,
 * used for second lines in charts.
 */
private val lightTones = Tones(
    0xFFFFFFFF, 0xFFF2F2F2, 0xFFE6E6E6, 0xFFE91D20, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410003, 0xFFE8E8E8, 0xFF1A1A1A,
    0xFF5D6670, 0xFFDDE3EA, 0xFF161C22, 0xFF111111, 0xFF5C5F63, 0xFFD0D0D0,
)
private val darkTones = Tones(
    0xFF000000, 0xFF333333, 0xFF424242, 0xFFE91D20, 0xFFFFFFFF, 0xFF5C0B0D, 0xFFFFDAD6, 0xFF3D3D3D, 0xFFFFFFFF,
    0xFFAFB6BE, 0xFF3A3F45, 0xFFE2E6EA, 0xFFFFFFFF, 0xFFAFB6BE, 0xFF4A4A4A,
)

private fun Tones.scheme(dark: Boolean): ColorScheme {
    val c = { v: Long -> Color(v) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(pri), onPrimary = c(onPri),
        primaryContainer = c(pc), onPrimaryContainer = c(onPc),
        secondary = c(pri), onSecondary = c(onPri),
        secondaryContainer = c(sc), onSecondaryContainer = c(onSc),
        tertiary = c(ter), onTertiary = c(if (dark) tc else 0xFFFFFFFF),
        tertiaryContainer = c(tc), onTertiaryContainer = c(onTc),
        background = c(bg), onBackground = c(tx),
        surface = c(bg), onSurface = c(tx),
        surfaceVariant = c(s2), onSurfaceVariant = c(mu),
        surfaceContainerLowest = c(bg), surfaceContainerLow = c(s1),
        surfaceContainer = c(s1), surfaceContainerHigh = c(s2), surfaceContainerHighest = c(s2),
        surfaceBright = c(s2), surfaceDim = c(bg),
        inverseSurface = c(tx), inverseOnSurface = c(bg), inversePrimary = c(pc),
        outline = c(mu), outlineVariant = c(ol),
        surfaceTint = c(pri),
        error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
        onError = if (dark) Color(0xFF690005) else Color.White,
        errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6),
        onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002),
    )
}

object Palettes {
    /** Volta's colors. Dark uses a true-black background. */
    fun colors(dark: Boolean): ColorScheme = if (dark) darkTones.scheme(true) else lightTones.scheme(false)
}
