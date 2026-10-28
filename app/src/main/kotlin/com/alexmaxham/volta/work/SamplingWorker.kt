package com.alexmaxham.volta.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alexmaxham.volta.core.Source
import com.alexmaxham.volta.data.SettingsStore
import java.util.concurrent.TimeUnit

/** Background logging: Android runs this about every 15 minutes and may delay it during sleep or battery saving. */
class SamplingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val logging = SettingsStore(applicationContext).current().backgroundLogging
        Recorder.record(applicationContext, Source.BACKGROUND, store = logging)
        Recorder.morningSummary(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "sampling"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<SamplingWorker>(15, TimeUnit.MINUTES).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }
}
