package com.lirui.oneline.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lirui.oneline.data.TodoRepository
import kotlinx.coroutines.delay
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
 *
 * ★ 归档为什么分两帧（见 [WidgetUpdater.markCompleting]）：
 *   RemoteViews 没有动画 API，只能靠两次 updateAppWidget 拼出"勾亮起 → 行消失"。
 *   第 1 帧只改外观（不写库），第 2 帧才 archive + 刷新 —— 所以第 1 帧的 refresh
 *   必须在 archive **之前**，否则查出来这条已经没了，两帧会变成"什么都没发生"。
 */
class TodoWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodoWidgetProvider.ACTION_COMPLETE) return
        val id = intent.getLongExtra(TodoWidgetProvider.EXTRA_TODO_ID, -1L)
        if (id <= 0L) return
        // goAsync 把广播生命周期拉到协程结束（含中间的 300ms delay），
        // 否则 onReceive 一返回系统就可能回收进程，第 2 帧永远不执行。
        val pendingResult = goAsync()
        val app = context.applicationContext
        WidgetUpdater.ioScope.launch {
            try {
                archiveWithTick(app, id)
            } finally {
                // 失败也要清掉瞬时状态，否则这条会一直画着勾。
                WidgetUpdater.clearCompleting()
                pendingResult.finish()
            }
        }
    }
}

/**
 * 「勾亮起 → 行消失」的两帧归档。**必须在 IO 线程调用**（内部同步查库）。
 *
 * 第 1 帧只改外观：`markCompleting` + refresh，这条被画成已勾选；
 * 第 2 帧才写库：archive 之后清掉标记再 refresh，这一行从列表里消失、后面的补位。
 *
 * ★ 顺序不能反：先 archive 再 refresh，第 1 帧查库时这条已经没了，两帧就退化成
 *   "什么都没发生"（用户看到的还是"没有动效"）。
 */
internal suspend fun archiveWithTick(context: Context, id: Long) {
    WidgetUpdater.markCompleting(id)
    WidgetUpdater.refresh(context)
    delay(WidgetUpdater.TICK_MILLIS)
    TodoWidgetRepo.get(context).archive(id)
    WidgetUpdater.clearCompleting()
    WidgetUpdater.refresh(context)
}
