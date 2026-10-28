package com.alexmaxham.volta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Estimate
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.data.Range
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.LocalTwoPane
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.BatteryHero
import com.alexmaxham.volta.ui.components.CircleIconButton
import com.alexmaxham.volta.ui.components.ConnectedButtons
import com.alexmaxham.volta.ui.components.IconBadge
import com.alexmaxham.volta.ui.components.LegendDot
import com.alexmaxham.volta.ui.components.NoteCard
import com.alexmaxham.volta.ui.components.RowsCard
import com.alexmaxham.volta.ui.components.SectionTitle
import com.alexmaxham.volta.ui.components.SegmentStrip
import com.alexmaxham.volta.ui.components.StatTile
import com.alexmaxham.volta.ui.components.LevelChart
import com.alexmaxham.volta.ui.components.TimePoint
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.ui.share.StatsImageDialog
import kotlin.math.roundToInt

@Composable
fun TodayScreen(snap: Snapshot, live: Reading?, s: VoltaSettings, vm: VoltaViewModel, go: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val current = live ?: snap.readings.lastOrNull()
    var sharing by remember { mutableStateOf(false) }

    val title: @Composable () -> Unit = {
        TabTitle("Today", Fmt.longDay(snap.now, snap.zone)) {
            CircleIconButton(VIcons.Share, "Share battery stats", { sharing = true })
        }
    }
    val hero: @Composable (Dp) -> Unit = { size ->
        if (current == null) {
            Text("Wait for the first battery reading.", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 120.dp))
        } else {
            BatteryHero(
                level = current.level,
                charging = current.plugged && current.level < 100,
                eyebrow = Fmt.status(current),
                // Until the snapshot catches up with a plug change, don't show a stale estimate.
                caption = Fmt.estimate(if (current.plugged == snap.readings.lastOrNull()?.plugged) snap.estimate else Estimate.Learning(0f), current.plugged),
                size = size,
            )
        }
    }
    val pills: @Composable () -> Unit = {
        current?.tempC?.let { Pill(VIcons.Thermo, Format.temp(it, s.fahrenheit)) }
        current?.powerW?.let { Pill(VIcons.Bolt, (if (it > 0) "+" else "−") + Format.watts(kotlin.math.abs(it))) }
        snap.health?.percent?.let { Pill(VIcons.Heart, "$it% health") }
    }
    val learning: @Composable () -> Unit = {
        if (snap.estimate is Estimate.Learning && current?.plugged == false) {
            Muted(
                "Learning needs at least 45 minutes and a 3-point drop of continuous unplugged use." +
                    if (!s.backgroundLogging) " Turn on background logging in Settings to learn while the app is closed." else "",
                Modifier.fillMaxWidth(),
            )
        }
    }
    val details: @Composable () -> Unit = { TodayDetails(snap, current, s, vm, go) }

    TabletopScreen(
        // Half folded like a laptop: the level stands in the upper half, the numbers scroll below.
        top = {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val heroSize = minOf(maxHeight, 312.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    hero(heroSize)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { pills() }
                }
            }
        },
        bottom = {
            title()
            learning()
            details()
        },
        fallback = {
            AdaptiveScreen(
                start = {
                    title()
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Panes on big screens have room for a larger hero.
                        hero(minOf(maxWidth, if (LocalTwoPane.current) 380.dp else 312.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) { pills() }
                    learning()
                },
                end = { details() },
            )
        },
    )

    if (sharing) StatsImageDialog(snap, live, s, onDismiss = { sharing = false })
}

/** Usage totals, the cycle chart and the cards under the hero. */
@Composable
private fun TodayDetails(snap: Snapshot, current: Reading?, s: VoltaSettings, vm: VoltaViewModel, go: (String) -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val w = if (s.range == Range.SINCE_FULL) snap.sinceFull else snap.sinceMidnight
    val access = snap.usageAccess
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ConnectedButtons(
            listOf("Since 100%", "Since midnight"),
            if (s.range == Range.SINCE_FULL) 0 else 1,
            { i -> vm.update { it.copy(range = if (i == 0) Range.SINCE_FULL else Range.SINCE_MIDNIGHT) } },
            Modifier.padding(top = 6.dp),
        )
        if (s.range == Range.SINCE_FULL && snap.cycleStart == null) {
            Muted("No full charge recorded yet. Totals start from your first reading; open the app at 100% or keep background logging on.")
        }

        val dash = "—"
        val onRate = w.usage.screenOn.perHour
        val offRate = w.usage.screenOff.perHour
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Screen on", if (access) Format.duration(w.screen.on) else dash,
                onRate?.let { Format.rate(it) } ?: "Drain rate needs more readings",
                Modifier.weight(1f), c.primaryContainer, c.onPrimaryContainer,
            )
            StatTile(
                "Screen off", if (access) Format.duration(w.screen.off) else dash,
                offRate?.let { Format.rate(it) } ?: "Drain rate needs more readings",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val sleepShare = if (w.screen.off > 0) w.sleep.asleep.toDouble() / w.screen.off else null
            StatTile(
                "Deep sleep", if (w.sleep.coverage > 0.2) Format.duration(w.sleep.asleep) else dash,
                when {
                    w.sleep.coverage <= 0.2 -> "Needs more readings"
                    sleepShare != null && access -> "${(sleepShare * 100).roundToInt().coerceAtMost(100)}% of screen-off"
                    else -> "${Format.percent(w.sleep.coverage)} clock coverage"
                },
                Modifier.weight(1f),
            )
            StatTile(
                if (s.range == Range.SINCE_FULL) "Used since 100%" else "Used today",
                Fmt.pts(w.usage.used),
                "over ${Format.duration(w.to - w.from)}",
                Modifier.weight(1f), c.tertiaryContainer, c.onTertiaryContainer,
            )
        }
        if (!access) {
            NoteCard(
                "See how much time your screen is awake",
                "Screen-on time and app usage need Usage Access. No root required; your data stays on this phone.",
                "Enable usage access",
            ) { runCatching { context.startActivity(UsageReader.accessIntent()) } }
        }

        SectionTitle(if (s.range == Range.SINCE_FULL) "Current cycle" else "Today so far")
        VCard {
            val from = w.from
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("−${w.usage.used}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    " pts " + if (s.range == Range.SINCE_FULL && snap.cycleStart != null) "since unplugged at ${Fmt.time(from, snap.zone)}" else "since ${Fmt.time(from, snap.zone)}",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            val points = snap.readings.filter { it.time >= from }.map { TimePoint(it.time, it.level.toFloat()) } +
                listOfNotNull(current?.takeIf { it.time > (snap.readings.lastOrNull()?.time ?: 0L) }?.let { TimePoint(snap.now, it.level.toFloat()) })
            val showScreen = access && w.screen.total > 0
            LevelChart(
                points, from, snap.now, if (showScreen) w.screen.onIntervals else null,
                { Fmt.time(it, snap.zone) },
                "Battery level from ${points.firstOrNull()?.value?.toInt() ?: 0}% to ${points.lastOrNull()?.value?.toInt() ?: 0}% since ${Fmt.time(from, snap.zone)}",
                Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(c.primary, "Battery %")
                if (showScreen) LegendDot(c.tertiary, "Screen on")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VCard(Modifier.weight(1f), onClick = { go("settings") }) {
                IconBadge(VIcons.Heart, c.secondaryContainer, c.onSecondaryContainer)
                Text("Battery health", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                val h = snap.health
                val design = h?.designMah
                Text(h?.percent?.let { "$it%" } ?: h?.let { Format.mah(it.estimateMah) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        h == null -> "Needs a 30-point charge"
                        design == null -> "Add the design capacity in Settings"
                        else -> "${Format.mah(h.estimateMah)} of ${Format.mah(design.toFloat())}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            VCard(Modifier.weight(1f), onClick = { go("night/latest") }) {
                IconBadge(VIcons.Moon, c.secondaryContainer, c.onSecondaryContainer, CircleShape)
                Text("Last night", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                val n = snap.lastNight
                val drop = n.drop
                Text(
                    when {
                        !n.hasData -> "—"
                        n.chargingSeen -> "Charged"
                        drop != null -> Fmt.signedPts(drop)
                        else -> "—"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    n.ratePerHour?.let { "${Format.rate(it)} while you slept" } ?: if (n.hasData) "Partial night" else "No readings yet",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }

        SectionTitle("Today at a glance")
        val t = snap.sinceMidnight
        RowsCard(
            listOf(
                "Screen on today" to if (access) Format.duration(t.screen.on) else "—",
                "Recorded battery use" to Fmt.pts(t.usage.used),
                "Lowest level" to (t.usage.lowest?.let { "$it%" } ?: "—"),
                "Peak battery temperature" to (t.usage.peakTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—"),
            ),
        )
        if (!s.backgroundLogging) {
            Muted("Logging while open · background logging is off. Samples are saved up to once a minute while you use the app.")
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerLow).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
