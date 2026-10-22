package com.lirui.oneline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lirui.oneline.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the notification action buttons 「完成」/「撤回」.
 *
 * 「完成」reuses [TodoRepository.archive], i.e. exactly what tapping the circle in the list
 * does (normal todo -> archived; repeating todo -> rolled forward to its next occurrence).
 * The same notification is then re-posted in its `done` state, so its button flips to 「撤回」.
 *
 * 「撤回」is the inverse: archived todos are un-archived, repeating ones are rolled back one
 * period (see [ReminderEngine.previousOccurrence]). The notification is deliberately KEPT and
 * flipped back to its 「完成」 state (silently), so the row stays usable instead of vanishing.
 * Reminders are re-armed either way, so a restored todo can still fire.
 *
 * Uses [BroadcastReceiver.goAsync] because the DB work is async; without the pending-result
 * window the process may be killed mid-write.
 *
 * 「测试通知」那一条走的是另一条分支：它没有待办，只把通知自己翻转成「完成 / 撤回」，
 * 同步完成、不进协程、不碰数据库（见 [NotificationHelper.EXTRA_IS_TEST]）。
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // 测试通知没有对应的待办：只把这条通知自己翻转成「完成 / 撤回」，绝不碰数据库。
        // 判断必须在 todoId 校验之前 —— 测试通知压根不带 todoId。
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_IS_TEST, false)) {
            NotificationHelper.postTest(
                context.applicationContext,
                intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "通知测试（事件）",
                intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "该办了",
                quote = intent.getStringExtra(NotificationHelper.EXTRA_QUOTE),
                done = action == NotificationHelper.ACTION_COMPLETE
            )
            return
        }

        val todoId = intent.getLongExtra(NotificationHelper.EXTRA_TODO_ID, -1L)
        if (todoId < 0L) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = TodoRepository(appContext)
                when (action) {
                    NotificationHelper.ACTION_COMPLETE -> {
                        val todo = repo.getById(todoId) ?: return@launch
                        if (todo.archived) return@launch
                        repo.completeFromNotification(todoId)
                        // Re-post on the SAME notification id, so the action flips in place
                        // from 「完成」to 「撤回」instead of stacking a second notification.
                        NotificationHelper.postReminder(
                            appContext,
                            todoId,
                            todo.title,
                            "已完成",
                            done = true
                        )
                    }
                    NotificationHelper.ACTION_UNDO -> {
                        val todo = repo.undoFromNotification(todoId) ?: return@launch
                        // The notification must STAY: the todo is active again and the user may
                        // want to complete it right from here. Re-post on the same id so the
                        // action flips back to 「完成」, silent because they just acted on it.
                        NotificationHelper.postReminder(
                            appContext,
                            todoId,
                            todo.title,
                            intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE)
                                ?: "别忘了处理",
                            done = false,
                            silent = true
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
