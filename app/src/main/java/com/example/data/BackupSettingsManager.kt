package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the "Copias" setting with persistent local storage.
 * Controls whether Modstudio automatically saves previous container backups before modifications.
 * Defaults to true (Active) as requested: "por default siempre el sistema guarda los contenedores anteriores".
 * When turned off (false), the system does not store previous backup containers in cache or history.
 */
class BackupSettingsManager private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _isBackupEnabled = MutableStateFlow(isSavedBackupEnabled())
  val isBackupEnabled: StateFlow<Boolean> = _isBackupEnabled.asStateFlow()

  fun isSavedBackupEnabled(): Boolean {
    // Defaults to true
    return prefs.getBoolean(KEY_BACKUP_ENABLED, true)
  }

  fun setBackupEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_BACKUP_ENABLED, enabled).apply()
    _isBackupEnabled.value = enabled
  }

  companion object {
    private const val PREFS_NAME = "modstudio_backup_prefs"
    private const val KEY_BACKUP_ENABLED = "is_container_backup_enabled"

    @Volatile
    private var INSTANCE: BackupSettingsManager? = null

    fun getInstance(context: Context): BackupSettingsManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: BackupSettingsManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
