package com.lirui.oneline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lirui.oneline.data.model.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE archived = 0 ORDER BY COALESCE(dueAtMillis, 253402300799999) ASC, createdAt ASC")
    fun observeActive(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE archived = 1 ORDER BY completedAt DESC")
    fun observeArchived(): Flow<List<Todo>>

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

    @Query("SELECT * FROM todos WHERE archived = 0 ORDER BY COALESCE(dueAtMillis, 253402300799999) ASC, createdAt ASC")
    suspend fun getActiveAll(): List<Todo>

    @Query("UPDATE todos SET archived = 1 WHERE id = :id")
    suspend fun markArchivedById(id: Long)

    @Query("UPDATE todos SET archived = 0 WHERE id = :id")
    suspend fun unarchiveById(id: Long)

    @Query("UPDATE todos SET title = :title, detail = :detail, dueAtMillis = :dueAtMillis, startAtMillis = :startAtMillis, endAtMillis = :endAtMillis, hasExactTime = :hasExactTime, repeatDays = :repeatDays WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, detail: String, dueAtMillis: Long?, startAtMillis: Long?, endAtMillis: Long?, hasExactTime: Boolean, repeatDays: Int)

    // ---------- 本地备份（v1.0.11）----------
    // 导出必须含**已归档**的待办：那是用户数据，不是缓存。
    @Query("SELECT * FROM todos")
    suspend fun getAllIncludingArchived(): List<Todo>

    /** 整份覆盖用：保留备份里的原 id（通知 / 组件里的 todoId 才能继续对上）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Todo>)

    /** 去重导入用：同 id 已存在就跳过，不覆盖现有行。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<Todo>)

    /** 只在 BackupManager 的外层事务里调用，不用 clearAllTables（后者会自己开事务）。 */
    @Query("DELETE FROM todos")
    suspend fun clearAll()
}
