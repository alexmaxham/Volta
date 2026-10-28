package com.alexmaxham.volta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alexmaxham.volta.core.AlertOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class Scheme { DYNAMIC, BLOOM, SAGE, OCEAN, EMBER, IRIS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the Today totals start counting from. */
enum class Range { SINCE_FULL, SINCE_MIDNIGHT }

data class VoltaSettings(
    val onboarded: Boolean = false,
    val scheme: Scheme = Scheme.BLOOM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fahrenheit: Boolean = false,
    val backgroundLogging: Boolean = true,
    val reviewRecordingDefault: Boolean = true,
    val designMah: Int? = null,
    val range: Range = Range.SINCE_FULL,
    val alerts: AlertOptions = AlertOptions(),
    val morningSummary: Boolean = false,
    val nightStart: LocalTime = LocalTime.of(23, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
)

private val Context.store: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val scheme = stringPreferencesKey("scheme")
        val themeMode = stringPreferencesKey("theme_mode")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val logging = booleanPreferencesKey("background_logging")
        val reviewDefault = booleanPreferencesKey("review_recording_default")
        val designMah = intPreferencesKey("design_mah")
        val range = stringPreferencesKey("range")
        val lowOn = booleanPreferencesKey("low_on")
        val lowLevel = intPreferencesKey("low_level")
        val chargeOn = booleanPreferencesKey("charge_on")
        val chargeLevel = intPreferencesKey("charge_level")
        val warmOn = booleanPreferencesKey("warm_on")
        val warmC = floatPreferencesKey("warm_c")
        val morning = booleanPreferencesKey("morning_summary")
        val nightStart = intPreferencesKey("night_start_min")
        val nightEnd = intPreferencesKey("night_end_min")
    }

    val flow: Flow<VoltaSettings> = context.store.data.map { read(it) }

    private fun read(p: Preferences): VoltaSettings {
        val d = VoltaSettings()
        return VoltaSettings(
            onboarded = p[K.onboarded] ?: d.onboarded,
            scheme = p[K.scheme]?.let { runCatching { Scheme.valueOf(it) }.getOrNull() } ?: d.scheme,
            themeMode = p[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            fahrenheit = p[K.fahrenheit] ?: d.fahrenheit,
            backgroundLogging = p[K.logging] ?: d.backgroundLogging,
            reviewRecordingDefault = p[K.reviewDefault] ?: d.reviewRecordingDefault,
            designMah = p[K.designMah],
            range = p[K.range]?.let { runCatching { Range.valueOf(it) }.getOrNull() } ?: d.range,
            alerts = AlertOptions(
                lowEnabled = p[K.lowOn] ?: false,
                lowLevel = p[K.lowLevel] ?: 20,
                chargeEnabled = p[K.chargeOn] ?: false,
                chargeLevel = p[K.chargeLevel] ?: 80,
                warmEnabled = p[K.warmOn] ?: false,
                warmC = p[K.warmC] ?: 40f,
            ),
            morningSummary = p[K.morning] ?: d.morningSummary,
            nightStart = p[K.nightStart]?.let { LocalTime.of(it / 60, it % 60) } ?: d.nightStart,
            nightEnd = p[K.nightEnd]?.let { LocalTime.of(it / 60, it % 60) } ?: d.nightEnd,
        )
    }

    suspend fun current(): VoltaSettings = flow.first()

    suspend fun update(block: (VoltaSettings) -> VoltaSettings) {
        context.store.edit { p ->
            val now = read(p)
            val s = block(now)
            p[K.onboarded] = s.onboarded
            p[K.scheme] = s.scheme.name
            p[K.themeMode] = s.themeMode.name
            p[K.fahrenheit] = s.fahrenheit
            p[K.logging] = s.backgroundLogging
            p[K.reviewDefault] = s.reviewRecordingDefault
            if (s.designMah != null) p[K.designMah] = s.designMah else p.remove(K.designMah)
            p[K.range] = s.range.name
            p[K.lowOn] = s.alerts.lowEnabled
            p[K.lowLevel] = s.alerts.lowLevel
            p[K.chargeOn] = s.alerts.chargeEnabled
            p[K.chargeLevel] = s.alerts.chargeLevel
            p[K.warmOn] = s.alerts.warmEnabled
            p[K.warmC] = s.alerts.warmC
            p[K.morning] = s.morningSummary
            p[K.nightStart] = s.nightStart.hour * 60 + s.nightStart.minute
            p[K.nightEnd] = s.nightEnd.hour * 60 + s.nightEnd.minute
        }
    }
}
