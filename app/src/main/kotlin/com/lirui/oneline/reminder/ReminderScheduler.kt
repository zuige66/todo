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
     *
     * @param startAtMillis 时间段开始日（null = 单日待办），决定要不要额外排一条「今天开始」。
     */
    fun scheduleFor(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long? = null
    ) {
        scope.launch {
            scheduleBlocking(context, todoId, dueAtMillis, hasExactTime, startAtMillis)
        }
    }

    suspend fun scheduleBlocking(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long? = null
    ) {
        // Cancel any previously set reminders for this todo.
        cancelForId(context, todoId)

        // Master reminder switch: when off, nothing is scheduled.
        if (!SettingsRepo.remindersEnabled(context)) return

        val reminders = ReminderEngine.computeReminders(
            dueAtMillis = dueAtMillis,
            hasExactTime = hasExactTime,
            startAtMillis = startAtMillis,
            // 「今天开始」跟用户设的默认时刻走，和"只写日期的待办用默认时刻"是同一个约定。
            startMinuteOfDay = SettingsRepo.defaultMinuteOfDay(context)
        )
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
                // START 归「到点提醒」：它就是"这一天到了"的那一声，和 DUE 是同一类语义。
                // 归到 inAdvance 会让关掉"到点提醒"、只留提前提醒的人照样收到它。
                ReminderEngine.Reminder.Kind.DUE,
                ReminderEngine.Reminder.Kind.START -> atDue
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
        scope.launch { scheduleAllActiveBlocking(context) }
    }

    /**
     * 可等待的全量重建。
     *
     * 与 [scheduleAllActive] 的区别只有一个：**调用方能等它做完**。
     * 广播接收器（开机 / 升级）必须用这个版本 —— `onReceive` 返回后进程优先级立刻回落，
     * 用 fire-and-forget 的版本可能整批闹钟还没排完就被回收（这就是"开机后提醒偶尔丢"的成因）。
     */
    suspend fun scheduleAllActiveBlocking(context: Context) {
        val dao = AppDatabase.get(context).todoDao()
        dao.getActiveAll().forEach { todo ->
            todo.dueAtMillis?.let { due ->
                scheduleBlocking(context, todo.id, due, todo.hasExactTime, todo.startAtMillis)
            }
        }
    }

    fun cancelForId(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (am == null) return
        // ★ 遍历枚举而不是写死 0..3：以前加新 kind 忘了改这里，旧的闹钟就永远取消不掉，
        //   改一条待办的时间后会同时收到新旧两条提醒。ordinal 就是 requestCode 尾数。
        ReminderEngine.Reminder.Kind.values().forEach { kind ->
            val pi = pendingReminder(context, todoId, kind.ordinal)
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
                scheduleBlocking(context, todo.id, due, todo.hasExactTime, todo.startAtMillis)
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