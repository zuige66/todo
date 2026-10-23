package com.lirui.oneline

import android.app.Application
import com.lirui.oneline.data.TodoRepository
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OnelineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channels up front (idempotent).
        NotificationHelper.init(this)
        // 兜底巡检（每 12h）。KEEP 语义，重复调用无副作用。
        // 放在 Application 而不是 MainActivity：它要覆盖"开机后一直没打开过 App"的区间，
        // 而那种情况下 MainActivity 根本不会执行。
        ReminderWatchdogWorker.ensureScheduled(this)
        // 回收站过期清理：超过 30 天的软删除记录在冷启动时真正抹掉。
        // 不需要精确时点 —— 差一天没人看得出来，所以不排闹钟，只跟着冷启动走。
        val app = this
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TodoRepository(app).purgeExpiredTrash() }
        }
    }
}
