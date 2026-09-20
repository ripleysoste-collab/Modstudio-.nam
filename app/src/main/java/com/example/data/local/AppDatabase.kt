package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConfigDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.ModFileDao
import com.example.data.local.entity.ConfigEntry
import com.example.data.local.entity.HistoryEntry
import com.example.data.local.entity.ModFileEntry

@Database(
  entities = [HistoryEntry::class, ModFileEntry::class, ConfigEntry::class],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun historyDao(): HistoryDao
  abstract fun modFileDao(): ModFileDao
  abstract fun configDao(): ConfigDao

  companion object {
    private const val DATABASE_NAME = "modstudio_local.db"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          DATABASE_NAME
        ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
      }
    }
  }
}
