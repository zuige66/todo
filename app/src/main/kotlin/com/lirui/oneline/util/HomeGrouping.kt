package com.lirui.oneline.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The three "boards" (板块) the home list is split into. Their order and visibility are user
 * settings (设置 → 首页分组), which is why this is an enum and not a hard-coded `when` chain.
 *
 * A dated todo that is already overdue belongs to [OVERDUE], never to [DATED] — the two are
 * mutually exclusive, otherwise one todo would show up twice.
 */
enum class HomeBoard(val id: String, val label: String) {
    OVERDUE("overdue", "逾期"),
    DATED("dated", "有日期"),
    UNDATED("undated", "无期");

    companion object {
        val all: List<HomeBoard> = entries

        /**
         * 默认顺序 = 逾期 → 无期 → 有日期（zuige 2026-10-23 定为首装默认）。
         *
         * 逾期置顶：该处理的先看见；有日期垫底，因为它们自己带时间、有单独的档位分组。
         */
        val defaultOrder: List<HomeBoard> = listOf(OVERDUE, UNDATED, DATED)

        fun byId(id: String): HomeBoard? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Sub-groups *inside* [HomeBoard.DATED]. Which ones are actually rendered is user-selectable;
 * unselected buckets are merged into the next selected one (see [HomeGrouping.resolveBucket]).
 *
 * Declaration order matters: it is the chronological order of the timeline and the order the
 * merge walks in.
 */
enum class HomeBucket(val id: String, val label: String) {
    TODAY("today", "今日"),
    TOMORROW("tomorrow", "明天"),
    DAY_AFTER("day_after", "后天"),
    THIS_WEEK("this_week", "本周"),
    NEXT_WEEK("next_week", "下周"),
    LATER("later", "更晚");

    companion object {
        val all: List<HomeBucket> = entries

        /**
         * 默认只勾 今日 / 本周 / 更晚（zuige 2026-10-23 定为首装默认）。
         *
         * 明天 / 后天 / 下周不单独成组，按 [HomeGrouping.resolveBucket] 并入下一个勾中的档位
         * （明天、后天并入「本周」，下周并入「更晚」），所以首页不会漏掉任何一天。
         */
        val defaultEnabled: Set<HomeBucket> = setOf(TODAY, THIS_WEEK, LATER)

        fun byId(id: String): HomeBucket? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Pure grouping rules for the home list. No Compose, no Android — unit-testable.
 *
 * Two layers:
 * 1. [HomeBoard] — 逾期 / 有日期 / 无日期, ordered & toggled by the user.
 * 2. [HomeBucket] — 今日 / 明天 / 后天 / 本周 / 下周 / 更晚, only inside 有日期.
 */
object HomeGrouping {

    /** Which board a todo belongs to. [due] null = 无日期 (never overdue, never dated). */
    fun boardOf(
        due: Long?,
        today: LocalDate = AppTime.today(),
        nowMillis: Long = AppTime.nowMillis(),
        hasExactTime: Boolean = true
    ): HomeBoard = when {
        due == null -> HomeBoard.UNDATED
        // Same single source of truth the red marking uses.
        Format.isOverdue(due, today, nowMillis, hasExactTime) -> HomeBoard.OVERDUE
        else -> HomeBoard.DATED
    }

    /**
     * The *natural* bucket of a dated, not-yet-overdue todo — ignoring which buckets the user
     * enabled. Today/tomorrow/day-after win over 本周 on purpose: they sit inside the current
     * natural week, so the week branch must never swallow them.
     */
    fun bucketOf(due: Long, today: LocalDate = AppTime.today()): HomeBucket {
        val d = Format.dueAtMillis(due)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val thisSunday = today.with(DayOfWeek.SUNDAY)
        val nextMonday = thisMonday.plusWeeks(1)
        val nextSunday = thisSunday.plusWeeks(1)
        return when {
            d == today -> HomeBucket.TODAY
            d == today.plusDays(1) -> HomeBucket.TOMORROW
            d == today.plusDays(2) -> HomeBucket.DAY_AFTER
            !d.isBefore(thisMonday) && !d.isAfter(thisSunday) -> HomeBucket.THIS_WEEK
            !d.isBefore(nextMonday) && !d.isAfter(nextSunday) -> HomeBucket.NEXT_WEEK
            else -> HomeBucket.LATER
        }
    }

    /**
     * Map a todo's natural bucket onto the bucket it is rendered under.
     *
     * "Merge into the next enabled bucket" is what makes a partial selection like
     * `今日 + 本周 + 更晚` still cover every todo: tomorrow and the day after fall into 本周.
     *
     * @return null when nothing is selected — caller renders the board flat, without headers.
     */
    fun resolveBucket(original: HomeBucket, enabled: Set<HomeBucket>): HomeBucket? {
        if (enabled.isEmpty()) return null
        for (b in HomeBucket.all) {
            if (b.ordinal >= original.ordinal && b in enabled) return b
        }
        // Nothing later is enabled: the last enabled bucket catches everything beyond it.
        return HomeBucket.all.lastOrNull { it in enabled }
    }

    /**
     * Boards to render, in the user's order.
     *
     * Falling back to [HomeBoard.defaultOrder] when everything is hidden is deliberate: an
     * empty home screen with no way back would be a trap. The settings UI also refuses to
     * switch off the last visible board, so this is a second line of defence.
     */
    fun visibleBoards(
        order: List<HomeBoard> = HomeBoard.defaultOrder,
        hidden: Set<HomeBoard> = emptySet()
    ): List<HomeBoard> {
        val kept = order.filter { it !in hidden }
        return kept.ifEmpty { HomeBoard.defaultOrder }
    }
}
