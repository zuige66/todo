package com.lirui.oneline.reminder

import com.lirui.oneline.util.AppTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Core reminder scheduling logic, pure and unit-testable.
 *
 * Rule set (from DESIGN §2):
 * - D >= 30 days  -> remind at D-7, 21:00
 * - 7 <= D < 30   -> remind at D-3, 21:00
 * - 2 <= D < 7    -> remind at D-1, 21:00
 * - D == 1 (明天)  -> remind today 12:00
 * - D == 0 (今天)  -> no graded reminder; the DUE reminder still fires at the due time
 * - If the todo has an exact time -> additionally remind at due - 1h
 * - Always, if the due instant is in the future -> a DUE reminder exactly at due time
 */
object ReminderEngine {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** 时间段「今天开始」这条提醒的默认时刻（用户没设默认时刻时用 09:00）。 */
    const val DEFAULT_START_MINUTE_OF_DAY = 9 * 60

    /** One reminder point: a concrete wall-clock instant to fire a notification at. */
    data class Reminder(
        val fireAtMillis: Long,
        val kind: Kind,
        val message: String
    ) {
        /**
         * ★ 新增 kind 只能往后追加。ordinal 就是 PendingIntent 的 requestCode 尾数
         *   （`todoId*10 + ordinal`），插在中间会让已有闹钟整体错位、cancel 不掉。
         */
        enum class Kind { GRADED, EXACT_PREP, DUE, OVERDUE, START }
    }

    /**
     * Compute all reminders for a todo.
     * @param dueAtMillis the due epoch ms
     * @param hasExactTime whether an exact time was parsed
     * @param startAtMillis 时间段开始日（null = 单日待办）。非空且开始日 != 结束日时，
     *   开始日当天额外提醒一次 —— 否则「25号到1号出差」在 25 号那天一条通知都没有，
     *   而开始那天恰恰是最该被提醒的。
     * @param nowMillis reference "now" for determinism in tests
     * @param zone device timezone
     * @param startMinuteOfDay 开始日提醒落在几点几分（读用户设置里的默认时刻）
     */
    fun computeReminders(
        dueAtMillis: Long,
        hasExactTime: Boolean,
        startAtMillis: Long? = null,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = AppTime.ZONE,
        startMinuteOfDay: Int = DEFAULT_START_MINUTE_OF_DAY
    ): List<Reminder> {
        if (dueAtMillis <= 0L) return emptyList()

        val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dueAtMillis), zone)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

        // Only whole-days difference matters for the graded bucket (floor at day granularity).
        val dueDate = due.toLocalDate()
        val today = now.toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)

        val reminders = mutableListOf<Reminder>()

        startReminder(startAtMillis, dueAtMillis, now, zone, startMinuteOfDay)?.let {
            reminders += it
        }

        val graded = gradedReminder(days, due, now, zone)
        if (graded != null) reminders += graded

        if (hasExactTime) {
            // Due - 1 hour.
            val exactOption = exactPrepReminder(due, now, zone)
            if (exactOption != null && exactOption.fireAtMillis != graded?.fireAtMillis) {
                reminders += exactOption
            }
        }

        // 到点提醒（DUE）：只要到期时刻还在未来，就必定触发一条"到时间了"的通知。
        // 这修复了"当天到期却没有提醒"的问题（此前只有提前提醒，当天到期可能一条都不发）。
        val dueMs = due.toInstant().toEpochMilli()
        if (dueMs >= now.toInstant().toEpochMilli()) {
            val dueReminder = Reminder(
                dueMs,
                Reminder.Kind.DUE,
                "到时间了：${due.toLocalDate()}"
            )
            // 不与同刻的其它提醒重复（如也是正点到期的 exact prep）。
            if (reminders.none { it.fireAtMillis == dueMs }) {
                reminders += dueReminder
            }
        }

        return reminders.sortedBy { it.fireAtMillis }
    }

    /**
     * Compute the next overdue-nag point.
     *
     * Deliberately does NOT require the todo to be overdue yet: a todo created with a due
     * time two hours in the future must have its first nag (due + interval) scheduled at
     * creation time, otherwise the whole nag chain never starts — the DUE alarm only posts a
     * notification and the receiver only re-arms on an OVERDUE firing, so nothing would ever
     * schedule the first OVERDUE point. Scheduling ahead is harmless: nag points are always
     * >= due + interval, i.e. strictly after the due moment.
     *
     * Points are aligned to the next boundary at/after now, so re-scheduling never fires
     * immediately in a loop.
     *
     * Pure epoch arithmetic, hence timezone-free: `due + k·interval` is the same instant
     * whatever the device timezone is.
     */
    fun nextOverdueReminder(
        dueAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        nagIntervalHours: Int = 24
    ): Reminder? {
        if (dueAtMillis <= 0L) return null
        val hours = if (nagIntervalHours <= 0) 24 else nagIntervalHours
        val stepMs = hours * 3600_000L
        val firstNagMs = dueAtMillis + stepMs
        val steps = if (firstNagMs >= nowMillis) 0L else ((nowMillis - firstNagMs) / stepMs) + 1L
        return Reminder(
            firstNagMs + steps * stepMs,
            Reminder.Kind.OVERDUE,
            "未完成：已逾期，记得处理"
        )
    }

    /**
     * The occurrence *before* [dueAtMillis] for a repeating todo — used by the notification
     * "撤回" action, which has to undo a roll-forward.
     *
     * Note this steps back exactly one period, not back to the original completion instant.
     * A daily todo left unfinished for five days rolls forward to tomorrow; undoing brings it
     * back to today (the most recent past occurrence), which is the sane interpretation of
     * "I did not actually do this yet".
     */
    fun previousOccurrence(
        dueAtMillis: Long?,
        repeatDays: Int,
        zone: ZoneId = AppTime.ZONE
    ): Long? {
        if (dueAtMillis == null || dueAtMillis <= 0L || repeatDays <= 0) return null
        val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dueAtMillis), zone)
        return due.minusDays(repeatDays.toLong()).toInstant().toEpochMilli()
    }

    /**
     * 时间段「开始日」的那一条提醒。
     *
     * 只有真正的区间才提醒：
     * - `startAtMillis == null` → 单日待办，没有"开始"可言
     * - 开始日 == 结束日 → 退化成单日（[com.lirui.oneline.data.TodoRepository] 不会这么存，
     *   但解析器/老数据可能出现，防御性跳过，否则会跟 DUE 同一天响两次）
     * - 开始日已经在过去 → 不补发（用户是在看一条历史区间，不是刚新建）
     *
     * 文案故意只说「今天开始」，不带日期：通知标题会拼成「出差 今天开始」，
     * 那一天的语境已经由"今天"承担，再塞一个日期是多余的。
     */
    private fun startReminder(
        startAtMillis: Long?,
        dueAtMillis: Long,
        now: ZonedDateTime,
        zone: ZoneId,
        minuteOfDay: Int
    ): Reminder? {
        if (startAtMillis == null || startAtMillis <= 0L) return null
        if (startAtMillis >= dueAtMillis) return null

        val startDate = Instant.ofEpochMilli(startAtMillis).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(dueAtMillis).atZone(zone).toLocalDate()
        if (startDate == dueDate) return null

        val minute = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        val fireMs = ZonedDateTime.of(
            startDate.atTime(minute / 60, minute % 60), zone
        ).toInstant().toEpochMilli()
        if (fireMs < now.toInstant().toEpochMilli()) return null

        return Reminder(fireMs, Reminder.Kind.START, "今天开始")
    }

    private fun gradedReminder(
        days: Long,
        due: ZonedDateTime,
        now: ZonedDateTime,
        zone: ZoneId
    ): Reminder? {
        return when {
            days >= 30 -> atDayTime(due.minusDays(7), 21, 0, now, zone,
                Reminder.Kind.GRADED, "提前一周提醒：${due.toLocalDate()}")
            days >= 7 -> atDayTime(due.minusDays(3), 21, 0, now, zone,
                Reminder.Kind.GRADED, "提前3天提醒：${due.toLocalDate()}")
            days >= 2 -> atDayTime(due.minusDays(1), 21, 0, now, zone,
                Reminder.Kind.GRADED, "明天到期")
            days == 1L -> atDayTime(due.minusDays(1), 12, 0, now, zone,
                Reminder.Kind.GRADED, "明天到期，别忘了")
            days == 0L -> null // today: handled immediately by caller
            else -> null // overdue
        }
    }

    private fun atDayTime(
        target: ZonedDateTime,
        hour: Int,
        minute: Int,
        now: ZonedDateTime,
        zone: ZoneId,
        kind: Reminder.Kind,
        message: String
    ): Reminder? {
        val fire = ZonedDateTime.of(
            target.toLocalDate().atTime(hour, minute), zone
        )
        val fireMs = fire.toInstant().toEpochMilli()
        // Only now-or-future points fire (>= so an exact-now target isn't dropped).
        return if (fireMs >= now.toInstant().toEpochMilli()) {
            Reminder(fireMs, kind, message)
        } else null
    }

    private fun exactPrepReminder(
        due: ZonedDateTime,
        now: ZonedDateTime,
        zone: ZoneId
    ): Reminder? {
        val fire = due.minusHours(1)
        val fireMs = fire.toInstant().toEpochMilli()
        return if (fireMs >= now.toInstant().toEpochMilli()) {
            Reminder(fireMs, Reminder.Kind.EXACT_PREP, "1小时后到期：${due.toLocalDate()}")
        } else null
    }
}