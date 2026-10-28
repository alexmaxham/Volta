package com.alexmaxham.volta.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.alexmaxham.volta.core.Charger
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.Source
import kotlin.math.abs
import kotlin.math.roundToInt

object BatteryReader {
    fun read(context: Context, source: Source, now: Long = System.currentTimeMillis()): Reading? {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return null
        return fromIntent(context, intent, source, now)
    }

    fun fromIntent(context: Context, intent: Intent, source: Source, now: Long = System.currentTimeMillis()): Reading? {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        if (raw < 0) return null
        val level = (raw * 100f / scale).roundToInt().coerceIn(0, 100)
        val pluggedType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = pluggedType != 0
        val charger = when (pluggedType) {
            0 -> Charger.NONE
            BatteryManager.BATTERY_PLUGGED_AC -> Charger.AC
            BatteryManager.BATTERY_PLUGGED_USB -> Charger.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Charger.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> Charger.DOCK
            else -> Charger.UNKNOWN
        }
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE && it in -400..1000 }?.let { it / 10f }
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            .takeIf { it > 0 }?.let { if (it > 100) it / 1000f else it.toFloat() }

        val bm = context.getSystemService(BatteryManager::class.java)
        val current = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
            ?.let { normaliseCurrent(it, status, plugged) }
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
            ?.let { it / 1000f }

        return Reading(
            time = now,
            level = level,
            plugged = plugged,
            charger = charger,
            tempC = temp,
            voltageV = voltage,
            currentMa = current,
            chargeCounterMah = counter,
            elapsed = SystemClock.elapsedRealtime(),
            uptime = SystemClock.uptimeMillis(),
            source = source,
        )
    }

    /**
     * Phones disagree on units (µA or mA) and on sign. Values above 20,000 are taken as µA. The
     * sign follows the charging status: positive while charging, negative while discharging.
     */
    private fun normaliseCurrent(raw: Int, status: Int, plugged: Boolean): Int {
        val ma = if (abs(raw) > 20_000) raw / 1000 else raw
        return when {
            status == BatteryManager.BATTERY_STATUS_CHARGING -> abs(ma)
            !plugged || status == BatteryManager.BATTERY_STATUS_DISCHARGING -> -abs(ma)
            else -> ma
        }
    }
}
