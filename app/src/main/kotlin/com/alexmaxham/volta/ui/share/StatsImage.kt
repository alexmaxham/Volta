package com.alexmaxham.volta.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.alexmaxham.volta.R
import com.alexmaxham.volta.core.BenchReport
import com.alexmaxham.volta.core.ChargingSession
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.data.Range
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.components.ChargeChartData
import com.alexmaxham.volta.ui.components.ChargeChartPainter
import com.alexmaxham.volta.ui.components.ChargeChartStyle
import com.alexmaxham.volta.ui.components.ConnectedButtons
import com.alexmaxham.volta.ui.components.LevelChartPainter
import com.alexmaxham.volta.ui.components.TimePoint
import com.alexmaxham.volta.ui.screens.Fmt
import com.alexmaxham.volta.ui.screens.chargerName
import java.io.File

/** Shapes of exported images. Square and 16:9 suit reviews; portrait suits phone screens. */
enum class ExportFormat(val label: String, val width: Int, val height: Int) {
    SQUARE("Square", 1080, 1080),
    WIDE("16:9", 1920, 1080),
    PORTRAIT("Portrait", 1080, 1350),
}

/** "Google Pixel 9 Pro · Android 16". */
fun deviceLine(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    return "$model · Android ${Build.VERSION.RELEASE}"
}

/** Paints, fonts and drawing helpers for an export canvas in the current palette. */
private class Ink(context: Context, val colors: ColorScheme, val c: Canvas, val scale: Float) {
    val display: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.volta_display) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    val headline: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.volta_headline) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun text(s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = Typeface.DEFAULT, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f): Float {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        p.typeface = face
        p.textSize = size * scale
        p.letterSpacing = spacing
        p.textAlign = align
        c.drawText(s, x, y, p)
        return p.measureText(s)
    }

    fun measure(s: String, size: Float, face: Typeface): Float {
        p.reset()
        p.typeface = face
        p.textSize = size * scale
        return p.measureText(s)
    }

    fun box(r: RectF, radius: Float, color: Int) {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        c.drawRoundRect(r, radius * scale, radius * scale, p)
    }

    /** A labelled stat tile. */
    fun tile(r: RectF, label: String, value: String, bg: Int, fg: Int) {
        box(r, 28f, bg)
        val pad = 22f * scale
        text(label, r.left + pad, r.top + pad + 22f * scale, 22f, fg)
        var size = 44f
        while (size > 24f && measure(value, size, display) > r.width() - 2 * pad) size -= 2f
        text(value, r.left + pad, r.bottom - pad, size, fg, display)
    }

    fun legend(x: Float, y: Float, color: Int, label: String): Float {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        c.drawRoundRect(x, y - 9f * scale, x + 22f * scale, y - 3f * scale, 3f * scale, 3f * scale, p)
        return 30f * scale + text(label, x + 30f * scale, y, 20f, colors.onSurfaceVariant.toArgb())
    }
}

/** Lays out tiles in a grid inside [area]. */
private fun grid(area: RectF, count: Int, cols: Int, gap: Float): List<RectF> {
    val rows = (count + cols - 1) / cols
    val w = (area.width() - gap * (cols - 1)) / cols
    val h = (area.height() - gap * (rows - 1)) / rows
    return (0 until count).map { i ->
        val col = i % cols
        val row = i / cols
        RectF(area.left + col * (w + gap), area.top + row * (h + gap), area.left + col * (w + gap) + w, area.top + row * (h + gap) + h)
    }
}

object ExportImages {
    /** A charging session with its milestones, chart and stats, for reviews. */
    fun session(context: Context, cs: ChargingSession, s: VoltaSettings, colors: ColorScheme, format: ExportFormat): Bitmap {
        val bmp = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ink = Ink(context, colors, c, 1f)
        c.drawColor(colors.background.toArgb())
        val zone = java.time.ZoneId.systemDefault()
        val m = 64f
        val wide = format == ExportFormat.WIDE
        val tx = colors.onSurface.toArgb()
        val mu = colors.onSurfaceVariant.toArgb()

        // Header
        ink.text("VOLTA · CHARGING SESSION", m, m + 20f, 22f, mu, ink.bold, spacing = 0.16f)
        ink.text(deviceLine(), m, m + 58f, 26f, tx, ink.headline)
        val range = Fmt.whenAt(cs.start, System.currentTimeMillis(), zone) + " – " + if (cs.ongoing) "now" else Fmt.time(cs.end, zone)
        ink.text(range, m, m + 92f, 22f, mu)

        val leftW = if (wide) 760f else format.width - 2 * m
        val heroY = m + 210f
        val heroSize = if (wide) 120f else 110f
        val w1 = ink.text("${cs.startLevel} → ${cs.endLevel}", m - 4f, heroY, heroSize, tx, ink.display)
        ink.text("%", m + w1 + 8f, heroY, 44f, mu, ink.headline)
        ink.text(listOf(Format.duration(cs.duration), chargerName(cs.charger)).joinToString(" · ") + if (cs.ongoing) " · still charging" else "", m, heroY + 48f, 24f, mu)

        val tiles = listOf(
            Triple("to 50%", cs.to50?.let { Format.duration(it) } ?: "—", true),
            Triple("to 80%", cs.to80?.let { Format.duration(it) } ?: "—", false),
            Triple("to 100%", cs.to100?.let { Format.duration(it) } ?: "—", false),
            Triple("Peak power", cs.peakPowerW?.let { Format.watts(it) } ?: "—", false),
            Triple("Average", cs.avgPowerW?.let { Format.watts(it) } ?: "—", false),
            Triple("Peak temp", cs.peakTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—", false),
        )
        val pc = colors.primaryContainer.toArgb()
        val onPc = colors.onPrimaryContainer.toArgb()
        val s1 = colors.surfaceContainerLow.toArgb()
        val chartRect: RectF
        if (wide) {
            val area = RectF(m, heroY + 90f, m + leftW, format.height - m - 40f)
            grid(area, tiles.size, 2, 14f).forEachIndexed { i, r -> ink.tile(r, tiles[i].first, tiles[i].second, if (tiles[i].third) pc else s1, if (tiles[i].third) onPc else tx) }
            chartRect = RectF(m + leftW + 40f, m, format.width - m, format.height - m - 40f)
        } else {
            val tilesH = if (format == ExportFormat.SQUARE) 250f else 300f
            val area = RectF(m, format.height - m - 40f - tilesH, format.width - m, format.height - m - 40f)
            grid(area, tiles.size, 3, 12f).forEachIndexed { i, r -> ink.tile(r, tiles[i].first, tiles[i].second, if (tiles[i].third) pc else s1, if (tiles[i].third) onPc else tx) }
            chartRect = RectF(m, heroY + 84f, format.width - m, area.top - 18f)
        }

        // Chart card
        ink.box(chartRect, 36f, s1)
        val pad = 32f
        ink.text("Charging speed & temperature", chartRect.left + pad, chartRect.top + pad + 26f, 28f, tx, ink.headline)
        val d = ChargeChartData(cs, s.fahrenheit)
        var lx = chartRect.left + pad
        val ly = chartRect.top + pad + 66f
        lx += ink.legend(lx, ly, colors.primary.toArgb(), "Net power (W)") + 24f
        ink.legend(lx, ly, colors.tertiary.toArgb(), "Battery temp (${d.tempUnit})")
        if (d.hasPower || d.hasTemp) {
            ChargeChartPainter.draw(
                c, chartRect.left + pad, ly + 24f, chartRect.right - pad, chartRect.bottom - pad, d,
                ChargeChartStyle(colors.primary.toArgb(), colors.tertiary.toArgb(), colors.outlineVariant.toArgb(), mu, 2f, 10f),
            )
        } else {
            ink.text("This phone doesn't report current and voltage together.", chartRect.left + pad, ly + 60f, 22f, mu)
        }

        ink.text("Recorded with Volta · net battery power from battery-side readings, not charger output", m, format.height - m + 6f, 18f, colors.primary.toArgb())
        return bmp
    }

    /** Today's discharge: battery curve, screen time and drain for the selected window. */
    fun stats(context: Context, snap: Snapshot, live: Reading?, s: VoltaSettings, colors: ColorScheme, format: ExportFormat): Bitmap {
        val bmp = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ink = Ink(context, colors, c, 1f)
        c.drawColor(colors.background.toArgb())
        val m = 64f
        val wide = format == ExportFormat.WIDE
        val tx = colors.onSurface.toArgb()
        val mu = colors.onSurfaceVariant.toArgb()
        val win = if (s.range == Range.SINCE_FULL) snap.sinceFull else snap.sinceMidnight
        val sinceFull = s.range == Range.SINCE_FULL && snap.cycleStart != null

        ink.text("VOLTA", m, m + 20f, 22f, mu, ink.bold, spacing = 0.18f)
        ink.text(deviceLine(), m, m + 58f, 26f, tx, ink.headline)
        ink.text(Fmt.longDay(snap.now, snap.zone), m, m + 92f, 22f, mu)

        // Current level as a pill in the corner.
        val current = live ?: snap.readings.lastOrNull()
        if (current != null) {
            val label = "${current.level}% · " + Fmt.estimate(snap.estimate, current.plugged)
            val w = ink.measure(label, 24f, ink.headline) + 48f
            val r = RectF(format.width - m - w, m, format.width - m, m + 60f)
            ink.box(r, 30f, colors.primaryContainer.toArgb())
            ink.text(label, r.centerX(), r.centerY() + 9f, 24f, colors.onPrimaryContainer.toArgb(), ink.headline, Paint.Align.CENTER)
        }

        // The discharge headline.
        val heroY = m + 210f
        val used = "−${win.usage.used}"
        val w1 = ink.text(used, m - 4f, heroY, 110f, tx, ink.display)
        ink.text("pts", m + w1 + 12f, heroY, 40f, mu, ink.headline)
        ink.text(
            (if (sinceFull) "used since unplugged at ${Fmt.time(win.from, snap.zone)}" else if (s.range == Range.SINCE_FULL) "used since tracking began" else "used since midnight") +
                " · " + Format.duration(win.to - win.from),
            m, heroY + 46f, 24f, mu,
        )

        val tiles = listOf(
            "Screen on" to if (snap.usageAccess) Format.duration(win.screen.on) else "—",
            "Screen off" to if (snap.usageAccess) Format.duration(win.screen.off) else "—",
            "Deep sleep" to if (win.sleep.coverage > 0.2) Format.duration(win.sleep.asleep) else "—",
            "Screen-on drain" to (win.usage.screenOn.perHour?.let { Format.rate(it) } ?: "—"),
            "Screen-off drain" to (win.usage.screenOff.perHour?.let { Format.rate(it) } ?: "—"),
            "Temperature" to ((live?.tempC ?: snap.readings.lastOrNull()?.tempC)?.let { Format.temp(it, s.fahrenheit) } ?: "—"),
        )
        val s1 = colors.surfaceContainerLow.toArgb()
        val pc = colors.primaryContainer.toArgb()
        val onPc = colors.onPrimaryContainer.toArgb()
        val chartRect: RectF
        if (wide) {
            val area = RectF(m, heroY + 90f, m + 760f, format.height - m - 40f)
            grid(area, tiles.size, 2, 14f).forEachIndexed { i, r -> ink.tile(r, tiles[i].first, tiles[i].second, if (i == 0) pc else s1, if (i == 0) onPc else tx) }
            chartRect = RectF(m + 800f, m + 84f, format.width - m, format.height - m - 40f)
        } else {
            val tilesH = if (format == ExportFormat.SQUARE) 250f else 320f
            val area = RectF(m, format.height - m - 40f - tilesH, format.width - m, format.height - m - 40f)
            grid(area, tiles.size, 3, 12f).forEachIndexed { i, r -> ink.tile(r, tiles[i].first, tiles[i].second, if (i == 0) pc else s1, if (i == 0) onPc else tx) }
            chartRect = RectF(m, heroY + 84f, format.width - m, area.top - 18f)
        }

        ink.box(chartRect, 36f, s1)
        val pad = 32f
        ink.text("Battery level", chartRect.left + pad, chartRect.top + pad + 26f, 28f, tx, ink.headline)
        val ly = chartRect.top + pad + 66f
        val lx = chartRect.left + pad
        val shown = snap.usageAccess && win.screen.total > 0
        val legendW = ink.legend(lx, ly, colors.primary.toArgb(), "Battery %")
        if (shown) ink.legend(lx + legendW + 24f, ly, colors.tertiary.toArgb(), "Screen on")
        val points = snap.readings.filter { it.time >= win.from }.map { TimePoint(it.time, it.level.toFloat()) } +
            listOfNotNull(live?.let { TimePoint(snap.now, it.level.toFloat()) })
        LevelChartPainter.draw(
            c, chartRect.left + pad, ly + 24f, chartRect.right - pad, chartRect.bottom - pad,
            points, win.from, snap.now, if (shown) win.screen.onIntervals else null,
            { Fmt.time(it, snap.zone) },
            ChargeChartStyle(colors.primary.toArgb(), colors.tertiary.toArgb(), colors.outlineVariant.toArgb(), mu, 2f, 10f),
            colors.surfaceContainerHighest.toArgb(),
        )

        ink.text("A little battery clarity · Volta", m, format.height - m + 6f, 18f, colors.primary.toArgb())
        return bmp
    }

    /** An app benchmark: how long the battery lasted and the drain for each app, as bars. */
    fun bench(context: Context, run: ReviewRun, rep: BenchReport, s: VoltaSettings, colors: ColorScheme, format: ExportFormat): Bitmap {
        val bmp = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ink = Ink(context, colors, c, 1f)
        c.drawColor(colors.background.toArgb())
        val zone = java.time.ZoneId.systemDefault()
        val m = 64f
        val tx = colors.onSurface.toArgb()
        val mu = colors.onSurfaceVariant.toArgb()
        val s1 = colors.surfaceContainerLow.toArgb()

        ink.text("VOLTA · APP BENCHMARK", m, m + 20f, 22f, mu, ink.bold, spacing = 0.16f)
        ink.text(deviceLine(), m, m + 58f, 26f, tx, ink.headline)
        ink.text(listOf(Fmt.longDay(run.started, zone), run.conditions.brightness).filter { it.isNotBlank() }.joinToString(" · "), m, m + 92f, 22f, mu)

        val heroY = m + 210f
        ink.text(Format.duration(rep.elapsed), m - 4f, heroY, 104f, tx, ink.display)
        val levels = if (rep.startLevel != null) "${rep.startLevel}% → ${rep.endLevel}%" else ""
        ink.text(listOf(if (run.active) "so far" else "until stopped", levels, "1 h per app on repeat").filter { it.isNotBlank() }.joinToString(" · "), m, heroY + 46f, 24f, mu)

        // Tiles along the bottom, bars in a card between.
        val tiles = listOf(
            "Average temp" to (rep.avgTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—"),
            "Peak temp" to (rep.peakTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—"),
            "Rounds" to "${rep.rounds}",
        )
        val tilesH = if (format == ExportFormat.PORTRAIT) 150f else 130f
        val area = RectF(m, format.height - m - 40f - tilesH, format.width - m, format.height - m - 40f)
        grid(area, tiles.size, 3, 12f).forEachIndexed { i, r -> ink.tile(r, tiles[i].first, tiles[i].second, s1, tx) }

        val card = RectF(m, heroY + 84f, format.width - m, area.top - 18f)
        ink.box(card, 36f, s1)
        val pad = 32f
        ink.text("Drain by app", card.left + pad, card.top + pad + 26f, 28f, tx, ink.headline)
        ink.text("battery points per hour", card.right - pad, card.top + pad + 26f, 20f, mu, align = Paint.Align.RIGHT)
        val apps = rep.apps
        if (apps.isNotEmpty()) {
            val top = card.top + pad + 56f
            val rowH = (card.bottom - pad - top) / apps.size
            val max = apps.mapNotNull { it.perHour }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val labelW = 250f
            val valueW = 150f
            val barL = card.left + pad + labelW
            val barR = card.right - pad - valueW
            val barH = minOf(22f, rowH * 0.32f)
            apps.forEachIndexed { i, a ->
                val cy = top + rowH * i + rowH / 2f
                ink.text(a.name, card.left + pad, cy + 8f, 23f, tx, ink.bold)
                ink.box(RectF(barL, cy - barH / 2f, barR, cy + barH / 2f), barH / 2f, colors.surfaceContainerHighest.toArgb())
                val f = ((a.perHour ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
                if (f > 0f) ink.box(RectF(barL, cy - barH / 2f, barL + maxOf(barH, (barR - barL) * f), cy + barH / 2f), barH / 2f, colors.primary.toArgb())
                ink.text(a.perHour?.let { "%.1f".format(java.util.Locale.getDefault(), it) } ?: "—", card.right - pad, cy + 9f, 26f, tx, ink.display, Paint.Align.RIGHT)
                val sub = listOfNotNull(a.mahPerHour?.let { Format.mah(it.toFloat()) + "/h" }, a.peakTempC?.let { "peak " + Format.temp(it, s.fahrenheit) }).joinToString(" · ")
                if (sub.isNotEmpty() && rowH >= 64f) ink.text(sub, barL, cy + barH / 2f + 24f, 17f, mu)
            }
        }

        ink.text("Recorded with Volta · battery-side readings, run by Volta's app benchmark", m, format.height - m + 6f, 18f, colors.primary.toArgb())
        return bmp
    }

    fun share(context: Context, bmp: Bitmap): Boolean = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "volta-${System.currentTimeMillis()}.png")
        dir.listFiles()?.filter { it.name != file.name }?.forEach { it.delete() }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun save(context: Context, bmp: Bitmap): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "volta-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Volta")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    }.getOrDefault(false)
}

/** Preview an exported image, pick its shape, then save or share it. */
@Composable
fun ExportDialog(
    title: String,
    formats: List<ExportFormat>,
    onDismiss: () -> Unit,
    render: (Context, ColorScheme, ExportFormat) -> Bitmap,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var format by rememberSaveable { mutableStateOf(formats.first()) }
    val bmp = remember(format, colors) { runCatching { render(context, colors, format) }.getOrNull() }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConnectedButtons(formats.map { it.label }, formats.indexOf(format), { format = formats[it] })
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), "Preview of the exported image",
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("Couldn't create the image.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton({ if (bmp != null && ExportImages.save(context, bmp)) toast("Image saved to Pictures/Volta.") else toast("Couldn't save the image.") }) { Text("Save PNG") }
                TextButton({ if (bmp != null && ExportImages.share(context, bmp)) onDismiss() else toast("Couldn't open sharing. You can save the PNG instead.") }) { Text("Share") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** The Today stats image. */
@Composable
fun StatsImageDialog(snap: Snapshot, live: Reading?, s: VoltaSettings, onDismiss: () -> Unit) {
    ExportDialog("Battery stats image", listOf(ExportFormat.SQUARE, ExportFormat.WIDE, ExportFormat.PORTRAIT), onDismiss) { ctx, colors, f ->
        ExportImages.stats(ctx, snap, live, s, colors, f)
    }
}

/** The charging session image. */
@Composable
fun SessionImageDialog(cs: ChargingSession, s: VoltaSettings, onDismiss: () -> Unit) {
    ExportDialog("Export charging session", listOf(ExportFormat.SQUARE, ExportFormat.WIDE), onDismiss) { ctx, colors, f ->
        ExportImages.session(ctx, cs, s, colors, f)
    }
}

/** The app benchmark results image. */
@Composable
fun BenchImageDialog(run: ReviewRun, rep: BenchReport, s: VoltaSettings, onDismiss: () -> Unit) {
    ExportDialog("Share benchmark results", listOf(ExportFormat.PORTRAIT, ExportFormat.SQUARE), onDismiss) { ctx, colors, f ->
        ExportImages.bench(ctx, run, rep, s, colors, f)
    }
}
