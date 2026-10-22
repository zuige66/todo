package com.lirui.oneline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lirui.oneline.keepalive.KeepAlive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟的**重建入口**。
 *
 * 为什么需要三个 action：AlarmManager 的闹钟不落盘，只要进程相关的"命"被换掉就全没了 ——
 *
 * | 事件 | 谁清掉了闹钟 | 不处理的后果 |
 * |---|---|---|
 * | `BOOT_COMPLETED` | 关机 / 重启 | 重启后所有提醒静默失效 |
 * | `MY_PACKAGE_REPLACED` | **安装新版本**（PackageInstaller 杀进程） | **每升一次级，用户此前的提醒全部不响**，直到他手动打开 App |
 * | `TIME_SET` | 用户改了系统时间 | RTC 闹钟是绝对 epoch，墙钟一变，提醒就偏移 |
 *
 * `MY_PACKAGE_REPLACED` 是这三者里最容易被忽略、又最容易踩到的一个（v0.5 之前的真实 bug）。
 *
 * 关于 `TIMEZONE_CHANGED`：本应用所有日期/时间都在固定时区 `AppTime.ZONE`（北京时间）里解析，
 * 存的是 epoch。改时区不改变 epoch 对应的北京墙钟，所以不需要重建 —— 这不是遗漏。
 *
 * 关于 `LOCKED_BOOT_COMPLETED`（直接启动模式）：需要 `android:directBootAware="true"`，
 * 而那时 Room 数据库还在凭据加密存储里、**读不到任何待办**，重建出来是空集，所以不做。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED -> rebuild(context, intent.action == Intent.ACTION_BOOT_COMPLETED)
        }
    }

    /**
     * 用 `goAsync()` 把重建放进接收器的 pending-result 窗口里 —— `onReceive` 一返回，
     * 进程优先级立刻回落，fire-and-forget 的协程可能排到一半被杀。
     */
    private fun rebuild(context: Context, bootEvent: Boolean) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleAllActiveBlocking(appContext)
                ReminderWatchdogWorker.ensureScheduled(appContext)
                if (bootEvent) {
                    // 「后台增强」是用户主动开的开关，开机后替他恢复。
                    // specialUse 是 Android 15 仍然允许从 BOOT_COMPLETED 启动的类型；
                    // start() 内部已 try/catch，ROM 拒绝也不会让接收器崩掉。
                    KeepAlive.startIfEnabled(appContext)
                }            } finally {
                pending.finish()
            }
        }
    }
}
