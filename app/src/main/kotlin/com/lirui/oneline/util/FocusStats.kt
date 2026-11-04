package com.lirui.oneline.util

import com.lirui.oneline.data.model.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 一天的专注合计。 */
data class DayTotal(
    val date: LocalDate,
    val seconds: Int,
    val count: Int
)

/** 统计页顶部三个数字 + 连续天数。 */
data class FocusSummary(
    val todaySeconds: Int = 0,
    val todayCount: Int = 0,
    val weekSeconds: Int = 0,
    val weekCount: Int = 0,
    val totalSeconds: Int = 0,
    val totalCount: Int = 0,
    val streakDays: Int = 0
)

/**
 * 专注记录的聚合。
 *
 * ★ 全部是**纯函数**，输入 `List<FocusSession>` + 一个"今天"，输出数字。
 *   这样统计口径能被单元测试钉死（FocusStatsTest），也不会因为 Compose 重组
 *   反复查库 —— UI 侧只订阅一次全量 Flow，聚合在内存里做。
 *
 * ★ 归日按 `startedAtMillis`（开始那一刻），不是结束那一刻。
 *   23:50 开始、00:20 结束的一次专注整段算在**前一天** —— 否则跨午夜那次
 *   会被劈成两半，两边各 10 分钟，热力图和"今日时长"都变得莫名其妙。
 */
object FocusStats {

    fun dateOf(millis: Long, zone: ZoneId = AppTime.ZONE): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** 按天汇总。 */
    fun dayTotals(
        sessions: List<FocusSession>,
        zone: ZoneId = AppTime.ZONE
    ): Map<LocalDate, DayTotal> {
        val acc = HashMap<LocalDate, DayTotal>()
        sessions.forEach { s ->
            val d = dateOf(s.startedAtMillis, zone)
            val prev = acc[d]
            acc[d] = if (prev == null) {
                DayTotal(d, s.actualSeconds, 1)
            } else {
                prev.copy(seconds = prev.seconds + s.actualSeconds, count = prev.count + 1)
            }
        }
        return acc
    }

    /** 本周起点（周一）。中文语境里「本周」默认从周一开始，不是周日。 */
    fun weekStart(today: LocalDate): LocalDate =
        today.minusDays((today.dayOfWeek.value - 1).toLong())

    fun summary(
        sessions: List<FocusSession>,
        today: LocalDate,
        zone: ZoneId = AppTime.ZONE
    ): FocusSummary {
        val totals = dayTotals(sessions, zone)
        val weekFrom = weekStart(today)
        val weekTo = weekFrom.plusDays(6)
        var weekSeconds = 0
        var weekCount = 0
        totals.forEach { (d, t) ->
            if (!d.isBefore(weekFrom) && !d.isAfter(weekTo)) {
                weekSeconds += t.seconds
                weekCount += t.count
            }
        }
        val todayTotal = totals[today]
        return FocusSummary(
            todaySeconds = todayTotal?.seconds ?: 0,
            todayCount = todayTotal?.count ?: 0,
            weekSeconds = weekSeconds,
            weekCount = weekCount,
            totalSeconds = sessions.sumOf { it.actualSeconds },
            totalCount = sessions.size,
            streakDays = streak(totals, today)
        )
    }

    /** 最近 [days] 天（含今天），按日期升序，没记录的补 0。 */
    fun lastDays(
        sessions: List<FocusSession>,
        today: LocalDate,
        days: Int,
        zone: ZoneId = AppTime.ZONE
    ): List<DayTotal> {
        val totals = dayTotals(sessions, zone)
        val start = today.minusDays((days - 1).toLong())
        return (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            totals[d] ?: DayTotal(d, 0, 0)
        }
    }

    /**
     * 热力图：最近 [weeks] 周，**从周一起算**，按天升序。
     *
     * 返回长度恒为 `weeks * 7`，末尾几天是"本周剩下的、还没到的日子"（值为 0）。
     * 这样 UI 直接按 7 个一行切块就行，不用自己补空位。
     */
    fun heatmap(
        sessions: List<FocusSession>,
        today: LocalDate,
        weeks: Int,
        zone: ZoneId = AppTime.ZONE
    ): List<DayTotal> {
        val totals = dayTotals(sessions, zone)
        val offsetInWeek = (today.dayOfWeek.value - 1).toLong()
        val start = today.minusDays(offsetInWeek + (weeks - 1) * 7L)
        return (0 until weeks * 7).map { i ->
            val d = start.plusDays(i.toLong())
            totals[d] ?: DayTotal(d, 0, 0)
        }
    }

    /**
     * 连续专注天数。
     *
     * 口径：
     *  - 今天有记录 → 从今天往前数；
     *  - 今天还没记 → 从**昨天**往前数（一天还没过完，不能算断）；
     *  - 昨天也没有 → 0。
     *
     * 这样"早上起来打开 App"不会看到连续天数被清零 —— 那是把用户推走的做法。
     */
    fun streak(totals: Map<LocalDate, DayTotal>, today: LocalDate): Int {
        val has = { d: LocalDate -> (totals[d]?.count ?: 0) > 0 }
        var cursor = when {
            has(today) -> today
            has(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var n = 0
        while (has(cursor)) {
            n++
            cursor = cursor.minusDays(1)
        }
        return n
    }
}
