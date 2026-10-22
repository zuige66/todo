package com.lirui.oneline.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.util.SettingsRepo
import java.util.concurrent.TimeUnit

/**
 * 兜底巡检（L2 自愈的第三道）。
 *
 * 它解决的是一个**真实存在**的场景：
 *
 * ```
 * 用户 09:00 排了 14:30 的会
 * 12:00 手机被"一键清理" / 在设置里被强行停止  → 系统取消该包的全部闹钟
 * 14:30 什么也没发生
 * 用户第二天打开 App 才发现"提醒没响"
 * ```
 *
 * 前两道自愈（开机重建、升级重建）都要等一个系统事件；这一道是**每 12 小时主动补一次**，
 * 让"漏掉的那段时间"最多 12 小时。
 *
 * 注意边界：
 *  - WorkManager 的周期任务**不保证准点**（系统按电量/待机批量调度），
 *    所以它只做"补"，绝不承担"准点提醒"——准点永远归 AlarmManager；
 *  - 巡检本身要幂等：[ReminderScheduler.scheduleBlocking] 内部先 cancel 再 set，
 *    重复跑不会产生重复闹钟。
 */
class ReminderWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // 1) 全量重排：把被 force-stop / 被 ROM 清掉的闹钟补回来。
        ReminderScheduler.scheduleAllActiveBlocking(ctx)
        // 2) 补发：在重排之前就已经错过的提醒，用通知补一条（仅限最近 24 小时）。
        catchUpMissed(ctx)
        return Result.success()
    }

    /**
     * 给"刚刚错过"的待办补一条通知。
     *
     * 三个克制的设计：
     *  1. **只补 24 小时内的**。补一条 3 天前的过期提醒对用户是骚扰，不是帮助；
     *  2. **已经有通知在托盘里就跳过**（`hasActiveReminder`）——逾期唠叨链还活着的时候
     *     托盘里本来就有这条，再发一条等于同一件事响两遍；
     *  3. **只补「到点提醒」这一种**。提前提醒补发没有意义（时间点已经过了）。
     */
    private suspend fun catchUpMissed(context: Context) {
        if (!SettingsRepo.remindersEnabled(context) || !SettingsRepo.remindAtDue(context)) return

        val now = System.currentTimeMillis()
        val lookBackWindow = 24 * 60 * 60 * 1000L
        val dueWord = SettingsRepo.notificationStyle(context).dueWord

        val todos = AppDatabase.get(context).todoDao().getActiveAll()
        for (todo in todos) {
            val due = todo.dueAtMillis ?: continue
            if (todo.archived) continue
            if (due > now) continue                       // 还没到点，交给闹钟
            if (now - due > lookBackWindow) continue      // 太久远的，只重排不补发
            if (NotificationHelper.hasActiveReminder(context, todo.id)) continue
            NotificationHelper.postReminder(context, todo.id, todo.title, dueWord)
        }
    }

    companion object {
        private const val UNIQUE_NAME = "reminder_watchdog"

        /**
         * 每 12 小时一次。`KEEP` 语义：已经排过就不动 —— 多个入口
         * （App 启动 / 开机 / 升级）都会调它，而重复 enqueue 会把计时重置，
         * 用 `KEEP` 才不会让巡检永远等不到第一次执行。
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWatchdogWorker>(12, TimeUnit.HOURS)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR,
                    30, TimeUnit.MINUTES
                )
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
