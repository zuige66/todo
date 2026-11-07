package com.lirui.oneline.focus

import android.content.Context
import com.lirui.oneline.data.FocusRepository

/**
 * 专注的**唯一状态机**。
 *
 * 所有入口（专注页的按钮、通知上的暂停/结束、到点闹钟、开机重建）都走这里，
 * 不在各自的地方再写一遍"存盘 + 发通知 + 排闹钟"这三件套 ——
 * 少写一处就会漏一处，最后表现成"某条路走完不落库"。
 */
object FocusEngine {

    /** 开一次专注。返回新建的 run（UI 直接拿去渲染）。 */
    fun start(
        context: Context,
        minutes: Int,
        label: String,
        nowMillis: Long,
        countUp: Boolean = false
    ): FocusRun {
        val run = FocusRun(
            // 正计时没有目标时长：plannedSeconds = 0，也就不会有"到点"这件事。
            plannedSeconds = if (countUp) 0 else minutes * 60,
            accumulatedMillis = 0L,
            segmentStartMillis = nowMillis,
            paused = false,
            startedAtMillis = nowMillis,
            label = label.trim(),
            countUp = countUp
        )
        FocusStore.saveActive(context, run)
        FocusStore.setLastMinutes(context, minutes)
        FocusStore.setLastCountUp(context, countUp)
        FocusNotifier.showOngoing(context, run, nowMillis)
        // 正计时不排闹钟：它没有终点，只能手动结束。
        if (!countUp) FocusScheduler.schedule(context, nowMillis + run.plannedMillis)
        return run
    }

    fun pause(context: Context, nowMillis: Long): FocusRun? {
        val run = FocusStore.active(context) ?: return null
        val paused = run.pause(nowMillis)
        FocusStore.saveActive(context, paused)
        // 暂停了就别再响：闹钟留着会在暂停期间把这次专注"判完成"。
        FocusScheduler.cancel(context)
        FocusNotifier.showOngoing(context, paused, nowMillis)
        return paused
    }

    fun resume(context: Context, nowMillis: Long): FocusRun? {
        val run = FocusStore.active(context) ?: return null
        val resumed = run.resume(nowMillis)
        FocusStore.saveActive(context, resumed)
        FocusNotifier.showOngoing(context, resumed, nowMillis)
        if (!resumed.countUp) {
            FocusScheduler.schedule(context, nowMillis + FocusRules.remainingMillis(resumed, nowMillis))
        }
        return resumed
    }

    /**
     * 自然走完。
     *
     * @return true = 这次真的落库了；false = 已经没有进行中的专注（被另一条路先收走了）。
     *         两条触发源（闹钟 / 前台 tick）靠 [FocusStore.takeActive] 的原子性去重。
     */
    suspend fun complete(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val run = FocusStore.takeActive(context) ?: return false
        FocusScheduler.cancel(context)
        FocusNotifier.cancelOngoing(context)
        // elapsed 已被 FocusRules 截到 planned，正常到点就是满时长；
        // 闹钟被系统拖晚几秒也不会记成"超时"。
        val seconds = FocusRules.elapsedSeconds(run, nowMillis).coerceAtLeast(1)
        FocusRepository(context).record(
            label = run.label,
            startedAtMillis = run.startedAtMillis,
            endedAtMillis = nowMillis,
            plannedSeconds = run.plannedSeconds,
            actualSeconds = seconds,
            completed = true
        )
        FocusNotifier.showDone(context, seconds, run.label)
        return true
    }

    /**
     * 中途结束。
     *
     * @param record 用户的选择：太短时会先问一句（见 [FocusRules.shouldAskBeforeDiscard]），
     *               够长时调用方直接传 true 不问。传 false 表示"就当没发生过"。
     * @param overrideSeconds 超长专注时用户手填的时长（秒）。null = 按实际经过时间记。
     *                        调用方（UI）负责把它钳在 `1 分钟 .. < 当前值`；这里只信任传入值。
     *                        通知栏那条路径不传，行为与旧版完全一致。
     * @return 记入的秒数；0 表示什么都没记。
     */
    suspend fun finishEarly(
        context: Context,
        nowMillis: Long,
        record: Boolean,
        overrideSeconds: Int? = null
    ): Int {
        val run = FocusStore.takeActive(context) ?: return 0
        FocusScheduler.cancel(context)
        FocusNotifier.cancelOngoing(context)
        if (!record) return 0
        val seconds = overrideSeconds ?: FocusRules.elapsedSeconds(run, nowMillis)
        if (seconds <= 0) return 0
        // ★ 手填时长时把结束时刻一起对齐：否则 endedAt - startedAt 还是 3 小时，
        //   actualSeconds 却写了 25 分钟，这条记录自己跟自己对不上。
        val endedAt = if (overrideSeconds != null) {
            run.startedAtMillis + seconds * 1000L
        } else {
            nowMillis
        }
        FocusRepository(context).record(
            label = run.label,
            startedAtMillis = run.startedAtMillis,
            endedAtMillis = endedAt,
            plannedSeconds = run.plannedSeconds,
            actualSeconds = seconds,
            // ★ 正计时没有"提前"这个概念 —— 点结束就是正常结束，记成已完成。
            //   倒计时才有"没走完就停了"，记 completed=false（列表里显示「提前结束」）。
            completed = run.countUp
        )
        return seconds
    }

    /** 计时中途改名：只更新 run，不动闹钟（时长没变）。 */
    fun rename(context: Context, label: String, nowMillis: Long): FocusRun? {
        val run = FocusStore.active(context) ?: return null
        val renamed = run.rename(label.trim())
        FocusStore.saveActive(context, renamed)
        FocusNotifier.showOngoing(context, renamed, nowMillis)
        return renamed
    }

    /**
     * 冷启动 / 开机 / 升级后对账：闹钟条目已经没了，但 run 还在盘上。
     *
     * 这是"重启后专注还能继续"的唯一原因 —— AlarmManager 的条目不落盘，
     * 不重排的话计时会一直走到天荒地老也不会响。
     */
    fun restore(context: Context, nowMillis: Long) {
        val run = FocusStore.active(context) ?: return
        if (run.paused) {
            // 暂停中本来就不该有闹钟，只把通知补回来（进程重启后通知还在，但文案可能过期）。
            FocusNotifier.showOngoing(context, run, nowMillis)
            return
        }
        val remaining = FocusRules.remainingMillis(run, nowMillis)
        if (run.countUp) {
            // 正计时没有终点，本来就不排闹钟，只补通知。
            FocusNotifier.showOngoing(context, run, nowMillis)
            return
        }
        if (remaining <= 0L) {
            // ★ 已过点（v1.0.10 修）：旧代码和正计时合并成一个分支，只 showOngoing 就 return，
            //   注释写「交给 UI 的 tick 或闹钟去 complete」。但升级 / 开机 / force-stop 之后
            //   AlarmManager 条目已经没了，而用户未必会进专注页 —— UI tick 根本不跑，
            //   于是这次专注永远不落库、常驻通知一直挂着。
            //   立刻排一个 now 的闹钟，让 FocusAlarmReceiver → complete() 走统一的
            //   落库 + 清通知路径（takeActive 原子去重，不会和 UI tick 写两条）。
            //   这里不直接调 complete()：它是 suspend，restore 是同步函数。
            FocusScheduler.schedule(context, nowMillis)
            return
        }
        FocusScheduler.schedule(context, nowMillis + remaining)
        FocusNotifier.showOngoing(context, run, nowMillis)
    }
}
