package com.lirui.oneline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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

    @Query("UPDATE todos SET title = :title, detail = :detail, dueAtMillis = :dueAtMillis, startAtMillis = :startAtMillis, hasExactTime = :hasExactTime, repeatDays = :repeatDays WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, detail: String, dueAtMillis: Long?, startAtMillis: Long?, hasExactTime: Boolean, repeatDays: Int)
}
