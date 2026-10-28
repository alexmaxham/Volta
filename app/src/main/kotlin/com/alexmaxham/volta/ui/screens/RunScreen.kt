package com.alexmaxham.volta.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Review
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.core.TestType
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.AxisLabels
import com.alexmaxham.volta.ui.components.CircleIconButton
import com.alexmaxham.volta.ui.components.CurvesChart
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.GroupDivider
import com.alexmaxham.volta.ui.components.ChevronRow
import com.alexmaxham.volta.ui.components.LegendDot
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.RowsCard
import com.alexmaxham.volta.ui.components.SectionTitle
import com.alexmaxham.volta.ui.components.StatTile
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import kotlinx.coroutines.delay

@Composable
fun RunScreen(vm: VoltaViewModel, s: VoltaSettings, id: String, onBack: () -> Unit, go: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    var run by remember { mutableStateOf<ReviewRun?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        while (true) {
            vm.loadRun(id) { run = it; loaded = true }
            now = System.currentTimeMillis()
            delay(if (run?.active == true) Review.INTERVAL else 60_000)
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.exportRunCsv(id, uri) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.backupRun(id, uri) }

    ScreenColumn(bottom = 32.dp) {
        DetailBar("Review run", onBack) {
            if (run?.active == false) CircleIconButton(VIcons.Trash, "Delete this run", { confirmDelete = true })
        }
        val r = run
        if (r == null) {
            Muted(if (loaded) "This review no longer exists." else "Loading run…")
            return@ScreenColumn
        }
        val sum = Review.summary(r, now)
        val cond = r.conditions
        Text(cond.protocol.ifBlank { "Lab test" }, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 4.dp))
        Muted(listOfNotNull(Fmt.whenAt(r.started, now, java.time.ZoneId.systemDefault()), if (cond.type == TestType.DRAIN) "Drain test" else "Charging test", conditionsLine(cond).ifBlank { null }).joinToString(" · "))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (r.active) "Elapsed" else "Duration", Format.clock(sum.elapsed), if (r.active) "In progress" else "Finished", Modifier.weight(1f), c.primaryContainer, c.onPrimaryContainer)
            StatTile(
                "Battery", if (sum.startLevel != null) "${sum.startLevel} → ${sum.endLevel}%" else "—",
                rateLine(cond.type, sum.ratePerHour), Modifier.weight(1f),
            )
        }

        if (r.active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Finish and save run", { vm.finishRun(r.id) }, Modifier.weight(1f))
                if (cond.reviewRecording) PillButton("Resume recording", { vm.resumeRecording() }, tonal = true)
            }
            Muted(if (cond.reviewRecording) "Review recording · Android may delay readings while the phone sleeps." else "App-open recording · readings are saved while Volta is open.")
        }

        VCard {
            Text("Battery level", style = MaterialTheme.typography.titleMedium)
            val pts = Review.curve(r).map { it.minutes to it.level }
            if (pts.size >= 2) {
                CurvesChart(listOf(pts to c.primary), "Battery level over the run", Modifier.padding(top = 12.dp))
                AxisLabels(listOf("0 min", Format.duration((pts.last().first * 60_000).toLong())), Modifier.padding(top = 4.dp))
            } else {
                Muted("Need at least two recorded readings.", Modifier.padding(top = 8.dp))
            }
        }

        RowsCard(
            buildList {
                add("Average temperature" to (sum.avgTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—"))
                add("Peak battery temperature" to (sum.peakTempC?.let { Format.temp(it, s.fahrenheit) } ?: "—"))
                add("Average battery power" to (sum.avgPowerW?.let { Format.watts(it) } ?: "—"))
                add("Peak battery power" to (sum.peakPowerW?.let { Format.watts(it) } ?: "—"))
                if (cond.type == TestType.CHARGE) {
                    add("To 50%" to (sum.to50?.let { Format.duration(it) } ?: "—"))
                    add("To 80%" to (sum.to80?.let { Format.duration(it) } ?: "—"))
                    add("To 100%" to (sum.to100?.let { Format.duration(it) } ?: "—"))
                }
                add("Recorded samples" to "${sum.sampleCount}")
                add("Sample coverage" to Format.percent(sum.coverage))
            },
        )

        SectionTitle("Test conditions · entered by reviewer")
        RowsCard(
            listOf(
                "Phone" to cond.phone, "Software" to cond.software, "Brightness" to cond.brightness, "Refresh rate" to cond.refreshRate,
                "Resolution" to cond.resolution, "Network" to cond.network, "Performance" to cond.performance, "Charger" to cond.charger,
                "Recording" to if (cond.reviewRecording) "Review mode" else "App-open",
            ).filter { it.second.isNotBlank() },
        )
        if (cond.notes.isNotBlank()) {
            VCard {
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text(cond.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Muted("Battery temperature is not surface or chipset temperature. Conditions are entered by the reviewer.")

        if (!r.active) {
            GroupCard {
                ChevronRow("Compare matching tests", "Curves aligned by elapsed time") { go("compare/${r.id}") }
                GroupDivider()
                ChevronRow("Export CSV", "Full notes and readings") { csv.launch("volta-run-${r.started}.csv") }
                GroupDivider()
                ChevronRow("Back up run", "A file you can import on another phone") { backup.launch("volta-run-${r.started}.json") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this review run?") },
            text = { Text("This removes its notes and readings from this phone. Exported files are unaffected.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    vm.deleteRun(id)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CompareScreen(vm: VoltaViewModel, snap: Snapshot, id: String, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val base = snap.runs.firstOrNull { it.id == id }
    val matching = base?.let { Review.matching(it, snap.runs).take(5) }.orEmpty()
    var runs by remember { mutableStateOf<List<ReviewRun>?>(null) }
    LaunchedEffect(matching.map { it.id }) { vm.loadRuns(matching.map { it.id }) { runs = it } }
    val palette = listOf(c.primary, c.tertiary, c.onSurfaceVariant, c.secondary, c.outline)

    ScreenColumn(bottom = 32.dp) {
        DetailBar("Compare matching tests", onBack)
        val loaded = runs
        when {
            base == null -> Muted("This review no longer exists.")
            loaded == null -> Muted("Loading…")
            loaded.size < 2 -> Muted("No other finished runs match these conditions yet. Repeat the test with the same protocol, settings and recording mode to compare.")
            else -> {
                VCard {
                    Text("Battery curves", style = MaterialTheme.typography.titleMedium)
                    CurvesChart(
                        loaded.mapIndexed { i, r -> Review.curve(r).map { it.minutes to it.level } to palette[i % palette.size] },
                        "Battery percentage curves aligned by elapsed time for ${loaded.size} runs",
                        Modifier.padding(top = 12.dp),
                        height = 180.dp,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        loaded.forEachIndexed { i, r -> LegendDot(palette[i % palette.size], Fmt.day(r.started, snap.zone)) }
                    }
                }
                RowsCard(
                    loaded.map { r ->
                        val sum = Review.summary(r, snap.now)
                        Fmt.day(r.started, snap.zone) + " · " + Format.duration(sum.elapsed) to rateLine(r.conditions.type, sum.ratePerHour)
                    },
                )
            }
        }
        Muted("Groups match protocol, test type, brightness, refresh rate, resolution, network, performance, charger and recording mode. Notes and software can still differ—check them before drawing conclusions.")
    }
}
