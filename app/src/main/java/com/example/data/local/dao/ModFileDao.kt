package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ModFileEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ModFileDao {
  @Query("SELECT * FROM mod_files ORDER BY fileName ASC")
  fun getAllFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE fileType = :fileType ORDER BY fileName ASC")
  fun getFilesByType(fileType: String): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE fileType = 'IMG' AND fileName NOT IN ('gta3.img', 'gta_int.img') ORDER BY fileName ASC")
  fun getImgDffFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE (fileType = 'GTA3_DFF' OR (fileType = 'IMG' AND relativePath LIKE '%gta3%')) AND fileName NOT IN ('gta3.img', 'gta_int.img') AND fileName LIKE '%.dff' ORDER BY fileName ASC")
  fun getGta3DffFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE (fileType = 'GTA_INT_DFF' OR (fileType = 'IMG' AND relativePath LIKE '%gta_int%')) AND fileName NOT IN ('gta3.img', 'gta_int.img') AND fileName LIKE '%.dff' ORDER BY fileName ASC")
  fun getGtaIntDffFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE fileType = 'GTA3_TXD' ORDER BY fileName ASC")
  fun getGta3TxdFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE fileType = 'GTA_INT_TXD' ORDER BY fileName ASC")
  fun getGtaIntTxdFiles(): Flow<List<ModFileEntry>>

  @Query("DELETE FROM mod_files WHERE fileType IN ('GTA3_TXD', 'GTA_INT_TXD')")
  suspend fun clearTxdFiles()

  @Query("SELECT * FROM mod_files WHERE (fileType = 'CSA' OR fileName LIKE '%.csa') ORDER BY fileName ASC")
  fun getCsaFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE (fileType = 'CSI' OR fileName LIKE '%.csi') ORDER BY fileName ASC")
  fun getCsiFiles(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE (fileType = 'FXT' OR fileName LIKE '%.fxt') ORDER BY fileName ASC")
  fun getFxtFiles(): Flow<List<ModFileEntry>>

  @Query("DELETE FROM mod_files WHERE fileType IN ('CSA', 'CSI', 'FXT') OR fileName LIKE '%.csa' OR fileName LIKE '%.csi' OR fileName LIKE '%.fxt'")
  suspend fun clearScriptFiles()

  @Query("DELETE FROM mod_files WHERE fileName NOT IN ('gta3.img', 'gta_int.img') AND fileType IN ('IMG', 'GTA3_DFF', 'GTA_INT_DFF')")
  suspend fun clearDffFiles()

  @Query("SELECT COUNT(*) FROM mod_files WHERE fileType = :fileType")
  fun getCountByType(fileType: String): Flow<Int>

  @Query("SELECT * FROM mod_files WHERE fileName IN ('gta3.img', 'gta_int.img') ORDER BY fileName ASC")
  fun getGtaContainers(): Flow<List<ModFileEntry>>

  @Query("SELECT * FROM mod_files WHERE fileName IN ('gta3.img', 'gta_int.img') ORDER BY fileName ASC")
  suspend fun getGtaContainersDirect(): List<ModFileEntry>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(file: ModFileEntry): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(files: List<ModFileEntry>)

  @Query("DELETE FROM mod_files WHERE fileName = :fileName")
  suspend fun deleteByFileName(fileName: String)

  @Query("DELETE FROM mod_files WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM mod_files")
  suspend fun clearAll()
}
