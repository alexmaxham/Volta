package com.alexmaxham.volta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.ui.components.FloatingNavBar
import com.alexmaxham.volta.ui.components.FloatingNavRail
import com.alexmaxham.volta.ui.components.NavItem
import com.alexmaxham.volta.ui.components.VIcons
import com.alexmaxham.volta.ui.screens.AppsScreen
import com.alexmaxham.volta.ui.screens.BenchScreen
import com.alexmaxham.volta.ui.screens.ChargingScreen
import com.alexmaxham.volta.ui.screens.CompareScreen
import com.alexmaxham.volta.ui.screens.HistoryScreen
import com.alexmaxham.volta.ui.screens.LabScreen
import com.alexmaxham.volta.ui.screens.NightScreen
import com.alexmaxham.volta.ui.screens.RunScreen
import com.alexmaxham.volta.ui.screens.SettingsScreen
import com.alexmaxham.volta.ui.screens.TodayScreen
import com.alexmaxham.volta.ui.screens.WelcomeScreen
import com.alexmaxham.volta.ui.theme.VoltaTheme
import com.alexmaxham.volta.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val tabs = listOf(
    NavItem("today", "Today", VIcons.Battery),
    NavItem("history", "History", VIcons.History),
    NavItem("apps", "Apps", VIcons.Apps),
    NavItem("settings", "Settings", VIcons.Tune),
)

/** Navigation within the app. Routes are plain strings like "today" or "run/<id>". */
class Nav(private val stack: SnapshotStateList<String>) {
    val current: String get() = stack.last()

    /** The tab this stack of screens started from. */
    val tab: String get() = stack.first()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(route: String) {
        val tab = tabs.firstOrNull { it.key == route }
        if (tab != null) {
            stack.clear()
            stack += route
        } else if (stack.last() != route) {
            stack += route
        }
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun VoltaRoot(vm: VoltaViewModel, pendingRoute: MutableStateFlow<String?>, fold: StateFlow<Fold?>, onDarkChanged: (Boolean) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentFold by fold.collectAsStateWithLifecycle()
    val s = settings ?: return
    VoltaTheme(s.scheme, s.themeMode) {
        val dark = isDark(s.themeMode)
        LaunchedEffect(dark) { onDarkChanged(dark) }
        // Surface sets the default content colour, so plain Text follows the theme in light and dark.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalVoltaLayout provides VoltaLayout(maxWidth, currentFold)) {
                    if (!s.onboarded) WelcomeScreen(vm) else Main(vm, s, pendingRoute)
                }
                // Keeps scrolled content from running under the status bar icons.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
                )
            }
        }
    }
}

@Composable
private fun Main(vm: VoltaViewModel, s: VoltaSettings, pendingRoute: MutableStateFlow<String?>) {
    val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })) {
        mutableStateListOf("today")
    }
    val nav = remember(stack) { Nav(stack) }
    val data by vm.data.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val pending by pendingRoute.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(pending) {
        pending?.let {
            nav.go(if (it == "night") "night/latest" else it)
            pendingRoute.value = null
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(nav.canGoBack) { nav.back() }

    val route = nav.current
    val rail = LocalVoltaLayout.current.rail
    val onTab = tabs.any { it.key == route }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            // With the side rail the screens sit to its right; the rail stays put on every screen.
            modifier = if (rail) Modifier.padding(start = RAIL_WIDTH) else Modifier,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(120))
            },
            label = "route",
        ) { r ->
            val snap = data
            if (snap == null) {
                Box(Modifier.fillMaxSize())
                return@AnimatedContent
            }
            val parts = r.split("/", limit = 2)
            when (parts[0]) {
                "today" -> TodayScreen(snap, live, s, vm, nav::go)
                "history" -> HistoryScreen(snap, s, vm, nav::go)
                "apps" -> AppsScreen(snap, vm)
                "lab" -> LabScreen(snap, s, vm, nav::go, nav::back)
                "bench" -> BenchScreen(snap, live, vm, nav::go, nav::back)
                "settings" -> SettingsScreen(snap, s, vm, nav::go)
                "charge" -> ChargingScreen(snap, s, parts.getOrNull(1)?.toLongOrNull(), nav::back)
                "night" -> NightScreen(snap, s, vm, parts.getOrNull(1), nav::back)
                "run" -> RunScreen(vm, s, parts.getOrNull(1) ?: "", nav::back, nav::go)
                "compare" -> CompareScreen(vm, snap, parts.getOrNull(1) ?: "", nav::back)
                else -> TodayScreen(snap, live, s, vm, nav::go)
            }
        }
        AnimatedVisibility(
            visible = rail,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).navigationBarsPadding().displayCutoutPadding().padding(start = 12.dp),
        ) {
            FloatingNavRail(tabs, nav.tab, nav::go)
        }
        AnimatedVisibility(
            visible = onTab && !rail,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            FloatingNavBar(tabs, route, nav::go)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (onTab && !rail) 96.dp else 16.dp))
    }
}

/** Room the side rail takes at the start of the window. */
private val RAIL_WIDTH = 96.dp
