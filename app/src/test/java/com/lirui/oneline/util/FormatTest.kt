package com.lirui.oneline.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- 时间段（v0.8.4）：start = 开始日，end = 最后一天（dueAtMillis）----

    @Test fun inProgress_coversBothEnds() {
        val start = epoch(2026, 10, 20)
        val end = epoch(2026, 10, 23)
        assertTrue(Format.isInProgress(start, end, today))
        assertTrue(Format.isInProgress(epoch(2026, 10, 21), end, today))
        assertTrue(Format.isInProgress(start, epoch(2026, 10, 21), today))
    }

    @Test fun inProgress_falseOutsideRange() {
        val end = epoch(2026, 10, 23)
        assertFalse(Format.isInProgress(epoch(2026, 10, 22), end, today)) // 还没开始
        assertFalse(Format.isInProgress(epoch(2026, 10, 18), epoch(2026, 10, 20), today)) // 已结束
    }

    // 单日待办（start == null）不参与"进行中"，避免给所有老数据多出一层状态。
    @Test fun inProgress_nullStart_isFalse() {
        assertFalse(Format.isInProgress(null, epoch(2026, 10, 21), today))
    }

    @Test fun daysLeft_countsToEndDay() {
        assertEquals(2L, Format.daysLeft(epoch(2026, 10, 23), today))
        assertEquals(0L, Format.daysLeft(epoch(2026, 10, 21), today))
        assertEquals(-2L, Format.daysLeft(epoch(2026, 10, 19), today))
    }

    @Test fun rangeLine_sameMonth_collapsesDay() {
        assertEquals(
            "10月21日–23日 周五",
            Format.rangeLine(epoch(2026, 10, 21), epoch(2026, 10, 23), today = today, showTime = false)
        )
    }

    @Test fun rangeLine_crossMonth_keepsBothMonths() {
        assertEquals(
            "10月28日–11月3日 周二",
            Format.rangeLine(epoch(2026, 10, 28), epoch(2026, 11, 3), today = today, showTime = false)
        )
    }

    @Test fun rangeLine_crossYear_showsBothYears() {
        assertEquals(
            "2026年12月30日–2027年1月2日 周六",
            Format.rangeLine(epoch(2026, 12, 30), epoch(2027, 1, 2), today = today, showTime = false)
        )
    }

    @Test fun rangeLine_appendsEndTime() {
        assertEquals(
            "10月21日–23日 周五 15:30",
            Format.rangeLine(epoch(2026, 10, 21), epoch(2026, 10, 23, 15, 30), today = today)
        )
    }

    // 单日 / start 缺失都必须退回 dueLine，保证老数据文案一字不变。
    @Test fun rangeLine_fallsBackToDueLine() {
        val expected = Format.dueLine(epoch(2026, 10, 23), today = today)
        assertEquals(expected, Format.rangeLine(null, epoch(2026, 10, 23), today = today))
        assertEquals(
            expected,
            Format.rangeLine(epoch(2026, 10, 23), epoch(2026, 10, 23), today = today)
        )
    }

    @Test fun monthDayRange_compactForms() {
        assertEquals("21–23日", Format.monthDayRange(epoch(2026, 10, 21), epoch(2026, 10, 23)))
        assertEquals("10/28–11/3", Format.monthDayRange(epoch(2026, 10, 28), epoch(2026, 11, 3)))
        // 单日退回 monthDay
        assertEquals("10月23日", Format.monthDayRange(null, epoch(2026, 10, 23)))
        assertEquals("10月23日", Format.monthDayRange(epoch(2026, 10, 23), epoch(2026, 10, 23)))
    }

    @Test fun rangeStatus_notStarted() {
        assertEquals(
            "未开始",
            Format.rangeStatus(epoch(2026, 10, 22), epoch(2026, 10, 25), today, epoch(2026, 10, 21, 12))
        )
    }

    @Test fun rangeStatus_lastDay() {
        assertEquals(
            "最后一天",
            Format.rangeStatus(
                epoch(2026, 10, 19), epoch(2026, 10, 21), today, epoch(2026, 10, 21, 12),
                hasExactTime = false
            )
        )
    }

    @Test fun rangeStatus_countsRemainingDays() {
        assertEquals(
            "还剩2天",
            Format.rangeStatus(epoch(2026, 10, 20), epoch(2026, 10, 23), today, epoch(2026, 10, 21, 12))
        )
        // 开始日当天也算"还剩"，不显示"进行中"这种空话
        assertEquals(
            "还剩3天",
            Format.rangeStatus(epoch(2026, 10, 21), epoch(2026, 10, 24), today, epoch(2026, 10, 21, 12))
        )
    }

    // 核心诉求：最后一天过完才算逾期，结束日当天 23:59 仍是"最后一天"。
    @Test fun rangeStatus_overdueOnlyAfterEndDay() {
        val endOfToday = epoch(2026, 10, 21)
        assertEquals(
            "最后一天",
            Format.rangeStatus(epoch(2026, 10, 19), endOfToday, today, epoch(2026, 10, 21, 23, 59), hasExactTime = false)
        )
        assertEquals(
            "逾期",
            Format.rangeStatus(epoch(2026, 10, 17), epoch(2026, 10, 20), today, epoch(2026, 10, 21, 0, 1), hasExactTime = false)
        )
    }

    @Test fun rangeStatus_nullStart_isNull() {
        assertNull(Format.rangeStatus(null, epoch(2026, 10, 23), today, epoch(2026, 10, 21, 12)))
    }

    // 编辑待办回填输入框的句子：必须是解析器认识的形式（不能用 21–26日 那种紧凑写法）。
    @Test fun rangeSentence_isParseableShape() {
        assertEquals("10月24日到26日", Format.rangeSentence(epoch(2026, 10, 24), epoch(2026, 10, 26)))
        assertEquals(
            "10月28日到11月3日",
            Format.rangeSentence(epoch(2026, 10, 28), epoch(2026, 11, 3))
        )
        assertEquals(
            "2026年12月30日到2027年1月2日",
            Format.rangeSentence(epoch(2026, 12, 30), epoch(2027, 1, 2))
        )
        // 单日退回 monthDay（不带星期，避免「周一」被解析成本周一）
        assertEquals("10月24日", Format.rangeSentence(epoch(2026, 10, 24), epoch(2026, 10, 24)))
    }

    companion object {
        init {
            System.setProperty("user.timezone", "Asia/Shanghai")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        }
    }
}
