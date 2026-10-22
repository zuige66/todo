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
}
