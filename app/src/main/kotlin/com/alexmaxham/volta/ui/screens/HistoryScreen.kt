package com.alexmaxham.volta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Charger
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.History
import com.alexmaxham.volta.core.Night
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.LocalTwoPane
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.RowsCard
import com.alexmaxham.volta.ui.components.StatTile
import com.alexmaxham.volta.ui.components.AxisLabels
import com.alexmaxham.volta.ui.components.BarChart
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.GroupDivider
import com.alexmaxham.volta.ui.components.IconBadge
import com.alexmaxham.volta.ui.components.ListRow
import com.alexmaxham.volta.ui.components.SectionTitle
import com.alexmaxham.volta.ui.components.SelectChip
import com.alexmaxham.volta.ui.components.TimeLineChart
import com.alexmaxham.volta.ui.components.TimePoint
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val historyTabs = listOf("Level", "Charging", "Nights", "Cycles")

/**
 * On big screens History is a list and a detail side by side: picking a charging session or a
 * night opens it in the second pane instead of on its own screen.
 */
@Composable
fun HistoryScreen(snap: Snapshot, s: VoltaSettings, vm: VoltaViewModel, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var session by rememberSaveable { mutableStateOf<Long?>(null) }
    var night by rememberSaveable { mutableStateOf<Long?>(null) }
    val shownSession = snap.sessions.firstOrNull { it.start == session } ?: snap.sessions.firstOrNull()
    val shownNight = night ?: latestMorning(snap, s).toEpochDay()
    AdaptiveScreen(
        start = {
            TabTitle("History") {}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                historyTabs.forEachIndexed { i, t -> SelectChip(t, tab == i, { tab = i }) }
            }
            val twoPane = LocalTwoPane.current
            when (tab) {
                0 -> LevelTab(snap)
                1 -> ChargingTab(snap, shownSession?.start?.takeIf { twoPane }) { start ->
                    if (twoPane) session = start else go("charge/$start")
                }
                2 -> NightsTab(snap, s, shownNight.takeIf { twoPane }) { day ->
                    if (twoPane) night = day else go("night/$day")
                }
                else -> CyclesTab(snap)
            }
        },
        end = {
            val twoPane = LocalTwoPane.current
            when (tab) {
                0 -> RecentReadings(snap, s, if (twoPane) 12 else 5)
                1 -> if (twoPane && shownSession != null) ChargingDetail(snap, s, shownSession)
                2 -> if (twoPane) NightDetail(snap, s, vm, LocalDate.ofEpochDay(shownNight))
                else -> if (twoPane) CyclesSummary(snap)
            }
        },
    )
}

@Composable
private fun LevelTab(snap: Snapshot) {
    val c = MaterialTheme.colorScheme
    val from = snap.now - 7 * Time.DAY
    VCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Battery level", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Last 7 days", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        val points = snap.readings.filter { it.time >= from }.map { TimePoint(it.time, it.level.toFloat()) }
        if (points.size < 2) {
            Text(
                "More readings will appear as you use Volta. Turn on background logging in Settings to collect readings while the app is closed.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            TimeLineChart(points, from, snap.now, "Battery level over the last 7 days", Modifier.padding(top = 12.dp), height = 150.dp)
            AxisLabels(snap.daily.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, Modifier.padding(top = 6.dp))
        }
    }

    VCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your week so far", style = MaterialTheme.typography.titleMedium)
                Text("Daily recorded battery use", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            snap.week.change?.let { ch ->
                val pct = (ch * 100).roundToInt()
                Text(
                    (if (pct > 0) "+" else if (pct < 0) "−" else "") + "${kotlin.math.abs(pct)}% vs last week",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onTertiaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.tertiaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        BarChart(
            snap.daily.map { it.used },
            snap.daily.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
            "Battery points used per day: " + snap.daily.joinToString { "${it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.used}" },
            Modifier.padding(top = 16.dp),
        )
        Muted(
            "How much charge was used, in percentage points. After recharging, a day's total can exceed 100. Battery health describes capacity instead.",
            Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun RecentReadings(snap: Snapshot, s: VoltaSettings, count: Int) {
    SectionTitle("Recent readings")
    GroupCard {
        val recent = snap.readings.takeLast(count).reversed()
        if (recent.isEmpty()) ListRow("No readings yet.")
        recent.forEachIndexed { i, r ->
            if (i > 0) GroupDivider()
            ListRow(
                "${r.level}%" + if (r.plugged) " · charging" else "",
                Fmt.whenAt(r.time, snap.now, snap.zone) + (r.tempC?.let { " · " + Format.temp(it, s.fahrenheit) } ?: ""),
            )
        }
    }
}

@Composable
private fun ChargingTab(snap: Snapshot, selected: Long?, open: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    val sessions = snap.sessions.take(50)
    if (sessions.isEmpty()) {
        VCard {
            Text("No charging sessions yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your charging sessions will appear here as readings are collected. Turn on background logging to record while the app is closed.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
        }
        return
    }
    GroupCard {
        sessions.forEachIndexed { i, cs ->
            if (i > 0) GroupDivider()
            ListRow(
                "${cs.startLevel}% → ${cs.endLevel}%" + if (cs.ongoing) " · now" else "",
                listOfNotNull(
                    Fmt.whenAt(cs.start, snap.now, snap.zone),
                    Format.duration(cs.duration),
                    cs.peakPowerW?.let { "${Format.watts(it)} peak" },
                ).joinToString(" · "),
                leading = { IconBadge(VIcons.Bolt, c.primaryContainer, c.onPrimaryContainer, RoundedCornerShape(16.dp), 48.dp) },
                modifier = selectedRow(cs.start == selected),
                trailing = { Icon(VIcons.Chevron, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                onClick = { open(cs.start) },
            )
        }
    }
    Muted(
        "Observed sessions from the last 30 days. Timing follows saved readings, not the exact plug-in moment. Gaps over 30 minutes split sessions." +
            if (snap.sessions.size > 50) " Showing the 50 most recent sessions." else "",
    )
}

@Composable
private fun NightsTab(snap: Snapshot, s: VoltaSettings, selected: Long?, open: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    val today = History.dateOf(snap.now, snap.zone)
    val nights = (0L..6L).map { back ->
        val morning = today.minusDays(back)
        morning to Night.report(Night.window(morning, s.nightStart, s.nightEnd, snap.zone), snap.readings, snap.screenEvents, snap.now)
    }.filter { (_, r) -> r.window.start < snap.now }
    SectionTitle("Overnight reports")
    GroupCard {
        nights.forEachIndexed { i, (morning, r) ->
            if (i > 0) GroupDivider()
            val drop = r.drop
            ListRow(
                Fmt.day(r.window.start, snap.zone) + " → " + morning.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                when {
                    r.inProgress -> "Night in progress"
                    !r.hasData -> "No recorded battery readings"
                    r.chargingSeen -> "Charging was observed overnight"
                    drop != null -> listOfNotNull(Fmt.signedPts(drop), r.ratePerHour?.let { Format.rate(it) }).joinToString(" · ")
                    else -> "Partial / unavailable"
                },
                leading = { IconBadge(VIcons.Moon, c.secondaryContainer, c.onSecondaryContainer, CircleShape) },
                modifier = selectedRow(morning.toEpochDay() == selected),
                trailing = { Icon(VIcons.Chevron, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                onClick = { open(morning.toEpochDay()) },
            )
        }
    }
    Muted("Nights run from ${Fmt.time(nights.firstOrNull()?.second?.window?.start ?: 0L, snap.zone)} to ${Fmt.time(nights.firstOrNull()?.second?.window?.end ?: 0L, snap.zone)}. Change the hours in any report.")
}

@Composable
private fun CyclesTab(snap: Snapshot) {
    val c = MaterialTheme.colorScheme
    SectionTitle("Charge cycles")
    if (snap.cycles.isEmpty()) {
        Muted("No readings yet.")
        return
    }
    GroupCard {
        snap.cycles.forEachIndexed { i, cy ->
            if (i > 0) GroupDivider()
            ListRow(
                when {
                    cy.ongoing && cy.partial -> "Since tracking began"
                    cy.ongoing -> "Current cycle"
                    cy.partial -> "First partial cycle"
                    else -> Fmt.day(cy.start, snap.zone)
                },
                listOf(
                    Fmt.whenAt(cy.start, snap.now, snap.zone) + if (cy.ongoing) " → now" else " → " + Fmt.whenAt(cy.end, snap.now, snap.zone),
                    "${Fmt.pts(cy.used)} used",
                ).joinToString(" · "),
                trailing = { Text(Format.duration(cy.end - cy.start), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.onSurface) },
            )
        }
    }
    Muted("Each window runs from one observed 100% charge to the next. These windows are separate from the phone's lifetime cycle count.")
}

/** Totals across the charge cycles, shown beside the list on big screens. */
@Composable
private fun CyclesSummary(snap: Snapshot) {
    val c = MaterialTheme.colorScheme
    val done = snap.cycles.filter { !it.ongoing && !it.partial }
    SectionTitle("Across your cycles")
    if (done.isEmpty()) {
        Muted("Averages appear once a full cycle, from one 100% charge to the next, has been recorded.")
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            "Average cycle", Format.duration(done.sumOf { it.end - it.start } / done.size), "from 100% to the next 100%",
            Modifier.weight(1f), c.primaryContainer, c.onPrimaryContainer,
        )
        StatTile("Average use", Fmt.pts(done.sumOf { it.used } / done.size), "per cycle", Modifier.weight(1f))
    }
    val longest = done.maxBy { it.end - it.start }
    RowsCard(
        listOf(
            "Complete cycles" to "${done.size}",
            "Longest" to Format.duration(longest.end - longest.start),
            "Started" to Fmt.day(longest.start, snap.zone),
        ),
    )
}

/** Marks the row whose detail is open in the second pane. */
@Composable
private fun selectedRow(selected: Boolean): Modifier =
    if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer).semantics { this.selected = true } else Modifier

internal fun chargerName(c: Charger): String = when (c) {
    Charger.AC -> "Wall charger"
    Charger.USB -> "USB"
    Charger.WIRELESS -> "Wireless"
    Charger.DOCK -> "Dock"
    Charger.NONE, Charger.UNKNOWN -> "Charger type not recorded"
}
