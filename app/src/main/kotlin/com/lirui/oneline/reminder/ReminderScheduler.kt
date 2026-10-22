package com.lirui.oneline.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Encloses all AlarmManager scheduling for a todo's reminders.
 * Each reminder gets a distinct requestCode = id*10 + kindIndex so editing/cancelling works.
 */
object ReminderScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun requestCode(todoId: Long, kindOrdinal: Int): Int = (todoId * 10 + kindOrdinal).toInt()

    /**
     * Recompute and (re)schedule reminders for one todo. Best-effort: exact alarm capabilities
     * are checked and fall back to inexact if not granted.
     */
    fun scheduleFor(context: Context, todoId: Long, dueAtMillis: Long, hasExactTime: Boolean) {
        scope.launch {
            scheduleBlocking(context, todoId, dueAtMillis, hasExactTime)
        }
    }

    suspend fun scheduleBlocking(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean
    ) {
        // Cancel any previously set reminders for this todo.
        cancelForId(context, todoId)

        // Master reminder switch: when off, nothing is scheduled.
        if (!SettingsRepo.remindersEnabled(context)) return

        val reminders = ReminderEngine.computeReminders(dueAtMillis, hasExactTime)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (am == null) return

        val atDue = SettingsRepo.remindAtDue(context)
        val inAdvance = SettingsRepo.remindInAdvance(context)
        val nagEnabled = SettingsRepo.overdueNagEnabled(context)
        val nagInterval = SettingsRepo.overdueIntervalHours(context)

        // 逾期反复叮嘱：若已到期，调度下一个 OVERDUE 提醒（触发后由 receiver 继续排程）。
        if (nagEnabled) {
            ReminderEngine.nextOverdueReminder(dueAtMillis, nagIntervalHours = nagInterval)?.let { nag ->
                val pi = pendingReminder(context, todoId, ReminderEngine.Reminder.Kind.OVERDUE.ordinal)
                if (canScheduleExact(context)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nag.fireAtMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nag.fireAtMillis, pi)
                }
            }
        }

        reminders.forEach { r ->
            val allowed = when (r.kind) {
                ReminderEngine.Reminder.Kind.DUE -> atDue
                else -> inAdvance // GRADED and EXACT_PREP are advance reminders
            }
            if (!allowed) return@forEach

            val pi = pendingReminder(context, todoId, r.kind.ordinal)
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, r.fireAtMillis, pi
                )
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAtMillis, pi)
            }
        }
    }

    fun scheduleAllActive(context: Context) {
        scope.launch {
            val dao = AppDatabase.get(context).todoDao()
            val active = dao.getActiveAll()
            active.forEach { todo ->
                todo.dueAtMillis?.let { due ->
                    scheduleBlocking(context, todo.id, due, todo.hasExactTime)
                }
            }
        }
    }

    fun cancelForId(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (am == null) return
        // GRADED / EXACT_PREP / DUE / OVERDUE are ordinals 0 / 1 / 2 / 3.
        for (kind in 0..3) {
            val pi = pendingReminder(context, todoId, kind)
            am.cancel(pi)
        }
    }

    /** Whether the overdue-nag master switch + reminders master are on (receiver re-nag gate). */
    fun overdueNagActive(context: Context): Boolean =
        SettingsRepo.remindersEnabled(context) && SettingsRepo.overdueNagEnabled(context)

    /** After an OVERDUE fires, schedule the following nag for the same todo. */
    fun reScheduleOverdue(context: Context, todo: com.lirui.oneline.data.model.Todo) {
        scope.launch {
            todo.dueAtMillis?.let { due ->
                scheduleBlocking(context, todo.id, due, todo.hasExactTime)
            }
        }
    }

    private fun pendingReminder(context: Context, todoId: Long, kindOrdinal: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.lirui.oneline.REMINDER"
            putExtra("todoId", todoId)
            putExtra("kind", kindOrdinal)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(todoId, kindOrdinal),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return when {
            Build.VERSION.SDK_INT >= 31 -> am.canScheduleExactAlarms()
            else -> true
        }
    }
}