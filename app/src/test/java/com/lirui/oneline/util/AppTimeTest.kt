package com.lirui.oneline.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material3 DatePicker 的毫秒口径（v0.8.7 修的「点编辑时日期比实际小 1」）。
 *
 * 契约：`initialSelectedDateMillis` / `selectedDateMillis` 都是**UTC 当日 00:00**。
 * 以前初始值传的是本地午夜（北京时间 00:00 = UTC 前一天 16:00），日历按 UTC 读出来
 * 就是前一天 —— 用户点日期 chip 看到的日期比实际小 1 天，点「确定」还会把它真的存回去。
 */
class AppTimeTest {

    @Test
    fun pickerMillis_readsBackAsTheSameDayInUtc() {
        val d = LocalDate.of(2026, 10, 24)
        val ms = AppTime.pickerMillis(d)

        // DatePicker 内部按 UTC 解读 → 必须还是 10/24
        assertEquals(d, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(d, AppTime.pickerDate(ms))
    }

    @Test
    fun localMidnight_isTheBug_localMidnightIsPreviousDayInUtc() {
        val d = LocalDate.of(2026, 10, 24)
        val localMidnight = d.atStartOfDay(AppTime.ZONE).toInstant().toEpochMilli()

        // 两者不是同一个值：本地午夜早了 8 小时
        assertNotEquals(localMidnight, AppTime.pickerMillis(d))
        // 而本地午夜按 UTC 读，正好是前一天 —— 就是用户看到的现象
        assertEquals(
            LocalDate.of(2026, 10, 23),
            Instant.ofEpochMilli(localMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        )
    }

    @Test
    fun pickerMillis_roundTripsForEveryDayOfTwoYears() {
        var d = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 12, 31)
        while (!d.isAfter(end)) {
            assertEquals(d, AppTime.pickerDate(AppTime.pickerMillis(d)))
            d = d.plusDays(1)
        }
    }
}
