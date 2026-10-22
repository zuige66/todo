package com.lirui.oneline.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lirui.oneline.data.TodoRepository
import kotlinx.coroutines.launch

/**
 * 小组件「圆圈完成」的独立接收器（exported=false）。
 *
 * ★ 为什么从 [TodoWidgetProvider] 拆出来：v0.8.1 起主 provider 改为 exported=true
 *   （部分国产 Launcher bind 组件时不是特权身份，exported=false 会被拒 → 组件
 *   进不了系统列表，澎湃3/vivo 实测）。但 COMPLETE 动作**不能**跟着 exported=true ——
 *   那等于任何应用都能广播一条 todoId 把待办"完成"掉。
 *
 *   拆开后：圆圈的 PendingIntent 用**显式 ComponentName** 指到这里。non-exported 的
 *   receiver 可以收到我们自己（同 UID）发出的广播，系统投递没问题。
 */
class TodoWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodoWidgetProvider.ACTION_COMPLETE) return
        val id = intent.getLongExtra(TodoWidgetProvider.EXTRA_TODO_ID, -1L)
        if (id <= 0L) return
        val pendingResult = goAsync()
        WidgetUpdater.ioScope.launch {
            try {
                TodoWidgetRepo.get(context).archive(id)
                WidgetUpdater.refresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
