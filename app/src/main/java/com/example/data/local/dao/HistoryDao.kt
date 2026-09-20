package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
  @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
  fun getAllHistory(): Flow<List<HistoryEntry>>

  @Query("SELECT COUNT(*) FROM history_entries")
  fun getCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: HistoryEntry): Long

  @Query("DELETE FROM history_entries WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM history_entries")
  suspend fun clearAll()
}
