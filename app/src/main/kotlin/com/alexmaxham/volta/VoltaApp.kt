package com.alexmaxham.volta

import android.app.Application
import com.alexmaxham.volta.data.SettingsStore
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.work.Notifier
import com.alexmaxham.volta.work.SamplingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VoltaApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        scope.launch {
            SettingsStore(this@VoltaApp).flow
                .map { needsBackground(it) }
                .distinctUntilChanged()
                .collect { SamplingWorker.schedule(this@VoltaApp, it) }
        }
    }

    companion object {
        /** Reminders and the morning summary also rely on the background check. */
        fun needsBackground(s: VoltaSettings): Boolean =
            s.backgroundLogging || s.morningSummary || s.alerts.lowEnabled || s.alerts.chargeEnabled || s.alerts.warmEnabled
    }
}
