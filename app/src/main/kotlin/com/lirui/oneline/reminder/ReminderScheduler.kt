package com.lirui.oneline.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Encloses all AlarmManager scheduling for a todo's reminders.
 * Each reminder gets a distinct requestCode = id*10 + kindIndex so editing/cancelling works.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    /**
     * 重建/重排的**唯一串行闸**（v1.0.10）。
     *
     * 四个入口可能同时跑：MainActivity 冷启动、设置页 onRescheduleReminders、
     * ReminderWatchdog（12h）、BootReceiver / MY_PACKAGE_REPLACED。
     * 它们是不同协程作用域，没有闸时会并发对同一批 PendingIntent 又 cancel 又 set，
     * 最后谁赢取决于时序。这里用一把 Mutex 让整批重建互斥。
     *
     * ★ Mutex **不可重入**：公开的 [scheduleBlocking] 持锁后调
     *   [scheduleBlockingLocked]（不加锁），[scheduleAllActiveBlocking] 也只持一次锁，
     *   否则全量重建内部逐条调用会自己把自己锁死。
     */
    private val gate = Mutex()

    // fire-and-forget 的两路（scheduleFor / scheduleAllActive）再叠一层单线程兜底；
    // 能等的那两路由 gate 收口。两者不冲突。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun requestCode(todoId: Long, kindOrdinal: Int): Int = (todoId * 10 + kindOrdinal).toInt()

    /**
     * Recompute and (re)schedule reminders for one todo. Best-effort: exact alarm capabilities
     * are checked and fall back to inexact if not granted.
     *
     * @param startAtMillis 时间段开始日（null = 单日待办），决定要不要额外排一条「今天开始」。
     * @param endAtMillis 时间区间结束点（#1，null = 普通待办）。只影响**逾期唠叨链**的锚点：
     *   「15:00~17:00」在 16:00 是进行中（列表不标红），此时不该开始唠叨 —— 所以链从
     *   结束点起算；到点/提前提醒仍然按开始点 [dueAtMillis] 响（zuige：结束点不响）。
     */
    fun scheduleFor(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long? = null,
        endAtMillis: Long? = null
    ) {
        scope.launch {
            scheduleBlocking(context, todoId, dueAtMillis, hasExactTime, startAtMillis, endAtMillis)
        }
    }

    suspend fun scheduleBlocking(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long? = null,
        endAtMillis: Long? = null
    ) {
        gate.withLock {
            scheduleBlockingLocked(
                context, todoId, dueAtMillis, hasExactTime, startAtMillis, endAtMillis
            )
        }
    }

    /** 不带锁的实作；只允许被已经持有 [gate] 的调用方使用（见 [gate] 的重入说明）。 */
    private fun scheduleBlockingLocked(
        context: Context,
        todoId: Long,
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long?,
        endAtMillis: Long?
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
        // 锚点是**结束点**（#1）：区间进行中不唠叨，与"进行中不标红"是同一条判定。
        if (nagEnabled) {
            ReminderEngine.nextOverdueReminder(
                endAtMillis ?: dueAtMillis, nagIntervalHours = nagInterval
            )?.let { nag ->
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

            val pi = pendingReminder(context, todoId, r.kind.ordinal, r.leadDays)
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
        gate.withLock {
            val dao = AppDatabase.get(context).todoDao()
            dao.getActiveAll().forEach { todo ->
                todo.dueAtMillis?.let { due ->
                    // ★ 逐条隔离：一条脏数据 / 某个 ROM 的 AlarmManager 抛异常，
                    //   不该让后面几十条待办的提醒整批哑掉。
                    runCatching {
                        scheduleBlockingLocked(
                            context, todo.id, due, todo.hasExactTime,
                            todo.startAtMillis, todo.endAtMillis
                        )
                    }.onFailure { e ->
                        Log.w(TAG, "skip rebuild for todo ${todo.id}", e)
                    }
                }
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
                scheduleBlocking(
                    context, todo.id, due, todo.hasExactTime, todo.startAtMillis, todo.endAtMillis
                )
            }
        }
    }

    private fun pendingReminder(
        context: Context,
        todoId: Long,
        kindOrdinal: Int,
        leadDays: Int = 0
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.lirui.oneline.REMINDER"
            putExtra("todoId", todoId)
            putExtra("kind", kindOrdinal)
            // 提前天数：投递侧据此把「提前提醒」细化成「提前N天提醒」。
            // 默认值 0 = 旧版已排的闹钟没有这个 extra 时，回退到笼统的「提前提醒」。
            putExtra("leadDays", leadDays)
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