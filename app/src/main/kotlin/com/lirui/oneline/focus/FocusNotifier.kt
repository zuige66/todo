package com.lirui.oneline.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R

/**
 * 专注相关的两条通知。
 *
 * ★ 为什么**不需要**前台服务（这一点和 `KeepAliveService` 完全不同）：
 *   倒计时交给系统的 chronometer 渲染 —— `setUsesChronometer(true)` +
 *   `setChronometerCountDown(true)` + `setWhen(结束时刻)`。系统每帧自己算剩余时间，
 *   **我们的进程死不死都不影响**它继续走。真正到点那一下由 AlarmManager 兜底
 *   （见 [FocusScheduler]），也是 system_server 持有，进程被回收照样响。
 *
 *   所以这里没有 `startForeground`、没有 `FOREGROUND_SERVICE` 权限、
 *   没有"后台服务被国产 ROM 杀掉"这类问题，也少一道商店审核材料。
 *   代价只有一条：暂停/结束时通知不能自己变，得靠 [FocusActionReceiver] 被点一下。
 */
object FocusNotifier {

    const val CHANNEL_ONGOING = "focus_ongoing"
    const val CHANNEL_DONE = "focus_done"

    const val NOTIF_ONGOING = 9101
    const val NOTIF_DONE = 9102

    const val ACTION_OPEN_FOCUS = "com.lirui.oneline.action.OPEN_FOCUS"

    fun init(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // 常驻条：静音、无横幅、不震动 —— 它只是"还剩多久"的显示板，不是提醒。
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "专注倒计时", NotificationManager.IMPORTANCE_LOW).apply {
                description = "专注进行中的常驻倒计时"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        // 完成条：要响、要横幅。它是一次真正的事件，用户应该被打断。
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "专注完成", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "一次专注走完时的提醒"
                setShowBadge(true)
            }
        )
    }

    /**
     * 刷新常驻倒计时条。
     *
     * 运行中走系统 chronometer（不再自己每秒重发通知 —— 那是最耗电的做法）；
     * 暂停时改成静态文案，因为 chronometer 没有"停住"这个能力。
     */
    fun showOngoing(context: Context, run: FocusRun, nowMillis: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val elapsedSec = (FocusRules.elapsedMillis(run, nowMillis) / 1000L).toInt()
        val remainingSec = (FocusRules.remainingMillis(run, nowMillis) / 1000L).toInt()
        val title = run.label.ifBlank { if (run.countUp) "正计时中" else "专注中" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openFocusIntent(context))
            .addAction(0, if (run.paused) "继续" else "暂停", actionIntent(context, if (run.paused) FocusActionReceiver.ACTION_RESUME else FocusActionReceiver.ACTION_PAUSE))
            .addAction(0, "结束", actionIntent(context, FocusActionReceiver.ACTION_FINISH))

        if (run.paused) {
            val pausedText = if (run.countUp) {
                "已暂停 · 已专注 ${FocusRules.formatClock(elapsedSec)}"
            } else {
                "已暂停 · 剩余 ${FocusRules.formatClock(remainingSec)}"
            }
            builder.setContentText(pausedText)
                .setShowWhen(false)
        } else {
            builder.setUsesChronometer(true)
                .setShowWhen(true)
            if (run.countUp) {
                // 正计时：chronometer 往上数。setWhen 要给"起点"，
                // 也就是现在往前推已经过去的那段（含暂停前累计的）。
                builder.setChronometerCountDown(false)
                    .setContentText("已专注")
                    .setWhen(nowMillis - FocusRules.elapsedMillis(run, nowMillis))
            } else {
                // 倒计时：chronometer 往下数，setWhen 是终点。
                builder.setChronometerCountDown(true)
                    .setContentText("剩余时间")
                    .setWhen(nowMillis + FocusRules.remainingMillis(run, nowMillis))
            }
        }

        runCatching { nm.notify(NOTIF_ONGOING, builder.build()) }
    }

    fun cancelOngoing(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(NOTIF_ONGOING) }
    }

    /** 走完了。文案刻意短：一句话说清"多久、什么"。 */
    fun showDone(context: Context, seconds: Int, label: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val what = label.ifBlank { "专注" }
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle("专注完成")
            .setContentText("$what · ${FocusRules.formatDuration(seconds)}")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(openFocusIntent(context))
            .build()
        runCatching { nm.notify(NOTIF_DONE, n) }
    }

    private fun openFocusIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, FocusActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
