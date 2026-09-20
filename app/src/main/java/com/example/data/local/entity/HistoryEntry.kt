package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room Entity representing actions, scans, backups, and mod operations in Historial.
 */
@Entity(tableName = "history_entries")
data class HistoryEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val description: String,
  val category: String, // "SCAN", "MOD", "BACKUP", "FILES", "CONTAINER_BACKUP"
  val timestamp: Long = System.currentTimeMillis(),
  val backupFilePath: String? = null,
  val containerType: String? = null,
  val dffCount: Int = 0
)
