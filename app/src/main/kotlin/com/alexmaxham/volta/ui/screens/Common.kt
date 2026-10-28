package com.alexmaxham.volta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Estimate
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.ui.components.CircleIconButton
import com.alexmaxham.volta.ui.components.VIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A scrolling screen with Volta's margins. Tab screens leave room for the floating nav bar. */
@Composable
fun ScreenColumn(bottom: Dp = 120.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** The large title row on tab screens. */
@Composable
fun TabTitle(title: String, sub: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        }
        actions()
    }
}

/** A back button and a title for detail screens. */
@Composable
fun DetailBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircleIconButton(VIcons.Back, "Back", onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
        actions()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(horizontal = 6.dp))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

object Fmt {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM")

    fun time(ms: Long, zone: ZoneId): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun day(ms: Long, zone: ZoneId): String = dayShort.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun longDay(ms: Long, zone: ZoneId): String = dayLong.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "Today, 6:02 AM", "Yesterday, 11:40 PM" or "Mon 27 Oct, 9:15 AM". */
    fun whenAt(ms: Long, now: Long, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day(ms, zone)
        }
        return "$day, ${time(ms, zone)}"
    }

    fun estimate(e: Estimate, charging: Boolean = false): String = when (e) {
        is Estimate.Remaining -> if (e.millis >= com.alexmaxham.volta.core.Drain.MAX_REMAINING) "Over 3 days at this pace" else "About ${Format.duration(e.millis)} left"
        is Estimate.UntilFull -> "Full in ${Format.duration(e.millis)}"
        Estimate.Full -> "Fully charged"
        is Estimate.Learning -> if (charging) "Estimating time to full" else "Learning your battery"
    }

    fun status(r: Reading?): String = when {
        r == null -> "Battery"
        r.plugged && r.level >= 100 -> "Fully charged"
        r.plugged && (r.currentMa ?: 1) > 0 -> "Charging"
        r.plugged -> "Plugged in"
        else -> "On battery"
    }

    fun pts(n: Int): String = if (n == 1) "1 pt" else "$n pts"

    fun signedPts(n: Int): String = if (n > 0) "−${pts(n)}" else pts(-n)
}
