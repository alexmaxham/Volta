package com.alexmaxham.volta.bench

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A walk from Penn Station to Grand Central at walking pace, fed to the phone as a test location,
 * so Google Maps navigates it turn by turn while the phone sits still. It needs Volta chosen as
 * the mock location app in Developer options.
 */
class MockWalk(context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private var started = false

    /** Takes over the phone's location providers. False when Volta isn't allowed to. */
    fun start(): Boolean {
        if (started) return true
        val lm = lm ?: return false
        started = PROVIDERS.all { p ->
            runCatching {
                runCatching { lm.removeTestProvider(p) }
                @Suppress("DEPRECATION")
                lm.addTestProvider(p, false, false, false, false, true, true, true, android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
                lm.setTestProviderEnabled(p, true)
            }.isSuccess
        }
        if (!started) stop()
        return started
    }

    /** Puts the phone [walked] ms into the walk; past the end it waits at Grand Central. */
    fun at(walked: Long) {
        if (!started) return
        val lm = lm ?: return
        val (lat, lon, bearing) = point(walked * SPEED / 1000.0)
        val now = System.currentTimeMillis()
        for (p in PROVIDERS) {
            val loc = Location(p).apply {
                latitude = lat
                longitude = lon
                accuracy = 4f
                altitude = 15.0
                speed = if (walked * SPEED / 1000.0 < LENGTH) SPEED.toFloat() else 0f
                this.bearing = bearing
                time = now
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            runCatching { lm.setTestProviderLocation(p, loc) }
        }
    }

    /** Gives the phone its real location back. */
    fun stop() {
        val lm = lm ?: return
        for (p in PROVIDERS) runCatching { lm.removeTestProvider(p) }
        started = false
    }

    companion object {
        /** Walking pace, metres a second. */
        const val SPEED = 1.4

        /** Penn Station, east along 34th Street, up Fifth Avenue, east along 42nd Street to Grand Central. */
        private val ROUTE = listOf(
            40.7506 to -73.9910,
            40.7511 to -73.9905,
            40.7486 to -73.9849,
            40.7531 to -73.9817,
            40.7527 to -73.9772,
        )

        val PROVIDERS = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        }

        private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dy = (b.first - a.first) * 111_320.0
            val dx = (b.second - a.second) * 111_320.0 * cos(Math.toRadians(a.first))
            return sqrt(dx * dx + dy * dy)
        }

        /** The walk's length in metres. */
        val LENGTH: Double = ROUTE.zipWithNext().sumOf { (a, b) -> metres(a, b) }

        /** How long the walk takes, in ms. */
        val DURATION: Long = (LENGTH / SPEED * 1000).toLong()

        /** Latitude, longitude and heading [d] metres along the route. */
        private fun point(d: Double): Triple<Double, Double, Float> {
            var left = d.coerceAtLeast(0.0)
            for ((a, b) in ROUTE.zipWithNext()) {
                val len = metres(a, b)
                val f = if (left >= len) 1.0 else left / len
                val bearing = Math.toDegrees(atan2((b.second - a.second) * cos(Math.toRadians(a.first)), b.first - a.first)).toFloat().let { (it + 360f) % 360f }
                if (left < len) return Triple(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f, bearing)
                left -= len
            }
            val (lat, lon) = ROUTE.last()
            return Triple(lat, lon, 0f)
        }

        /** Whether Volta is the mock location app, so Maps can walk the route. */
        fun allowed(context: Context): Boolean = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
