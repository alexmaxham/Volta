package com.alexmaxham.volta.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.alexmaxham.volta.data.Scheme

/** The tones each curated palette defines, for one brightness. */
private data class Tones(
    val bg: Long, val s1: Long, val s2: Long,
    val pri: Long, val onPri: Long, val pc: Long, val onPc: Long,
    val sc: Long, val onSc: Long,
    val ter: Long, val tc: Long, val onTc: Long,
    val tx: Long, val mu: Long, val ol: Long,
)

private class Palette(val light: Tones, val dark: Tones)

private val palettes = mapOf(
    Scheme.BLOOM to Palette(
        Tones(0xFFFFF8F7, 0xFFFBEEEF, 0xFFF4E2E4, 0xFFA23F5A, 0xFFFFFFFF, 0xFFFFD9DF, 0xFF3F0018, 0xFFF5DDE1, 0xFF2B1519, 0xFF7A5733, 0xFFFFDDBC, 0xFF2C1700, 0xFF22191B, 0xFF5B4A4D, 0xFFE2CDD0),
        Tones(0xFF000000, 0xFF161213, 0xFF221C1D, 0xFFFFB1C1, 0xFF5E1129, 0xFF7F2843, 0xFFFFD9DF, 0xFF3A2C2F, 0xFFF5DDE1, 0xFFECBD92, 0xFF5D4020, 0xFFFFDDBC, 0xFFF2DFE1, 0xFFD3C0C3, 0xFF3B3133),
    ),
    Scheme.SAGE to Palette(
        Tones(0xFFF6FAF2, 0xFFEDF2E8, 0xFFE3EADD, 0xFF3B6934, 0xFFFFFFFF, 0xFFBCF0AE, 0xFF002203, 0xFFD7E8CD, 0xFF121F0E, 0xFF386568, 0xFFBCEBEE, 0xFF002022, 0xFF181D16, 0xFF42493F, 0xFFC9D3C2),
        Tones(0xFF000000, 0xFF121510, 0xFF1C211A, 0xFFA1D494, 0xFF0A390A, 0xFF23501E, 0xFFBCF0AE, 0xFF2B3427, 0xFFD7E8CD, 0xFFA0CFD2, 0xFF1E4D50, 0xFFBCEBEE, 0xFFE0E4DA, 0xFFC1C9BA, 0xFF2F362C),
    ),
    Scheme.OCEAN to Palette(
        Tones(0xFFF8F9FF, 0xFFEDF0F9, 0xFFE2E6F1, 0xFF3A5F94, 0xFFFFFFFF, 0xFFD6E3FF, 0xFF001B3E, 0xFFDAE2F9, 0xFF131C2B, 0xFF00687A, 0xFFACEDFF, 0xFF001F26, 0xFF191C20, 0xFF44474E, 0xFFCBD0DD),
        Tones(0xFF000000, 0xFF111318, 0xFF1C2027, 0xFFAAC7FF, 0xFF0A305F, 0xFF264777, 0xFFD6E3FF, 0xFF283041, 0xFFDAE2F9, 0xFF84D2E5, 0xFF004E5C, 0xFFACEDFF, 0xFFE2E2E9, 0xFFC4C6D0, 0xFF2E323B),
    ),
    Scheme.EMBER to Palette(
        Tones(0xFFFFF8F3, 0xFFFAEEE4, 0xFFF3E3D6, 0xFF8A5100, 0xFFFFFFFF, 0xFFFFDCBE, 0xFF2C1600, 0xFFF6DFCB, 0xFF261A0E, 0xFF5D6234, 0xFFE3E8AE, 0xFF1A1D00, 0xFF211A13, 0xFF52443A, 0xFFDEC9B9),
        Tones(0xFF000000, 0xFF18120D, 0xFF231B15, 0xFFFFB871, 0xFF4A2800, 0xFF6A3C00, 0xFFFFDCBE, 0xFF3B2E24, 0xFFF6DFCB, 0xFFC7CC94, 0xFF454A1F, 0xFFE3E8AE, 0xFFEDE0D7, 0xFFD6C3B5, 0xFF3A3029),
    ),
    Scheme.IRIS to Palette(
        Tones(0xFFFDF7FF, 0xFFF5EFF8, 0xFFECE6F1, 0xFF65558F, 0xFFFFFFFF, 0xFFE9DDFF, 0xFF21005D, 0xFFE8DEF8, 0xFF1D192B, 0xFF7E5260, 0xFFFFD9E3, 0xFF31101D, 0xFF1D1B20, 0xFF49454F, 0xFFD6D0DC),
        Tones(0xFF000000, 0xFF141218, 0xFF201E25, 0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF, 0xFF332D41, 0xFFE8DEF8, 0xFFEFB8C8, 0xFF633B48, 0xFFFFD9E3, 0xFFE6E0E9, 0xFFCAC4D0, 0xFF36343B),
    ),
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
    /** The color scheme for [scheme]. Dark themes use a true-black background with dim cards. */
    fun colors(context: Context, scheme: Scheme, dark: Boolean): ColorScheme {
        if (scheme == Scheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return if (dark) d.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black, surfaceDim = Color.Black) else d
        }
        val p = palettes[scheme] ?: palettes.getValue(Scheme.BLOOM)
        return if (dark) p.dark.scheme(true) else p.light.scheme(false)
    }

    /** Swatch colors for the picker: container, primary, tertiary container. */
    fun swatch(context: Context, scheme: Scheme): Triple<Color, Color, Color> {
        val c = colors(context, scheme, false)
        return Triple(c.primaryContainer, c.primary, c.tertiaryContainer)
    }

    val curated = listOf(Scheme.BLOOM, Scheme.SAGE, Scheme.OCEAN, Scheme.EMBER, Scheme.IRIS)
}
