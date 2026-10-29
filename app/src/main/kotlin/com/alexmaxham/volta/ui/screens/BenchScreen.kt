package com.alexmaxham.volta.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexmaxham.volta.bench.Bench
import com.alexmaxham.volta.bench.Workloads
import com.alexmaxham.volta.core.BenchStep
import com.alexmaxham.volta.core.Benchmark
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.GroupDivider
import com.alexmaxham.volta.ui.components.ListRow
import com.alexmaxham.volta.ui.components.PillButton
import com.alexmaxham.volta.ui.components.SectionTitle
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.work.Notifier
import kotlinx.coroutines.delay

@Composable
fun BenchScreen(snap: Snapshot, live: Reading?, vm: VoltaViewModel, go: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var steps by remember { mutableStateOf<List<BenchStep>?>(null) }
    var accessibility by remember { mutableStateOf(Bench.enabled(context)) }
    var notify by remember { mutableStateOf(Notifier.canPost(context)) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember { mutableStateOf<BenchStep?>(null) }
    var checklist by remember { mutableStateOf(false) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = Notifier.canPost(context) }

    LaunchedEffect(Unit) { vm.loadBenchSteps { steps = it } }
    // Settings change outside the app, so check again while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            accessibility = Bench.enabled(context)
            notify = Notifier.canPost(context)
            installed = Benchmark.DEFAULT.filter { Workloads.installed(context, it.id) }.map { it.id }.toSet()
            delay(2_000)
        }
    }

    val active = snap.activeRun
    val list = steps.orEmpty()
    val runnable = list.filter { it.id in installed }
    val ready = accessibility && notify && runnable.isNotEmpty() && active == null

    AdaptiveScreen(bottom = 32.dp, start = {
        DetailBar("App benchmark", onBack)
        Muted("Volta opens each app below for an hour, in order, then starts again, until the battery reaches ${Benchmark.STOP_LEVEL}% or a charger is connected. It scrolls, swipes and taps in each app and records the level, charge, power and temperature every 15 seconds. You get the drain for each app.")

        if (active != null) {
            VCard(color = c.primaryContainer, onClick = { go("run/${active.id}") }) {
                Text(
                    if (active.conditions.protocol == Benchmark.PROTOCOL) "BENCHMARK RUNNING · tap for live results" else "A LAB RUN IS IN PROGRESS",
                    style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer,
                )
                Text(
                    if (active.conditions.protocol == Benchmark.PROTOCOL) "To stop early, tap Finish and save in the notification." else "Finish it before starting a benchmark.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionTitle("Set up once")
        GroupCard {
            ListRow(
                "Turn on the benchmark runner",
                "Accessibility → Volta app benchmark. It lets Volta open apps, scroll and tap. If it's greyed out, open App info for Volta, tap ⋮ and Allow restricted settings, then try again.",
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
                trailing = { Status(accessibility) },
            )
            GroupDivider()
            ListRow(
                "Allow notifications",
                "The run records in the background with a visible notification, where you can also stop it.",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !notify) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                },
                trailing = { Status(notify) },
            )
            GroupDivider()
            ListRow(
                "Open each app once",
                "Sign in and close any welcome or update screens, so each app opens straight to its feed. Pause Play Store auto-updates for the test.",
            )
        }
    }, end = {
        SectionTitle("Apps · 1 hour each, in this order")
        GroupCard {
            list.forEachIndexed { i, st ->
                if (i > 0) GroupDivider()
                val has = st.id in installed
                val link = Workloads.takesLink(st.id)
                ListRow(
                    st.name,
                    if (!has) "Not installed · skipped"
                    else Workloads.hint(st.id) + if (link) "\n" + (if (st.link == Benchmark.defaultLink(st.id)) "Default: " else "Your link: ") + st.link else "",
                    leading = {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(if (has) c.secondaryContainer else c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (has) c.onSecondaryContainer else c.onSurfaceVariant)
                        }
                    },
                    trailing = { if (link && has) Text("Change", style = MaterialTheme.typography.labelLarge, color = c.primary) },
                    onClick = if (link && has) { { editing = st } } else null,
                )
            }
        }
        Muted("4K 60 fps video can fill 20 GB or more an hour, so free up space first; many camera apps stop recording when storage runs low or the phone gets hot. Results depend on what each feed shows, so compare runs with care.")
        PillButton(
            if (active == null) "Start benchmark" else "A run is in progress",
            { checklist = true },
            Modifier.fillMaxWidth(),
            icon = VIcons.Play,
            enabled = ready,
        )
        if (active == null && !ready) {
            Muted(
                when {
                    !accessibility -> "Turn on the benchmark runner to start."
                    !notify -> "Allow notifications to start."
                    else -> "Install at least one of the apps to start."
                },
            )
        }
    })

    editing?.let { st ->
        LinkDialog(st, onDismiss = { editing = null }) { link ->
            vm.setBenchLink(st.id, link) { vm.loadBenchSteps { steps = it } }
            editing = null
        }
    }

    if (checklist) {
        ChecklistDialog(
            live = live,
            camera = runnable.any { it.id == "camera" },
            onDismiss = { checklist = false },
            onStart = {
                checklist = false
                vm.startBenchmark(runnable)
            },
        )
    }
}

@Composable
private fun Status(ok: Boolean) {
    Text(
        if (ok) "On" else "Set up",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

/** Changes the link a step plays. Saving an empty link, or Use default, goes back to the default. */
@Composable
private fun LinkDialog(step: BenchStep, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val default = Benchmark.defaultLink(step.id)
    var text by remember { mutableStateOf(step.link) }
    val valid = text.isBlank() || (text.trim().startsWith("https://") && text.length <= 500)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${step.name} link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (if (step.id == "youtube") "Paste a video at least an hour long" else "Paste a YouTube Music playlist or album link") +
                        ". Use the same link on every phone you compare, so each plays the same thing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    text, { text = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("https://…") },
                    isError = !valid,
                    supportingText = { if (!valid) Text("Use a https:// link.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                if (step.link != default) TextButton({ onSave("") }) { Text("Use default") }
                TextButton({ onSave(text) }, enabled = valid) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** The checks to confirm before a benchmark starts. Brightness mode and battery are checked live. */
@Composable
private fun ChecklistDialog(live: Reading?, camera: Boolean, onDismiss: () -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    var auto by remember { mutableStateOf(Bench.autoBrightness(context)) }
    var autoTicked by rememberSaveable { mutableStateOf(false) }
    var nits by rememberSaveable { mutableStateOf(false) }
    var cameraSet by rememberSaveable { mutableStateOf(false) }
    var white by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            auto = Bench.autoBrightness(context)
            delay(1_000)
        }
    }

    // When the phone doesn't say whether adaptive brightness is on, the user ticks it instead.
    val autoOff = if (auto == null) autoTicked else auto == false
    val level = live?.level
    val charged = level == 100 && live?.plugged == false
    val all = autoOff && nits && charged && (!camera || cameraSet)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you start") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CheckItem(
                    "Auto brightness is off",
                    when (auto) {
                        null -> "Turn off adaptive brightness in Display settings, then tick this."
                        true -> "Adaptive brightness is still on."
                        false -> "Adaptive brightness is off."
                    },
                    autoOff,
                    onToggle = if (auto == null) { { autoTicked = it } } else null,
                    action = "Display settings" to { runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } },
                )
                CheckItem(
                    "Brightness set to 200 nits with a lux meter",
                    "Show a white screen and adjust the brightness slider until your meter reads 200 nits. A lux meter held flat against the screen reads roughly 630 lux at 200 nits.",
                    nits,
                    onToggle = { nits = it },
                    action = "White screen" to { white = true },
                )
                CheckItem(
                    "Charged to 100%",
                    when {
                        level == null -> "Reading the battery…"
                        level < 100 -> "At $level%. Charge to 100%, then unplug."
                        live?.plugged == true -> "At 100%. Unplug the charger to start."
                        else -> "At 100% and unplugged."
                    },
                    charged,
                )
                if (camera) {
                    CheckItem(
                        "Camera set to 4K 60 fps",
                        "Open your camera app, switch to video and choose 4K at 60 fps. Volta can't change this for you.",
                        cameraSet,
                        onToggle = { cameraSet = it },
                    )
                }
                Text(
                    "Keep the phone still in a room at a steady temperature, with a case off if you can. Once it starts, leave it until it finishes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onStart, enabled = all) { Text("Start benchmark") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )

    if (white) WhiteScreen { white = false }
}

/** One checklist line. Without [onToggle] the box is ticked by Volta's own check. */
@Composable
private fun CheckItem(title: String, sub: String, checked: Boolean, onToggle: ((Boolean) -> Unit)? = null, action: Pair<String, () -> Unit>? = null) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, onCheckedChange = onToggle)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onToggle == null) {
                Text(if (checked) "Checked by Volta" else "Volta checks this for you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (action != null) {
                Text(
                    action.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = action.second).padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Full white, for measuring brightness. Tap anywhere to close. */
@Composable
private fun WhiteScreen(onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Text("Tap to close", color = Color(0xFF9E9E9E), style = MaterialTheme.typography.labelLarge, modifier = Modifier.navigationBarsPadding().padding(24.dp))
        }
    }
}
