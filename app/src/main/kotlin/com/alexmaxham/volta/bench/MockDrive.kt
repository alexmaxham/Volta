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
 * A drive from the World Trade Center to Central Park at city traffic speed, fed to the phone as a
 * test location, so Google Maps navigates it turn by turn while the phone sits still. The map keeps
 * moving and loading new streets as it would in a car. It needs Volta chosen as the mock location
 * app in Developer options.
 */
class MockDrive(context: Context) {
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

    /** Puts the phone [driven] ms into the drive; past the end it waits at Central Park. */
    fun at(driven: Long) {
        if (!started) return
        val lm = lm ?: return
        val (lat, lon, bearing) = point(driven * SPEED / 1000.0)
        val now = System.currentTimeMillis()
        for (p in PROVIDERS) {
            val loc = Location(p).apply {
                latitude = lat
                longitude = lon
                accuracy = 4f
                altitude = 15.0
                speed = if (driven * SPEED / 1000.0 < LENGTH) SPEED.toFloat() else 0f
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
        /** City traffic speed, metres a second: 25 km/h. */
        const val SPEED = 7.0

        /**
         * The World Trade Center, up West Street and the West Side Highway, east along 57th Street
         * and up Eighth Avenue to Columbus Circle, at the corner of Central Park.
         */
        private val ROUTE = listOf(
            40.7136 to -74.0135,
            40.7174 to -74.0138,
            40.7287 to -74.0107,
            40.7410 to -74.0089,
            40.7495 to -74.0087,
            40.7623 to -74.0019,
            40.7716 to -73.9946,
            40.7703 to -73.9914,
            40.7665 to -73.9830,
            40.7681 to -73.9819,
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

        /** The drive's length in metres. */
        val LENGTH: Double = ROUTE.zipWithNext().sumOf { (a, b) -> metres(a, b) }

        /** How long the drive takes, in ms. */
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

        /** Whether Volta is the mock location app, so Maps can drive the route. */
        fun allowed(context: Context): Boolean = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
