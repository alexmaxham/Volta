package com.alexmaxham.volta.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.alexmaxham.volta.MainActivity
import com.alexmaxham.volta.core.Drain
import com.alexmaxham.volta.core.Estimate
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.data.Db
import com.alexmaxham.volta.data.Scheme
import com.alexmaxham.volta.data.SettingsStore
import com.alexmaxham.volta.ui.theme.Palettes

private data class WidgetData(val level: Int, val status: String, val estimate: String, val temp: String?)

/** Home-screen widget: level, status, time left and temperature in the app's colours. */
class VoltaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 80.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context).current()
        val now = System.currentTimeMillis()
        val live = BatteryReader.read(context, Source.APP, now)
        val readings = Db.get(context).readings(now - Time.DAY) + listOfNotNull(live)
        val e = Drain.estimate(readings)
        val data = WidgetData(
            level = live?.level ?: readings.lastOrNull()?.level ?: 0,
            status = when {
                live == null -> "Battery"
                live.plugged && live.level >= 100 -> "Full"
                live.plugged -> "Charging"
                else -> "On battery"
            },
            estimate = when (e) {
                is Estimate.Remaining -> Format.duration(e.millis) + " left"
                is Estimate.UntilFull -> "Full in " + Format.duration(e.millis)
                Estimate.Full -> "Fully charged"
                is Estimate.Learning -> "Learning"
            },
            temp = live?.tempC?.let { Format.temp(it, settings.fahrenheit) },
        )
        val colors = if (settings.scheme == Scheme.DYNAMIC) null else ColorProviders(
            light = Palettes.colors(context, settings.scheme, false),
            dark = Palettes.colors(context, settings.scheme, true),
        )
        provideContent {
            if (colors != null) GlanceTheme(colors) { Content(data) } else GlanceTheme { Content(data) }
        }
    }

    @Composable
    private fun Content(d: WidgetData) {
        val wide = LocalSize.current.width >= 250.dp
        val c = GlanceTheme.colors
        val root = GlanceModifier
            .fillMaxSize()
            .cornerRadius(32.dp)
            .background(if (wide) c.surface else c.primaryContainer)
            .clickable(actionStartActivity<MainActivity>())
            .padding(18.dp)
        if (!wide) {
            Column(root, verticalAlignment = Alignment.Vertical.Bottom) {
                Text(d.status, style = TextStyle(color = c.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.defaultWeight())
                Text("${d.level}%", style = TextStyle(color = c.onPrimaryContainer, fontSize = 48.sp, fontWeight = FontWeight.Bold))
                Text(d.estimate, style = TextStyle(color = c.onPrimaryContainer, fontSize = 13.sp))
            }
        } else {
            Row(root, verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("${d.level}%", style = TextStyle(color = c.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight()) {
                    LinearProgressIndicator(
                        progress = d.level / 100f,
                        modifier = GlanceModifier.fillMaxWidth().height(10.dp).cornerRadius(5.dp),
                        color = c.primary,
                        backgroundColor = c.surfaceVariant,
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        listOfNotNull(d.status, d.estimate, d.temp).joinToString(" · "),
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = VoltaWidget().updateAll(context)
    }
}

class VoltaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoltaWidget()
}
