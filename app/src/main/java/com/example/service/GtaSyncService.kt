package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.NotificationHelper
import com.example.data.repository.ContainerSearchResult
import com.example.data.repository.ContainerUpdateResult
import com.example.data.repository.ModstudioRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SyncServiceState {
  object Idle : SyncServiceState()
  data class Running(val action: String, val message: String) : SyncServiceState()
  data class Finished(val action: String, val success: Boolean, val message: String) : SyncServiceState()
}

/**
 * Foreground service that keeps container backup, update, and compression operations running
 * reliably in the background, even when the user minimizes or exits the application.
 */
class GtaSyncService : Service() {

  companion object {
    const val ACTION_FIND_BACKUP = "com.example.service.ACTION_FIND_BACKUP"
    const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
    const val ACTION_GAME_BACKUP = "com.example.service.ACTION_GAME_BACKUP"
    const val ACTION_COMPRESS_BACKUP = "com.example.service.ACTION_COMPRESS_BACKUP"
    const val EXTRA_ONLY_MISSING = "com.example.service.EXTRA_ONLY_MISSING"
    const val EXTRA_FILE_PATHS = "com.example.service.EXTRA_FILE_PATHS"

    private val _serviceState = MutableStateFlow<SyncServiceState>(SyncServiceState.Idle)
    val serviceState: StateFlow<SyncServiceState> = _serviceState.asStateFlow()

    fun startFindBackup(context: Context) {
      val intent = Intent(context, GtaSyncService::class.java).apply {
        action = ACTION_FIND_BACKUP
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun startUpdate(context: Context) {
      val intent = Intent(context, GtaSyncService::class.java).apply {
        action = ACTION_UPDATE
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun startGameBackup(context: Context, onlyMissing: Boolean = false) {
      val intent = Intent(context, GtaSyncService::class.java).apply {
        action = ACTION_GAME_BACKUP
        putExtra(EXTRA_ONLY_MISSING, onlyMissing)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun startCompressBackup(context: Context, filePaths: ArrayList<String>) {
      val intent = Intent(context, GtaSyncService::class.java).apply {
        action = ACTION_COMPRESS_BACKUP
        putStringArrayListExtra(EXTRA_FILE_PATHS, filePaths)
      }
      ContextCompat.startForegroundService(context, intent)
    }
  }

  private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
  private lateinit var repository: ModstudioRepository

  override fun onCreate() {
    super.onCreate()
    repository = ModstudioRepository.getInstance(applicationContext)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: return START_NOT_STICKY
    val onlyMissing = intent.getBooleanExtra(EXTRA_ONLY_MISSING, false)

    // 1. Promote to Foreground Service immediately to satisfy system requirement
    val initialMessage = when (action) {
      ACTION_UPDATE -> getString(R.string.status_updating)
      ACTION_GAME_BACKUP -> getString(R.string.game_backup_action_in_progress)
      ACTION_COMPRESS_BACKUP -> getString(R.string.game_backup_compressing)
      else -> getString(R.string.step_checking_storage)
    }
    val notification = NotificationHelper.buildForegroundNotification(this, initialMessage)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NotificationHelper.NOTIFICATION_ID_FOREGROUND,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
    }

    // 2. Launch background coroutine
    serviceScope.launch {
      try {
        when (action) {
          ACTION_FIND_BACKUP -> executeFindBackup()
          ACTION_UPDATE -> executeUpdate()
          ACTION_GAME_BACKUP -> executeGameBackup(onlyMissing)
          ACTION_COMPRESS_BACKUP -> {
            val paths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: arrayListOf()
            executeCompressBackup(paths)
          }
          else -> stopSelf()
        }
      } catch (e: Exception) {
        _serviceState.value = SyncServiceState.Finished(
          action = action,
          success = false,
          message = e.localizedMessage ?: "Error en segundo plano"
        )
        NotificationHelper.showCompletedNotification(
          this@GtaSyncService,
          getString(R.string.notification_title_active),
          e.localizedMessage ?: "Error en segundo plano"
        )
      } finally {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
      }
    }

    return START_NOT_STICKY
  }

  private suspend fun executeFindBackup() {
    _serviceState.value = SyncServiceState.Running(ACTION_FIND_BACKUP, getString(R.string.step_checking_storage))

    val result = repository.findAndBackupGtaContainers { stepMsg ->
      _serviceState.value = SyncServiceState.Running(ACTION_FIND_BACKUP, stepMsg)
      NotificationHelper.updateForegroundNotification(this@GtaSyncService, stepMsg)
    }

    when (result) {
      is ContainerSearchResult.Success -> {
        val successMsg = getString(R.string.status_found_success)
        _serviceState.value = SyncServiceState.Finished(ACTION_FIND_BACKUP, true, successMsg)
        NotificationHelper.showCompletedNotification(
          this,
          getString(R.string.notification_title_completed),
          "gta3.img (${ModstudioRepository.getInstance(this).formatFileSize(result.gta3Size)}) y gta_int.img listos."
        )
      }
      is ContainerSearchResult.NotFound -> {
        _serviceState.value = SyncServiceState.Finished(ACTION_FIND_BACKUP, false, result.reason)
        NotificationHelper.showCompletedNotification(
          this,
          getString(R.string.notification_title_active),
          result.reason
        )
      }
    }
  }

  private suspend fun executeUpdate() {
    _serviceState.value = SyncServiceState.Running(ACTION_UPDATE, getString(R.string.status_updating))

    val result = repository.updateOrRefreshGtaContainers { stepMsg ->
      _serviceState.value = SyncServiceState.Running(ACTION_UPDATE, stepMsg)
      NotificationHelper.updateForegroundNotification(this@GtaSyncService, stepMsg)
    }

    when (result) {
      is ContainerUpdateResult.Success -> {
        _serviceState.value = SyncServiceState.Finished(ACTION_UPDATE, true, result.message)
        val notifTitle = if (result.changed) {
          getString(R.string.notification_title_updated)
        } else {
          getString(R.string.notification_title_active)
        }
        NotificationHelper.showCompletedNotification(this, notifTitle, result.message)
      }
      is ContainerUpdateResult.Error -> {
        _serviceState.value = SyncServiceState.Finished(ACTION_UPDATE, false, result.message)
        NotificationHelper.showCompletedNotification(
          this,
          getString(R.string.notification_title_active),
          result.message
        )
      }
    }
  }

  private suspend fun executeGameBackup(onlyMissing: Boolean = false) {
    _serviceState.value = SyncServiceState.Running(ACTION_GAME_BACKUP, getString(R.string.game_backup_action_in_progress))

    val manager = com.example.data.GameBackupManager.getInstance(this)
    val success = manager.performGameBackup(onlyMissing = onlyMissing) { stepMsg, _ ->
      _serviceState.value = SyncServiceState.Running(ACTION_GAME_BACKUP, stepMsg)
      NotificationHelper.updateForegroundNotification(this@GtaSyncService, stepMsg)
    }

    val finalMsg = if (success) {
      getString(R.string.game_backup_success_title)
    } else {
      getString(R.string.status_not_found)
    }

    _serviceState.value = SyncServiceState.Finished(ACTION_GAME_BACKUP, success, finalMsg)
    NotificationHelper.showCompletedNotification(
      this,
      if (success) getString(R.string.game_backup_success_title) else getString(R.string.notification_title_active),
      if (success) getString(R.string.game_backup_success_desc) else finalMsg
    )
  }

  private suspend fun executeCompressBackup(filePaths: List<String>) {
    val filesToCompress = filePaths.map { File(it) }.filter { it.exists() }
    if (filesToCompress.isEmpty()) {
      _serviceState.value = SyncServiceState.Finished(ACTION_COMPRESS_BACKUP, false, "No se encontraron archivos para comprimir.")
      return
    }

    val initialMsg = getString(R.string.game_backup_compressing)
    _serviceState.value = SyncServiceState.Running(ACTION_COMPRESS_BACKUP, initialMsg)
    NotificationHelper.updateForegroundNotification(this@GtaSyncService, initialMsg)

    val tempDir = File(cacheDir, "compressed").apply { mkdirs() }
    val tempZip = File(tempDir, "GTA_SA_Backup.zip")
    if (tempZip.exists()) tempZip.delete()

    val totalFiles = filesToCompress.size
    val ok = com.example.data.parser.ArchiveExplorerHelper.zipFiles(
      sourceFiles = filesToCompress,
      destinationZip = tempZip
    ) { currentFile, fileIndex, _, percent ->
      val progressMsg = "Comprimiendo: $currentFile ($fileIndex/$totalFiles) $percent%"
      _serviceState.value = SyncServiceState.Running(ACTION_COMPRESS_BACKUP, progressMsg)
      NotificationHelper.updateForegroundNotification(this@GtaSyncService, progressMsg)
    }

    if (ok && tempZip.exists() && tempZip.length() > 0L) {
      val backupManager = com.example.data.GameBackupManager.getInstance(this)
      backupManager.recordCompressedBackup(tempZip)

      val doneMsg = getString(R.string.game_backup_compress_completed)
      _serviceState.value = SyncServiceState.Finished(ACTION_COMPRESS_BACKUP, true, doneMsg)
      NotificationHelper.showCompletedNotification(
        this,
        getString(R.string.game_backup_compress_completed),
        getString(R.string.game_backup_compress_ready)
      )
    } else {
      val errorMsg = "Error al comprimir los archivos de copia de seguridad."
      _serviceState.value = SyncServiceState.Finished(ACTION_COMPRESS_BACKUP, false, errorMsg)
      NotificationHelper.showCompletedNotification(
        this,
        getString(R.string.notification_title_active),
        errorMsg
      )
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
