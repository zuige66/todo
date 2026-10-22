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

    /** One reminder point: a concrete wall-clock instant to fire a notification at. */
    data class Reminder(
        val fireAtMillis: Long,
        val kind: Kind,
        val message: String
    ) {
        enum class Kind { GRADED, EXACT_PREP, DUE, OVERDUE }
    }

    /**
     * Compute all reminders for a todo.
     * @param dueAtMillis the due epoch ms
     * @param hasExactTime whether an exact time was parsed
     * @param nowMillis reference "now" for determinism in tests
     * @param zone device timezone
     */
    fun computeReminders(
        dueAtMillis: Long,
        hasExactTime: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = AppTime.ZONE
    ): List<Reminder> {
        if (dueAtMillis <= 0L) return emptyList()

        val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dueAtMillis), zone)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

        // Only whole-days difference matters for the graded bucket (floor at day granularity).
        val dueDate = due.toLocalDate()
        val today = now.toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)

        val reminders = mutableListOf<Reminder>()

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
     * Compute the next overdue-nag point (ONLY when the todo is already overdue).
     * Returns null when the todo is not yet due.
     */
    fun nextOverdueReminder(
        dueAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = AppTime.ZONE,
        nagIntervalHours: Int = 24
    ): Reminder? {
        if (dueAtMillis <= 0L) return null
        val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dueAtMillis), zone)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        if (due.toInstant().toEpochMilli() > now.toInstant().toEpochMilli()) return null
        val hours = if (nagIntervalHours <= 0) 24 else nagIntervalHours
        val stepMs = hours * 3600_000L
        val firstNagMs = due.toInstant().toEpochMilli() + stepMs
        val nowMs = now.toInstant().toEpochMilli()
        // Align to the next boundary at/after now (so repeated reschedules don't fire immediately).
        val steps = if (firstNagMs >= nowMs) 0L else ((nowMs - firstNagMs) / stepMs) + 1L
        return Reminder(
            firstNagMs + steps * stepMs,
            Reminder.Kind.OVERDUE,
            "未完成：已逾期，记得处理"
        )
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