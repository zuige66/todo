package com.lirui.oneline.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Formatting + the overdue predicate.
 *
 * Grouping used to live here as `Format.groupLabel`; since v2.1 it is
 * [HomeGrouping] (user-configurable boards + buckets), covered by `HomeGroupingTest`.
 * What remains here is the single source of truth for "is this overdue".
 */
class FormatTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 21)

    private fun epoch(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun dueLine_sameYear_orders_month_day_weekday_time() {
        assertEquals("10月21日 周三 9点", Format.dueLine(epoch(2026, 10, 21), today = today))
    }

    @Test fun dueLine_sameYear_withMinutes() {
        assertEquals("10月21日 周三 15:30", Format.dueLine(epoch(2026, 10, 21, 15, 30), today = today))
    }

    @Test fun dueLine_otherYear_puts_year_first() {
        assertEquals("2027年 1月15日 周五 15点", Format.dueLine(epoch(2027, 1, 15, 15), today = today))
    }

    // ---- 添加弹层预览的日期 chip：跨年必须显示年份 ----
    // 「1月二日」在 10 月输入会被顺延到明年，不显示年份用户看不出落在哪一年。
    @Test fun dateShort_sameYear_hasNoYear() {
        assertEquals("10月24日 周六", Format.dateShort(LocalDate.of(2026, 10, 24), today))
    }

    @Test fun dateShort_otherYear_showsYear() {
        assertEquals("2027年 1月2日 周六", Format.dateShort(LocalDate.of(2027, 1, 2), today))
    }

    // ---- 只写日期（无精确时刻）的待办：过了那一天才算逾期，不该 09:00 一过就变红 ----
    @Test fun dateOnly_todayIsNotOverdue_whenDefaultTimeHasPassed() {
        // 只写"今天"：默认提醒时刻 09:00 虽然过了，但整天都还算数
        val due = epoch(2026, 10, 21, 9)
        val late = epoch(2026, 10, 21, 23, 59)
        assertFalse(Format.isOverdue(due, today, late, hasExactTime = false))
    }

    @Test fun dateOnly_yesterdayIsOverdue() {
        val due = epoch(2026, 10, 20, 9)
        val now = epoch(2026, 10, 21, 10, 0)
        assertTrue(Format.isOverdue(due, today, now, hasExactTime = false))
    }

    // ---- 有精确时刻：过点即逾期（此前的 bug 就是这里被"只看日期"卡住）----
    @Test fun exactTime_todayIsOverdue_onceInstantPassed() {
        val due = epoch(2026, 10, 21, 23, 39)
        val now = epoch(2026, 10, 21, 23, 59)
        assertTrue(Format.isOverdue(due, today, now, hasExactTime = true))
    }

    @Test fun exactTime_todayIsNotOverdue_beforeInstant() {
        val due = epoch(2026, 10, 21, 23, 39)
        val now = epoch(2026, 10, 21, 12, 0)
        assertFalse(Format.isOverdue(due, today, now, hasExactTime = true))
    }

    // The exact instant counts as overdue (>= now), not "still today".
    @Test fun exactTime_atTheExactInstant_isOverdue() {
        val due = epoch(2026, 10, 21, 23, 39)
        assertTrue(Format.isOverdue(due, today, due, hasExactTime = true))
    }

    @Test fun noDate_isNeverOverdue() {
        val now = epoch(2026, 10, 21, 23, 59)
        assertFalse(Format.isOverdue(null, today, now))
    }

    companion object {
        init {
            System.setProperty("user.timezone", "Asia/Shanghai")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        }
    }
}
