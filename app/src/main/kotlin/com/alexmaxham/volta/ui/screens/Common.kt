package com.alexmaxham.volta.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexmaxham.volta.core.Estimate
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.ui.LocalTwoPane
import com.alexmaxham.volta.ui.LocalVoltaLayout
import com.alexmaxham.volta.ui.components.CircleIconButton
import com.alexmaxham.volta.ui.components.VIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Room at the bottom of tab screens: the floating nav bar, or a little air when the side rail is used. */
@Composable
fun tabBottom(): Dp = if (LocalVoltaLayout.current.rail) 40.dp else 120.dp

/** A scrolling screen with Volta's margins. Tab screens leave room for the floating nav bar. */
@Composable
fun ScreenColumn(bottom: Dp = tabBottom(), content: @Composable ColumnScope.() -> Unit) {
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

/**
 * A screen in two parts. On phones [start] and [end] stack in one scrolling column, just like
 * [ScreenColumn]. With room for both (tablets, unfolded foldables, phones in landscape) they sit
 * side by side and scroll on their own; across a vertical fold each half gets one pane, so
 * nothing lands in the hinge. Inside the panes [LocalTwoPane] is true.
 */
@Composable
fun AdaptiveScreen(bottom: Dp = tabBottom(), start: @Composable ColumnScope.() -> Unit, end: @Composable ColumnScope.() -> Unit) {
    val layout = LocalVoltaLayout.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        // Where the fold sits, measured from this screen's left edge.
        val fold = layout.bookFold?.let { f ->
            with(density) { (f.left - origin.x).toDp() to (f.right - origin.x).toDp() }
        }?.takeIf { (left, right) -> left >= MIN_FOLD_PANE && maxWidth - right >= MIN_FOLD_PANE }
        when {
            fold != null -> Row(Modifier.fillMaxSize()) {
                Pane(Modifier.width(fold.first), bottom, start)
                Spacer(Modifier.width(fold.second - fold.first))
                Pane(Modifier.weight(1f), bottom, end)
            }
            maxWidth >= MIN_PANE * 2 -> Row(
                Modifier.widthIn(max = MAX_TWO_PANE).fillMaxSize().align(Alignment.TopCenter),
            ) {
                Pane(Modifier.weight(1f), bottom, start)
                Pane(Modifier.weight(1f), bottom, end)
            }
            else -> ScreenColumn(bottom) {
                start()
                end()
            }
        }
    }
}

/** One scrolling half of an [AdaptiveScreen]. */
@Composable
private fun Pane(modifier: Modifier, bottom: Dp, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalTwoPane provides true) {
        Column(
            modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

/**
 * For a foldable standing half open like a laptop: [top] fills the upper half, above the hinge,
 * and [bottom] scrolls in the lower half. Falls back to one column when there's no such fold or
 * the upper half is too short.
 */
@Composable
fun TabletopScreen(top: @Composable () -> Unit, bottom: @Composable ColumnScope.() -> Unit, fallback: @Composable () -> Unit) {
    val density = LocalDensity.current
    val tabletop = LocalVoltaLayout.current.tabletop
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val fold = tabletop?.let { f -> with(density) { (f.top - origin.y).toDp() to (f.bottom - origin.y).toDp() } }
            ?.takeIf { (upper, lower) -> upper >= 240.dp && maxHeight - lower >= 200.dp }
        if (fold == null) {
            fallback()
            return@BoxWithConstraints
        }
        Box(Modifier.fillMaxWidth().height(fold.first).statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            top()
        }
        Column(
            Modifier
                .offset(y = fold.second)
                .fillMaxWidth()
                .height(maxHeight - fold.second)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = tabBottom()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = bottom)
        }
    }
}

/** The narrowest a pane may be, margins included; about a small phone. */
private val MIN_PANE = 340.dp

/** Halves of a fold may be a little narrower, so the panes can follow the hinge. */
private val MIN_FOLD_PANE = 300.dp

/** Beyond this, wide tablets keep the two panes together in the middle. */
private val MAX_TWO_PANE = 1200.dp

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

/** The title over a detail shown beside its list, where there's nothing to go back to. */
@Composable
fun PaneTitle(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
