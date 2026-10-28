package com.alexmaxham.volta.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alexmaxham.volta.MainActivity
import com.alexmaxham.volta.R

object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_SUMMARY = "summary"
    const val CH_REVIEW = "review"

    const val ID_LOW = 1
    const val ID_CHARGE = 2
    const val ID_WARM = 3
    const val ID_MORNING = 4
    const val ID_REVIEW = 10

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, "Battery reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Low battery, charge level and warm battery alerts"
                },
                NotificationChannel(CH_SUMMARY, "Morning summary", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Your overnight battery report"
                },
                NotificationChannel(CH_REVIEW, "Review recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a Lab test is recording"
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openApp(context: Context, route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route) }
        return PendingIntent.getActivity(context, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun post(context: Context, id: Int, channel: String, title: String, text: String, route: String? = null) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_volta)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, route))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
