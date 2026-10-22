package com.lirui.oneline.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderEngineTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    // Build a due instant at a given local date/time (same zone).
    private fun due(y: Int, mo: Int, d: Int, h: Int = 9, m: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun compute(dueAt: Long, exact: Boolean): List<ReminderEngine.Reminder> =
        ReminderEngine.computeReminders(dueAt, exact, now, zone)

    // D=1 (tomorrow) => graded today 12:00 + DUE at due time (tomorrow 09:00).
    @Test fun tomorrow_gradedReminder_atNoon() {
        val r = compute(due(2026, 10, 22), false)
        assertEquals(2, r.size)
        assertEquals(due(2026, 10, 21, 12, 0), r[0].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.GRADED, r[0].kind)
        // DUE fires exactly at the due instant.
        assertEquals(due(2026, 10, 22, 9, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[1].kind)
    }

    // D=1 tomorrow with exact time => graded today 12:00 + exact-prep (due-1h) + DUE.
    @Test fun tomorrow_exactTime_extraPrep() {
        val r = compute(due(2026, 10, 22, 15, 0), true)
        assertEquals(3, r.size)
        // graded at today 12:00
        assertEquals(due(2026, 10, 21, 12, 0), r[0].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.GRADED, r[0].kind)
        // exact prep at due-1h = 10/22 14:00
        assertEquals(due(2026, 10, 22, 14, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.EXACT_PREP, r[1].kind)
        // DUE at the due instant.
        assertEquals(due(2026, 10, 22, 15, 0), r[2].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[2].kind)
    }

    // D=2 => graded due-1 day 21:00 + DUE.
    @Test fun twoDays_graded() {
        val r = compute(due(2026, 10, 23), false)
        assertEquals(2, r.size)
        assertEquals(due(2026, 10, 22, 21, 0), r[0].fireAtMillis)
        assertEquals(due(2026, 10, 23, 9, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[1].kind)
    }

    // 7 <= D <30 => graded due-3 days 21:00 + DUE.
    @Test fun sevenDays_graded() {
        val r = compute(due(2026, 10, 28), false)
        assertEquals(2, r.size)
        assertEquals(due(2026, 10, 25, 21, 0), r[0].fireAtMillis)
        assertEquals(due(2026, 10, 28, 9, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[1].kind)
    }

    // D >=30 => graded due-7 days 21:00 + DUE.
    @Test fun thirtyDays_graded() {
        val r = compute(due(2026, 11, 20), false)
        assertEquals(2, r.size)
        assertEquals(due(2026, 11, 13, 21, 0), r[0].fireAtMillis)
        assertEquals(due(2026, 11, 20, 9, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[1].kind)
    }

    // exact prep is deduped against graded when they coincide.
    @Test fun exactPrep_notDup() {
        // due today 13:00 => exact prep would be 12:00. No graded (D=0). Only prep + DUE.
        val r = compute(due(2026, 10, 21, 13, 0), true)
        assertEquals(2, r.size)
        assertEquals(due(2026, 10, 21, 12, 0), r[0].fireAtMillis)
        assertEquals(due(2026, 10, 21, 13, 0), r[1].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[1].kind)
    }

    // A todo due in the future but *today* with no exact time still gets a DUE reminder.
    @Test fun todayFuture_noExact_getsDue() {
        // today 18:00 due (past now but later today), no exact time parsed.
        // Defaults are handled by caller; here just check DUE with a future due instant.
        val r = compute(due(2026, 10, 21, 15, 0), false)
        assertEquals(1, r.size)
        assertEquals(due(2026, 10, 21, 15, 0), r[0].fireAtMillis)
        assertEquals(ReminderEngine.Reminder.Kind.DUE, r[0].kind)
    }

    @Test fun overdue_returnsEmpty() {
        val r = compute(due(2026, 10, 20), false) // yesterday
        assertTrue(r.isEmpty())
    }

    // --- Overdue nag ---

    // Regression for the "nag chain never starts" bug: a todo whose due time is still in the
    // FUTURE must still get its first nag (due + interval) scheduled at creation time.
    // Previously this returned null, so nothing ever scheduled the first OVERDUE alarm.
    @Test fun overdueNag_notYetDue_returnsFirstPoint() {
        val nag = ReminderEngine.nextOverdueReminder(due(2026, 10, 22), now, zone, 24)
        assertEquals(ReminderEngine.Reminder.Kind.OVERDUE, nag!!.kind)
        // due 10/22 09:00 + 24h
        assertEquals(due(2026, 10, 23, 9, 0), nag.fireAtMillis)
    }

    // A nag point is never earlier than the due instant (no nagging before the DUE reminder).
    @Test fun overdueNag_notYetDue_isAlwaysAfterDue() {
        val dueAt = due(2026, 10, 22, 9, 0)
        val nag = ReminderEngine.nextOverdueReminder(dueAt, now, zone, 6)!!
        assertTrue(nag.fireAtMillis >= dueAt)
    }

    // Re-scheduling (edit / reboot / settings change) must be idempotent before the due time.
    @Test fun overdueNag_notYetDue_stableAcrossReschedules() {
        val dueAt = due(2026, 10, 22)
        val first = ReminderEngine.nextOverdueReminder(dueAt, now, zone, 24)!!.fireAtMillis
        val again = ReminderEngine.nextOverdueReminder(dueAt, now + 3 * 3600_000L, zone, 24)!!.fireAtMillis
        assertEquals(first, again)
    }

    // Just overdue: first nag is due + interval.
    @Test fun overdueNag_firstBoundary() {
        // overdue since 10/20 09:00; now 10/21 12:00; interval 24h.
        val nag = ReminderEngine.nextOverdueReminder(due(2026, 10, 20), now, zone, 24)
        assertEquals(ReminderEngine.Reminder.Kind.OVERDUE, nag!!.kind)
        // first boundary = 10/21 09:00 (past now), so next boundary = 10/22 09:00.
        assertEquals(due(2026, 10, 22, 9, 0), nag.fireAtMillis)
    }

    // Aligns to the next whole-interval boundary at/after now.
    @Test fun overdueNag_alignsToBoundary() {
        // overdue 5h; now 10/21 12:00; interval 6h => due was 10/21 07:00? No, set due 10/21 06:00.
        val nag = ReminderEngine.nextOverdueReminder(due(2026, 10, 21, 6, 0), now, zone, 6)
        // first tiere: due+6h = 12:00, which equals now -> boundary at/after now.
        assertEquals(due(2026, 10, 21, 12, 0), nag!!.fireAtMillis)
    }

    // Non-positive interval falls back to 24h.
    @Test fun overdueNag_invalidInterval() {
        val nag = ReminderEngine.nextOverdueReminder(due(2026, 10, 20), now, zone, 0)
        assertEquals(due(2026, 10, 22, 9, 0), nag!!.fireAtMillis)
    }

    // --- previousOccurrence (notification 「撤回」的重复任务回滚) ---

    @Test fun previousOccurrence_daily_stepsBackOneDay() {
        val next = due(2026, 10, 23, 18, 0)
        assertEquals(due(2026, 10, 22, 18, 0), ReminderEngine.previousOccurrence(next, 1, zone))
    }

    @Test fun previousOccurrence_weekly_stepsBackOneWeek() {
        val next = due(2026, 10, 29, 9, 0)
        assertEquals(due(2026, 10, 22, 9, 0), ReminderEngine.previousOccurrence(next, 7, zone))
    }

    @Test fun previousOccurrence_nonRepeat_returnsNull() {
        assertNull(ReminderEngine.previousOccurrence(due(2026, 10, 22), 0, zone))
    }

    @Test fun previousOccurrence_noDue_returnsNull() {
        assertNull(ReminderEngine.previousOccurrence(null, 1, zone))
    }
}