package com.lirui.oneline.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Home-list grouping rules. The date anchor is 2026-10-21 (Wednesday), so the natural week
 * runs 10-19 (Mon) .. 10-25 (Sun) and next week is 10-26 .. 11-01.
 */
class HomeGroupingTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 21)

    private fun at(y: Int, mo: Int, d: Int, h: Int = 9, m: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun now(h: Int = 12, m: Int = 0): Long = at(2026, 10, 21, h, m)

    // --- board ---

    @Test fun board_noDue_isUndated() {
        assertEquals(HomeBoard.UNDATED, HomeGrouping.boardOf(null, today, now()))
    }

    @Test fun board_future_isDated() {
        assertEquals(HomeBoard.DATED, HomeGrouping.boardOf(at(2026, 10, 22), today, now()))
    }

    @Test fun board_exactTimePassed_isOverdue() {
        assertEquals(
            HomeBoard.OVERDUE,
            HomeGrouping.boardOf(at(2026, 10, 21, 9, 0), today, now(12, 0), hasExactTime = true)
        )
    }

    // Date-only todos keep the whole day: 09:00 has passed but the date has not.
    @Test fun board_dateOnlyToday_isDated() {
        assertEquals(
            HomeBoard.DATED,
            HomeGrouping.boardOf(at(2026, 10, 21, 9, 0), today, now(12, 0), hasExactTime = false)
        )
    }

    @Test fun board_dateOnlyYesterday_isOverdue() {
        assertEquals(
            HomeBoard.OVERDUE,
            HomeGrouping.boardOf(at(2026, 10, 20), today, now(), hasExactTime = false)
        )
    }

    // --- bucket ---

    @Test fun bucket_today() {
        assertEquals(HomeBucket.TODAY, HomeGrouping.bucketOf(at(2026, 10, 21, 18, 0), today))
    }

    @Test fun bucket_tomorrow() {
        assertEquals(HomeBucket.TOMORROW, HomeGrouping.bucketOf(at(2026, 10, 22), today))
    }

    @Test fun bucket_dayAfter() {
        assertEquals(HomeBucket.DAY_AFTER, HomeGrouping.bucketOf(at(2026, 10, 23), today))
    }

    // Sat 10-24 is inside the current natural week but past 后天.
    @Test fun bucket_sameWeek() {
        assertEquals(HomeBucket.THIS_WEEK, HomeGrouping.bucketOf(at(2026, 10, 24), today))
    }

    @Test fun bucket_nextWeek() {
        assertEquals(HomeBucket.NEXT_WEEK, HomeGrouping.bucketOf(at(2026, 10, 26), today))
        assertEquals(HomeBucket.NEXT_WEEK, HomeGrouping.bucketOf(at(2026, 11, 1), today))
    }

    @Test fun bucket_later() {
        assertEquals(HomeBucket.LATER, HomeGrouping.bucketOf(at(2026, 11, 2), today))
    }

    // 后天 must win over 本周 even when it crosses a month boundary.
    @Test fun bucket_nearFutureWrapsAcrossMonth() {
        val fri = LocalDate.of(2026, 10, 30)
        assertEquals(HomeBucket.DAY_AFTER, HomeGrouping.bucketOf(at(2026, 11, 1), fri))
        assertEquals(HomeBucket.NEXT_WEEK, HomeGrouping.bucketOf(at(2026, 11, 2), fri))
    }

    // Grouping is date-based: at 23:59 tonight, tomorrow's todo is still 明天/有日期.
    @Test fun board_tomorrowLateTonight_staysDated() {
        assertEquals(
            HomeBoard.DATED,
            HomeGrouping.boardOf(at(2026, 10, 22, 9), today, at(2026, 10, 21, 23, 59))
        )
    }

    // --- bucket (时间段) ---
    //
    // 规则：进行中 -> 今日；还没开始 -> 按开始日归档；单日（start=null）-> 按结束日归档。
    // 「进行中进今日」是关键取舍：一段跨到下周的任务今天就在做，沉到"下周"用户会漏看。

    @Test fun rangeBucket_inProgress_isToday() {
        assertEquals(
            HomeBucket.TODAY,
            HomeGrouping.bucketOfRange(at(2026, 10, 20), at(2026, 10, 23), today)
        )
    }

    @Test fun rangeBucket_startingToday_isToday() {
        assertEquals(
            HomeBucket.TODAY,
            HomeGrouping.bucketOfRange(at(2026, 10, 21), at(2026, 10, 28), today)
        )
    }

    @Test fun rangeBucket_endingToday_isToday() {
        assertEquals(
            HomeBucket.TODAY,
            HomeGrouping.bucketOfRange(at(2026, 10, 19), at(2026, 10, 21), today)
        )
    }

    // 还没开始 -> 用开始日归档，不能因为结束日在很远就沉到"更晚"。
    @Test fun rangeBucket_notStarted_usesStartBucket() {
        assertEquals(
            HomeBucket.TOMORROW,
            HomeGrouping.bucketOfRange(at(2026, 10, 22), at(2026, 11, 20), today)
        )
        assertEquals(
            HomeBucket.NEXT_WEEK,
            HomeGrouping.bucketOfRange(at(2026, 10, 26), at(2026, 11, 20), today)
        )
    }

    // 单日（start = null）必须和 v0.8.3 完全一致：只看结束日。
    @Test fun rangeBucket_nullStart_fallsBackToEnd() {
        assertEquals(HomeBucket.TODAY, HomeGrouping.bucketOfRange(null, at(2026, 10, 21), today))
        assertEquals(HomeBucket.TOMORROW, HomeGrouping.bucketOfRange(null, at(2026, 10, 22), today))
        assertEquals(HomeBucket.LATER, HomeGrouping.bucketOfRange(null, at(2026, 11, 20), today))
    }

    @Test fun rangeBucket_noDates_isLater() {
        assertEquals(HomeBucket.LATER, HomeGrouping.bucketOfRange(null, null, today))
    }

    // --- merge rule ---

    @Test fun resolve_allEnabled_keepsOriginal() {
        // 用 all 而不是 defaultEnabled：默认档位从 2026-10-23 起只勾 今日/本周/更晚，
        // 本用例测的是"全勾时不发生合并"，与默认值无关。
        val all = HomeBucket.all.toSet()
        for (b in HomeBucket.all) assertEquals(b, HomeGrouping.resolveBucket(b, all))
    }

    @Test fun resolve_nothingEnabled_isFlat() {
        assertNull(HomeGrouping.resolveBucket(HomeBucket.TOMORROW, emptySet()))
    }

    // 今日 + 本周 + 更晚: tomorrow and the day after must be absorbed by 本周, not dropped.
    @Test fun resolve_partial_mergesForward() {
        val enabled = setOf(HomeBucket.TODAY, HomeBucket.THIS_WEEK, HomeBucket.LATER)
        assertEquals(HomeBucket.TODAY, HomeGrouping.resolveBucket(HomeBucket.TODAY, enabled))
        assertEquals(HomeBucket.THIS_WEEK, HomeGrouping.resolveBucket(HomeBucket.TOMORROW, enabled))
        assertEquals(HomeBucket.THIS_WEEK, HomeGrouping.resolveBucket(HomeBucket.DAY_AFTER, enabled))
        assertEquals(HomeBucket.THIS_WEEK, HomeGrouping.resolveBucket(HomeBucket.THIS_WEEK, enabled))
        assertEquals(HomeBucket.LATER, HomeGrouping.resolveBucket(HomeBucket.NEXT_WEEK, enabled))
        assertEquals(HomeBucket.LATER, HomeGrouping.resolveBucket(HomeBucket.LATER, enabled))
    }

    // 今日 + 明天 + 本周 + 更晚: only 后天 differs from the full set.
    @Test fun resolve_todayTomorrowWeekLater() {
        val enabled = setOf(
            HomeBucket.TODAY, HomeBucket.TOMORROW, HomeBucket.THIS_WEEK, HomeBucket.LATER
        )
        assertEquals(HomeBucket.TOMORROW, HomeGrouping.resolveBucket(HomeBucket.TOMORROW, enabled))
        assertEquals(HomeBucket.THIS_WEEK, HomeGrouping.resolveBucket(HomeBucket.DAY_AFTER, enabled))
        assertEquals(HomeBucket.LATER, HomeGrouping.resolveBucket(HomeBucket.NEXT_WEEK, enabled))
    }

    // Only 今日 selected: nothing later is enabled, so 今日 catches the rest (no data loss).
    @Test fun resolve_singleEarlyBucket_catchesEverything() {
        val enabled = setOf(HomeBucket.TODAY)
        assertEquals(HomeBucket.TODAY, HomeGrouping.resolveBucket(HomeBucket.LATER, enabled))
    }

    // --- boards order / visibility ---

    @Test fun visibleBoards_followsConfiguredOrder() {
        val order = listOf(HomeBoard.UNDATED, HomeBoard.DATED, HomeBoard.OVERDUE)
        assertEquals(order, HomeGrouping.visibleBoards(order, emptySet()))
    }

    @Test fun visibleBoards_dropsHidden() {
        val got = HomeGrouping.visibleBoards(HomeBoard.defaultOrder, setOf(HomeBoard.UNDATED))
        assertEquals(listOf(HomeBoard.OVERDUE, HomeBoard.DATED), got)
    }

    // Safety net: hiding everything must not produce an empty home screen.
    @Test fun visibleBoards_allHidden_fallsBackToDefault() {
        val got = HomeGrouping.visibleBoards(HomeBoard.defaultOrder, HomeBoard.all.toSet())
        assertEquals(HomeBoard.defaultOrder, got)
        assertTrue(got.isNotEmpty())
    }
}
