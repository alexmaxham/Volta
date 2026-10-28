package com.alexmaxham.volta.ui.components

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Axis
import com.alexmaxham.volta.core.ChargingSession
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Scale
import com.alexmaxham.volta.core.Time
import java.util.Locale

/** Net power and battery temperature over a charging session, ready to draw. */
class ChargeChartData(session: ChargingSession, val fahrenheit: Boolean) {
    val from = session.start
    val to = maxOf(session.end, session.start + Time.MINUTE)

    // The unplugged reading that closes a session isn't charging power.
    val power: List<TimePoint> = session.readings.filter { it.plugged }
        .mapNotNull { r -> r.powerW?.let { TimePoint(r.time, it.coerceAtLeast(0f)) } }
    val temp: List<TimePoint> = session.readings
        .mapNotNull { r -> r.tempC?.let { TimePoint(r.time, if (fahrenheit) it * 9 / 5 + 32 else it) } }

    val hasPower get() = power.size >= 2
    val hasTemp get() = temp.size >= 2
    val tempUnit get() = if (fahrenheit) "°F" else "°C"

    val powerScale: Scale
    val tempScale: Scale

    init {
        val p = Axis.scale(0f, power.maxOfOrNull { it.value } ?: 10f, 4, fromZero = true)
        val t = Axis.scale(temp.minOfOrNull { it.value } ?: 25f, temp.maxOfOrNull { it.value } ?: 35f, 4)
        val (pp, tt) = Axis.paired(p, t)
        powerScale = pp
        tempScale = tt
    }

    val peak: TimePoint? get() = power.maxByOrNull { it.value }

    fun describe(): String = buildString {
        append("Charging power and battery temperature over ${Format.duration(to - from)}.")
        peak?.let { append(" Peak ${Format.watts(it.value)}.") }
        temp.maxByOrNull { it.value }?.let { append(" Peak temperature ${"%.1f".format(Locale.getDefault(), it.value)} $tempUnit.") }
    }
}

/** Colours and sizes for [ChargeChartPainter]. [unit] is the size of 1 dp in pixels. */
class ChargeChartStyle(
    val power: Int,
    val temp: Int,
    val grid: Int,
    val label: Int,
    val unit: Float,
    val textSize: Float = 11f,
    val typeface: Typeface = Typeface.DEFAULT,
)

/**
 * Draws the charging chart with a watts scale on the left, a temperature scale on the right that
 * shares its gridlines, elapsed time along the bottom and the peak power marked. Used on screen and
 * for exported images.
 */
object ChargeChartPainter {
    fun draw(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, d: ChargeChartData, s: ChargeChartStyle) {
        val u = s.unit
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = s.textSize * u
            color = s.label
            typeface = s.typeface
        }
        val ps = d.powerScale
        val ts = d.tempScale
        fun watts(v: Float) = if (ps.step < 1f) "%.1f".format(Locale.getDefault(), v) else "%.0f".format(Locale.getDefault(), v)
        fun degrees(v: Float) = if (ts.step < 1f) "%.1f".format(Locale.getDefault(), v) else "%.0f".format(Locale.getDefault(), v)

        val leftLabels = if (d.hasPower) ps.ticks.map { watts(it) } else emptyList()
        val rightLabels = if (d.hasTemp) ts.ticks.map { degrees(it) } else emptyList()
        val gap = 8 * u
        val padL = (leftLabels.maxOfOrNull { text.measureText(it) } ?: 0f).let { if (it > 0) it + gap else 0f }
        val padR = (rightLabels.maxOfOrNull { text.measureText(it) } ?: 0f).let { if (it > 0) it + gap else 0f }
        val headerH = text.textSize + 6 * u
        val footerH = text.textSize + 10 * u
        val x0 = left + padL
        val x1 = right - padR
        val y0 = top + headerH
        val y1 = bottom - footerH
        val h = y1 - y0

        // Unit captions above each scale.
        text.textAlign = Paint.Align.LEFT
        if (d.hasPower) c.drawText("W", left, top + text.textSize, text)
        text.textAlign = Paint.Align.RIGHT
        if (d.hasTemp) c.drawText(d.tempUnit, right, top + text.textSize, text)

        // Gridlines and scale labels.
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.grid
            strokeWidth = 1 * u
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(2 * u, 4 * u), 0f)
        }
        val n = ps.intervals
        for (i in 0..n) {
            val y = y1 - h * i / n
            c.drawLine(x0, y, x1, y, grid)
            val mid = y + text.textSize * 0.35f
            if (d.hasPower) {
                text.textAlign = Paint.Align.RIGHT
                c.drawText(leftLabels[i], x0 - gap, mid, text)
            }
            if (d.hasTemp) {
                text.textAlign = Paint.Align.LEFT
                c.drawText(rightLabels[i], x1 + gap, mid, text)
            }
        }

        // Elapsed-time ticks along the bottom.
        val minutes = (d.to - d.from).toFloat() / Time.MINUTE
        val xs = Axis.scale(0f, minutes, 4, fromZero = true)
        text.textAlign = Paint.Align.CENTER
        for (m in xs.ticks) {
            if (m > minutes + 0.01f) break
            val x = x0 + (x1 - x0) * (m / minutes)
            val label = when {
                m == 0f -> "0"
                minutes >= 120 && m % 60f == 0f -> "${(m / 60).toInt()} h"
                else -> "${m.toInt()} min"
            }
            text.textAlign = when {
                x - text.measureText(label) / 2 < x0 -> Paint.Align.LEFT
                x + text.measureText(label) / 2 > x1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            c.drawText(label, x, bottom - 2 * u, text)
        }

        fun px(t: Long) = x0 + (x1 - x0) * ((t - d.from).toFloat() / (d.to - d.from))
        fun line(points: List<TimePoint>, scale: Scale, paint: Paint, fill: Paint?) {
            if (points.size < 2) return
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = px(p.time)
                val y = y1 - h * scale.fraction(p.value).coerceIn(0f, 1f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (fill != null) {
                val area = Path(path)
                area.lineTo(px(points.last().time), y1)
                area.lineTo(px(points.first().time), y1)
                area.close()
                c.drawPath(area, fill)
            }
            c.drawPath(path, paint)
        }

        val powerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.power
            style = Paint.Style.STROKE
            strokeWidth = 3 * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val powerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (s.power and 0x00FFFFFF) or (0x24 shl 24)
            style = Paint.Style.FILL
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.temp
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * u
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(0.1f * u, 6 * u), 0f)
        }
        line(d.temp, ts, tempPaint, null)
        line(d.power, ps, powerPaint, powerFill)

        // Peak power marker.
        d.peak?.takeIf { d.hasPower }?.let { p ->
            val x = px(p.time)
            val y = y1 - h * ps.fraction(p.value)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.power }
            c.drawCircle(x, y, 4.5f * u, dot)
            val label = "Peak " + Format.watts(p.value)
            text.color = s.power
            text.typeface = Typeface.create(s.typeface, Typeface.BOLD)
            text.textAlign = when {
                x - text.measureText(label) / 2 < x0 -> Paint.Align.LEFT
                x + text.measureText(label) / 2 > x1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            c.drawText(label, x, maxOf(y - 10 * u, y0 + text.textSize), text)
        }
    }
}

/** The charging chart on screen. */
@Composable
fun ChargeChart(d: ChargeChartData, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    val cs = MaterialTheme.colorScheme
    val power = cs.primary.toArgb()
    val temp = cs.tertiary.toArgb()
    val grid = cs.outlineVariant.toArgb()
    val label = cs.onSurfaceVariant.toArgb()
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = d.describe() }) {
        val style = ChargeChartStyle(power, temp, grid, label, density)
        drawIntoCanvas { ChargeChartPainter.draw(it.nativeCanvas, 0f, 0f, size.width, size.height, d, style) }
    }
}

/**
 * Draws battery level over time as a discharge curve: a percentage scale on the left, clock times
 * along the bottom and, when known, a strip marking when the screen was on.
 */
object LevelChartPainter {
    fun draw(
        c: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        points: List<TimePoint>,
        from: Long, to: Long,
        screenOn: List<com.alexmaxham.volta.core.Interval>?,
        timeLabel: (Long) -> String,
        s: ChargeChartStyle,
        screenOff: Int = s.grid,
    ) {
        val u = s.unit
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = s.textSize * u
            color = s.label
            typeface = s.typeface
        }
        val lo = points.minOfOrNull { it.value } ?: 0f
        val hi = points.maxOfOrNull { it.value } ?: 100f
        val raw = Axis.scale(lo, hi, 4)
        val scale = Scale(raw.min.coerceAtLeast(0f), raw.max.coerceAtMost(100f).coerceAtLeast(raw.min + raw.step), raw.step)
        val labels = scale.ticks.map { "${it.toInt()}%" }
        val gap = 8 * u
        val padL = labels.maxOf { text.measureText(it) } + gap
        val stripH = if (screenOn != null) 8 * u else 0f
        val footerH = text.textSize + 10 * u + if (screenOn != null) stripH + 8 * u else 0f
        val x0 = left + padL
        val x1 = right
        val y0 = top + text.textSize / 2
        val y1 = bottom - footerH
        val h = y1 - y0
        val span = (to - from).coerceAtLeast(1)
        fun px(t: Long) = x0 + (x1 - x0) * ((t - from).toFloat() / span)

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.grid
            strokeWidth = 1 * u
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(2 * u, 4 * u), 0f)
        }
        val n = scale.intervals.coerceAtLeast(1)
        text.textAlign = Paint.Align.RIGHT
        for (i in 0..n) {
            val y = y1 - h * i / n
            c.drawLine(x0, y, x1, y, grid)
            labels.getOrNull(i)?.let { c.drawText(it, x0 - gap, y + text.textSize * 0.35f, text) }
        }

        val visible = points.filter { it.time in from..to }.sortedBy { it.time }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.power
            style = Paint.Style.STROKE
            strokeWidth = 3 * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (s.power and 0x00FFFFFF) or (0x2E shl 24) }
        fun py(v: Float) = y1 - h * scale.fraction(v).coerceIn(0f, 1f)
        if (visible.size >= 2) {
            // Break the line across recording gaps.
            val segments = mutableListOf(mutableListOf<TimePoint>())
            for ((i, p) in visible.withIndex()) {
                if (i > 0 && p.time - visible[i - 1].time > com.alexmaxham.volta.core.Limits.GAP) segments += mutableListOf<TimePoint>()
                segments.last() += p
            }
            for (seg in segments.filter { it.size >= 2 }) {
                val path = Path()
                seg.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.time), py(p.value)) else path.lineTo(px(p.time), py(p.value)) }
                val area = Path(path).apply {
                    lineTo(px(seg.last().time), y1)
                    lineTo(px(seg.first().time), y1)
                    close()
                }
                c.drawPath(area, fill)
                c.drawPath(path, line)
            }
            val last = visible.last()
            c.drawCircle(px(last.time), py(last.value), 5 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.power })
        }

        if (screenOn != null) {
            val top2 = y1 + 8 * u
            val r = stripH / 2
            val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = screenOff }
            val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.temp }
            c.drawRoundRect(x0, top2, x1, top2 + stripH, r, r, off)
            for (iv in screenOn) {
                val a = px(maxOf(iv.start, from))
                val b = px(minOf(iv.end, to))
                if (b - a >= 1f) c.drawRoundRect(a, top2, maxOf(b, a + stripH), top2 + stripH, r, r, on)
            }
        }

        text.textAlign = Paint.Align.LEFT
        c.drawText(timeLabel(from), x0, bottom - 2 * u, text)
        text.textAlign = Paint.Align.CENTER
        c.drawText(timeLabel(from + span / 2), (x0 + x1) / 2, bottom - 2 * u, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText(timeLabel(to), x1, bottom - 2 * u, text)
    }
}

/** The discharge curve on screen. Screen-on time is marked in the tertiary colour. */
@Composable
fun LevelChart(
    points: List<TimePoint>,
    from: Long,
    to: Long,
    screenOn: List<com.alexmaxham.volta.core.Interval>?,
    timeLabel: (Long) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val cs = MaterialTheme.colorScheme
    val style = ChargeChartStyle(cs.primary.toArgb(), cs.tertiary.toArgb(), cs.outlineVariant.toArgb(), cs.onSurfaceVariant.toArgb(), 1f)
    val off = cs.surfaceContainerHigh.toArgb()
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val st = ChargeChartStyle(style.power, style.temp, style.grid, style.label, density)
        drawIntoCanvas { LevelChartPainter.draw(it.nativeCanvas, 0f, 0f, size.width, size.height, points, from, to, screenOn, timeLabel, st, off) }
    }
}
