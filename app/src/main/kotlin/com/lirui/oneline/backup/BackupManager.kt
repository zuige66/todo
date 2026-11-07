package com.lirui.oneline.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.reminder.NotificationDismissScheduler
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderScheduler
import com.lirui.oneline.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 备份的 I/O 与恢复副作用。
 *
 * SAF 全程不需要任何权限：系统文件选择器返回 Uri，App 只对这一个 Uri 读写字节流。
 * 保存位置是用户自己选的（本地目录 / 用户自己的网盘），App 不发起网络请求、
 * 不上传、也不知道文件最终落在哪。
 */
object BackupManager {

    suspend fun writeTo(context: Context, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("无法打开所选文件")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun readFrom(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
    }

    /**
     * 按选定模式写入一份备份。
     *
     * ★ 为什么不用 Room 的 `clearAllTables()`：那个方法**自己会开事务**，
     *   套在 `withTransaction` 里就成了嵌套事务。改用 DAO 的两条 `DELETE FROM`，
     *   由外层事务统一兜住 —— 中途失败整批回滚，不会留下「删光了但没插回来」的空库。
     *
     * ★ 恢复后必须重建提醒：导入的待办带着原来的 dueAtMillis，但 AlarmManager 里
     *   既没有新闹钟，也可能还留着已被删掉的旧 id 的闹钟（整份覆盖时先逐条撤销）。
     */
    suspend fun restore(
        context: Context,
        data: BackupData,
        mode: BackupRules.RestoreMode
    ): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context.applicationContext)
        val todoDao = db.todoDao()
        val focusDao = db.focusDao()

        if (mode == BackupRules.RestoreMode.REPLACE) {
            todoDao.getAllIncludingArchived().forEach { todo ->
                ReminderScheduler.cancelForId(context, todo.id)
                NotificationHelper.cancelReminder(context, todo.id)
                NotificationDismissScheduler.cancelScheduled(todo.id)
            }
        }

        db.withTransaction {
            when (mode) {
                BackupRules.RestoreMode.REPLACE -> {
                    todoDao.clearAll()
                    focusDao.clearAll()
                    // 保留原 id：通知/组件里的 todoId 才能继续对得上。
                    todoDao.insertAll(data.todos)
                    focusDao.insertAll(data.sessions)
                }
                BackupRules.RestoreMode.KEEP_AND_IMPORT -> {
                    // id 归零让 Room 重新自增 —— 否则和现有行主键冲突，整批 ABORT。
                    todoDao.insertAll(data.todos.map { it.copy(id = 0L) })
                    focusDao.insertAll(data.sessions.map { it.copy(id = 0L) })
                }
                BackupRules.RestoreMode.DEDUPLICATE -> {
                    // IGNORE：同 id 已存在就跳过，只补进没见过的记录。
                    todoDao.insertAllIgnore(data.todos)
                    focusDao.insertAllIgnore(data.sessions)
                }
            }
        }

        BackupCodec.applySettings(context, data)
        ReminderScheduler.scheduleAllActiveBlocking(context)
        WidgetUpdater.refreshAsync(context)

        "已恢复（${mode.label}）：" + BackupRules.describe(data.todos.size, data.sessions.size)
    }
}
