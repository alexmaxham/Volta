package com.alexmaxham.volta.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.alexmaxham.volta.core.AppEvent
import com.alexmaxham.volta.core.ScreenEvent

/** Screen and app events from Android's usage history. Needs Usage Access. */
object UsageReader {
    /** Android keeps detailed events for roughly a week. */
    const val RETENTION_DAYS = 7L

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    data class Events(val screen: List<ScreenEvent>, val apps: List<AppEvent>)

    fun events(context: Context, from: Long, to: Long): Events {
        if (!hasAccess(context)) return Events(emptyList(), emptyList())
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return Events(emptyList(), emptyList())
        val screen = ArrayList<ScreenEvent>()
        val apps = ArrayList<AppEvent>()
        val stream = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return Events(screen, apps)
        val e = UsageEvents.Event()
        val self = context.packageName
        while (stream.hasNextEvent()) {
            stream.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> screen += ScreenEvent(e.timeStamp, true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screen += ScreenEvent(e.timeStamp, false)
                UsageEvents.Event.ACTIVITY_RESUMED -> if (e.packageName != self) apps += AppEvent(e.timeStamp, e.packageName, true)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (e.packageName != self) apps += AppEvent(e.timeStamp, e.packageName, false)
            }
        }
        return Events(screen, apps)
    }

    private val labels = HashMap<String, String>()

    @Suppress("DEPRECATION")
    fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    @Suppress("DEPRECATION")
    fun icon(context: Context, pkg: String): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
}
