package com.lirui.oneline.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The app pins ONE timezone: 北京时间 (Asia/Shanghai).
 *
 * `dueAtMillis` is an absolute epoch, but every date the user sees — and every
 * relative word the parser resolves ("今天" / "明天" / "下周三") — must use the same zone.
 * Previously the parser used `LocalDate.now()` (device zone) while the repository
 * built the epoch from `ZoneId.systemDefault()`; on a device that changes timezone
 * the two drift apart and the list grouping stops matching the alarms.
 *
 * The zone is also pinned in the unit tests (`Asia/Shanghai`), so parsing and
 * formatting stay reproducible everywhere.
 */
object AppTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun now(): LocalTime = LocalTime.now(ZONE)

    fun nowMillis(): Long = System.currentTimeMillis()

    // ---------- Material3 DatePicker 的毫秒口径 ----------
    //
    // `rememberDatePickerState(initialSelectedDateMillis = …)` 与 `selectedDateMillis` 的契约都是
    // **UTC 当日 00:00**，不是本地午夜。把 `atStartOfDay(AppTime.ZONE)` 塞进去早了 8 小时，
    // 日历按 UTC 解读就成了**前一天** —— 用户点编辑时看到「日期比实际小 1 天」。
    // 出入两端都走这两个函数，别再各自做时区换算。

    private const val MILLIS_PER_DAY = 86_400_000L

    /** [date] → DatePicker 要的「UTC 当日 00:00」毫秒。 */
    fun pickerMillis(date: LocalDate): Long = date.toEpochDay() * MILLIS_PER_DAY

    /** DatePicker 给的毫秒 → 用户选的那一天（按 UTC 解析，不做本地时区换算）。 */
    fun pickerDate(millis: Long): LocalDate =
        LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))
}
