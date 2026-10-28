package com.alexmaxham.volta.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.alexmaxham.volta.core.AppTime
import com.alexmaxham.volta.core.Apps
import com.alexmaxham.volta.core.Drain
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.ScreenTimeline
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.data.Json
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.NoteCard
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.SelectChip
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons

private data class AppWindow(val key: String, val label: String, val from: Long, val to: Long, val saved: String?)

@Composable
fun AppsScreen(snap: Snapshot, vm: VoltaViewModel) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val windows = remember(snap) {
        buildList {
            add(AppWindow("charge", "This charge", snap.sinceFull.from, snap.now, null))
            add(AppWindow("today", "Today", snap.sinceMidnight.from, snap.now, null))
            for (cy in snap.cycles.filter { !it.ongoing }.take(6)) {
                add(AppWindow(cy.start.toString(), Fmt.day(cy.end, snap.zone), cy.start, cy.end, snap.cycleApps[cy.start]?.second))
            }
        }
    }
    var key by rememberSaveable { mutableStateOf("charge") }
    val w = windows.firstOrNull { it.key == key } ?: windows.first()
    val partial = snap.now - w.from > UsageReader.RETENTION_DAYS * Time.DAY
    val rows: List<AppTime> = remember(snap, w) {
        w.saved?.let { runCatching { Json.apps(it) }.getOrNull() } ?: Apps.usage(snap.appEvents, w.from, w.to)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportApps(uri, rows)
    }

    val screen = remember(snap, w) { ScreenTimeline.build(snap.screenEvents, w.from, w.to) }
    val used = remember(snap, w) { Drain.usage(snap.readings, screen.onIntervals, w.from, w.to).used }
    // On big screens the window and its total sit beside the list of apps.
    AdaptiveScreen(start = start@{
        TabTitle("Apps") {
            if (snap.usageAccess && rows.isNotEmpty()) PillButton("CSV", { export.launch("volta-apps-${w.label.replace(' ', '-').lowercase()}.csv") }, tonal = true, icon = VIcons.Download)
        }
        if (!snap.usageAccess) {
            NoteCard(
                "See which apps you used",
                "App usage needs Usage Access. Open Settings → Apps → Special app access → Usage access, then allow Volta.",
                "Enable usage access",
            ) { runCatching { context.startActivity(UsageReader.accessIntent()) } }
            return@start
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (win in windows) SelectChip(win.label, win.key == w.key, { key = win.key })
        }

        VCard(color = c.primaryContainer) {
            Text(
                when (w.key) {
                    "charge" -> if (snap.cycleStart != null) "Since unplugged at 100% · ${Fmt.whenAt(w.from, snap.now, snap.zone)}" else "Since tracking began"
                    "today" -> "Since local midnight"
                    else -> Fmt.whenAt(w.from, snap.now, snap.zone) + " → " + Fmt.whenAt(w.to, snap.now, snap.zone)
                },
                style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer,
            )
            Text(if (w.saved == null) Format.duration(screen.on) else Format.duration(rows.sumOf { it.foreground }), style = MaterialTheme.typography.displaySmall, color = c.onPrimaryContainer)
            Text(
                (if (w.saved == null) "screen on" else "in apps") + " across ${rows.size} apps · ${Fmt.pts(used)} used",
                style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer,
            )
        }
        if (partial && w.saved == null) {
            Muted("Partial history: Android keeps about a week of app events, and may keep less.")
        }
    }, end = end@{
        if (!snap.usageAccess) return@end
        if (rows.isEmpty()) {
            Muted("No foreground app intervals were observed in this window.")
        } else {
            val max = rows.first().foreground.coerceAtLeast(1)
            GroupCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    for (a in rows.take(40)) AppRow(a, max)
                }
            }
            Muted("Time each app spent in the foreground. Android doesn't report per-app battery drain to other apps, so this shows where your screen time went.")
        }
    })
}

@Composable
private fun AppRow(a: AppTime, max: Long) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val label = remember(a.pkg) { UsageReader.label(context, a.pkg) }
    val icon: ImageBitmap? = remember(a.pkg) {
        runCatching { UsageReader.icon(context, a.pkg)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (icon != null) {
            Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(label.take(1), color = c.onSecondaryContainer, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.duration(a.foreground), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth((a.foreground.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.primary))
            }
        }
    }
}
