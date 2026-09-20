package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local key-value setting entity stored persistently in Room SQLite database.
 */
@Entity(tableName = "app_config")
data class ConfigEntry(
  @PrimaryKey val key: String,
  val value: String
)
