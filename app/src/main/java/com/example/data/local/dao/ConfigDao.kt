package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ConfigEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
  @Query("SELECT value FROM app_config WHERE key = :key LIMIT 1")
  fun getValue(key: String): Flow<String?>

  @Query("SELECT value FROM app_config WHERE key = :key LIMIT 1")
  suspend fun getValueSync(key: String): String?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun setConfig(entry: ConfigEntry)

  @Query("DELETE FROM app_config WHERE key = :key")
  suspend fun removeConfig(key: String)
}
