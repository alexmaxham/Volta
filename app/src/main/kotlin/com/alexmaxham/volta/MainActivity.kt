package com.alexmaxham.volta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.ui.VoltaRoot
import com.alexmaxham.volta.ui.VoltaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: VoltaViewModel by viewModels()
    private val route = MutableStateFlow<String?>(null)

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            BatteryReader.fromIntent(context, intent, Source.APP)?.let(vm::onBattery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route.value = intent?.getStringExtra(EXTRA_ROUTE)
        vm.readNow()
        vm.refresh()
        setContent {
            VoltaRoot(vm, route, onDarkChanged = ::applySystemBars)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContextCompat.registerReceiver(this@MainActivity, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
                try {
                    while (true) {
                        vm.tick()
                        delay(60_000)
                    }
                } finally {
                    unregisterReceiver(battery)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
