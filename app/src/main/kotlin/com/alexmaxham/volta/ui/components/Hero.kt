package com.alexmaxham.volta.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexmaxham.volta.ui.theme.ExpressiveShapes
import com.alexmaxham.volta.ui.theme.PolygonShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The battery level inside a scalloped shape, wrapped in a wavy progress ring. While charging
 * the wave flows and the shape turns slowly.
 */
@Composable
fun BatteryHero(
    level: Int,
    charging: Boolean,
    eyebrow: String,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 312.dp,
) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(level / 100f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow), label = "level")
    val motion = if (charging) chargingMotion() else null

    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "Battery $level%, $caption" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            wavyRing(progress, motion?.phase?.value ?: 0f, colors.primary, colors.outlineVariant)
        }
        Box(
            Modifier
                .size(size * 0.86f)
                .graphicsLayer { rotationZ = motion?.turn?.value ?: 0f }
                .clip(cookie)
                .background(colors.primaryContainer),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Row(verticalAlignment = Alignment.Top) {
                Text("$level", style = MaterialTheme.typography.displayLarge, color = colors.onPrimaryContainer)
                Text(
                    "%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(top = 14.dp, start = 2.dp),
                )
            }
            Text(caption, style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
        }
    }
}

private val cookie = PolygonShape(ExpressiveShapes.cookie9)

private class Motion(val phase: State<Float>, val turn: State<Float>)

/** The flowing wave and slow turn shown while charging. Nothing animates on battery. */
@Composable
private fun chargingMotion(): Motion {
    val t = rememberInfiniteTransition(label = "charging")
    val phase = t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    val turn = t.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    return Motion(phase, turn)
}

private fun DrawScope.wavyRing(progress: Float, phase: Float, wave: Color, track: Color) {
    val stroke = 7.dp.toPx()
    val radius = size.minDimension / 2 - stroke - 4.dp.toPx()
    val amp = 3.5.dp.toPx()
    val c = Offset(size.width / 2, size.height / 2)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    val gap = 7f

    if (sweep < 360f - 2 * gap) {
        drawArc(
            color = track,
            startAngle = -90f + sweep + gap,
            sweepAngle = 360f - sweep - 2 * gap,
            useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    if (sweep <= 0.5f) return
    val path = Path()
    val steps = (sweep * 2).toInt().coerceAtLeast(2)
    for (i in 0..steps) {
        val deg = -90f + sweep * i / steps
        val rad = deg * PI.toFloat() / 180f
        val r = radius + amp * sin(28 * (rad + PI.toFloat() / 2) - phase)
        val x = c.x + r * cos(rad)
        val y = c.y + r * sin(rad)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, wave, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
