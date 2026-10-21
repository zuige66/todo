package com.lirui.oneline.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lirui.oneline.R

object NotificationHelper {

    const val CHANNEL_REMINDER = "reminders"
    const val CHANNEL_ID_COMPLETE = "completions"
    const val NOTIFICATION_ID_REMINDER = 1001

    fun init(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "到期的待办提醒" }
        nm.createNotificationChannel(reminder)
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Post a reminder notification. Requires [canPostNotifications]; silently no-ops if denied
     * (excluding: exact-alarm scheduling still runs; only display is suppressed).
     */
    fun postReminder(context: Context, title: String, message: String) {
        if (!canPostNotifications(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMINDER, builder.build())
        } catch (_: SecurityException) {
            // permission revoked mid-flight; ignore
        }
    }
}