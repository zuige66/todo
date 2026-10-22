package com.lirui.oneline.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R

object NotificationHelper {

    const val CHANNEL_REMINDER = "reminders"

    /**
     * Fixed id for the "发送测试通知" button — it isn't tied to any todo.
     * Real reminders use [reminderId] so several can coexist.
     */
    private const val NOTIFICATION_ID_TEST = 1001

    /** Action ids handled by [NotificationActionReceiver]. */
    const val ACTION_COMPLETE = "com.lirui.oneline.action.COMPLETE"
    const val ACTION_UNDO = "com.lirui.oneline.action.UNDO"
    const val EXTRA_TODO_ID = "todoId"
    const val EXTRA_MESSAGE = "message"

    /**
     * Per-todo notification id. The previous code reused ONE constant for every reminder,
     * so two todos going off at the same instant silently overwrote each other and the user
     * only ever saw the last one. Ids stay well clear of [NOTIFICATION_ID_TEST].
     */
    private fun reminderId(todoId: Long): Int = (200_000 + todoId % 1_000_000).toInt()

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
     *
     * The action button lives in the system's action row, which StatusBar lays out from the
     * leading edge — its position (left) is not something an app can override, only a fully
     * custom `RemoteViews` layout could, at the cost of losing the stock notification styling.
     *
     * @param done renders the "already completed" state of the SAME notification, whose only
     *   action becomes 「撤回」. Tapping 「完成」 re-posts this method with `done = true` on the
     *   same stable id, which is what makes the button "flip" in place instead of stacking a
     *   second notification.
     * @param silent suppresses sound/vibration/heads-up for this update. Used when the user
     *   undoes a completion: the notification must stay (and flip back to 「完成」) but there is
     *   no reason to alert them again for an action they just took.
     */
    fun postReminder(
        context: Context,
        todoId: Long,
        title: String,
        message: String,
        done: Boolean = false,
        silent: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (done) "已完成 · 点「撤回」可恢复" else message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, todoId, done))
            .setAutoCancel(true)
            // The in-place update must not buzz/ring a second time.
            .setOnlyAlertOnce(done || silent)
            .addAction(
                R.drawable.ic_notification,
                if (done) "撤回" else "完成",
                // Carry the original message so 「撤回」 can restore the exact same text.
                actionIntent(context, todoId, if (done) ACTION_UNDO else ACTION_COMPLETE, message)
            )
        if (silent) builder.setSilent(true)
        try {
            NotificationManagerCompat.from(context).notify(reminderId(todoId), builder.build())
        } catch (_: SecurityException) {
            // permission revoked mid-flight; ignore
        }
    }

    /** One-off notification from the settings test button (fixed id, replaces itself). */
    fun postTest(context: Context, title: String, message: String) {
        if (!canPostNotifications(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, builder.build())
        } catch (_: SecurityException) {
            // permission revoked mid-flight; ignore
        }
    }

    /** Removes a todo's notification (e.g. after undo puts it back in the list). */
    fun cancelReminder(context: Context, todoId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId(todoId))
    }

    /** Tapping the notification body opens the app. */
    private fun openAppIntent(context: Context, todoId: Long, done: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.lirui.oneline.OPEN_TODO"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(todoId, if (done) 2 else 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Distinct requestCode per (todo, action): reusing one would let `FLAG_UPDATE_CURRENT`
     * silently rewrite the other action's extras.
     *
     * [message] rides along so 「撤回」 can restore the original reminder text after the action
     * row flips back to 「完成」.
     */
    private fun actionIntent(
        context: Context,
        todoId: Long,
        action: String,
        message: String
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_MESSAGE, message)
        }
        val slot = if (action == ACTION_UNDO) 4 else 3
        return PendingIntent.getBroadcast(
            context,
            requestCode(todoId, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(todoId: Long, slot: Int): Int =
        (todoId % 100_000L * 10L + slot).toInt()
}
