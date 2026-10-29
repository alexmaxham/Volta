package com.alexmaxham.volta.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Conditions
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.TestType
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.CircleIconButton
import com.alexmaxham.volta.ui.components.ConnectedButtons
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.GroupDivider
import com.alexmaxham.volta.ui.components.IconBadge
import com.alexmaxham.volta.ui.components.ListRow
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.SectionTitle
import com.alexmaxham.volta.ui.components.SelectChip
import com.alexmaxham.volta.ui.components.SwitchRow
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.work.Notifier
import kotlinx.coroutines.delay

@Composable
fun LabScreen(snap: Snapshot, s: VoltaSettings, vm: VoltaViewModel, go: (String) -> Unit, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importRun(uri) }
    val active = snap.activeRun

    // Keep the elapsed timer moving while a run is active.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.id) {
        while (active != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Room under the lists for the New test button.
        AdaptiveScreen(bottom = 120.dp, start = {
            DetailBar("Lab", onBack) {
                CircleIconButton(VIcons.Upload, "Import run", { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
            Muted("Start a timed drain or charging test at any battery level. Record your test conditions, then export the results or compare repeat runs.", Modifier.padding(top = 0.dp))

            if (active != null) {
                val last = active.samples.lastOrNull()
                VCard(color = c.primaryContainer, shape = RoundedCornerShape(32.dp), padding = 20.dp, onClick = { go("run/${active.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
                        Text("RUN IN PROGRESS · tap to finish or resume", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer)
                    }
                    Text(
                        listOf(active.conditions.protocol.ifBlank { "Lab test" }, active.conditions.refreshRate, active.conditions.network).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer, modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(Format.clock(now - active.started), style = MaterialTheme.typography.displaySmall, color = c.onPrimaryContainer)
                        val first = active.samples.firstOrNull()
                        if (first != null && last != null) Text("${first.level} → ${last.level}%", style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        if (active.conditions.reviewRecording) "Review mode · 15-second target" else "App-open recording · samples while Volta is open",
                        style = MaterialTheme.typography.bodySmall, color = c.onPrimaryContainer,
                    )
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Finish and save", { vm.finishRun(active.id) }, Modifier.weight(1f))
                    }
                }
            }

            VCard(onClick = { go("bench") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(VIcons.Play, c.secondaryContainer, c.onSecondaryContainer, RoundedCornerShape(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App benchmark", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Chrome, Subway Surfers, X, camera, TikTok, Maps, Meet, YouTube, Instagram and YouTube Music for an hour each, on repeat, run by Volta",
                            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        )
                    }
                    Icon(VIcons.Chevron, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            if (snap.runs.none { !it.active } && active == null) {
                VCard {
                    IconBadge(VIcons.Lab, c.tertiaryContainer, c.onTertiaryContainer, RoundedCornerShape(16.dp))
                    Text("Your next review starts here", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Fill in your test conditions, then save a preset to reuse them next time. Saved reviews, presets, comparison charts and exports live here.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                }
            }
        }, end = {
            val finished = snap.runs.filter { !it.active }
            if (finished.isNotEmpty()) {
                SectionTitle("Saved runs (${finished.size})")
                GroupCard {
                    finished.forEachIndexed { i, r ->
                        if (i > 0) GroupDivider()
                        val drain = r.conditions.type == TestType.DRAIN
                        ListRow(
                            r.conditions.protocol.ifBlank { if (drain) "Drain test" else "Charging test" } + if (drain) " · drain" else " · charge",
                            listOfNotNull(Fmt.day(r.started, snap.zone), r.ended?.let { Format.duration(it - r.started) }, r.conditions.phone.takeIf { it.isNotBlank() }).joinToString(" · "),
                            leading = {
                                IconBadge(
                                    if (drain) VIcons.Battery else VIcons.Bolt,
                                    if (drain) c.tertiaryContainer else c.secondaryContainer,
                                    if (drain) c.onTertiaryContainer else c.onSecondaryContainer,
                                    if (drain) RoundedCornerShape(12.dp) else CircleShape, 40.dp,
                                )
                            },
                            trailing = { Icon(VIcons.Chevron, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                            onClick = { go("run/${r.id}") },
                        )
                    }
                }
            }
            if (snap.presets.isNotEmpty()) {
                SectionTitle("Test presets")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, _) in snap.presets) SelectChip(name, false, { creating = true })
                }
            }
        })

        if (active == null) {
            PillButton(
                "New test", { creating = true },
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp)
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .height(60.dp),
                icon = VIcons.Plus,
            )
        }
    }

    if (creating) NewRunSheet(snap, s, vm, onDismiss = { creating = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRunSheet(snap: Snapshot, s: VoltaSettings, vm: VoltaViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val last = snap.runs.firstOrNull()?.conditions
    var cond by remember {
        mutableStateOf(
            Conditions(
                phone = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                software = "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}",
                reviewRecording = s.reviewRecordingDefault,
                brightness = last?.brightness.orEmpty(),
                refreshRate = last?.refreshRate.orEmpty(),
                resolution = last?.resolution.orEmpty(),
                network = last?.network.orEmpty(),
                performance = last?.performance.orEmpty(),
            ),
        )
    }
    var presetName by remember { mutableStateOf("") }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) vm.say("Notification permission was not granted. You can still start a run with review recording turned off.")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New review run", style = MaterialTheme.typography.headlineMedium)
            if (snap.presets.isNotEmpty()) {
                Text("Load test preset", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, p) in snap.presets) {
                        SelectChip(name, false, {
                            cond = p.copy(phone = cond.phone, software = cond.software)
                            presetName = name
                        })
                    }
                }
            }
            ConnectedButtons(listOf("Drain test", "Charging test"), if (cond.type == TestType.DRAIN) 0 else 1, { cond = cond.copy(type = if (it == 0) TestType.DRAIN else TestType.CHARGE) })
            Field("Test protocol / workload", cond.protocol, "e.g. Video loop, gaming, web browsing") { cond = cond.copy(protocol = it) }
            Field("Phone", cond.phone) { cond = cond.copy(phone = it) }
            Field("Software", cond.software) { cond = cond.copy(software = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Brightness", cond.brightness, "e.g. 200 nits", Modifier.weight(1f)) { cond = cond.copy(brightness = it) }
                Field("Refresh rate", cond.refreshRate, "e.g. 120 Hz", Modifier.weight(1f)) { cond = cond.copy(refreshRate = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Resolution", cond.resolution, "e.g. FHD+", Modifier.weight(1f)) { cond = cond.copy(resolution = it) }
                Field("Network", cond.network, "e.g. Wi-Fi", Modifier.weight(1f)) { cond = cond.copy(network = it) }
            }
            Field("Performance mode", cond.performance, "e.g. Standard") { cond = cond.copy(performance = it) }
            if (cond.type == TestType.CHARGE) Field("Charger and cable", cond.charger, "e.g. 45 W PPS, 1 m cable") { cond = cond.copy(charger = it) }
            Field("Notes / ambient temperature", cond.notes, singleLine = false) {
                if (it.length <= 4000) {
                    cond = cond.copy(notes = it)
                } else {
                    vm.say("Test notes are too long.")
                }
            }
            GroupCard {
                SwitchRow(
                    "Review recording for this run",
                    "Samples every 15 seconds with a visible notification. Off: samples only while Volta is open.",
                    cond.reviewRecording,
                ) { on ->
                    cond = cond.copy(reviewRecording = on)
                    if (on && !Notifier.canPost(context) && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Preset name", presetName, modifier = Modifier.weight(1f)) { presetName = it }
                PillButton("Save preset", { vm.savePreset(presetName, cond) }, tonal = true)
            }
            Muted("Presets keep conditions, notes and recording mode. The phone and software are filled from the new run. Saving an existing name updates that preset.")
            PillButton(
                "Start test",
                {
                    if (cond.protocol.isBlank()) {
                        vm.say("Enter a test protocol first.")
                    } else {
                        vm.startRun(cond)
                        onDismiss()
                    }
                },
                Modifier.fillMaxWidth(),
                icon = VIcons.Play,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

/** A short label for a run's conditions. */
internal fun conditionsLine(c: Conditions): String =
    listOf(c.brightness, c.refreshRate, c.resolution, c.network, c.performance, c.charger).filter { it.isNotBlank() }.joinToString(" · ")

internal fun rateLine(type: TestType, perHour: Double?): String =
    perHour?.let { (if (type == TestType.DRAIN) "−" else "+") + Format.rate(it) } ?: "Need 10+ min"

