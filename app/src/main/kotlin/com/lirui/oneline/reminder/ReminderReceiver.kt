package com.lirui.oneline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lirui.oneline.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager when a reminder becomes due. Loads the todo and posts a status-bar
 * notification. An OVERDUE nag also re-schedules the *next* OVERDUE point so the todo keeps
 * nagging until completed.
 *
 * Uses [BroadcastReceiver.goAsync] so the async DB load is protected by the receiver's pending
 * result window; without it the process can be killed mid-load on some devices.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra("todoId", -1L)
        val kind = intent.getIntExtra("kind", 0)
        if (todoId < 0L) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(appContext).todoDao()
                val todo = dao.getById(todoId)
                if (todo != null && !todo.archived) {
                    val message = when (ReminderEngine.Reminder.Kind.entries.getOrNull(kind)) {
                        ReminderEngine.Reminder.Kind.GRADED -> "提前提醒"
                        ReminderEngine.Reminder.Kind.EXACT_PREP -> "1小时后到期"
                        ReminderEngine.Reminder.Kind.DUE -> "到时间了，该办啦"
                        ReminderEngine.Reminder.Kind.OVERDUE -> "还没完成，别忘了哦"
                        else -> "提醒"
                    }
                    NotificationHelper.postReminder(appContext, todo.id, todo.title, message)
                    // OVERDUE: reschedule the next nag so a still-unfinished todo keeps nagging.
                    if (ReminderEngine.Reminder.Kind.entries.getOrNull(kind) == ReminderEngine.Reminder.Kind.OVERDUE) {
                        if (ReminderScheduler.overdueNagActive(appContext)) {
                            ReminderScheduler.reScheduleOverdue(appContext, todo)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}