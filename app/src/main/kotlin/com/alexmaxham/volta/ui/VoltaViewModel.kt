package com.alexmaxham.volta.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexmaxham.volta.core.AppEvent
import com.alexmaxham.volta.core.AppTime
import com.alexmaxham.volta.core.Apps
import com.alexmaxham.volta.core.Capacity
import com.alexmaxham.volta.core.ChargingSession
import com.alexmaxham.volta.core.Charging
import com.alexmaxham.volta.core.Conditions
import com.alexmaxham.volta.core.Cycle
import com.alexmaxham.volta.core.DayUse
import com.alexmaxham.volta.core.DeepSleep
import com.alexmaxham.volta.core.Drain
import com.alexmaxham.volta.core.Estimate
import com.alexmaxham.volta.core.Health
import com.alexmaxham.volta.core.History
import com.alexmaxham.volta.core.Limits
import com.alexmaxham.volta.core.Night
import com.alexmaxham.volta.core.NightReport
import com.alexmaxham.volta.core.Reading
import com.alexmaxham.volta.core.Review
import com.alexmaxham.volta.core.ReviewRun
import com.alexmaxham.volta.core.ScreenEvent
import com.alexmaxham.volta.core.ScreenTime
import com.alexmaxham.volta.core.ScreenTimeline
import com.alexmaxham.volta.core.SleepClock
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.core.Usage
import com.alexmaxham.volta.core.WeekComparison
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.data.Db
import com.alexmaxham.volta.data.Json
import com.alexmaxham.volta.data.SettingsStore
import com.alexmaxham.volta.data.UsageReader
import com.alexmaxham.volta.data.VoltaSettings
import com.alexmaxham.volta.work.Notifier
import com.alexmaxham.volta.work.Recorder
import com.alexmaxham.volta.work.ReviewService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Battery use, screen time and deep sleep over one window. */
data class WindowStats(val from: Long, val to: Long, val usage: Usage, val screen: ScreenTime, val sleep: DeepSleep)

data class Snapshot(
    val now: Long,
    val zone: ZoneId,
    val readings: List<Reading>,
    val screenEvents: List<ScreenEvent>,
    val appEvents: List<AppEvent>,
    val usageAccess: Boolean,
    val estimate: Estimate,
    val lastFull: Long?,
    val sinceFull: WindowStats,
    val sinceMidnight: WindowStats,
    val health: Health?,
    val sessions: List<ChargingSession>,
    val cycles: List<Cycle>,
    val daily: List<DayUse>,
    val week: WeekComparison,
    val lastNight: NightReport,
    val runs: List<ReviewRun>,
    val activeRun: ReviewRun?,
    val presets: List<Pair<String, Conditions>>,
    val cycleApps: Map<Long, Pair<Long, String>>,
)

class VoltaViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val store = SettingsStore(app)
    private val loadLock = Mutex()

    val settings: StateFlow<VoltaSettings?> = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _live = MutableStateFlow<Reading?>(null)
    val live: StateFlow<Reading?> = _live.asStateFlow()

    private val _data = MutableStateFlow<Snapshot?>(null)
    val data: StateFlow<Snapshot?> = _data.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val context get() = getApplication<Application>()

    fun say(text: String) {
        _messages.tryEmit(text)
    }

    /** Reloads everything from storage and Android's usage history. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            loadLock.withLock { _data.value = load() }
        }
    }

    private suspend fun load(): Snapshot {
        val s = store.current()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val readings = withContext(Dispatchers.IO) { db.readings(now - Limits.RETENTION) }
        val access = UsageReader.hasAccess(context)
        val events = withContext(Dispatchers.IO) { UsageReader.events(context, now - (UsageReader.RETENTION_DAYS + 1) * Time.DAY, now) }
        val all = readings + listOfNotNull(_live.value?.takeIf { l -> readings.none { it.time >= l.time } })
        val lastFull = Charging.lastFull(all)
        val today = LocalDate.now(zone)
        val midnight = History.startOfDay(today, zone)
        val interactive = context.getSystemService(android.os.PowerManager::class.java)?.isInteractive

        fun window(from: Long): WindowStats {
            val screen = ScreenTimeline.build(events.screen, from, now, interactive)
            return WindowStats(from, now, Drain.usage(all, screen.onIntervals, from, now), screen, SleepClock.measure(all, from, now))
        }

        val firstTime = all.firstOrNull()?.time ?: now
        val nightMorning = Night.window(today, s.nightStart, s.nightEnd, zone).let { if (now >= it.end) today else today.minusDays(1) }
        val nightWindow = Night.window(nightMorning, s.nightStart, s.nightEnd, zone)
        val runs = withContext(Dispatchers.IO) { db.runs(false) }
        val active = runs.firstOrNull { it.active }?.let { withContext(Dispatchers.IO) { db.run(it.id) } }

        return Snapshot(
            now = now,
            zone = zone,
            readings = all,
            screenEvents = events.screen,
            appEvents = events.apps,
            usageAccess = access,
            estimate = Drain.estimate(all),
            lastFull = lastFull,
            sinceFull = window(lastFull ?: firstTime),
            sinceMidnight = window(midnight),
            health = Capacity.health(db.capacity(), s.designMah),
            sessions = Charging.sessions(all),
            cycles = History.cycles(all, now),
            daily = History.daily(all, today, 7, zone),
            week = History.week(all, today, zone),
            lastNight = Night.report(nightWindow, all, events.screen, now),
            runs = runs,
            activeRun = active,
            presets = db.presets(),
            cycleApps = db.cycleApps(),
        )
    }

    /** Live updates from the battery broadcast while the app is open. */
    fun onBattery(reading: Reading) {
        val prev = _live.value
        _live.value = reading
        if (prev == null || prev.level != reading.level || prev.plugged != reading.plugged) save(force = prev?.plugged != reading.plugged)
    }

    /** Called about once a minute while the app is visible. */
    fun tick() {
        save(force = false)
        sampleOpenRun()
    }

    private fun save(force: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val last = db.lastReadingFrom(Source.APP)
            if (force || last == null || System.currentTimeMillis() - last.time >= Time.MINUTE) {
                Recorder.record(context, Source.APP)?.let { _live.value = it }
            }
            refresh()
        }
    }

    fun readNow() {
        BatteryReader.read(context, Source.APP)?.let { _live.value = it }
    }

    // Settings

    fun update(block: (VoltaSettings) -> VoltaSettings) {
        viewModelScope.launch { store.update(block) }
    }

    fun clearHistory() = io {
        db.clearHistory()
        say("History cleared.")
    }

    fun clearCapacity() = io {
        db.clearCapacity()
        db.put("capacity_session", System.currentTimeMillis().toString())
        say("Estimates cleared.")
    }

    // Lab

    fun startRun(conditions: Conditions) = io {
        if (db.activeRunId() != null) {
            say("Finish the current run first.")
            return@io
        }
        val now = System.currentTimeMillis()
        val run = ReviewRun(UUID.randomUUID().toString(), now, null, conditions, emptyList())
        db.insertRun(run)
        BatteryReader.read(context, Source.REVIEW, now)?.let { db.insertRunSamples(run.id, listOf(it)) }
        if (conditions.reviewRecording) {
            if (Notifier.canPost(context)) {
                runCatching { ReviewService.start(context) }.onFailure {
                    say("Run created, but recording could not start. Open the run and tap Resume recording.")
                }
            } else {
                say("Enable Volta notifications in Android Settings, or turn off review recording for this run.")
            }
        }
    }

    fun resumeRecording() {
        if (!Notifier.canPost(context)) {
            say("Enable Volta notifications in Android Settings before resuming recording.")
            return
        }
        runCatching { ReviewService.start(context) }.onFailure { say("Couldn't complete that action. Please try again.") }
    }

    fun finishRun(id: String) = io {
        val run = db.run(id) ?: return@io
        if (!run.conditions.reviewRecording) {
            BatteryReader.read(context, Source.REVIEW)?.let { db.insertRunSamples(id, listOf(it)) }
        }
        db.finishRun(id, System.currentTimeMillis())
        runCatching { ReviewService.finish(context) }
        say("Run saved. You can now export or compare it.")
    }

    fun deleteRun(id: String) = io {
        db.deleteRun(id)
        say("Run deleted.")
    }

    /** App-open recording: adds a sample to an active run that isn't using review recording. */
    fun sampleOpenRun() = io {
        val id = db.activeRunId() ?: return@io
        val run = db.run(id) ?: return@io
        if (run.conditions.reviewRecording) return@io
        BatteryReader.read(context, Source.REVIEW)?.let { db.insertRunSamples(id, listOf(it)) }
    }

    fun loadRun(id: String, into: (ReviewRun?) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { db.run(id) }
            into(r)
        }
    }

    fun loadRuns(ids: List<String>, into: (List<ReviewRun>) -> Unit) {
        viewModelScope.launch {
            into(withContext(Dispatchers.IO) { ids.mapNotNull { db.run(it) } })
        }
    }

    fun savePreset(name: String, conditions: Conditions) = io {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 80) {
            say("Use a preset name of 1–80 characters.")
            return@io
        }
        val existing = db.presets()
        if (existing.size >= 50 && existing.none { it.first == trimmed }) {
            say("You can keep up to 50 presets. Delete one first.")
            return@io
        }
        db.savePreset(trimmed, conditions.copy(phone = "", software = ""), System.currentTimeMillis())
        say("Preset saved.")
    }

    fun deletePreset(name: String) = io {
        db.deletePreset(name)
    }

    fun exportRunCsv(id: String, uri: Uri) = io {
        val run = db.run(id) ?: return@io say("This review no longer exists.")
        if (run.active) return@io say("Finish the run before exporting.")
        write(uri, Review.csv(run), "Export saved.")
    }

    fun backupRun(id: String, uri: Uri) = io {
        val run = db.run(id) ?: return@io say("This review no longer exists.")
        if (run.active) return@io say("Finish the run before backing it up.")
        write(uri, Json.backup(run), "Export saved.")
    }

    fun importRun(uri: Uri) = io {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (out.size() <= Json.MAX_BACKUP_BYTES) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull() ?: return@io say("Couldn't read the selected backup.")
        val run = runCatching { Json.restore(text) }.getOrElse { return@io say(it.message ?: "Couldn't read the selected backup.") }
        if (db.run(run.id) != null) return@io say("This run is already saved.")
        db.insertRun(run.copy(samples = emptyList()))
        db.insertRunSamples(run.id, run.samples)
        say("Review imported.")
    }

    fun exportApps(uri: Uri, rows: List<AppTime>) = io {
        write(uri, Apps.csv(rows.map { UsageReader.label(context, it.pkg) to it }), "App usage CSV saved.")
    }

    private fun write(uri: Uri, text: String, ok: String) {
        val done = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
        say(if (done) ok else "Couldn't save the file. Try another location.")
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            refresh()
        }
    }
}
