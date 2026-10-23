package com.lirui.oneline.util

import java.time.LocalDate
import java.util.Locale

object Format {

    private val WEEKDAYS_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dueAtMillis(due: Long): LocalDate =
        java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE).toLocalDate()

    fun date(due: Long): String {
        val d = dueAtMillis(due)
        return "%d月%d日 %s".format(Locale.CHINA, d.monthValue, d.dayOfMonth, WEEKDAYS_CN[d.dayOfWeek.value - 1])
    }

    /**
     * Short date label with weekday for the add-sheet preview, e.g. "10月24日 周六".
     *
     * 跨年时必须带上年份：没写年份的「1月2日」在 10 月输入会被顺延到**明年**，
     * 不显示年份的话用户看不出它到底落在哪一年，会以为解析错了。
     */
    fun dateShort(d: LocalDate, today: LocalDate = AppTime.today()): String {
        val md = "%d月%d日 %s".format(
            Locale.CHINA, d.monthValue, d.dayOfMonth, WEEKDAYS_CN[d.dayOfWeek.value - 1]
        )
        return if (d.year != today.year) "%d年 %s".format(Locale.CHINA, d.year, md) else md
    }

    /** Time label: whole hours read "15点", otherwise "15:30". */
    fun timeLabel(t: java.time.LocalTime): String =
        if (t.minute == 0) "${t.hour}点" else String.format("%d:%02d", t.hour, t.minute)

    /** Date without weekday, e.g. "10月24日" (right-hand meta on a todo row). */
    fun monthDay(d: LocalDate): String =
        "%d月%d日".format(Locale.CHINA, d.monthValue, d.dayOfMonth)

    /** Weekday + time, e.g. "周六 15点" (secondary line under the title). */
    fun weekdayTime(due: Long): String {
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE)
        return "%s %s".format(WEEKDAYS_CN[zdt.dayOfWeek.value - 1], timeLabel(zdt.toLocalTime()))
    }

    fun time(due: Long): String {
        val t = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE).toLocalTime()
        return timeLabel(t)
    }

    /** The fine detail line under the title, ordered 年份(如有)→日期→星期→时间.
     *  [showWeekday]=false 时省略「周几」（小组件默认不带星期，zuige 要求）。
     *  [showTime]=false 时只到日期（「10月23日」），不带「14点」——小组件「只显示日期」开关用。
     *  参数顺序保持 today 在 showTime 之前，老的 `dueLine(due, today)` 位置调用不受影响。 */
    fun dueLine(
        due: Long,
        showWeekday: Boolean = true,
        today: LocalDate = AppTime.today(),
        showTime: Boolean = true
    ): String {
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE)
        val d = zdt.toLocalDate()
        val t = zdt.toLocalTime()
        val parts = ArrayList<String>()
        if (d.year != today.year) parts += "%d年".format(d.year, Locale.CHINA)
        parts += "%d月%d日".format(Locale.CHINA, d.monthValue, d.dayOfMonth)
        if (showWeekday) parts += WEEKDAYS_CN[d.dayOfWeek.value - 1]
        if (showTime) {
            parts += if (t.minute == 0) "${t.hour}点" else String.format("%d:%02d", t.hour, t.minute)
        }
        return parts.joinToString(" ")
    }

    /**
     * Single source of truth for "is this todo overdue?" — the list grouping and the red
     * marking MUST both call this, otherwise you get rows sitting under 已逾期 that aren't
     * red (or vice versa).
     *
     * Two different clocks are involved:
     * - **exact time given** ("23:39"): overdue once that instant has passed.
     * - **date only** ("今天开会"): the whole day is still available, so it is only overdue
     *   once the *date* has passed. (The row still carries the default remind time of 09:00
     *   for notification purposes, but falling back to it for the overdue test would
     *   make every date-only todo instantly red as soon as 09:00 has gone by.)
     *
     * `overduePin` (设置 → 逾期置顶) deliberately does NOT gate this: it only controls
     * sorting. Colouring must never depend on a sort preference.
     */
    fun isOverdue(
        due: Long?,
        today: LocalDate = AppTime.today(),
        nowMillis: Long = AppTime.nowMillis(),
        hasExactTime: Boolean = true
    ): Boolean {
        if (due == null) return false
        if (!hasExactTime) return dueAtMillis(due).isBefore(today)
        return due <= nowMillis
    }

    // ---------- 时间段（v0.8.4）----------
    //
    // 约定：start = 开始日（可空，null = 单日），end = 最后一天（即 dueAtMillis）。
    // 逾期永远只看 end —— 见 [isOverdue]，所以「最后一天结束才算逾期」是天然成立的。

    /** 今天是否落在 [start, end] 之间（含两端）。单日（start == null）恒 false。 */
    fun isInProgress(
        start: Long?,
        end: Long?,
        today: LocalDate = AppTime.today()
    ): Boolean {
        if (start == null || end == null) return false
        val s = dueAtMillis(start)
        val e = dueAtMillis(end)
        return !today.isBefore(s) && !today.isAfter(e)
    }

    /** 距结束日还有几天：0 = 今天结束，负数 = 已过结束日。 */
    fun daysLeft(end: Long, today: LocalDate = AppTime.today()): Long =
        java.time.temporal.ChronoUnit.DAYS.between(today, dueAtMillis(end))

    /**
     * 时间段的一行文案：`10月21日–23日` / `10月21日–11月3日` / `2026年10月21日–2027年1月3日`，
     * 末尾按需带星期与结束时刻。单日退回 [dueLine]。
     */
    fun rangeLine(
        start: Long?,
        end: Long,
        showWeekday: Boolean = true,
        today: LocalDate = AppTime.today(),
        showTime: Boolean = true
    ): String {
        if (start == null) return dueLine(end, showWeekday, today, showTime)
        val s = dueAtMillis(start)
        val e = dueAtMillis(end)
        if (s == e) return dueLine(end, showWeekday, today, showTime)

        val suffix = buildString {
            if (showWeekday) append(" ").append(WEEKDAYS_CN[e.dayOfWeek.value - 1])
            if (showTime) {
                val t = java.time.Instant.ofEpochMilli(end).atZone(AppTime.ZONE).toLocalTime()
                append(" ").append(if (t.minute == 0) "${t.hour}点" else String.format("%d:%02d", t.hour, t.minute))
            }
        }
        return when {
            s.year == e.year && s.monthValue == e.monthValue ->
                "%d月%d日–%d日%s".format(Locale.CHINA, s.monthValue, s.dayOfMonth, e.dayOfMonth, suffix)
            s.year == e.year ->
                "%d月%d日–%d月%d日%s".format(Locale.CHINA, s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth, suffix)
            else ->
                "%d年%d月%d日–%d年%d月%d日%s".format(Locale.CHINA, s.year, s.monthValue, s.dayOfMonth, e.year, e.monthValue, e.dayOfMonth, suffix)
        }
    }

    /**
     * 能被解析器**原样读回去**的区间句子：`10月24日到26日` / `10月28日到11月3日` / 跨年带年份。
     * 编辑待办时用它回填输入框 —— 不能用 [monthDayRange]（`21–26日` 解析器不认识）。
     */
    fun rangeSentence(start: Long, end: Long): String {
        val s = dueAtMillis(start)
        val e = dueAtMillis(end)
        if (s == e) return monthDay(e)
        return when {
            s.year == e.year && s.monthValue == e.monthValue ->
                "%d月%d日到%d日".format(Locale.CHINA, s.monthValue, s.dayOfMonth, e.dayOfMonth)
            s.year == e.year ->
                "%d月%d日到%d月%d日".format(
                    Locale.CHINA, s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth
                )
            else ->
                "%d年%d月%d日到%d年%d月%d日".format(
                    Locale.CHINA, s.year, s.monthValue, s.dayOfMonth, e.year, e.monthValue, e.dayOfMonth
                )
        }
    }

    /**
     * 编辑待办时回填输入框的句子。**必须能被 [com.lirui.oneline.parser.Parser] 原样读回成同一条待办**：
     * - 时间段写成 [rangeSentence]（`10月24日到26日`），否则开始日会丢、区间退化成单日；
     * - 单日只写日期，**不拼星期**。旧实现拼出「10月26日 周一」，而「周一」会被解析成
     *   本周一（10/19），一点保存就多出一条已经过期的待办。
     */
    fun editSentence(
        title: String,
        detail: String,
        start: Long?,
        due: Long?,
        today: LocalDate = AppTime.today()
    ): String = buildString {
        append(title)
        if (detail.isNotBlank()) {
            append(" ")
            append(detail)
        }
        if (due == null) return@buildString
        append(" ")
        if (start != null) {
            append(rangeSentence(start, due))
        } else {
            val d = dueAtMillis(due)
            append(if (d == today) "今天" else monthDay(d))
        }
        // Pinned zone —— 回填的句子必须和创建时读到的墙钟一致，不受设备时区影响。
        val t = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE).toLocalTime()
        append(" ")
        append(if (t.minute == 0) "${t.hour}点" else "${t.hour}点${t.minute}分")
    }

    /** 右侧紧凑范围：`21–23日` / `10/21–11/3`；单日退回 [monthDay]。 */    fun monthDayRange(start: Long?, end: Long): String {
        if (start == null) return monthDay(dueAtMillis(end))
        val s = dueAtMillis(start)
        val e = dueAtMillis(end)
        if (s == e) return monthDay(e)
        return if (s.year == e.year && s.monthValue == e.monthValue) {
            "%d–%d日".format(Locale.CHINA, s.dayOfMonth, e.dayOfMonth)
        } else {
            "%d/%d–%d/%d".format(Locale.CHINA, s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth)
        }
    }

    /**
     * 时间段的状态短语：`未开始` / `最后一天` / `还剩N天` / `进行中` / `逾期`。
     * 单日（start == null）返回 null —— 单日不需要这层状态。
     */
    fun rangeStatus(
        start: Long?,
        end: Long,
        today: LocalDate = AppTime.today(),
        nowMillis: Long = AppTime.nowMillis(),
        hasExactTime: Boolean = true
    ): String? {
        if (start == null) return null
        if (isOverdue(end, today, nowMillis, hasExactTime)) return "逾期"
        val s = dueAtMillis(start)
        if (today.isBefore(s)) return "未开始"
        if (today == dueAtMillis(end)) return "最后一天"
        val left = daysLeft(end, today)
        return if (left > 0) "还剩${left}天" else "进行中"
    }
}