package com.alexmaxham.volta.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.alexmaxham.volta.R
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.data.Range
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.screens.Fmt
import java.io.File

/** A shareable card with today's battery stats, drawn in the current palette. */
object StatsImage {
    fun render(context: Context, snap: Snapshot, live: Reading?, s: VoltaSettings, colors: ColorScheme): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val display = runCatching { ResourcesCompat.getFont(context, R.font.volta_display) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val headline = runCatching { ResourcesCompat.getFont(context, R.font.volta_headline) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        cv.drawColor(colors.background.toArgb())

        p.color = colors.onSurfaceVariant.toArgb()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 34f
        p.letterSpacing = 0.18f
        cv.drawText("VOLTA", 80f, 120f, p)
        p.letterSpacing = 0f
        p.typeface = Typeface.DEFAULT
        p.textSize = 34f
        cv.drawText(Fmt.longDay(snap.now, snap.zone), 80f, 172f, p)

        val level = live?.level ?: snap.readings.lastOrNull()?.level ?: 0
        p.color = colors.primaryContainer.toArgb()
        cv.drawRoundRect(RectF(80f, 230f, w - 80f, 640f), 96f, 96f, p)
        p.color = colors.onPrimaryContainer.toArgb()
        p.typeface = display
        p.textSize = 260f
        cv.drawText("$level%", 140f, 520f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 40f
        cv.drawText(Fmt.estimate(snap.estimate), 146f, 590f, p)

        val win = if (s.range == Range.SINCE_FULL) snap.sinceFull else snap.sinceMidnight
        val rows = listOfNotNull(
            "Screen on" to if (snap.usageAccess) Format.duration(win.screen.on) else "—",
            "Screen off" to if (snap.usageAccess) Format.duration(win.screen.off) else "—",
            "Deep sleep" to if (win.sleep.coverage > 0.2) Format.duration(win.sleep.asleep) else "—",
            (if (s.range == Range.SINCE_FULL) "Used since 100%" else "Used today") to Fmt.pts(win.usage.used),
            snap.health?.percent?.let { "Battery health" to "$it%" },
            (live?.tempC ?: snap.readings.lastOrNull()?.tempC)?.let { "Temperature" to Format.temp(it, s.fahrenheit) },
        )
        var y = 720f
        for ((label, value) in rows.take(6)) {
            p.color = colors.surfaceContainerLow.toArgb()
            cv.drawRoundRect(RectF(80f, y, w - 80f, y + 86f), 32f, 32f, p)
            p.color = colors.onSurfaceVariant.toArgb()
            p.typeface = Typeface.DEFAULT
            p.textSize = 36f
            cv.drawText(label, 120f, y + 56f, p)
            p.color = colors.onSurface.toArgb()
            p.typeface = headline
            p.textSize = 42f
            val tw = p.measureText(value)
            cv.drawText(value, w - 120f - tw, y + 58f, p)
            y += 98f
        }
        p.color = colors.primary.toArgb()
        p.typeface = Typeface.DEFAULT
        p.textSize = 30f
        cv.drawText("A little battery clarity · Volta", 80f, h - 70f, p)
        return bmp
    }

    fun share(context: Context, bmp: Bitmap): Boolean = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "volta-stats.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share battery stats").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

@Composable
fun StatsImageDialog(snap: Snapshot, live: Reading?, s: VoltaSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val bmp = remember(snap, s.scheme, s.themeMode) { runCatching { StatsImage.render(context, snap, live, s, colors) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery stats image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A snapshot of this window. Save it or send it to another app.", style = MaterialTheme.typography.bodyMedium)
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), "Battery stats image preview", Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)))
                } else {
                    Text("Couldn't create the stats image.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton({
                    if (bmp == null || !StatsImage.save(context, bmp)) android.widget.Toast.makeText(context, "Couldn't save the image.", android.widget.Toast.LENGTH_SHORT).show()
                    else android.widget.Toast.makeText(context, "Stats image saved.", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Save PNG") }
                TextButton({
                    if (bmp == null || !StatsImage.share(context, bmp)) android.widget.Toast.makeText(context, "Couldn't open sharing. You can save the PNG instead.", android.widget.Toast.LENGTH_SHORT).show()
                    else onDismiss()
                }) { Text("Share") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

