package com.lirui.oneline.util

import com.lirui.oneline.data.model.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计口径的钉子。
 *
 * 这几条如果松了，用户看到的数字就会和记忆里对不上（"我明明专注了 25 分钟，
 * 怎么今日是 0"），而这种错是最难被发现的 —— 数字永远是个看起来合理的数字。
 */
class FocusStatsTest {

    private val zone: ZoneId = AppTime.ZONE

    private fun session(
        date: LocalDate,
        startHour: Int,
        seconds: Int,
        completed: Boolean = true,
        label: String = ""
    ): FocusSession {
        val start = date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
        return FocusSession(
            label = label,
            startedAtMillis = start,
            endedAtMillis = start + seconds * 1000L,
            plannedSeconds = 25 * 60,
            actualSeconds = seconds,
            completed = completed,
            createdAt = start
        )
    }

    // ---------- 归日 ----------

    @Test
    fun sessionIsBucketedByItsStartDay_notItsEndDay() {
        // 23:50 开始、走了 40 分钟 → 跨过午夜。整段应算在开始那天。
        val day = LocalDate.of(2026, 10, 20)
        val start = day.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val s = FocusSession(
            startedAtMillis = start,
            endedAtMillis = start + 40 * 60_000L,
            plannedSeconds = 45 * 60,
            actualSeconds = 40 * 60,
            completed = false,
            createdAt = start
        )
        val totals = FocusStats.dayTotals(listOf(s), zone)
        assertEquals(setOf(day), totals.keys)
        assertEquals(2400, totals[day]!!.seconds)
    }

    @Test
    fun twoSessionsOnTheSameDayAreSummed() {
        val d = LocalDate.of(2026, 10, 21)
        val totals = FocusStats.dayTotals(
            listOf(session(d, 9, 1500), session(d, 14, 900)),
            zone
        )
        assertEquals(2400, totals[d]!!.seconds)
        assertEquals(2, totals[d]!!.count)
    }

    // ---------- 今日 / 本周 / 累计 ----------

    @Test
    fun summary_splitsTodayWeekAndTotal() {
        // 2026-10-21 是周三 → 本周从 10-19（周一）开始
        val today = LocalDate.of(2026, 10, 21)
        val sessions = listOf(
            session(today, 9, 1500),                    // 今天
            session(today.minusDays(1), 9, 600),        // 周二，本周
            session(today.minusDays(3), 9, 1200),       // 周日 10-18，上周
            session(today.minusDays(30), 9, 3000)       // 很久以前
        )
        val s = FocusStats.summary(sessions, today, zone)

        assertEquals(1500, s.todaySeconds)
        assertEquals(1, s.todayCount)
        assertEquals(2100, s.weekSeconds)   // 1500 + 600
        assertEquals(2, s.weekCount)
        assertEquals(6300, s.totalSeconds)
        assertEquals(4, s.totalCount)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), FocusStats.weekStart(LocalDate.of(2026, 10, 21)))
        // 周一自己 → 还是自己
        assertEquals(LocalDate.of(2026, 10, 19), FocusStats.weekStart(LocalDate.of(2026, 10, 19)))
        // 周日 10-25 → 仍属于 10-19 那一周（不是下一个周一）
        assertEquals(LocalDate.of(2026, 10, 19), FocusStats.weekStart(LocalDate.of(2026, 10, 25)))
    }

    // ---------- 连续天数 ----------

    @Test
    fun streak_countsBackFromToday() {
        val today = LocalDate.of(2026, 10, 21)
        val totals = FocusStats.dayTotals(
            listOf(
                session(today, 9, 1500),
                session(today.minusDays(1), 9, 1500),
                session(today.minusDays(2), 9, 1500),
                session(today.minusDays(4), 9, 1500) // 断了
            ),
            zone
        )
        assertEquals(3, FocusStats.streak(totals, today))
    }

    @Test
    fun streak_survivesWhenTodayIsStillEmpty() {
        // 早上刚起来、今天还没专注 → 连续天数不能清零
        val today = LocalDate.of(2026, 10, 21)
        val totals = FocusStats.dayTotals(
            listOf(
                session(today.minusDays(1), 9, 1500),
                session(today.minusDays(2), 9, 1500)
            ),
            zone
        )
        assertEquals(2, FocusStats.streak(totals, today))
    }

    @Test
    fun streak_isZeroWhenYesterdayAndTodayAreBothEmpty() {
        val today = LocalDate.of(2026, 10, 21)
        val totals = FocusStats.dayTotals(
            listOf(session(today.minusDays(2), 9, 1500)),
            zone
        )
        assertEquals(0, FocusStats.streak(totals, today))
    }

    // ---------- 最近 N 天 ----------

    @Test
    fun lastDays_padsEmptyDaysWithZero() {
        val today = LocalDate.of(2026, 10, 21)
        val days = FocusStats.lastDays(listOf(session(today, 9, 1500)), today, 7, zone)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(today, days.last().date)
        assertEquals(1500, days.last().seconds)
        assertEquals(0, days[days.size - 2].seconds)
    }

    // ---------- 热力图 ----------

    @Test
    fun heatmap_startsOnMondayAndAlwaysCoversWholeWeeks() {
        val today = LocalDate.of(2026, 10, 21) // 周三
        val cells = FocusStats.heatmap(emptyList(), today, weeks = 4, zone = zone)
        assertEquals(28, cells.size)
        assertEquals(java.time.DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        // 最后一天是本周日，不是今天 —— 本周剩下的格子留着，UI 按 7 个一行切就对齐了
        assertEquals(java.time.DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
        assertEquals(today.plusDays(4), cells.last().date)
    }

    @Test
    fun heatmap_fillsTheRightCell() {
        val today = LocalDate.of(2026, 10, 21)
        val target = LocalDate.of(2026, 10, 14)
        val cells = FocusStats.heatmap(listOf(session(target, 9, 1500)), today, weeks = 4, zone)
        val cell = cells.first { it.date == target }
        assertEquals(1500, cell.seconds)
        assertEquals(1, cell.count)
    }

    @Test
    fun emptyInputYieldsZeroesEverywhere() {
        val today = LocalDate.of(2026, 10, 21)
        val s = FocusStats.summary(emptyList(), today, zone)
        assertEquals(0, s.todaySeconds)
        assertEquals(0, s.weekSeconds)
        assertEquals(0, s.totalSeconds)
        assertEquals(0, s.streakDays)
    }
}
