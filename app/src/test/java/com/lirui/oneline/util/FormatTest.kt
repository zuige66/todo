package com.lirui.oneline.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 21)

    private fun epoch(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun dueLine_sameYear_orders_month_day_weekday_time() {
        assertEquals("10月21日 周三 9点", Format.dueLine(epoch(2026, 10, 21), today))
    }

    @Test fun dueLine_sameYear_withMinutes() {
        assertEquals("10月21日 周三 15:30", Format.dueLine(epoch(2026, 10, 21, 15, 30), today))
    }

    @Test fun dueLine_otherYear_puts_year_first() {
        assertEquals("2027年 1月15日 周五 15点", Format.dueLine(epoch(2027, 1, 15, 15), today))
    }

    // ---- natural-week grouping (today = Wed 2026-10-21; week runs Mon 10/19 – Sun 10/25) ----
    // Every call states "now" explicitly: the label is time-aware, and defaulting to the
    // real wall clock would make these assertions depend on when the suite happens to run.
    private val nowEarly = epoch(2026, 10, 21, 8, 0)

    @Test fun groupLabel_thisWeek() {
        assertEquals("今天", Format.groupLabel(epoch(2026, 10, 21), today, nowEarly))
        assertEquals("明天", Format.groupLabel(epoch(2026, 10, 22), today, nowEarly))
        assertEquals("后天", Format.groupLabel(epoch(2026, 10, 23), today, nowEarly))
        assertEquals("本周", Format.groupLabel(epoch(2026, 10, 24), today, nowEarly))
        assertEquals("本周", Format.groupLabel(epoch(2026, 10, 25), today, nowEarly)) // Sunday
    }

    @Test fun groupLabel_nextWeekIsNotThisWeek() {
        assertEquals("下周", Format.groupLabel(epoch(2026, 10, 26), today, nowEarly)) // next Monday
        assertEquals("下周", Format.groupLabel(epoch(2026, 10, 27), today, nowEarly)) // the reported bug
        assertEquals("下周", Format.groupLabel(epoch(2026, 11, 1), today, nowEarly))  // next Sunday
        assertEquals("更晚", Format.groupLabel(epoch(2026, 11, 2), today, nowEarly))
    }

    @Test fun groupLabel_nearFutureWrapsAcrossMonth() {
        // Today Fri 2026-10-30 -> week 10/26..11/1; 11/1 is 后天 (more specific wins).
        val fri = LocalDate.of(2026, 10, 30)
        val friMorning = epoch(2026, 10, 30, 8, 0)
        assertEquals("后天", Format.groupLabel(epoch(2026, 11, 1), fri, friMorning))
        assertEquals("下周", Format.groupLabel(epoch(2026, 11, 2), fri, friMorning))
    }

    // ---- time-of-day overdue: 今天 23:39 在 23:59 就是逾期，不能一直挂在"今天"下 ----
    @Test fun groupLabel_todayPastTime_isOverdue() {
        val due = epoch(2026, 10, 21, 23, 39)
        assertEquals("已逾期", Format.groupLabel(due, today, epoch(2026, 10, 21, 23, 59)))
    }

    @Test fun groupLabel_todayFutureTime_isToday() {
        val due = epoch(2026, 10, 21, 23, 39)
        assertEquals("今天", Format.groupLabel(due, today, epoch(2026, 10, 21, 12, 0)))
    }

    @Test fun groupLabel_todayExactNow_isOverdue() {
        val due = epoch(2026, 10, 21, 23, 39)
        assertEquals("已逾期", Format.groupLabel(due, today, due))
    }

    @Test fun groupLabel_tomorrowStaysTomorrow_evenLateTonight() {
        assertEquals("明天", Format.groupLabel(epoch(2026, 10, 22, 9), today, epoch(2026, 10, 21, 23, 59)))
    }

    companion object {
        init {
            System.setProperty("user.timezone", "Asia/Shanghai")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        }
    }
}