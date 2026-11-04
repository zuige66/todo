package com.lirui.oneline.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 专注到点的闹钟。
 *
 * ★ 为什么必须用 AlarmManager，而不是 `Handler.postDelayed` / 协程 `delay`：
 *   后两者都活在**本进程**里。专注 25 分钟，中间被系统回收、被一键清理、被划掉，
 *   定时器就没了 —— 用户回来发现"计时停了、通知还在走"，而这一次专注永远不落库。
 *   闹钟由 system_server 持有，进程死了照样到点拉起 [FocusAlarmReceiver]。
 *
 * 只用一个 requestCode：同一时刻只可能有**一次**专注在跑，
 * 再开第二次之前必须先结束第一次（UI 也不给这个入口）。
 */
object FocusScheduler {

    const val ACTION_FOCUS_DONE = "com.lirui.oneline.action.FOCUS_DONE"

    private const val REQUEST_CODE = 9201

    fun schedule(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pending(context)
        runCatching {
            if (canScheduleExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // 精确闹钟被系统收回（部分 ROM / 用户手动关掉）时退化。
                // 专注差几秒不影响结论，宁可晚一点也不能崩。
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pending(context)) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, FocusAlarmReceiver::class.java).setAction(ACTION_FOCUS_DONE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canScheduleExact(am: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
}
