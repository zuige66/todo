package com.lirui.oneline

import android.app.Application
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderWatchdogWorker

class OnelineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channels up front (idempotent).
        NotificationHelper.init(this)
        // 兜底巡检（每 12h）。KEEP 语义，重复调用无副作用。
        // 放在 Application 而不是 MainActivity：它要覆盖"开机后一直没打开过 App"的区间，
        // 而那种情况下 MainActivity 根本不会执行。
        ReminderWatchdogWorker.ensureScheduled(this)
    }
}
