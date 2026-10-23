package com.lirui.oneline.reminder

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 归档之后**延迟**撤掉那条待办的通知。
 *
 * 为什么不能立刻撤：通知上带着「撤回」按钮，是用户反悔的第二条路。应用内一点圆圈就把
 * 通知撤掉，等于把这条路当场砍断 —— 而列表里的撤销条只活 3 秒，比它更短。
 *
 * 为什么不能永不撤（修复前的行为）：待办已经进归档，通知却一直挂在状态栏，点开找不到
 * 那一行，点「完成」按钮 [com.lirui.oneline.reminder.NotificationActionReceiver] 里
 * `getById` 返回 null 后静默退出 —— 按了没反应，用户以为 App 卡死。
 *
 * 所以：留够反悔时间，然后消失。[DELAY_MILLIS] 取 10 秒，比撤销条的 3 秒宽裕，
 * 又短到不会让"已完成却在催"的错位感持续存在。
 *
 * ★ 这个 object 是**进程内**的。进程被杀，延时就没了 —— 通知会一直留着，也就是回到
 *   修复前的行为。这是刻意接受的降级：为了一个通知去排 AlarmManager 不值得，而且这个
 *   场景要求用户在前台完成待办后 10 秒内杀掉进程，几乎不会发生。
 * ★ 用单例而不是 ViewModel 的 scope：撤销也可能从**通知栏**发起（不经过 ViewModel），
 *   两边必须能撤掉同一个延时任务，否则撤回后 10 秒通知照样消失。
 */
object NotificationDismissScheduler {

    /** 归档到撤通知之间的窗口。 */
    const val DELAY_MILLIS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** todoId -> 待执行的撤销任务。用锁保护，因为 ViewModel 和广播接收器是两条线程。 */
    private val jobs = HashMap<Long, Job>()

    /**
     * 安排一次延时撤通知。
     *
     * 重复调用会先撤掉上一个任务再排新的 —— 连续归档同一条待办（理论上不该发生，
     * 但防重复是廉价的）时，不会有两个任务同时到点。
     */
    fun scheduleCancel(context: Context, todoId: Long, delayMillis: Long = DELAY_MILLIS) {
        val app = context.applicationContext
        val job = scope.launch {
            delay(delayMillis)
            synchronized(jobs) { jobs.remove(todoId) }
            NotificationHelper.cancelReminder(app, todoId)
        }
        synchronized(jobs) {
            jobs.remove(todoId)?.cancel()
            jobs[todoId] = job
        }
    }

    /**
     * 取消待执行的撤销 —— 待办被撤回（应用内撤销条或通知栏「撤回」）时调用。
     *
     * 只取消定时器，**不动通知本身**：待办已经回到活跃状态，那条提醒还有意义。
     */
    fun cancelScheduled(todoId: Long) {
        synchronized(jobs) { jobs.remove(todoId)?.cancel() }
    }
}
