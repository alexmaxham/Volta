package com.alexmaxham.volta.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/** Material 3 Expressive-style shapes built with graphics-shapes, normalised to a unit square. */
object ExpressiveShapes {
    /** The scalloped hero shape around the battery level. */
    val cookie9: RoundedPolygon = RoundedPolygon.star(9, radius = 1f, innerRadius = 0.86f, rounding = CornerRounding(0.28f), innerRounding = CornerRounding(0.28f)).normalized()

    val sunny: RoundedPolygon = RoundedPolygon.star(12, radius = 1f, innerRadius = 0.9f, rounding = CornerRounding(0.12f)).normalized()

    val clover4: RoundedPolygon = RoundedPolygon.star(4, radius = 1f, innerRadius = 0.7f, rounding = CornerRounding(0.45f), innerRounding = CornerRounding(0.3f)).normalized()

    val circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 12).normalized()

    val pill: RoundedPolygon = RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.36f)).normalized()
}

/** A [Shape] drawing a normalised [RoundedPolygon], optionally rotated. */
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(polygon.toPath().asComposePath().fit(size, rotation))
}

/** A [Shape] part-way through a morph between two polygons. */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.toPath(progress).asComposePath().fit(size, rotation))
}

private fun Path.fit(size: Size, rotation: Float): Path {
    val m = Matrix()
    m.scale(size.width, size.height)
    if (rotation != 0f) {
        m.translate(0.5f, 0.5f)
        m.rotateZ(rotation)
        m.translate(-0.5f, -0.5f)
    }
    transform(m)
    return this
}
