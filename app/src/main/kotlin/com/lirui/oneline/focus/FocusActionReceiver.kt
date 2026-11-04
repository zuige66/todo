package com.lirui.oneline.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知条上的「暂停 / 继续 / 结束」。
 *
 * ★ 「结束」在这里**不问**"这么短要不要记"：通知栏里弹不了对话框，
 *   而且从通知点结束本来就是"我要收手"的意思。口径定为：
 *   够长（≥ [FocusRules.ASK_THRESHOLD_SECONDS]）就记，太短就当没发生 ——
 *   和 UI 里"太短会问"并不冲突，因为那是**用户看着计时器**做的决定。
 */
class FocusActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        when (intent.action) {
            ACTION_PAUSE -> FocusEngine.pause(appContext, now)
            ACTION_RESUME -> FocusEngine.resume(appContext, now)
            ACTION_FINISH -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val run = FocusStore.active(appContext)
                        if (run != null) {
                            val elapsed = FocusRules.elapsedSeconds(run, now)
                            FocusEngine.finishEarly(
                                appContext,
                                now,
                                record = FocusRules.shouldRecordSilently(elapsed)
                            )
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.lirui.oneline.action.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.lirui.oneline.action.FOCUS_RESUME"
        const val ACTION_FINISH = "com.lirui.oneline.action.FOCUS_FINISH"
    }
}
