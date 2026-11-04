package com.lirui.oneline.focus

/**
 * 一次正在进行的专注。
 *
 * ★ 为什么记 `segmentStartMillis` + `accumulatedMillis` 而不是每秒 `++`：
 *   计时必须**只依赖时间戳**。Compose 的 `LaunchedEffect { while(true){ delay(1000) } }`
 *   在息屏 / 后台 / 进程被回收时会被挂起或直接掐掉，靠它累加，回来一看少了几十分钟。
 *   现在的模型是"记下起点，需要时做差"——UI 每秒只是**重绘**，不负责计时。
 *   这也让暂停/继续变成纯数据操作（见 [paused]），不牵涉任何协程生命周期。
 *
 * @param plannedSeconds     计划时长（秒）。**正计时时为 0** —— 没有目标就没有终点
 * @param accumulatedMillis  之前各运行段累计的时间（暂停时把它吃进来）
 * @param segmentStartMillis 当前运行段的起点。**暂停时这个值没有意义**，不要读
 * @param paused             是否处于暂停
 * @param startedAtMillis    整次专注真正的开始时刻（统计按它归日）
 * @param label              专注时填的说明，空串 = 没写
 * @param countUp            正计时（从 0 往上数）vs 倒计时（从 planned 往下数）
 */
data class FocusRun(
    val plannedSeconds: Int,
    val accumulatedMillis: Long,
    val segmentStartMillis: Long,
    val paused: Boolean,
    val startedAtMillis: Long,
    val label: String = "",
    val countUp: Boolean = false
) {

    val plannedMillis: Long get() = plannedSeconds * 1000L

    /** 暂停：把当前运行段折算进累计，之后 elapsed 不再随墙钟增长。 */
    fun pause(nowMillis: Long): FocusRun {
        if (paused) return this
        val delta = (nowMillis - segmentStartMillis).coerceAtLeast(0L)
        return copy(accumulatedMillis = accumulatedMillis + delta, paused = true)
    }

    /** 继续：开一个新的运行段，累计值保持不变。 */
    fun resume(nowMillis: Long): FocusRun {
        if (!paused) return this
        return copy(segmentStartMillis = nowMillis, paused = false)
    }

    /** 不改时长、只换说明（计时中途改名字用）。 */
    fun rename(label: String): FocusRun = copy(label = label)
}

object FocusRules {

    /**
     * 中途结束时「够不够长」的分界线（秒）。
     *
     * 语义（zuige 定的口径）：
     *  - **≥ 60 秒** → 这是真干了一会儿，直接记入统计（`completed = false`）；
     *  - **< 60 秒** → 分不清是误触还是真开始了，弹一句问用户记不记。
     *
     * 之所以不是"短的一律丢弃"：30 秒也可能是真的开了个头，
     * 用户自己最清楚；而 40 分钟的提前结束绝不该因为"没走完"就白干。
     */
    const val ASK_THRESHOLD_SECONDS = 60

    /** 时长预设（分钟）。 */
    val PRESET_MINUTES: List<Int> = listOf(15, 25, 45, 60)

    /** 首次进入默认 25 分钟（一个标准番茄钟）。 */
    const val DEFAULT_MINUTES = 25

    /**
     * 已经过的毫秒。暂停时不再增长。
     *
     * ★ 倒计时会截到 planned（闹钟晚几秒不算"超时"）；**正计时不截** ——
     *   正计时本来就没有上限，截了就等于永远停在 0。
     */
    fun elapsedMillis(run: FocusRun, nowMillis: Long): Long {
        val running = if (run.paused) 0L else (nowMillis - run.segmentStartMillis).coerceAtLeast(0L)
        val total = run.accumulatedMillis + running
        return if (run.countUp) total else total.coerceAtMost(run.plannedMillis)
    }

    /** 已经过的整秒（向下取整 —— 显示 00:01 的时候不能真的已经过了 2 秒）。 */
    fun elapsedSeconds(run: FocusRun, nowMillis: Long): Int =
        (elapsedMillis(run, nowMillis) / 1000L).toInt()

    /** 剩余毫秒，不为负。正计时恒为 0（没有终点）。 */
    fun remainingMillis(run: FocusRun, nowMillis: Long): Long =
        if (run.countUp) 0L
        else (run.plannedMillis - elapsedMillis(run, nowMillis)).coerceAtLeast(0L)

    /**
     * 剩余比例 1f → 0f，圆环按它画。
     *
     * 正计时恒为 1f：它没有"剩余"可言，环就整圈亮着当装饰 ——
     * 硬凑一个进度（比如按 60 分钟算百分比）是在编造用户没设定的目标。
     */
    fun remainingFraction(run: FocusRun, nowMillis: Long): Float {
        if (run.countUp) return 1f
        if (run.plannedMillis <= 0L) return 0f
        return (remainingMillis(run, nowMillis).toFloat() / run.plannedMillis).coerceIn(0f, 1f)
    }

    /** 时间到没到。正计时永远不到（只能手动结束）。 */
    fun isFinished(run: FocusRun, nowMillis: Long): Boolean =
        !run.countUp && elapsedMillis(run, nowMillis) >= run.plannedMillis

    /** 中途结束且够长 → 不问，直接记。 */
    fun shouldRecordSilently(elapsedSeconds: Int): Boolean = elapsedSeconds >= ASK_THRESHOLD_SECONDS

    /** 中途结束但太短 → 问一句「要记下来吗」。0 秒不问（什么都没发生）。 */
    fun shouldAskBeforeDiscard(elapsedSeconds: Int): Boolean =
        elapsedSeconds in 1 until ASK_THRESHOLD_SECONDS

    /**
     * 倒计时显示。`25:00` / `01:05:00`。
     *
     * 一小时以内只给 mm:ss —— 专注场景里 45 分钟是常态，多一个 `00:` 前缀
     * 会让圆环中心的字变小，反而不如两位数字醒目。
     */
    fun formatClock(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, sec)
        }
    }

    /**
     * 时长的人话写法：`2小时15分` / `45分钟` / `38秒`。
     *
     * 统计卡片上不用 `2h15m`：这个应用的其余文案全是中文（「明天」「归档」），
     * 混进英文单位会显得是另一套设计语言。
     */
    fun formatDuration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        if (s < 60) return "${s}秒"
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h == 0 -> "${m}分钟"
            m == 0 -> "${h}小时"
            else -> "${h}小时${m}分"
        }
    }
}
