package com.lirui.oneline.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lirui.oneline.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {

    /** 全部记录，新的在前。统计与列表共用这一份数据（见 FocusStats）。 */
    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC")
    suspend fun getAll(): List<FocusSession>

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---------- 本地备份（v1.0.11）----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FocusSession>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<FocusSession>)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll()
}
