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
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE)
        return "%s %s".format(WEEKDAYS_CN[zdt.dayOfWeek.value - 1], timeLabel(zdt.toLocalTime()))
    }

    fun time(due: Long): String {
        val t = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE).toLocalTime()
        return timeLabel(t)
    }

    /** The fine detail line under the title, ordered 年份(如有)→日期→星期→时间. */
    fun dueLine(due: Long, today: LocalDate = AppTime.today()): String {
        val zdt = java.time.Instant.ofEpochMilli(due).atZone(AppTime.ZONE)
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
}