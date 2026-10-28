package com.alexmaxham.volta.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.History
import com.alexmaxham.volta.core.Night
import com.alexmaxham.volta.core.ScreenTimeline
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.AxisLabels
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.LegendDot
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.SegmentStrip
import com.alexmaxham.volta.ui.components.StatTile
import com.alexmaxham.volta.ui.components.SwitchRow
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.work.Notifier
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun NightScreen(snap: Snapshot, s: VoltaSettings, vm: VoltaViewModel, arg: String?, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val today = History.dateOf(snap.now, snap.zone)
    val latest = Night.window(today, s.nightStart, s.nightEnd, snap.zone).let { if (snap.now >= it.end) today else today.minusDays(1) }
    val morning = arg?.toLongOrNull()?.let { LocalDate.ofEpochDay(it) } ?: latest
    val window = Night.window(morning, s.nightStart, s.nightEnd, snap.zone)
    val r = Night.report(window, snap.readings, snap.screenEvents, snap.now)
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.update { it.copy(morningSummary = true) } else vm.say("Notification permission is needed for the morning summary. You can enable it in Android Settings.")
    }

    ScreenColumn(bottom = 32.dp) {
        DetailBar("Overnight report", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("From ${Fmt.time(window.start, snap.zone)}", { editing = true }, tonal = true, modifier = Modifier.weight(1f))
            PillButton("To ${Fmt.time(window.end, snap.zone)}", { editing = false }, tonal = true, modifier = Modifier.weight(1f))
        }

        val drop = r.drop
        VCard(color = c.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp), padding = 22.dp) {
            Box {
                Icon(VIcons.Moon, null, tint = c.onPrimaryContainer.copy(alpha = 0.18f), modifier = Modifier.size(120.dp).align(Alignment.TopEnd).padding(start = 40.dp))
                androidx.compose.foundation.layout.Column {
                    Text(
                        (if (morning == latest) "Last night · " else "") +
                            Fmt.day(window.start, snap.zone) + " → " + morning.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer,
                    )
                    Text(
                        when {
                            !r.hasData -> "No data"
                            r.chargingSeen -> "Charged"
                            drop != null -> Fmt.signedPts(drop)
                            else -> "Partial"
                        },
                        style = MaterialTheme.typography.displayMedium, color = c.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        when {
                            r.inProgress -> "Night in progress · refresh for newer readings"
                            !r.hasData -> "No recorded battery readings in this window."
                            r.chargingSeen -> "Charging was observed overnight. No discharge rate is shown."
                            else -> listOfNotNull(
                                r.ratePerHour?.let { Format.rate(it) },
                                if (r.startLevel != null && r.endLevel != null) "${r.startLevel}% → ${r.endLevel}%" else null,
                            ).joinToString(" · ").ifEmpty { "Partial coverage" }
                        },
                        style = MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer,
                    )
                }
            }
        }

        if (r.hasData) {
            VCard {
                Text("How the night went", style = MaterialTheme.typography.titleMedium)
                SegmentStrip(nightSegments(snap, window.start, minOf(window.end, snap.now), c.primary, c.surfaceContainerHigh, c.tertiary, c.outlineVariant.copy(alpha = 0.4f)), Modifier.padding(top = 14.dp), height = 40.dp, gap = 2.dp, radius = 6.dp)
                AxisLabels(listOf(Fmt.time(window.start, snap.zone), Fmt.time(window.end, snap.zone)), Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(c.primary, "Deep sleep")
                    LegendDot(c.surfaceContainerHigh, "Awake, screen off")
                    LegendDot(c.tertiary, "Screen on")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ds = r.deepSleep
                StatTile("Deep sleep", if (ds.coverage >= 0.2) Format.duration(ds.asleep) else "—", if (ds.coverage >= 0.9) Format.percent(ds.fraction) + " of the night" else "Partial clocks", Modifier.weight(1f))
                StatTile("Screen woke", if (snap.usageAccess) "${r.screen.wakes}×" else "—", if (snap.usageAccess) Format.duration(r.screen.on) + " on" else "Needs usage access", Modifier.weight(1f))
                StatTile("Coverage", Format.percent(r.coverage), r.lowest?.let { "Lowest $it%" }, Modifier.weight(1f))
            }
        }

        GroupCard {
            SwitchRow("Morning summary", "A notification with this report when you wake up", s.morningSummary) { on ->
                if (on && !Notifier.canPost(context) && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.update { it.copy(morningSummary = on) }
            }
        }
        Muted("Deep sleep is time Android's clock shows the phone suspended. It's part of the night, not extra time on top of screen-off, and it isn't the same as Doze. Comparisons need at least 90% clock coverage.")
    }

    editing?.let { isStart ->
        TimeDialog(if (isStart) s.nightStart else s.nightEnd, if (isStart) "Night starts" else "Night ends", onDismiss = { editing = null }) { t ->
            if (t == (if (isStart) s.nightEnd else s.nightStart)) vm.say("This local-time window is empty. Choose different times.")
            else vm.update { if (isStart) it.copy(nightStart = t) else it.copy(nightEnd = t) }
            editing = null
        }
    }
}

/** Splits the night into deep sleep, awake and screen-on segments from consecutive readings. */
private fun nightSegments(snap: Snapshot, from: Long, to: Long, sleep: Color, awake: Color, screen: Color, gap: Color): List<Pair<Float, Color>> {
    val on = ScreenTimeline.build(snap.screenEvents, from, to).onIntervals
    val rs = snap.readings.filter { it.time in (from - 45 * 60_000)..(to + 45 * 60_000) }
    val out = mutableListOf<Pair<Float, Color>>()
    var cursor = from
    for ((a, b) in rs.zipWithNext()) {
        val s = maxOf(a.time, from)
        val e = minOf(b.time, to)
        if (e <= s) continue
        if (s > cursor) out += (s - cursor).toFloat() to gap
        val color = when {
            b.time - a.time > 45 * 60_000 -> gap
            ScreenTimeline.onFraction(on, a.time, b.time) > 0.5 -> screen
            a.elapsed != null && b.elapsed != null && a.uptime != null && b.uptime != null && b.elapsed!! > a.elapsed!! -> {
                val asleep = ((b.elapsed!! - a.elapsed!!) - (b.uptime!! - a.uptime!!)).toFloat() / (b.elapsed!! - a.elapsed!!)
                if (asleep >= 0.5f) sleep else awake
            }
            else -> awake
        }
        out += (e - s).toFloat() to color
        cursor = e
    }
    if (to > cursor) out += (to - cursor).toFloat() to gap
    // Merge neighbours of the same colour so the strip reads as calm blocks.
    return out.fold(mutableListOf<Pair<Float, Color>>()) { acc, seg ->
        if (acc.isNotEmpty() && acc.last().second == seg.second) acc[acc.lastIndex] = (acc.last().first + seg.first) to seg.second else acc += seg
        acc
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, title: String, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        confirmButton = { TextButton({ onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
