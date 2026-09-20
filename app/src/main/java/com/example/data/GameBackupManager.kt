package com.example.data

import android.content.Context
import android.os.Environment
import com.example.R
import com.example.data.repository.ModstudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

enum class GameBackupFileType {
  MAIN_OBB,
  PATCH_OBB,
  APK,
  SCRIPT
}

enum class BackupActionType {
  CREATE,         // "Crear copia de seguridad"
  COMPLETE,       // "Completar archivos"
  CREATED,        // "Creado"
  NOT_FOUND       // "No encontrados"
}

data class GameBackupItem(
  val id: String,
  val fileName: String,
  val type: GameBackupFileType,
  val title: String,
  val description: String,
  val sourceFile: File?,
  val sizeBytes: Long,
  val exists: Boolean
)

data class GameBackupState(
  val isScanning: Boolean = false,
  val isBackingUp: Boolean = false,
  val progress: Float = 0f,
  val statusMessage: String = "",
  val items: List<GameBackupItem> = emptyList(),          // Files currently in backup (for UI and backward compatibility)
  val backedUpItems: List<GameBackupItem> = emptyList(),  // Actual files existing in backup folder
  val sourceItems: List<GameBackupItem> = emptyList(),    // Found game source files on device
  val missingItems: List<GameBackupItem> = emptyList(),   // Source files missing in backup folder
  val actionType: BackupActionType = BackupActionType.CREATE,
  val lastBackupTimestamp: Long = 0L,
  val isSuccess: Boolean = false,
  val error: String? = null
)

/**
 * Manages smart detection and background backup of GTA San Andreas game files:
 * 1. Main OBB file (e.g. main.8.com.rockstargames.gtasa.obb)
 * 2. Patch OBB file (e.g. patch.8.com.rockstargames.gtasa.obb)
 * 3. Game APK (if present in obb folder or installed package; never invented if missing)
 *
 * Smart features:
 * - Tolerant search: detects GTA folders in Android/obb even if renamed with suffixes (e.g. com.rockstargames.gtasa9).
 * - Real-time disk sync: file list displays what ACTUALLY exists in Modstudio/Backups.
 * - Dynamic button state: "Crear copia de seguridad", "Completar archivos", "Creado", or "No encontrados".
 * - Selective copy: "Completar archivos" only copies missing items without re-copying existing files.
 */
class GameBackupManager private constructor(private val context: Context) {

  private val _state = MutableStateFlow(GameBackupState())
  val state: StateFlow<GameBackupState> = _state.asStateFlow()

  companion object {
    const val GTASA_PACKAGE = "com.rockstargames.gtasa"
    private const val PREFS_NAME = "modstudio_game_backup_prefs"
    private const val KEY_LAST_BACKUP = "last_game_backup_timestamp"
    private const val KEY_PENDING_ZIP_PATH = "key_pending_compressed_zip_path"
    private const val KEY_PENDING_ZIP_TIME = "key_pending_compressed_zip_timestamp"
    const val COMPRESSION_TIMEOUT_MILLIS = 15 * 60 * 1000L // 15 minutes

    @Volatile
    private var INSTANCE: GameBackupManager? = null

    fun getInstance(context: Context): GameBackupManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: GameBackupManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  init {
    val lastTimestamp = prefs.getLong(KEY_LAST_BACKUP, 0L)
    _state.value = _state.value.copy(lastBackupTimestamp = lastTimestamp)
  }

  /**
   * Scans Android/obb for GTA San Andreas files (including renamed folders)
   * and checks actual contents of the backup directory.
   */
  suspend fun scanGameFiles(): List<GameBackupItem> = withContext(Dispatchers.IO) {
    _state.value = _state.value.copy(isScanning = true, error = null)

    // 1. Detect game source files on device (smart search in Android/obb)
    val sourceFiles = findGameSourceFiles()

    // 2. Detect what is ACTUALLY present in backup folder
    val backedUpFiles = findExistingBackupFiles()

    // 3. Compute missing files (source files not yet backed up)
    val missingFiles = sourceFiles.filter { source ->
      backedUpFiles.none { backed -> backed.type == source.type }
    }

    // 4. Determine smart button action type
    val actionType = when {
      sourceFiles.isEmpty() && backedUpFiles.isEmpty() -> BackupActionType.NOT_FOUND
      backedUpFiles.isEmpty() -> BackupActionType.CREATE
      missingFiles.isNotEmpty() -> BackupActionType.COMPLETE
      else -> BackupActionType.CREATED
    }

    _state.value = _state.value.copy(
      isScanning = false,
      items = backedUpFiles,
      backedUpItems = backedUpFiles,
      sourceItems = sourceFiles,
      missingItems = missingFiles,
      actionType = actionType
    )

    sourceFiles
  }

  /**
   * Smartly searches for GTA San Andreas source files on the device:
   * Looks in Android/obb, checking standard folder and any folder renamed by users
   * (e.g. com.rockstargames.gtasa9, com.rockstargames.gtasa_backup, gtasa, etc.).
   * Also searches installed package APK.
   */
  private fun findGameSourceFiles(): List<GameBackupItem> {
    val storageRoot = Environment.getExternalStorageDirectory()
    val candidateDirs = mutableListOf<File>()

    // Standard primary path
    val standardObbDir = File(storageRoot, "Android/obb/$GTASA_PACKAGE")
    // Fallback app-specific obb directory
    val fallbackObbDir = File(context.getExternalFilesDir(null), "obb/$GTASA_PACKAGE")

    candidateDirs.add(standardObbDir)
    candidateDirs.add(fallbackObbDir)

    // Check Android/obb for renamed directories (e.g. com.rockstargames.gtasa9)
    val obbParent = File(storageRoot, "Android/obb")
    if (obbParent.exists() && obbParent.isDirectory) {
      val subDirs = obbParent.listFiles { file ->
        file.isDirectory && isGtaFolderName(file.name)
      }
      subDirs?.forEach { dir ->
        if (!candidateDirs.contains(dir)) candidateDirs.add(dir)
      }
    }

    // Check Android/data for renamed directories as well
    val dataParent = File(storageRoot, "Android/data")
    if (dataParent.exists() && dataParent.isDirectory) {
      val dataSubDirs = dataParent.listFiles { file ->
        file.isDirectory && isGtaFolderName(file.name)
      }
      dataSubDirs?.forEach { dir ->
        if (!candidateDirs.contains(dir)) candidateDirs.add(dir)
      }
    }

    var foundMainObb: File? = null
    var foundPatchObb: File? = null
    var foundApk: File? = null

    for (dir in candidateDirs) {
      if (!dir.exists() || !dir.canRead()) continue
      val files = dir.listFiles() ?: continue
      for (f in files) {
        if (!f.isFile || f.length() == 0L) continue
        val lowerName = f.name.lowercase(Locale.ROOT)
        if (foundMainObb == null && lowerName.startsWith("main.") && lowerName.endsWith(".obb")) {
          foundMainObb = f
        } else if (foundPatchObb == null && lowerName.startsWith("patch.") && lowerName.endsWith(".obb")) {
          foundPatchObb = f
        } else if (foundApk == null && lowerName.endsWith(".apk")) {
          foundApk = f
        }
      }
    }

    // If APK was not in OBB directory, check installed package APK
    if (foundApk == null) {
      try {
        val appInfo = context.packageManager.getApplicationInfo(GTASA_PACKAGE, 0)
        val installedApk = File(appInfo.sourceDir)
        if (installedApk.exists() && installedApk.length() > 0L) {
          foundApk = installedApk
        }
      } catch (_: Exception) {}
    }

    // Find CLEO scripts on the device (Android/data/com.rockstargames.gtasa or cached scripts)
    val foundScripts = mutableListOf<File>()
    val scriptSearchDirs = listOf(
      File(storageRoot, "Android/data/$GTASA_PACKAGE"),
      File(storageRoot, "Android/data/$GTASA_PACKAGE/files"),
      File(storageRoot, "Android/data/$GTASA_PACKAGE/files/CLEO"),
      File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts")
    )
    val visitedScriptNames = mutableSetOf<String>()
    for (sDir in scriptSearchDirs) {
      if (!sDir.exists() || !sDir.canRead()) continue
      sDir.walkTopDown().maxDepth(3).forEach { f ->
        if (f.isFile && f.length() > 0L) {
          val nameLower = f.name.lowercase(Locale.ROOT)
          if ((nameLower.endsWith(".csa") || nameLower.endsWith(".csi") || nameLower.endsWith(".fxt")) &&
              visitedScriptNames.add(nameLower)
          ) {
            foundScripts.add(f)
          }
        }
      }
    }

    // Strict detection rule: if neither of the two main game files (main.obb or patch.obb)
    // nor any scripts are found, the game installation is not present.
    if (foundMainObb == null && foundPatchObb == null && foundScripts.isEmpty()) {
      return emptyList()
    }

    val result = mutableListOf<GameBackupItem>()

    // Main OBB
    foundMainObb?.let { file ->
      result.add(
        GameBackupItem(
          id = "main_obb",
          fileName = file.name,
          type = GameBackupFileType.MAIN_OBB,
          title = context.getString(R.string.game_backup_main_obb_title),
          description = context.getString(R.string.game_backup_main_obb_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    // Patch OBB
    foundPatchObb?.let { file ->
      result.add(
        GameBackupItem(
          id = "patch_obb",
          fileName = file.name,
          type = GameBackupFileType.PATCH_OBB,
          title = context.getString(R.string.game_backup_patch_obb_title),
          description = context.getString(R.string.game_backup_patch_obb_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    // APK (only added if at least one game file is present AND APK is genuinely found)
    foundApk?.let { file ->
      result.add(
        GameBackupItem(
          id = "game_apk",
          fileName = if (file.name.endsWith(".apk")) file.name else "com.rockstargames.gtasa.apk",
          type = GameBackupFileType.APK,
          title = context.getString(R.string.game_backup_apk_title),
          description = context.getString(R.string.game_backup_apk_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    // Scripts (.csa, .csi, .fxt): listed cleanly one below the other
    foundScripts.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { file ->
      val ext = file.extension.uppercase(Locale.ROOT)
      result.add(
        GameBackupItem(
          id = "script_${file.name}",
          fileName = file.name,
          type = GameBackupFileType.SCRIPT,
          title = file.name,
          description = "Script CLEO ($ext)",
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    return result
  }

  /**
   * Helper that checks if a folder name matches GTA San Andreas (exact or renamed with numbers/suffixes).
   */
  private fun isGtaFolderName(name: String): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    return lower == GTASA_PACKAGE ||
        lower.startsWith(GTASA_PACKAGE) ||
        lower.contains("rockstargames.gtasa") ||
        (lower.contains("rockstar") && lower.contains("gtasa")) ||
        (lower.contains("gtasa") && !lower.contains("cache"))
  }

  fun getPrimaryBackupDir(): File {
    val storageRoot = Environment.getExternalStorageDirectory()
    return File(storageRoot, "Modstudio/Backups/$GTASA_PACKAGE")
  }

  fun getFallbackBackupDir(): File {
    return File(context.getExternalFilesDir(null), "backups/$GTASA_PACKAGE")
  }

  fun hasGameBackup(): Boolean {
    val primaryBackupDir = getPrimaryBackupDir()
    val fallbackBackupDir = getFallbackBackupDir()
    return (primaryBackupDir.exists() && primaryBackupDir.listFiles()?.isNotEmpty() == true) ||
           (fallbackBackupDir.exists() && fallbackBackupDir.listFiles()?.isNotEmpty() == true)
  }

  /**
   * Scans the actual Modstudio backup folder to verify what is genuinely stored on disk.
   */
  fun findExistingBackupFiles(): List<GameBackupItem> {
    val primaryBackupDir = getPrimaryBackupDir()
    val fallbackBackupDir = getFallbackBackupDir()

    val dirsToCheck = listOf(primaryBackupDir, fallbackBackupDir)
    val existing = mutableListOf<GameBackupItem>()

    var foundMain: File? = null
    var foundPatch: File? = null
    var foundApk: File? = null

    for (dir in dirsToCheck) {
      if (!dir.exists() || !dir.isDirectory) continue
      val files = dir.listFiles() ?: continue
      for (f in files) {
        if (!f.isFile || f.length() == 0L) continue
        val lowerName = f.name.lowercase(Locale.ROOT)
        if (foundMain == null && lowerName.startsWith("main.") && lowerName.endsWith(".obb")) {
          foundMain = f
        } else if (foundPatch == null && lowerName.startsWith("patch.") && lowerName.endsWith(".obb")) {
          foundPatch = f
        } else if (foundApk == null && lowerName.endsWith(".apk")) {
          foundApk = f
        }
      }
    }

    val existingScripts = mutableListOf<File>()
    val visitedScriptNames = mutableSetOf<String>()

    val scriptBackupDirs = mutableListOf<File>()
    for (dir in dirsToCheck) {
      if (dir.exists() && dir.isDirectory) {
        scriptBackupDirs.add(dir)
        scriptBackupDirs.add(File(dir, "scripts"))
      }
    }
    scriptBackupDirs.add(File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts"))

    for (sDir in scriptBackupDirs) {
      if (!sDir.exists() || !sDir.canRead()) continue
      val sFiles = sDir.listFiles() ?: continue
      for (f in sFiles) {
        if (f.isFile && f.length() > 0L) {
          val lower = f.name.lowercase(Locale.ROOT)
          if ((lower.endsWith(".csa") || lower.endsWith(".csi") || lower.endsWith(".fxt")) &&
              visitedScriptNames.add(lower)
          ) {
            existingScripts.add(f)
          }
        }
      }
    }

    // If neither main nor patch exists and no scripts exist, an APK alone is not considered a game backup
    if (foundMain == null && foundPatch == null && existingScripts.isEmpty()) {
      return emptyList()
    }

    foundMain?.let { file ->
      existing.add(
        GameBackupItem(
          id = "main_obb",
          fileName = file.name,
          type = GameBackupFileType.MAIN_OBB,
          title = context.getString(R.string.game_backup_main_obb_title),
          description = context.getString(R.string.game_backup_main_obb_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    foundPatch?.let { file ->
      existing.add(
        GameBackupItem(
          id = "patch_obb",
          fileName = file.name,
          type = GameBackupFileType.PATCH_OBB,
          title = context.getString(R.string.game_backup_patch_obb_title),
          description = context.getString(R.string.game_backup_patch_obb_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    // Only add APK if at least one game OBB was backed up
    foundApk?.let { file ->
      existing.add(
        GameBackupItem(
          id = "game_apk",
          fileName = file.name,
          type = GameBackupFileType.APK,
          title = context.getString(R.string.game_backup_apk_title),
          description = context.getString(R.string.game_backup_apk_desc),
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    // Scripts (.csa, .csi, .fxt): listed cleanly one below the other
    existingScripts.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { file ->
      val ext = file.extension.uppercase(Locale.ROOT)
      existing.add(
        GameBackupItem(
          id = "script_${file.name}",
          fileName = file.name,
          type = GameBackupFileType.SCRIPT,
          title = file.name,
          description = "Script CLEO ($ext)",
          sourceFile = file,
          sizeBytes = file.length(),
          exists = true
        )
      )
    }

    return existing
  }

  /**
   * Performs the copy operation to persistent Modstudio Backups storage.
   * If [onlyMissing] is true, only copies items in [missingItems].
   * Emits step progress for both UI and background notification.
   */
  suspend fun performGameBackup(
    onlyMissing: Boolean = false,
    onStepProgress: (message: String, progress: Float) -> Unit = { _, _ -> }
  ): Boolean = withContext(Dispatchers.IO) {
    _state.value = _state.value.copy(
      isBackingUp = true,
      progress = 0.05f,
      statusMessage = context.getString(R.string.step_checking_storage),
      isSuccess = false,
      error = null
    )
    onStepProgress(context.getString(R.string.step_checking_storage), 0.05f)

    try {
      // Refresh scan
      scanGameFiles()

      val filesToCopy = if (onlyMissing) {
        _state.value.missingItems.filter { it.sourceFile != null && it.sourceFile.exists() }
      } else {
        _state.value.sourceItems.filter { it.sourceFile != null && it.sourceFile.exists() }
      }

      if (filesToCopy.isEmpty()) {
        val noFilesMsg = context.getString(R.string.game_backup_status_not_found)
        _state.value = _state.value.copy(
          isBackingUp = false,
          progress = 0f,
          statusMessage = noFilesMsg,
          error = noFilesMsg,
          actionType = BackupActionType.NOT_FOUND
        )
        onStepProgress(noFilesMsg, 0f)
        return@withContext false
      }

      // Setup destination directories
      val storageRoot = Environment.getExternalStorageDirectory()
      val primaryBackupDir = File(storageRoot, "Modstudio/Backups/$GTASA_PACKAGE").apply { mkdirs() }
      val fallbackBackupDir = File(context.getExternalFilesDir(null), "backups/$GTASA_PACKAGE").apply { mkdirs() }

      val totalCount = filesToCopy.size
      val copiedFileNames = mutableListOf<String>()

      filesToCopy.forEachIndexed { index, item ->
        val source = item.sourceFile ?: return@forEachIndexed
        val stepProgress = (index + 1).toFloat() / (totalCount + 1)
        val copyMsg = "${context.getString(R.string.game_backup_status_in_progress)} ${item.fileName}"

        _state.value = _state.value.copy(
          progress = stepProgress,
          statusMessage = copyMsg
        )
        onStepProgress(copyMsg, stepProgress)

        val targetFile = File(primaryBackupDir, item.fileName)
        val fallbackTarget = File(fallbackBackupDir, item.fileName)

        try {
          copyFileWithProgress(source, targetFile)
          if (item.type == GameBackupFileType.SCRIPT) {
            val scriptSubdir = File(primaryBackupDir, "scripts").apply { mkdirs() }
            copyFileWithProgress(source, File(scriptSubdir, item.fileName))
          }
          copiedFileNames.add(item.fileName)
        } catch (_: Exception) {
          copyFileWithProgress(source, fallbackTarget)
          if (item.type == GameBackupFileType.SCRIPT) {
            val fallbackScriptSubdir = File(fallbackBackupDir, "scripts").apply { mkdirs() }
            copyFileWithProgress(source, File(fallbackScriptSubdir, item.fileName))
          }
          copiedFileNames.add(item.fileName)
        }
        delay(250)
      }

      // Mark complete & record to Room Historial
      val completionTimestamp = System.currentTimeMillis()
      prefs.edit().putLong(KEY_LAST_BACKUP, completionTimestamp).apply()

      val repo = ModstudioRepository.getInstance(context)
      val filesSummary = copiedFileNames.joinToString(", ")
      repo.addHistoryEntry(
        title = context.getString(R.string.game_backup_title),
        description = "${context.getString(R.string.game_backup_success_desc)} ($filesSummary)",
        category = "BACKUP"
      )

      // Keep scripts classified in repository
      try {
        repo.copyAndClassifyScripts()
      } catch (_: Exception) {}

      // Generate integrity manifest silently
      try {
        GameIntegrityManager.getInstance(context).generateManifest(primaryBackupDir)
      } catch (_: Exception) {}

      // Re-scan so backedUpItems is updated with new files on disk
      scanGameFiles()

      val successMsg = context.getString(R.string.game_backup_success_title)
      _state.value = _state.value.copy(
        isBackingUp = false,
        progress = 1.0f,
        statusMessage = successMsg,
        lastBackupTimestamp = completionTimestamp,
        isSuccess = true,
        actionType = BackupActionType.CREATED
      )
      onStepProgress(successMsg, 1.0f)
      true
    } catch (e: Exception) {
      val errMsg = e.localizedMessage ?: context.getString(R.string.game_backup_status_not_found)
      _state.value = _state.value.copy(
        isBackingUp = false,
        progress = 0f,
        statusMessage = errMsg,
        error = errMsg
      )
      onStepProgress(errMsg, 0f)
      false
    }
  }

  private fun copyFileWithProgress(source: File, destination: File) {
    destination.parentFile?.mkdirs()
    FileInputStream(source).use { input ->
      FileOutputStream(destination).use { output ->
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
          output.write(buffer, 0, read)
        }
        output.flush()
      }
    }
  }

  /**
   * Records a temporary compressed backup zip with current timestamp.
   */
  fun recordCompressedBackup(zipFile: File) {
    prefs.edit()
      .putString(KEY_PENDING_ZIP_PATH, zipFile.absolutePath)
      .putLong(KEY_PENDING_ZIP_TIME, System.currentTimeMillis())
      .apply()
  }

  /**
   * Retrieves the pending compressed backup zip if still valid (< 15 minutes old).
   * If older than 15 minutes, automatically purges the file and clears preference.
   */
  fun getPendingCompressedBackup(): File? {
    var path = prefs.getString(KEY_PENDING_ZIP_PATH, null)
    var timestamp = prefs.getLong(KEY_PENDING_ZIP_TIME, 0L)

    if (path == null) {
      val tempDir = File(context.cacheDir, "compressed")
      val tempZip = File(tempDir, "GTA_SA_Backup.zip")
      if (tempZip.exists() && tempZip.length() > 0L) {
        path = tempZip.absolutePath
        timestamp = tempZip.lastModified()
        prefs.edit()
          .putString(KEY_PENDING_ZIP_PATH, path)
          .putLong(KEY_PENDING_ZIP_TIME, timestamp)
          .apply()
      }
    }

    if (path == null) return null

    val file = File(path)
    if (!file.exists()) {
      clearPendingCompressedBackup(deleteFile = false)
      return null
    }

    val elapsed = System.currentTimeMillis() - timestamp
    if (elapsed > COMPRESSION_TIMEOUT_MILLIS) {
      // 15-minute timeout reached: automatically delete temporary file
      clearPendingCompressedBackup(deleteFile = true)
      return null
    }
    return file
  }

  /**
   * Clears the pending compressed backup record and optionally deletes the file.
   */
  fun clearPendingCompressedBackup(deleteFile: Boolean = true) {
    val path = prefs.getString(KEY_PENDING_ZIP_PATH, null)
    if (deleteFile && path != null) {
      try {
        val file = File(path)
        if (file.exists()) file.delete()
      } catch (_: Exception) {}
    }
    // Also delete any fallback in cache
    if (deleteFile) {
        try {
            val tempZip = File(File(context.cacheDir, "compressed"), "GTA_SA_Backup.zip")
            if (tempZip.exists()) tempZip.delete()
        } catch (_: Exception) {}
    }
    prefs.edit()
      .remove(KEY_PENDING_ZIP_PATH)
      .remove(KEY_PENDING_ZIP_TIME)
      .apply()
  }

  /**
   * Checks if a pending compression has expired beyond 15 minutes and deletes it if so.
   * Returns true if an expired file was purged.
   */
  fun checkAndPurgeExpiredCompression(): Boolean {
    // Ensure we pick up any unrecorded file first
    getPendingCompressedBackup()

    val path = prefs.getString(KEY_PENDING_ZIP_PATH, null) ?: return false
    val timestamp = prefs.getLong(KEY_PENDING_ZIP_TIME, 0L)
    val elapsed = System.currentTimeMillis() - timestamp
    if (elapsed > COMPRESSION_TIMEOUT_MILLIS) {
      clearPendingCompressedBackup(deleteFile = true)
      return true
    }
    return false
  }
}
