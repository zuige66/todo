package com.lirui.oneline.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「合并为时间段」纯函数测试。UI 侧只有开关，语义全在这里。
 */
class RangeMergeTest {

    private fun p(
        day: Int,
        time: LocalTime? = null,
        error: Boolean = false,
        repeatDays: Int = 0
    ) = ParsedTodo(
        title = "出差",
        dueDate = LocalDate.of(2026, 10, day),
        dueTime = time,
        repeatDays = repeatDays,
        dateError = error
    )

    @Test
    fun 两条合并成一条时间段_起止取最小最大() {
        val merged = RangeMerge.merge(listOf(p(24), p(25)))
        assertEquals(1, merged.size)
        assertEquals(LocalDate.of(2026, 10, 24), merged[0].startDate)
        assertEquals(LocalDate.of(2026, 10, 25), merged[0].dueDate)
        // 第一条的标题/时间保留
        assertEquals("出差", merged[0].title)
    }

    @Test
    fun 出现顺序颠倒也得到正序区间() {
        val merged = RangeMerge.merge(listOf(p(25), p(24)))
        assertEquals(1, merged.size)
        assertEquals(LocalDate.of(2026, 10, 24), merged[0].startDate)
        assertEquals(LocalDate.of(2026, 10, 25), merged[0].dueDate)
    }

    @Test
    fun 同一天两条合并后退化成单日() {
        val merged = RangeMerge.merge(listOf(p(24), p(24)))
        assertEquals(1, merged.size)
        // start == due：TodoRepository.parsedToStart 会按单日处理，这里保持显式相等
        assertEquals(merged[0].startDate, merged[0].dueDate)
    }

    @Test
    fun 时间取第一条的() {
        val merged = RangeMerge.merge(
            listOf(p(24, LocalTime.of(9, 30)), p(25, LocalTime.of(18, 0)))
        )
        assertEquals(LocalTime.of(9, 30), merged[0].dueTime)
        assertTrue(merged[0].hasExactTime)
    }

    @Test
    fun 任一条日期已过_合并后仍然报错() {
        val merged = RangeMerge.merge(listOf(p(24, error = true), p(25)))
        assertEquals(1, merged.size)
        assertTrue(merged[0].dateError)
    }

    @Test
    fun 一条都没问题则无错误() {
        val merged = RangeMerge.merge(listOf(p(24), p(25)))
        assertFalse(merged[0].dateError)
        assertFalse(merged[0].timeError)
    }

    @Test
    fun 无日期的条目混入时不允许合并() {
        val noDate = ParsedTodo(title = "买牛奶", dueDate = null, dueTime = null)
        assertFalse(RangeMerge.canMerge(listOf(p(24), noDate)))
        // merge 对不满足条件的输入原样返回
        val same = listOf(p(24), noDate)
        assertEquals(same, RangeMerge.merge(same))
    }

    @Test
    fun 单条不允许合并() {
        assertFalse(RangeMerge.canMerge(listOf(p(24))))
    }

    @Test
    fun 多条全部有日期才允许合并() {
        assertTrue(RangeMerge.canMerge(listOf(p(24), p(25), p(26))))
    }

    @Test
    fun 重复计划保留第一条的() {
        val merged = RangeMerge.merge(listOf(p(24, repeatDays = 1), p(25)))
        assertEquals(1, merged[0].repeatDays)
    }

    @Test
    fun 合并结果的起止都在同一个月内() {
        val merged = RangeMerge.merge(listOf(p(24), p(25)))
        assertEquals(10, merged[0].startDate?.monthValue)
        assertEquals(10, merged[0].dueDate?.monthValue)
        // 起止差一天
        assertEquals(1, merged[0].startDate?.until(merged[0].dueDate)?.days)
    }
}
