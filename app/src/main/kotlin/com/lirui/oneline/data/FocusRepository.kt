package com.lirui.oneline.data

import android.content.Context
import com.lirui.oneline.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

/**
 * 专注记录的唯一读写入口。
 *
 * 和 [TodoRepository] 一样薄：这里不做业务判断（阈值、是否记入统计都在
 * `focus.FocusRules` / `FocusViewModel` 里），只负责"写进去"和"读出来"。
 */
class FocusRepository(context: Context) {

    private val dao = AppDatabase.get(context).focusDao()

    val all: Flow<List<FocusSession>> = dao.observeAll()

    suspend fun allOnce(): List<FocusSession> = dao.getAll()

    /**
     * 记一次专注。
     *
     * @param actualSeconds 已经过 `FocusRules` 判定为"该记"的秒数。
     *                      调用方保证它 > 0 —— 这里不再兜底，0 秒的记录没有意义，
     *                      真写进去会让统计里出现"专注 0 秒"的脏行。
     */
    suspend fun record(
        label: String,
        startedAtMillis: Long,
        endedAtMillis: Long,
        plannedSeconds: Int,
        actualSeconds: Int,
        completed: Boolean
    ): Long = dao.insert(
        FocusSession(
            label = label.trim(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            plannedSeconds = plannedSeconds,
            actualSeconds = actualSeconds,
            completed = completed,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)
}
