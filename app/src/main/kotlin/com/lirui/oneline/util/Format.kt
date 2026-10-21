package com.lirui.oneline.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Format {

    private val WEEKDAYS_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dueAtMillis(due: Long): LocalDate =
        java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()

    fun date(due: Long): String {
        val d = dueAtMillis(due)
        return "%d月%d日 %s".format(Locale.CHINA, d.monthValue, d.dayOfMonth, WEEKDAYS_CN[d.dayOfWeek.value - 1])
    }

    /** Short date label with weekday for the add-sheet preview, e.g. "10月24日 周六". */
    fun dateShort(d: LocalDate): String =
        "%d月%d日 %s".format(Locale.CHINA, d.monthValue, d.dayOfMonth, WEEKDAYS_CN[d.dayOfWeek.value - 1])

    /** Time label: whole hours read "15点", otherwise "15:30". */
    fun timeLabel(t: java.time.LocalTime): String =
        if (t.minute == 0) "${t.hour}点" else String.format("%d:%02d", t.hour, t.minute)

    /** Date without weekday, e.g. "10月24日" (right-hand meta on a todo row). */
    fun monthDay(d: LocalDate): String =
        "%d月%d日".format(Locale.CHINA, d.monthValue, d.dayOfMonth)

    /** Weekday + time, e.g. "周六 15点" (secondary line under the title). */
    fun weekdayTime(due: Long): String {
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        return "%s %s".format(WEEKDAYS_CN[zdt.dayOfWeek.value - 1], timeLabel(zdt.toLocalTime()))
    }

    fun time(due: Long): String {
        val t = java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalTime()
        return timeLabel(t)
    }

    /** The fine detail line under the title, ordered 年份(如有)→日期→星期→时间. */
    fun dueLine(due: Long, today: LocalDate = LocalDate.now()): String {
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val d = zdt.toLocalDate()
        val t = zdt.toLocalTime()
        val parts = ArrayList<String>()
        if (d.year != today.year) parts += "%d年".format(d.year, Locale.CHINA)
        parts += "%d月%d日".format(Locale.CHINA, d.monthValue, d.dayOfMonth)
        parts += WEEKDAYS_CN[d.dayOfWeek.value - 1]
        parts += if (t.minute == 0) "${t.hour}点" else String.format("%d:%02d", t.hour, t.minute)
        return parts.joinToString(" ")
    }

    /**
     * Group label for the list headers, based on *natural weeks* (Monday–Sunday).
     * Previously this used "today + 2..+6 days", which pushed next week's Monday/Tuesday
     * into 本周 whenever today sat early in the week.
     *
     * [nowMillis] matters: a todo due **today at 23:39** is already overdue at 23:59, but
     * comparing dates alone kept it under 今天 forever. Callers should pass the same
     * ticking `now` they use for the red marking so the two never disagree.
     */
    fun groupLabel(
        due: Long?,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (due == null) return "无日期"
        val d = dueAtMillis(due)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val thisSunday = today.with(DayOfWeek.SUNDAY)
        val nextMonday = thisMonday.plusWeeks(1)
        val nextSunday = thisSunday.plusWeeks(1)
        return when {
            d.isBefore(today) -> "已逾期"
            // 今天的时刻已经过点 -> 同样算逾期（只比日期会一直挂在"今天"下）。
            d == today && due <= nowMillis -> "已逾期"
            d == today -> "今天"
            d == today.plusDays(1) -> "明天"
            d == today.plusDays(2) -> "后天"
            !d.isBefore(thisMonday) && !d.isAfter(thisSunday) -> "本周"
            !d.isBefore(nextMonday) && !d.isAfter(nextSunday) -> "下周"
            else -> "更晚"
        }
    }
}