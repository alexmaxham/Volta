package com.alexmaxham.volta.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Volta's line icons: 24-unit grid, 2-unit round strokes. */
object VIcons {
    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) {
                addPath(
                    pathData = addPathNodes(p),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun solid(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        }.build()

    private const val CIRCLE = "a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"

    val Battery = line("battery", "M9.5 4h5a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3h-5a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M10 2h4")
    val History = line("history", "M4 19V5M4 19h16", "M8 15l3-4 3 2 5-6")
    val Apps = line(
        "apps",
        "M6 4h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M16.5 4a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z",
        "M7.5 13a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z",
        "M15 13h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
    )
    val Lab = line("lab", "M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4a2 2 0 0 0 1.8-3l-5-9V3", "M7.5 15h9")
    val Tune = line("tune", "M4 7h9M17 7h3M4 17h3M11 17h9", "M13 7$CIRCLE", "M7 17$CIRCLE")
    val Share = line("share", "M12 3v12M7 8l5-5 5 5", "M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5")
    val Download = line("download", "M12 4v11M7 10l5 5 5-5", "M5 20h14")
    val Upload = line("upload", "M12 15V4M7 9l5-5 5 5", "M5 20h14")
    val Bolt = line("bolt", "M13 2L4 14h7l-1 8 9-12h-7l1-8z")
    val Moon = line("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Thermo = line("thermo", "M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0z")
    val Heart = line("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val Back = line("back", "M15 6l-6 6 6 6")
    val Chevron = line("chevron", "M9 6l6 6-6 6")
    val Plus = line("plus", "M12 5v14M5 12h14")
    val Check = line("check", "M5 12l5 5 9-10")
    val Close = line("close", "M6 6l12 12M18 6L6 18")
    val Trash = line("trash", "M4 7h16M10 11v6M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3")
    val Bell = line("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    val Phone = line("phone", "M9.5 3h5a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 18.5v-13A2.5 2.5 0 0 1 9.5 3z", "M11 18h2")
    val Compare = line("compare", "M4 18l5-7 4 3 7-9", "M4 12l5-2 4 4 7-3")
    val Edit = line("edit", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
    val Pause = solid("pause", "M7.5 5h3v14h-3a1.5 1.5 0 0 1-1.5-1.5v-11A1.5 1.5 0 0 1 7.5 5z", "M13.5 5h3a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1-1.5 1.5h-3z")
    val Play = solid("play", "M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z")
}
