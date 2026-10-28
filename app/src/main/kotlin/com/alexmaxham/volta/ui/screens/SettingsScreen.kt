package com.alexmaxham.volta.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.data.Scheme
import com.alexmaxham.volta.data.ThemeMode
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.Snapshot
import com.alexmaxham.volta.ui.VoltaViewModel
import com.alexmaxham.volta.ui.components.ChevronRow
import com.alexmaxham.volta.ui.components.ConnectedButtons
import com.alexmaxham.volta.ui.components.GroupCard
import com.alexmaxham.volta.ui.components.GroupDivider
import com.alexmaxham.volta.ui.components.ListRow
import com.alexmaxham.volta.ui.components.SwitchRow
import com.alexmaxham.volta.ui.components.VCard
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.ui.components.ValueRow
import com.alexmaxham.volta.ui.theme.ExpressiveShapes
import com.alexmaxham.volta.ui.theme.MorphShape
import com.alexmaxham.volta.ui.theme.Palettes
import com.alexmaxham.volta.widget.VoltaWidgetReceiver
import com.alexmaxham.volta.work.Notifier
import kotlin.math.roundToInt

private sealed interface Editing {
    data object Design : Editing
    data object Low : Editing
    data object Charge : Editing
    data object Warm : Editing
    data object ClearHistory : Editing
    data object ClearCapacity : Editing
}

@Composable
fun SettingsScreen(snap: Snapshot, s: VoltaSettings, vm: VoltaViewModel, go: (String) -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<Editing?>(null) }
    var pendingNotify by remember { mutableStateOf<((VoltaSettings) -> VoltaSettings)?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val change = pendingNotify
        pendingNotify = null
        if (ok && change != null) vm.update(change)
        else if (!ok) vm.say("Notification permission is needed for reminders. You can enable it in Android Settings.")
    }

    /** Applies a change that turns on a notification, asking for permission first when needed. */
    fun withNotify(change: (VoltaSettings) -> VoltaSettings) {
        if (Notifier.canPost(context) || Build.VERSION.SDK_INT < 33) {
            vm.update(change)
        } else {
            pendingNotify = change
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AdaptiveScreen(start = {
        TabTitle("Settings")

        Header("Make it yours")
        VCard {
            Text("Color scheme", style = MaterialTheme.typography.titleMedium)
            Text(
                if (s.scheme == Scheme.DYNAMIC) "Matches your wallpaper." else "Five curated palettes, or match your wallpaper.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            val schemes = buildList {
                if (Build.VERSION.SDK_INT >= 31) add(Scheme.DYNAMIC)
                addAll(Palettes.curated)
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                schemes.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        for (sc in row) Swatch(sc, sc == s.scheme, Modifier.weight(1f)) { vm.update { it.copy(scheme = sc) } }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
        VCard {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ConnectedButtons(
                listOf("System", "Light", "Dark"),
                s.themeMode.ordinal,
                { i -> vm.update { it.copy(themeMode = ThemeMode.entries[i]) } },
                Modifier.padding(top = 12.dp),
            )
            Text(
                "Dark uses a true-black AMOLED background with dim cards. System follows your phone's theme.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
            )
        }
        GroupCard {
            SwitchRow("Use Fahrenheit", "Show temperatures in °F", s.fahrenheit) { on -> vm.update { it.copy(fahrenheit = on) } }
        }

        Header("Tracking")
        GroupCard {
            SwitchRow(
                "Background logging",
                if (s.backgroundLogging) "A reading about every 15 minutes, even when the app is closed. Android may delay readings during sleep or battery saving."
                else "Readings are saved only while Volta is open.",
                s.backgroundLogging,
            ) { on -> vm.update { it.copy(backgroundLogging = on) } }
            GroupDivider()
            ListRow(
                "Usage access",
                if (snap.usageAccess) "Usage access is enabled." else "Needed for screen-on time and apps. No root required.",
                onClick = { runCatching { context.startActivity(UsageReader.accessIntent()) } },
                trailing = {
                    Text(
                        if (snap.usageAccess) "On" else "Off",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (snap.usageAccess) c.primary else c.onSurfaceVariant,
                    )
                },
            )
        }
    }, end = {
        Header("Battery")
        GroupCard {
            ValueRow(
                "Original design capacity",
                "The manufacturer's rated capacity, used to calculate health",
                s.designMah?.let { Format.mah(it.toFloat()) } ?: "Add",
            ) { editing = Editing.Design }
            GroupDivider()
            val h = snap.health
            ValueRow(
                "Estimated full capacity",
                h?.let { "From ${it.samples} charge${if (it.samples == 1) "" else "s"} of 30+ points" }
                    ?: "Charge 30+ points over 20+ minutes with background logging on, then unplug or reach 100%.",
                h?.let { Format.mah(it.estimateMah) } ?: "Learning",
            )
            GroupDivider()
            ChevronRow("Clear capacity estimates", "For a replacement battery") { editing = Editing.ClearCapacity }
        }

        Header("Widgets & reminders")
        GroupCard {
            SwitchRow("Low battery reminder", "At ${s.alerts.lowLevel}% or below · tap to change", s.alerts.lowEnabled) { on ->
                if (on) withNotify { it.copy(alerts = it.alerts.copy(lowEnabled = true)) } else vm.update { it.copy(alerts = it.alerts.copy(lowEnabled = false)) }
            }
            ChevronRow("Low battery level", "${s.alerts.lowLevel}%") { editing = Editing.Low }
            GroupDivider()
            SwitchRow("Charge reminder", "When charging reaches ${s.alerts.chargeLevel}%", s.alerts.chargeEnabled) { on ->
                if (on) withNotify { it.copy(alerts = it.alerts.copy(chargeEnabled = true)) } else vm.update { it.copy(alerts = it.alerts.copy(chargeEnabled = false)) }
            }
            ChevronRow("Charge reminder level", "${s.alerts.chargeLevel}%") { editing = Editing.Charge }
            GroupDivider()
            SwitchRow("Warm battery alert", "When the battery passes ${Format.temp(s.alerts.warmC, s.fahrenheit)}", s.alerts.warmEnabled) { on ->
                if (on) withNotify { it.copy(alerts = it.alerts.copy(warmEnabled = true)) } else vm.update { it.copy(alerts = it.alerts.copy(warmEnabled = false)) }
            }
            ChevronRow("Alert temperature", Format.temp(s.alerts.warmC, s.fahrenheit)) { editing = Editing.Warm }
            GroupDivider()
            SwitchRow("Morning summaries", "Your overnight report as a notification", s.morningSummary) { on ->
                if (on) withNotify { it.copy(morningSummary = true) } else vm.update { it.copy(morningSummary = false) }
            }
            GroupDivider()
            ChevronRow("Add home-screen widget") {
                val mgr = AppWidgetManager.getInstance(context)
                if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(context, VoltaWidgetReceiver::class.java), null, null)
                else vm.say("Long-press your home screen, open Widgets, and choose Volta.")
            }
            GroupDivider()
            ChevronRow("Open notification settings") {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        Muted("Reminders are checked with each background reading, so they can arrive a few minutes late.")

        Header("Your data, your device")
        GroupCard {
            ChevronRow("Clear battery history", "Readings, charging history and the last full charge", titleColor = c.error) { editing = Editing.ClearHistory }
            GroupDivider()
            ChevronRow("App info") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Muted("Everything Volta records stays on this phone. Review runs stay saved until you delete them in Lab.")

        // Lab is for people reviewing phones, so it lives here rather than in the nav bar.
        Header("Advanced")
        GroupCard {
            ChevronRow("Lab", "Timed drain and charging tests with recorded conditions, for reviewing phones") { go("lab") }
            GroupDivider()
            SwitchRow(
                "Review recording by default",
                "New Lab runs record every 15 seconds with a visible notification",
                s.reviewRecordingDefault,
            ) { on -> if (on) withNotify { it.copy(reviewRecordingDefault = true) } else vm.update { it.copy(reviewRecordingDefault = false) } }
        }
        Text(
            "Volta ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    })

    when (editing) {
        Editing.Design -> NumberDialog(
            "Original design capacity (mAh)",
            "Use the manufacturer's original rated capacity for this phone, not its remaining charge. Leave blank to remove.",
            s.designMah?.toString().orEmpty(),
            onDismiss = { editing = null },
        ) { text ->
            val v = text.filter { it.isDigit() }.toIntOrNull()
            when {
                text.isBlank() -> { vm.update { it.copy(designMah = null) }; editing = null }
                v == null || v !in 500..30_000 -> vm.say("Enter 500–30,000 mAh, or leave blank to remove.")
                else -> { vm.update { it.copy(designMah = v) }; vm.say("Design capacity saved."); editing = null }
            }
        }
        Editing.Low -> SliderDialog("Low battery reminder", s.alerts.lowLevel.toFloat(), 5f..50f, { "${it.roundToInt()}%" }, { editing = null }) { v ->
            vm.update { it.copy(alerts = it.alerts.copy(lowLevel = v.roundToInt())) }
        }
        Editing.Charge -> SliderDialog("Charge reminder", s.alerts.chargeLevel.toFloat(), 50f..95f, { "${it.roundToInt()}%" }, { editing = null }) { v ->
            vm.update { it.copy(alerts = it.alerts.copy(chargeLevel = v.roundToInt())) }
        }
        Editing.Warm -> SliderDialog("Warm battery alert", s.alerts.warmC, 35f..50f, { Format.temp(it.roundToInt().toFloat(), s.fahrenheit) }, { editing = null }) { v ->
            vm.update { it.copy(alerts = it.alerts.copy(warmC = v.roundToInt().toFloat())) }
        }
        Editing.ClearHistory -> Confirm(
            "Clear battery history?",
            "This deletes saved battery readings, derived charging history and the last full-charge time from this phone. Review runs are kept. New readings continue according to your settings.",
            "Clear history", { editing = null },
        ) { vm.clearHistory() }
        Editing.ClearCapacity -> Confirm(
            "Clear capacity estimates?",
            "Use this after a battery replacement. New estimates will be learned from future charges.",
            "Clear", { editing = null },
        ) { vm.clearCapacity() }
        null -> Unit
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

private val swatchMorph = Morph(ExpressiveShapes.circle, ExpressiveShapes.cookie9)

/** A palette swatch that morphs from a circle into the scalloped hero shape when selected. */
@Composable
private fun Swatch(scheme: Scheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val (a, b, d) = remember(scheme) { Palettes.swatch(context, scheme) }
    val progress by animateFloatAsState(if (selected) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "swatch")
    val name = scheme.name.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "$name color scheme" }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp).clip(MorphShape(swatchMorph, progress, progress * 20f))) {
                drawRect(a, size = Size(size.width, size.height / 2))
                drawRect(b, topLeft = Offset(0f, size.height / 2), size = Size(size.width / 2, size.height / 2))
                drawRect(d, topLeft = Offset(size.width / 2, size.height / 2), size = Size(size.width / 2, size.height / 2))
            }
            if (selected) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(VIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun NumberDialog(title: String, body: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.take(6) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("mAh") })
            }
        },
        confirmButton = { TextButton({ onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SliderDialog(title: String, initial: Float, range: ClosedFloatingPointRange<Float>, label: (Float) -> String, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(initial.coerceIn(range)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label(v), style = MaterialTheme.typography.displaySmall)
                Slider(v, { v = it }, valueRange = range, steps = (range.endInclusive - range.start).toInt() - 1)
            }
        },
        confirmButton = {
            TextButton({
                onSave(v)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Confirm(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton({
                onConfirm()
                onDismiss()
            }) { Text(action, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
