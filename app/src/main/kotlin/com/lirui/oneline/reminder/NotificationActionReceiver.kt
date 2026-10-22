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
 * period (see [ReminderEngine.previousOccurrence]). Reminders are re-armed either way, so a
 * restored todo can still fire.
 *
 * Uses [BroadcastReceiver.goAsync] because the DB work is async; without the pending-result
 * window the process may be killed mid-write.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(NotificationHelper.EXTRA_TODO_ID, -1L)
        val action = intent.action
        if (todoId < 0L || action == null) return

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
                        repo.undoFromNotification(todoId)
                        // The todo is back in the list with its reminders re-armed, so the
                        // reminder notification has no reason to stay — drop it.
                        NotificationHelper.cancelReminder(appContext, todoId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
