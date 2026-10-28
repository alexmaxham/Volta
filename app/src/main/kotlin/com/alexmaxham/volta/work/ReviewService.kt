package com.alexmaxham.volta.work

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alexmaxham.volta.R
import com.alexmaxham.volta.bench.Bench
import com.alexmaxham.volta.core.Format
import com.alexmaxham.volta.core.Review
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.data.BatteryReader
import com.alexmaxham.volta.data.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Records a Lab run every 15 seconds while showing a notification. */
class ReviewService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val db = Db.get(this)
        if (intent?.action == ACTION_FINISH) {
            db.activeRunId()?.let { db.finishRun(it, System.currentTimeMillis()) }
            stop()
            return START_NOT_STICKY
        }
        val runId = db.activeRunId()
        if (runId == null || !Notifier.canPost(this)) {
            stop()
            return START_NOT_STICKY
        }
        val run = db.run(runId) ?: return START_NOT_STICKY.also { stop() }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_REVIEW,
                notification(run.conditions.protocol, System.currentTimeMillis() - run.started, null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.isSuccess
        // Android can refuse to restart a foreground service from the background; the run stays
        // open and the user can resume recording from the app.
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val current = db.run(runId)
                    if (current == null || current.ended != null) break
                    BatteryReader.read(this@ReviewService, Source.REVIEW, now)?.let { r ->
                        db.insertRunSamples(runId, listOf(r))
                        db.insertReading(r)
                        @Suppress("MissingPermission")
                        if (Notifier.canPost(this@ReviewService)) {
                            NotificationManagerCompat.from(this@ReviewService)
                                .notify(Notifier.ID_REVIEW, notification(current.conditions.protocol, now - current.started, r.level, db.get(Bench.NOW)))
                        }
                    }
                    delay(Review.INTERVAL)
                }
                stop()
            }
        }
        return START_STICKY
    }

    /** [step] is the benchmark app in front, when a benchmark is running. */
    private fun notification(protocol: String, elapsed: Long, level: Int?, step: String? = null): Notification {
        val finish = PendingIntent.getService(
            this, 1, Intent(this, ReviewService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifier.CH_REVIEW)
            .setSmallIcon(R.drawable.ic_stat_volta)
            .setContentTitle(protocol.ifBlank { "Lab test" } + " · recording")
            .setContentText(listOfNotNull(Format.clock(elapsed), level?.let { "$it%" }, step ?: "Review mode · 15-second target").joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notifier.openApp(this, "lab"))
            .addAction(0, "Finish and save", finish)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FINISH = "com.alexmaxham.volta.FINISH_RUN"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReviewService::class.java))
        }

        fun finish(context: Context) {
            context.startService(Intent(context, ReviewService::class.java).setAction(ACTION_FINISH))
        }
    }
}
