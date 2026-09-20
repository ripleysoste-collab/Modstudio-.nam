package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room Entity for discovered or installed game files (IMG, TXD, CSI, CSA).
 */
@Entity(tableName = "mod_files")
data class ModFileEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val fileName: String,
  val fileType: String, // "IMG", "TXD", "CSI", "CSA"
  val relativePath: String,
  val sizeBytes: Long = 0,
  val isEnabled: Boolean = true,
  val lastModified: Long = System.currentTimeMillis()
)
