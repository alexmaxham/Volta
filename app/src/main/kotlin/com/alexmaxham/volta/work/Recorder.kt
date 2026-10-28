package com.alexmaxham.volta.work

import android.content.Context
import com.alexmaxham.volta.core.Alert
import com.alexmaxham.volta.core.AlertLatch
import com.alexmaxham.volta.core.Alerts
import com.alexmaxham.volta.core.Apps
import com.alexmaxham.volta.core.Capacity
import com.alexmaxham.volta.core.Charging
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.History
import com.alexmaxham.volta.core.Night
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.data.Db
import com.alexmaxham.volta.data.Json
import com.alexmaxham.volta.data.SettingsStore
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.widget.VoltaWidget
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Takes a reading and runs everything that follows from one. Safe to call from any thread but the main one. */
object Recorder {
    private val lock = Any()

    /** With [store] off the reading only drives reminders and is not saved. */
    suspend fun record(context: Context, source: Source, now: Long = System.currentTimeMillis(), store: Boolean = true): Reading? {
        val reading = BatteryReader.read(context, source, now) ?: return null
        val db = Db.get(context)
        val settings = SettingsStore(context).current()
        synchronized(lock) {
            if (!store) {
                alerts(context, db, reading, settings.alerts, settings.fahrenheit)
                return reading
            }
            db.insertReading(reading)
            if (now - (db.get("pruned")?.toLongOrNull() ?: 0L) > Time.DAY) {
                db.prune(now)
                db.put("pruned", now.toString())
            }
            learnCapacity(db, now)
            snapshotCycleApps(context, db, now)
            alerts(context, db, reading, settings.alerts, settings.fahrenheit)
        }
        runCatching { VoltaWidget.refresh(context) }
        return reading
    }

    /** Turns finished charging sessions into capacity estimates, each session once. */
    private fun learnCapacity(db: Db, now: Long) {
        val done = db.get("capacity_session")?.toLongOrNull() ?: 0L
        val sessions = Charging.sessions(db.readings(now - 3 * Time.DAY)).filter { !it.ongoing && it.end > done }
        for (s in sessions) Capacity.fromSession(s)?.let { db.insertCapacity(it) }
        sessions.maxOfOrNull { it.end }?.let { db.put("capacity_session", it.toString()) }
    }

    /**
     * Android only keeps about a week of app events, so when a battery cycle ends its app usage
     * is saved for the Apps tab.
     */
    private fun snapshotCycleApps(context: Context, db: Db, now: Long) {
        val cycles = History.cycles(db.readings(now - 10 * Time.DAY), now).filter { !it.ongoing }
        if (cycles.isEmpty() || !UsageReader.hasAccess(context)) return
        val saved = db.cycleApps().keys
        for (c in cycles.take(2)) {
            if (c.start in saved || now - c.start > UsageReader.RETENTION_DAYS * Time.DAY) continue
            val events = UsageReader.events(context, c.start - Time.HOUR, c.end)
            db.saveCycleApps(c.start, c.end, Json.apps(Apps.usage(events.apps, c.start, c.end)))
        }
    }

    private fun alerts(context: Context, db: Db, r: Reading, options: com.alexmaxham.volta.core.AlertOptions, fahrenheit: Boolean) {
        val bits = db.get("alert_latch") ?: "000"
        val latch = AlertLatch(bits.getOrNull(0) == '1', bits.getOrNull(1) == '1', bits.getOrNull(2) == '1')
        val (fire, next) = Alerts.check(r, options, latch)
        db.put("alert_latch", listOf(next.low, next.charge, next.warm).joinToString("") { if (it) "1" else "0" })
        for (a in fire) when (a) {
            Alert.LOW -> Notifier.post(
                context, Notifier.ID_LOW, Notifier.CH_REMINDERS,
                "Battery is getting low · ${r.level}%",
                "Consider charging when convenient. Your reminder is set to ${options.lowLevel}%.",
            )
            Alert.CHARGE -> Notifier.post(
                context, Notifier.ID_CHARGE, Notifier.CH_REMINDERS,
                "Charge reminder · ${r.level}%",
                "Your battery reached ${options.chargeLevel}%. Unplugging now goes easy on the battery.",
            )
            Alert.WARM -> Notifier.post(
                context, Notifier.ID_WARM, Notifier.CH_REMINDERS,
                "Battery temperature is high",
                "Battery temperature · ${r.tempC?.let { Format.temp(it, fahrenheit) } ?: "high"}. Let the phone cool down before heavy use or fast charging.",
            )
        }
    }

    /** Posts the overnight report once each morning, when enough readings exist. */
    suspend fun morningSummary(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = SettingsStore(context).current()
        if (!settings.morningSummary) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val window = Night.window(today, settings.nightStart, settings.nightEnd, zone)
        if (now < window.end || now > window.end + 4 * Time.HOUR) return
        val db = Db.get(context)
        val key = "night_notified"
        if (db.get(key) == today.toString()) return
        val events = UsageReader.events(context, window.start - Time.HOUR, window.end)
        val report = Night.report(window, db.readings(window.start - Time.HOUR), events.screen, now)
        if (!report.hasData || report.coverage < 0.5) return
        db.put(key, today.toString())
        val title = when {
            report.chargingSeen -> "Charged overnight"
            report.drop != null -> "Overnight · −${report.drop} pts"
            else -> "Your overnight battery summary"
        }
        val parts = buildList {
            report.ratePerHour?.let { add(Format.rate(it) + " while you slept") }
            if (report.deepSleep.coverage >= 0.9) add("deep sleep ${(report.deepSleep.fraction * 100).roundToInt()}% of the night")
            if (report.screen.wakes > 0) add("screen woke ${report.screen.wakes} times")
        }
        Notifier.post(context, Notifier.ID_MORNING, Notifier.CH_SUMMARY, title, parts.joinToString(" · ").ifEmpty { "Tap to see last night." }, "night")
    }
}
