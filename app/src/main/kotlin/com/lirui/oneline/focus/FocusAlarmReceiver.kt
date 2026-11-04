package com.lirui.oneline.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 专注时间到。由 [FocusScheduler] 排的精确闹钟拉起。
 *
 * 用 `goAsync()` 包住写库：`onReceive` 一返回进程优先级立刻回落，
 * 协程可能还没 insert 完就被回收 —— 表现就是"到点了、通知也变了，但记录里没有"。
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FocusScheduler.ACTION_FOCUS_DONE) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FocusEngine.complete(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
