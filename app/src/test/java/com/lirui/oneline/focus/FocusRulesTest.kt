package com.lirui.oneline.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计时与文案的纯逻辑。
 *
 * 这些函数不碰 Android，也不碰时间源 —— `nowMillis` 永远是参数。
 * 所以"暂停 10 分钟后再继续，总时长没有多算 10 分钟"这种结论可以被钉死。
 */
class FocusRulesTest {

    private fun run(
        plannedSeconds: Int = 25 * 60,
        accumulatedMillis: Long = 0L,
        segmentStart: Long = 1_000L,
        paused: Boolean = false,
        startedAt: Long = 1_000L
    ) = FocusRun(
        plannedSeconds = plannedSeconds,
        accumulatedMillis = accumulatedMillis,
        segmentStartMillis = segmentStart,
        paused = paused,
        startedAtMillis = startedAt
    )

    // ---------- 基本计时 ----------

    @Test
    fun elapsedGrowsWithWallClock() {
        val r = run(segmentStart = 1_000L)
        assertEquals(0L, FocusRules.elapsedMillis(r, 1_000L))
        assertEquals(5_000L, FocusRules.elapsedMillis(r, 6_000L))
        assertEquals(5, FocusRules.elapsedSeconds(r, 6_000L))
    }

    @Test
    fun elapsedIsCappedAtPlanned() {
        val r = run(plannedSeconds = 60, segmentStart = 0L)
        // 走了 10 分钟，但计划只有 1 分钟
        assertEquals(60_000L, FocusRules.elapsedMillis(r, 600_000L))
        assertEquals(60, FocusRules.elapsedSeconds(r, 600_000L))
        assertEquals(0L, FocusRules.remainingMillis(r, 600_000L))
        assertTrue(FocusRules.isFinished(r, 600_000L))
    }

    @Test
    fun negativeDeltaDoesNotGoBackwards() {
        // 用户把系统时间往回调了
        val r = run(segmentStart = 100_000L)
        assertEquals(0L, FocusRules.elapsedMillis(r, 50_000L))
    }

    @Test
    fun remainingFractionGoesFromOneToZero() {
        val r = run(plannedSeconds = 100, segmentStart = 0L)
        assertEquals(1f, FocusRules.remainingFraction(r, 0L), 0.001f)
        assertEquals(0.5f, FocusRules.remainingFraction(r, 50_000L), 0.001f)
        assertEquals(0f, FocusRules.remainingFraction(r, 200_000L), 0.001f)
    }

    // ---------- 暂停 ----------

    @Test
    fun pausedTimerDoesNotAdvance() {
        val started = 0L
        val paused = run(segmentStart = started).pause(10_000L) // 走了 10 秒
        assertEquals(10_000L, paused.accumulatedMillis)
        // 暂停 5 分钟后，elapsed 仍然是 10 秒
        assertEquals(10_000L, FocusRules.elapsedMillis(paused, 310_000L))
        assertEquals(10, FocusRules.elapsedSeconds(paused, 310_000L))
    }

    @Test
    fun resumeStartsANewSegmentWithoutLosingAccumulatedTime() {
        val paused = run(segmentStart = 0L).pause(10_000L)
        val resumed = paused.resume(310_000L) // 5 分钟后继续
        // 继续的那一刻：还是 10 秒
        assertEquals(10_000L, FocusRules.elapsedMillis(resumed, 310_000L))
        // 再走 5 秒 → 15 秒（那 5 分钟的暂停没有被算进去）
        assertEquals(15_000L, FocusRules.elapsedMillis(resumed, 315_000L))
    }

    @Test
    fun pauseIsIdempotent() {
        val once = run(segmentStart = 0L).pause(10_000L)
        val twice = once.pause(20_000L)
        assertEquals(once, twice)
    }

    @Test
    fun resumeIsIdempotentWhenRunning() {
        val r = run(segmentStart = 0L)
        assertEquals(r, r.resume(5_000L))
    }

    // ---------- 取消阈值 ----------

    @Test
    fun shortSessionsAskBeforeBeingRecorded() {
        assertTrue(FocusRules.shouldAskBeforeDiscard(1))
        assertTrue(FocusRules.shouldAskBeforeDiscard(59))
        assertFalse(FocusRules.shouldAskBeforeDiscard(0))
        assertFalse(FocusRules.shouldAskBeforeDiscard(60))
    }

    @Test
    fun longSessionsAreRecordedWithoutAsking() {
        assertFalse(FocusRules.shouldRecordSilently(59))
        assertTrue(FocusRules.shouldRecordSilently(60))
        assertTrue(FocusRules.shouldRecordSilently(45 * 60))
    }

    // ---------- 正计时 ----------

    @Test
    fun countUpIsNeverCappedAndNeverFinishes() {
        // plannedSeconds = 0，但因为走的是 countUp 分支，不会被截成 0
        val r = FocusRun(
            plannedSeconds = 0,
            accumulatedMillis = 0L,
            segmentStartMillis = 0L,
            paused = false,
            startedAtMillis = 0L,
            countUp = true
        )
        assertEquals(600_000L, FocusRules.elapsedMillis(r, 600_000L))
        assertEquals(600, FocusRules.elapsedSeconds(r, 600_000L))
        assertFalse(FocusRules.isFinished(r, 600_000L))
        assertEquals(0L, FocusRules.remainingMillis(r, 600_000L))
        // 环整圈亮着当装饰，不编造一个用户没设过的目标
        assertEquals(1f, FocusRules.remainingFraction(r, 600_000L), 0.001f)
    }

    @Test
    fun countUpStillSupportsPause() {
        val r = FocusRun(
            plannedSeconds = 0,
            accumulatedMillis = 0L,
            segmentStartMillis = 0L,
            paused = false,
            startedAtMillis = 0L,
            countUp = true
        )
        val paused = r.pause(30_000L)
        // 暂停 4.5 分钟，elapsed 仍是 30 秒
        assertEquals(30_000L, FocusRules.elapsedMillis(paused, 300_000L))
        // 继续后再走 5 秒 → 35 秒
        assertEquals(35_000L, FocusRules.elapsedMillis(paused.resume(300_000L), 305_000L))
    }

    // ---------- 落盘数据的有效性 ----------

    @Test
    fun countUpRunIsUsableEvenWithZeroPlanned() {
        // 正计时没有目标时长，planned=0 是**正常**的，不能当坏数据扔掉。
        // 这一条钉死 v1.0.2 的 bug：正计时退出重进读不回 run，通知却还在走。
        assertTrue(FocusRules.isUsable(plannedSeconds = 0, countUp = true))
        assertTrue(FocusRules.isUsable(plannedSeconds = 1500, countUp = true))
    }

    @Test
    fun countdownNeedsAPlannedDuration() {
        assertTrue(FocusRules.isUsable(plannedSeconds = 1500, countUp = false))
        assertFalse(FocusRules.isUsable(plannedSeconds = 0, countUp = false))
        assertFalse(FocusRules.isUsable(plannedSeconds = -1, countUp = false))
    }

    // ---------- 文案 ----------

    @Test
    fun clockUsesMmSsUnderAnHour() {
        assertEquals("25:00", FocusRules.formatClock(25 * 60))
        assertEquals("00:09", FocusRules.formatClock(9))
        assertEquals("59:59", FocusRules.formatClock(3599))
        assertEquals("00:00", FocusRules.formatClock(0))
    }

    @Test
    fun clockAddsHoursPastOneHour() {
        assertEquals("1:00:00", FocusRules.formatClock(3600))
        assertEquals("1:30:05", FocusRules.formatClock(5405))
    }

    @Test
    fun clockNeverShowsNegative() {
        assertEquals("00:00", FocusRules.formatClock(-30))
    }

    @Test
    fun durationReadsAsChinese() {
        assertEquals("38秒", FocusRules.formatDuration(38))
        assertEquals("45分钟", FocusRules.formatDuration(45 * 60))
        assertEquals("1小时", FocusRules.formatDuration(3600))
        assertEquals("2小时15分", FocusRules.formatDuration(2 * 3600 + 15 * 60))
    }

    @Test
    fun presetsAreTheFourStandardOnes() {
        assertEquals(listOf(15, 25, 45, 60), FocusRules.PRESET_MINUTES)
        assertEquals(25, FocusRules.DEFAULT_MINUTES)
    }
}
