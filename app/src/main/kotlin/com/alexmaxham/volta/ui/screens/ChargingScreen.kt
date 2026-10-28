package com.alexmaxham.volta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.components.AxisLabels
import com.alexmaxham.volta.ui.components.DualLineChart
import com.alexmaxham.volta.ui.components.RowsCard
import com.alexmaxham.volta.ui.components.TimeLineChart
import com.alexmaxham.volta.ui.components.TimePoint
import com.alexmaxham.volta.ui.components.VCard

@Composable
fun ChargingScreen(snap: Snapshot, s: VoltaSettings, start: Long?, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val cs = snap.sessions.firstOrNull { it.start == start }
    ScreenColumn(bottom = 32.dp) {
        DetailBar("Charging session", onBack)
        if (cs == null) {
            Muted("Couldn't load this session. It may be older than 30 days.")
            return@ScreenColumn
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                Fmt.whenAt(cs.start, snap.now, snap.zone) + " – " + if (cs.ongoing) "now" else Fmt.time(cs.end, snap.zone),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${cs.startLevel} → ${cs.endLevel}", style = MaterialTheme.typography.displayMedium)
                Text("%", style = MaterialTheme.typography.headlineSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            Text(
                listOf(Format.duration(cs.duration), chargerName(cs.charger)).joinToString(" · ") + if (cs.ongoing) " · still connected at the last reading" else "",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
        }

        Text("Charging milestones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Milestone("to 50%", cs.to50, Modifier.weight(1f), RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp), c.primaryContainer, c.onPrimaryContainer)
            Milestone("to 80%", cs.to80, Modifier.weight(1f), RoundedCornerShape(8.dp), c.surfaceContainerLow, c.onSurface)
            Milestone("to 100%", cs.to100, Modifier.weight(1f), RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp), c.surfaceContainerLow, c.onSurface)
        }

        val power = cs.readings.mapNotNull { r -> r.powerW?.let { TimePoint(r.time, it.coerceAtLeast(0f)) } }
        val temp = cs.readings.mapNotNull { r -> r.tempC?.let { TimePoint(r.time, if (s.fahrenheit) it * 9 / 5 + 32 else it) } }
        VCard {
            Text("Charging speed & temperature", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(c.primary, "Net power (W)")
                Legend(c.tertiary, "Battery temp (${if (s.fahrenheit) "°F" else "°C"})")
            }
            if (power.size >= 2 || temp.size >= 2) {
                DualLineChart(power, temp, cs.start, cs.end, "Charging power and battery temperature over the session", Modifier.padding(top = 12.dp))
            } else {
                TimeLineChart(cs.readings.map { TimePoint(it.time, it.level.toFloat()) }, cs.start, cs.end, "Battery level during the session", Modifier.padding(top = 12.dp))
                Muted("This phone isn't reporting usable current and voltage together, so only the level is shown.")
            }
            AxisLabels(listOf(Fmt.time(cs.start, snap.zone), Fmt.time(cs.end, snap.zone)), Modifier.padding(top = 4.dp))
        }
        RowsCard(
            listOf(
                "Peak net power" to (cs.peakPowerW?.let { Format.watts(it) } ?: "—"),
                "Average" to (cs.avgPowerW?.let { Format.watts(it) } ?: "—"),
                "Peak battery temperature" to (cs.peakTempC?.let { Format.temp(it, s.fahrenheit) } ?: "Temp unavailable"),
                "Readings" to "${cs.readings.size}",
            ),
        )
        Muted("Estimated net battery power, not charger output. Current and voltage are battery-side readings; use an external meter for charger wattage.")
    }
}

@Composable
private fun Milestone(label: String, ms: Long?, modifier: Modifier, shape: RoundedCornerShape, bg: Color, fg: Color) {
    Column(modifier.clip(shape).background(bg).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
        Text(ms?.let { Format.duration(it) } ?: "—", style = MaterialTheme.typography.headlineSmall, color = fg, maxLines = 1)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.padding(top = 1.dp).clip(RoundedCornerShape(2.dp)).background(color).padding(horizontal = 7.dp, vertical = 2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
