package com.alexmaxham.volta.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A fold or hinge reported by the window, in window pixels. [vertical] folds run top to bottom
 * (book posture); [halfOpened] is true while the device is partly folded.
 */
data class Fold(val left: Int, val top: Int, val right: Int, val bottom: Int, val vertical: Boolean, val halfOpened: Boolean)

/** How much room the window has, read once at the root and shared with every screen. */
data class VoltaLayout(val width: Dp = 0.dp, val fold: Fold? = null) {
    /** Tablets, unfolded foldables and phones in landscape use a side rail instead of the bottom bar. */
    val rail: Boolean get() = width >= 600.dp

    /** A fold running top to bottom; two-pane screens split along it. */
    val bookFold: Fold? get() = fold?.takeIf { it.vertical }

    /** Half folded with the hinge across the screen, standing like a laptop. */
    val tabletop: Fold? get() = fold?.takeIf { !it.vertical && it.halfOpened }
}

val LocalVoltaLayout = compositionLocalOf { VoltaLayout() }

/** True inside the panes of a screen laid out side by side. */
val LocalTwoPane = compositionLocalOf { false }
