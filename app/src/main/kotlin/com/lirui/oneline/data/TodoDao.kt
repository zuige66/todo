package com.lirui.oneline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lirui.oneline.data.model.Todo
import kotlinx.coroutines.flow.Flow

/**
 * ★ 所有列表查询都必须带 `deletedAt IS NULL`。删除是软删除（进回收站），漏掉这个条件
 *   就会让回收站里的待办重新出现在活跃列表里 —— 而且因为 Flow 会实时刷新，表现是
 *   "删了又自己回来了"，非常像数据损坏。
 */
@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE archived = 0 AND deletedAt IS NULL ORDER BY COALESCE(dueAtMillis, 253402300799999) ASC, createdAt ASC")
    fun observeActive(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE archived = 1 AND deletedAt IS NULL ORDER BY completedAt DESC")
    fun observeArchived(): Flow<List<Todo>>

    /** 回收站：按删除时间倒序（最近删的在最上面，方便误删后立刻找回）。 */
    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM todos WHERE archived = 0 AND deletedAt IS NULL ORDER BY COALESCE(dueAtMillis, 253402300799999) ASC, createdAt ASC")
    suspend fun getActiveAll(): List<Todo>

    @Query("UPDATE todos SET archived = 1 WHERE id = :id")
    suspend fun markArchivedById(id: Long)

    @Query("UPDATE todos SET archived = 0 WHERE id = :id")
    suspend fun unarchiveById(id: Long)

    // ---------- 回收站 ----------

    /** 移入回收站。`archived` 保持原样 —— 从归档页删的回来还是归档页，从活跃列表删的
     *  恢复后回到活跃列表（恢复只看 deletedAt，不看 archived）。 */
    @Query("UPDATE todos SET deletedAt = :at WHERE id = :id")
    suspend fun softDeleteById(id: Long, at: Long)

    /** 从回收站恢复。 */
    @Query("UPDATE todos SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreById(id: Long)

    /** 回收站里"彻底删除" / 过期清理用。 */
    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeletedBefore(before: Long)

    @Query("UPDATE todos SET title = :title, detail = :detail, dueAtMillis = :dueAtMillis, startAtMillis = :startAtMillis, hasExactTime = :hasExactTime, repeatDays = :repeatDays WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, detail: String, dueAtMillis: Long?, startAtMillis: Long?, hasExactTime: Boolean, repeatDays: Int)
}
