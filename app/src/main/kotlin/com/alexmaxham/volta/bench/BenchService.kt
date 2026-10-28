package com.alexmaxham.volta.bench

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.alexmaxham.volta.MainActivity
import com.alexmaxham.volta.core.BenchPlan
import com.alexmaxham.volta.core.BenchSegment
import com.alexmaxham.volta.core.Benchmark
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.core.Time
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.data.Db
import com.alexmaxham.volta.work.Notifier
import com.alexmaxham.volta.work.ReviewService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the app benchmark. As an accessibility service it can open each app on schedule, scroll,
 * swipe and tap inside it, see which app is in front, and keep the screen on. Recording itself is
 * done by [ReviewService], as for any Lab run.
 */
class BenchService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var keepOn: View? = null
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        instance = this
        resume()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) event.packageName?.let { lastPackage = it.toString() }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        job?.cancel()
        keepScreenOn(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        keepScreenOn(false)
        super.onDestroy()
    }

    /** Starts or picks up the benchmark in the stored plan, if there is one. */
    fun resume() {
        if (job?.isActive == true) return
        job = scope.launch {
            val plan = withContext(Dispatchers.IO) { Bench.plan(this@BenchService) } ?: return@launch
            try {
                drive(plan)
            } finally {
                keepScreenOn(false)
            }
        }
    }

    private suspend fun drive(plan: BenchPlan) {
        val db = Db.get(this)
        val runId = plan.runId
        val started = withContext(Dispatchers.IO) { db.run(runId)?.takeIf { it.active }?.started }
        if (started == null) {
            withContext(Dispatchers.IO) { Bench.clear(this@BenchService) }
            return
        }
        // Pick up an unfinished slot after Volta was restarted.
        var slot: BenchSegment? = withContext(Dispatchers.IO) { db.segments(runId).lastOrNull { it.end == null } }

        // No readings for a while means the phone ran out or was switched off: the run ended then.
        val last = withContext(Dispatchers.IO) { db.runSamples(runId).lastOrNull()?.time }
        if (last != null && System.currentTimeMillis() - last > 10 * Time.MINUTE) {
            withContext(Dispatchers.IO) {
                slot?.let { db.saveSegment(runId, it.copy(end = maxOf(it.start, last))) }
                db.finishRun(runId, last)
                Bench.clear(this@BenchService)
            }
            runCatching { ReviewService.finish(this) }
            return
        }
        keepScreenOn(true)
        var work: Workload? = null
        var opened = -1
        val queue = ArrayDeque<Move>()
        var loopAt = 0
        var nextMove = 0L
        var awaySince = 0L
        var nextBattery = 0L

        suspend fun save(s: BenchSegment) = withContext(Dispatchers.IO) { db.saveSegment(runId, s) }

        while (true) {
            val now = System.currentTimeMillis()
            val ended = withContext(Dispatchers.IO) { runCatching { db.runEnded(runId) }.getOrElse { now } }
            if (ended != null) {
                // Finished from the notification or the app.
                work?.let { if (front() in it.packages) perform(it.leave) }
                slot?.let { save(it.copy(end = maxOf(it.start, ended))) }
                withContext(Dispatchers.IO) { Bench.clear(this@BenchService) }
                return
            }
            val pos = Benchmark.position(plan.steps, now - started) ?: return

            if (opened != pos.index) {
                val previous = slot
                if (previous != null && previous.index != pos.index) {
                    work?.let { if (front() in it.packages) perform(it.leave) }
                    save(previous.copy(end = now))
                    slot = null
                }
                if (slot == null) slot = BenchSegment(pos.index, pos.round, pos.step.id, now, null).also { save(it) }
                withContext(Dispatchers.IO) { db.put(Bench.NOW, "${pos.step.name} · round ${pos.round + 1}") }
                if (previous != null && previous.index != pos.index) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    delay(1_500)
                }
                work = Workloads.of(this, pos.step)
                work?.let { open(it.open) }
                opened = pos.index
                queue.clear()
                work?.let { queue.addAll(it.start) }
                loopAt = 0
                nextMove = System.currentTimeMillis() + OPEN_WAIT
                awaySince = 0L
            }

            val w = work
            val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            if (w != null && !locked) {
                val fg = front()
                when {
                    // Only touch the app under test. Volta in front means someone is checking on the run.
                    fg == null || fg in w.packages || fg == packageName -> awaySince = 0L
                    awaySince == 0L -> awaySince = now
                    now - awaySince > AWAY_LIMIT -> {
                        open(w.open)
                        queue.clear()
                        queue.addAll(w.start)
                        nextMove = now + OPEN_WAIT
                        awaySince = 0L
                        slot = slot?.let { it.copy(relaunches = it.relaunches + 1) }?.also { save(it) }
                    }
                }
                if (now >= nextMove && fg in w.packages) {
                    val m = queue.removeFirstOrNull()
                    if (m != null) {
                        perform(listOf(m))
                        nextMove = now + START_GAP
                    } else if (w.loop.isNotEmpty()) {
                        perform(listOf(w.loop[loopAt % w.loop.size]))
                        loopAt++
                        nextMove = now + w.every
                    }
                }
            }

            if (now >= nextBattery) {
                nextBattery = now + 15 * Time.SECOND
                val r = BatteryReader.read(this, Source.REVIEW, now)
                val reason = when {
                    r == null -> null
                    r.plugged -> "The charger was connected"
                    r.level <= plan.stopLevel -> "The battery reached ${r.level}%"
                    else -> null
                }
                if (reason != null) {
                    w?.let { if (front() in it.packages) perform(it.leave) }
                    slot?.let { save(it.copy(end = now)) }
                    finish(runId, started, now, reason)
                    return
                }
            }
            delay(TICK)
        }
    }

    private suspend fun finish(runId: String, started: Long, now: Long, reason: String) {
        withContext(Dispatchers.IO) {
            Db.get(this@BenchService).finishRun(runId, now)
            Bench.clear(this@BenchService)
        }
        runCatching { ReviewService.finish(this) }
        performGlobalAction(GLOBAL_ACTION_HOME)
        Notifier.post(
            this, Notifier.ID_BENCH, Notifier.CH_REVIEW, "App benchmark finished",
            "$reason after ${Format.duration(now - started)}. Tap for the results.", "run/$runId",
        )
        open(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, "run/$runId"),
        )
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The app in front: the active window's, or the last one that opened. */
    private fun front(): String? = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: lastPackage

    private suspend fun perform(moves: List<Move>) {
        for ((i, m) in moves.withIndex()) {
            if (i > 0) delay(START_GAP)
            when (m) {
                is Move.Swipe -> stroke(m.x1, m.y1, m.x2, m.y2, m.ms)
                is Move.Tap -> stroke(m.x, m.y, m.x, m.y, 60)
                is Move.TapLabel -> tapLabel(m)
                is Move.Open -> open(Workloads.view(m.url, Workloads.CHROME))
            }
        }
    }

    private fun screen(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30 && wm != null) {
            val b = wm.maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val d = resources.displayMetrics
        return d.widthPixels to d.heightPixels
    }

    /** A swipe between two points given as fractions of the screen, or a tap when they're the same. */
    private fun stroke(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val (w, h) = screen()
        strokePx(x1 * w, y1 * h, x2 * w, y2 * h, ms)
    }

    private fun strokePx(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            // A tap still needs a path with some length.
            lineTo(if (x1 == x2 && y1 == y2) x2 + 1f else x2, y2)
        }
        runCatching {
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null)
        }
    }

    private fun tapLabel(m: Move.TapLabel) {
        val node = runCatching { rootInActiveWindow?.let { find(it, m.words, m.avoid) } }.getOrNull()
        if (node != null) {
            var n: AccessibilityNodeInfo? = node
            while (n != null && !n.isClickable) n = n.parent
            if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty) return strokePx(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), 60)
        }
        stroke(m.x, m.y, m.x, m.y, 60)
    }

    /** The first visible control labelled with one of [words], trying each word in turn. */
    private fun find(root: AccessibilityNodeInfo, words: List<String>, avoid: List<String>): AccessibilityNodeInfo? {
        val nodes = ArrayList<Pair<AccessibilityNodeInfo, String>>()
        val todo = ArrayDeque(listOf(root))
        while (todo.isNotEmpty() && nodes.size < 3000) {
            val n = todo.removeFirst()
            val label = listOfNotNull(n.contentDescription, n.text).joinToString(" ").lowercase()
            if (label.isNotBlank() && n.isVisibleToUser) nodes += n to label
            for (i in 0 until n.childCount) n.getChild(i)?.let { todo += it }
        }
        for (w in words) {
            nodes.firstOrNull { (_, l) -> w in l && avoid.none { it in l } }?.let { return it.first }
        }
        return null
    }

    /** A 1-pixel window that keeps the screen awake while apps without their own wake lock are in front. */
    private fun keepScreenOn(on: Boolean) {
        val wm = getSystemService(WindowManager::class.java) ?: return
        if (on && keepOn == null) {
            val v = View(this)
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            if (runCatching { wm.addView(v, params) }.isSuccess) keepOn = v
        } else if (!on) {
            keepOn?.let { runCatching { wm.removeView(it) } }
            keepOn = null
        }
    }

    companion object {
        /** The connected service, while Accessibility has it turned on. */
        @Volatile var instance: BenchService? = null
            private set

        private const val TICK = 500L

        /** Time for an app to open before the first move. */
        private const val OPEN_WAIT = 5_000L
        private const val START_GAP = 2_500L

        /** How long another app may stay in front before the step's app is opened again. */
        private const val AWAY_LIMIT = 15_000L
    }
}
