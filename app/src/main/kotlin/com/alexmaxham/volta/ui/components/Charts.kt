package com.alexmaxham.volta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A point on a time chart. */
data class TimePoint(val time: Long, val value: Float)

/**
 * A line over time with a soft fill. Points further apart than [gap] break the line.
 * Values are plotted between [min] and [max].
 */
@Composable
fun TimeLineChart(
    points: List<TimePoint>,
    from: Long,
    to: Long,
    description: String,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 100f,
    gap: Long = 45 * 60_000L,
    line: Color = MaterialTheme.colorScheme.primary,
    fill: Color? = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    height: Dp = 140.dp,
    dashed: Boolean = false,
    showEndDot: Boolean = true,
    grid: Boolean = true,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val pad = 6.dp.toPx()
        fun x(t: Long) = if (to > from) (t - from).toFloat() / (to - from) * w else 0f
        fun y(v: Float) = pad + (1f - ((v - min) / (max - min)).coerceIn(0f, 1f)) * (h - 2 * pad)
        if (grid) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
            for (f in listOf(0.25f, 0.5f, 0.75f)) drawLine(gridColor, Offset(0f, h * f), Offset(w, h * f), 1.dp.toPx(), pathEffect = dash)
        }
        val visible = points.filter { it.time in from..to }.sortedBy { it.time }
        if (visible.isEmpty()) return@Canvas
        val segments = mutableListOf<MutableList<TimePoint>>(mutableListOf())
        for ((i, p) in visible.withIndex()) {
            if (i > 0 && p.time - visible[i - 1].time > gap) segments += mutableListOf<TimePoint>()
            segments.last() += p
        }
        for (seg in segments) {
            if (seg.size < 2) {
                drawCircle(line, 2.5.dp.toPx(), Offset(x(seg[0].time), y(seg[0].value)))
                continue
            }
            val path = Path()
            seg.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.time), y(p.value)) else path.lineTo(x(p.time), y(p.value)) }
            if (fill != null) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(x(seg.last().time), h)
                    lineTo(x(seg.first().time), h)
                    close()
                }
                drawPath(area, fill)
            }
            drawPath(
                path, line,
                style = Stroke(
                    width = if (dashed) 2.5.dp.toPx() else 3.dp.toPx(),
                    cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 6.dp.toPx())) else null,
                ),
            )
        }
        if (showEndDot) {
            val last = visible.last()
            drawCircle(line, 5.dp.toPx(), Offset(x(last.time), y(last.value)))
        }
    }
}

/** Two series over the same time span, each scaled to its own range. */
@Composable
fun DualLineChart(
    a: List<TimePoint>,
    b: List<TimePoint>,
    from: Long,
    to: Long,
    description: String,
    modifier: Modifier = Modifier,
    aColor: Color = MaterialTheme.colorScheme.primary,
    bColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val aMax = (a.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f) * 1.1f
    val bMin = (b.minOfOrNull { it.value } ?: 0f) - 2f
    val bMax = (b.maxOfOrNull { it.value } ?: 1f) + 2f
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        TimeLineChart(a, from, to, "", min = 0f, max = aMax, line = aColor, fill = null, height = 160.dp, showEndDot = false)
        TimeLineChart(b, from, to, "", min = bMin, max = bMax, line = bColor, fill = null, height = 160.dp, dashed = true, showEndDot = false, grid = false)
    }
}

/** Curves keyed by minutes since start, for comparing test runs. */
@Composable
fun CurvesChart(curves: List<Pair<List<Pair<Double, Int>>, Color>>, description: String, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val maxX = curves.flatMap { it.first }.maxOfOrNull { it.first }?.coerceAtLeast(1.0) ?: 1.0
    val minY = (curves.flatMap { it.first }.minOfOrNull { it.second } ?: 0).coerceAtMost(95) - 5
    val maxY = (curves.flatMap { it.first }.maxOfOrNull { it.second } ?: 100) + 2
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        for ((i, pair) in curves.withIndex()) {
            val (pts, color) = pair
            if (pts.size < 2) continue
            val path = Path()
            pts.forEachIndexed { j, (m, lvl) ->
                val x = (m / maxX).toFloat() * w
                val y = (1f - (lvl - minY).toFloat() / (maxY - minY)) * h
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path, color,
                style = Stroke(
                    width = if (i == 0) 3.dp.toPx() else 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (i >= 2) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
        }
    }
}

/** Rounded bars with labels; the last bar is highlighted. */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, description: String, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val c = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val f = (v.toFloat() / max).coerceIn(0.04f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == values.lastIndex) c.primary else c.surfaceContainerHigh),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (i == labels.lastIndex) c.onSurface else c.onSurfaceVariant,
                    fontWeight = if (i == labels.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** A horizontal strip of coloured segments, like a timeline of states. */
@Composable
fun SegmentStrip(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 10.dp, gap: Dp = 3.dp, radius: Dp = 5.dp) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for ((weight, color) in segments) {
            if (weight <= 0f) continue
            Box(Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(radius)).background(color))
        }
    }
}

@Composable
fun AxisLabels(labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (l in labels) Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
