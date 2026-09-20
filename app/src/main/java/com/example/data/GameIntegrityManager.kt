package com.example.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class FileRecord(
  val fileName: String,
  val sizeBytes: Long,
  val isDirectory: Boolean = false,
  val relativePath: String = "",
  val category: String = "game_file"
)

data class IntegrityReport(
  val isOk: Boolean,
  val corruptedFiles: List<FileRecord> = emptyList(),
  val missingFiles: List<FileRecord> = emptyList(),
  val extraFiles: List<FileRecord> = emptyList(),
  val errorMessage: String? = null
)

class GameIntegrityManager private constructor(private val context: Context) {
  companion object {
    @Volatile
    private var instance: GameIntegrityManager? = null
    fun getInstance(context: Context): GameIntegrityManager {
      return instance ?: synchronized(this) {
        instance ?: GameIntegrityManager(context.applicationContext).also { instance = it }
      }
    }
  }

  private val GTASA_PACKAGE = "com.rockstargames.gtasa"

  private fun getManifestFile(): File {
    val backupDir = File(Environment.getExternalStorageDirectory(), "Modstudio/Backups/$GTASA_PACKAGE")
    return File(backupDir, "manifest_v1.json")
  }

  private fun getFallbackManifestFile(): File {
    val backupDir = File(context.getExternalFilesDir(null), "backups/$GTASA_PACKAGE")
    return File(backupDir, "manifest_v1.json")
  }

  /**
   * Generates a manifest based on the pristine backup files and clean game directories.
   */
  suspend fun generateManifest(backupDir: File) = withContext(Dispatchers.IO) {
    val manifestObj = JSONObject()
    manifestObj.put("timestamp", System.currentTimeMillis())
    
    val filesArray = JSONArray()
    val registeredPaths = mutableSetOf<String>()

    // 1. Map all pristine backup files (OBBs, APK, scripts, configurations)
    val backupFiles = backupDir.walkTopDown().filter { it.isFile }.toList()
    for (f in backupFiles) {
      if (f.name == "manifest_v1.json") continue
      val relPath = f.relativeTo(backupDir).path
      val cat = when {
        f.name.endsWith(".obb", ignoreCase = true) -> "obb"
        f.name.endsWith(".apk", ignoreCase = true) -> "apk"
        f.name.endsWith(".csi", ignoreCase = true) || 
        f.name.endsWith(".csa", ignoreCase = true) || 
        f.name.endsWith(".fxt", ignoreCase = true) -> "script"
        else -> "game_data"
      }
      val record = JSONObject().apply {
        put("name", f.name)
        put("size", f.length())
        put("path", relPath)
        put("category", cat)
      }
      filesArray.put(record)
      registeredPaths.add(f.name.lowercase(Locale.ROOT))
    }

    // 2. Also map clean game data in Android/data/com.rockstargames.gtasa if present during backup
    val storageRoot = Environment.getExternalStorageDirectory()
    val dataDir = File(storageRoot, "Android/data/$GTASA_PACKAGE")
    if (dataDir.exists() && dataDir.isDirectory) {
      dataDir.walkTopDown().maxDepth(5).filter { it.isFile }.forEach { f ->
        if (!f.absolutePath.contains("/cache/")) {
          val relPath = f.relativeTo(dataDir).path
          val lowerRel = relPath.lowercase(Locale.ROOT)
          if (!registeredPaths.contains(lowerRel) && !registeredPaths.contains(f.name.lowercase(Locale.ROOT))) {
            val record = JSONObject().apply {
              put("name", f.name)
              put("size", f.length())
              put("path", relPath)
              put("category", "data")
            }
            filesArray.put(record)
            registeredPaths.add(lowerRel)
          }
        }
      }
    }
    
    manifestObj.put("files", filesArray)
    
    // Save to both locations
    try {
      getManifestFile().parentFile?.mkdirs()
      getManifestFile().writeText(manifestObj.toString())
    } catch (_: Exception) {}
    try {
      getFallbackManifestFile().parentFile?.mkdirs()
      getFallbackManifestFile().writeText(manifestObj.toString())
    } catch (_: Exception) {}
  }

  /**
   * Checks game integrity:
   * 1. Detects missing files (deleted OBB or game files).
   * 2. Detects corrupted/modified files (size mismatch from pristine).
   * 3. Detects extra/unrecognized/malicious files placed in OBB or game data folders.
   */
  suspend fun checkIntegrity(): IntegrityReport = withContext(Dispatchers.IO) {
    var manifestFile = getManifestFile()
    if (!manifestFile.exists()) {
      manifestFile = getFallbackManifestFile()
    }
    
    if (!manifestFile.exists()) {
      val primaryBackupDir = File(Environment.getExternalStorageDirectory(), "Modstudio/Backups/$GTASA_PACKAGE")
      if (primaryBackupDir.exists() && (primaryBackupDir.listFiles()?.isNotEmpty() == true)) {
        generateManifest(primaryBackupDir)
        manifestFile = getManifestFile()
      } else {
        return@withContext IntegrityReport(false, errorMessage = "No se encontró el mapa de integridad ni la copia de seguridad.")
      }
    }

    val content = manifestFile.readText()
    val manifestObj = JSONObject(content)
    val filesArray = manifestObj.getJSONArray("files")
    
    val manifestFiles = mutableListOf<FileRecord>()
    val knownObbNames = mutableSetOf<String>()
    val knownDataRelativePaths = mutableSetOf<String>()
    val knownFileNames = mutableSetOf<String>()

    for (i in 0 until filesArray.length()) {
      val obj = filesArray.getJSONObject(i)
      val name = obj.getString("name")
      val path = obj.getString("path")
      val cat = if (obj.has("category")) obj.getString("category") else "game_file"
      val record = FileRecord(
        fileName = name,
        sizeBytes = obj.getLong("size"),
        relativePath = path,
        category = cat
      )
      manifestFiles.add(record)
      knownFileNames.add(name.lowercase(Locale.ROOT))
      if (cat == "obb" || name.endsWith(".obb", ignoreCase = true)) {
        knownObbNames.add(name.lowercase(Locale.ROOT))
      } else {
        knownDataRelativePaths.add(path.lowercase(Locale.ROOT))
        knownDataRelativePaths.add(name.lowercase(Locale.ROOT))
      }
    }

    val storageRoot = Environment.getExternalStorageDirectory()
    val obbDir = File(storageRoot, "Android/obb/$GTASA_PACKAGE")
    val dataDir = File(storageRoot, "Android/data/$GTASA_PACKAGE")
    
    val missing = mutableListOf<FileRecord>()
    val corrupted = mutableListOf<FileRecord>()
    val extra = mutableListOf<FileRecord>()

    // 1. Check OBBs in Android/obb/com.rockstargames.gtasa
    for (record in manifestFiles.filter { it.category == "obb" || it.fileName.endsWith(".obb", ignoreCase = true) }) {
      val liveFile = File(obbDir, record.fileName)
      if (!liveFile.exists()) {
        missing.add(record.copy(relativePath = "Android/obb/$GTASA_PACKAGE/${record.fileName}"))
      } else if (liveFile.length() != record.sizeBytes) {
        corrupted.add(record.copy(relativePath = "Android/obb/$GTASA_PACKAGE/${record.fileName}"))
      }
    }

    // 1.1 Detect extra/foreign/malicious files in OBB directory
    if (obbDir.exists() && obbDir.isDirectory) {
      obbDir.listFiles()?.forEach { f ->
        if (f.isFile) {
          val nameLower = f.name.lowercase(Locale.ROOT)
          if (!knownObbNames.contains(nameLower)) {
            extra.add(
              FileRecord(
                fileName = f.name,
                sizeBytes = f.length(),
                relativePath = "Android/obb/$GTASA_PACKAGE/${f.name}",
                category = "obb"
              )
            )
          }
        }
      }
    }

    // 2. Check Data directory in Android/data/com.rockstargames.gtasa
    // 2.1 Check missing or modified files recorded in manifest for data
    val manifestDataRecords = manifestFiles.filter { 
      it.category != "obb" && it.category != "apk" && !it.fileName.endsWith(".obb", ignoreCase = true) 
    }
    for (record in manifestDataRecords) {
      val targetFile = File(dataDir, record.relativePath).let { 
        if (it.exists()) it else File(dataDir, record.fileName) 
      }
      if (!targetFile.exists()) {
        missing.add(record.copy(relativePath = "Android/data/$GTASA_PACKAGE/${record.relativePath}"))
      } else if (targetFile.length() != record.sizeBytes) {
        corrupted.add(record.copy(relativePath = "Android/data/$GTASA_PACKAGE/${record.relativePath}"))
      }
    }

    // 2.2 Detect extra / unrecognized / malicious files in Android/data/com.rockstargames.gtasa (recursively)
    if (dataDir.exists() && dataDir.isDirectory) {
      dataDir.walkTopDown().maxDepth(6).forEach { f ->
        if (f.isFile) {
          // Ignore cache directory and hidden temp files
          if (f.absolutePath.contains("/cache/") || f.name == ".nomedia") {
            return@forEach
          }
          val relPath = f.relativeTo(dataDir).path
          val relPathLower = relPath.lowercase(Locale.ROOT)
          val nameLower = f.name.lowercase(Locale.ROOT)

          val isKnown = knownDataRelativePaths.contains(relPathLower) || 
                        knownDataRelativePaths.contains(nameLower) ||
                        knownFileNames.contains(nameLower)

          if (!isKnown) {
            extra.add(
              FileRecord(
                fileName = f.name,
                sizeBytes = f.length(),
                relativePath = "Android/data/$GTASA_PACKAGE/$relPath",
                category = "extra"
              )
            )
          }
        }
      }
    }

    if (missing.isEmpty() && corrupted.isEmpty() && extra.isEmpty()) {
      return@withContext IntegrityReport(true)
    }

    val errorMsg = when {
      extra.isNotEmpty() && (missing.isNotEmpty() || corrupted.isNotEmpty()) ->
        "Se detectaron archivos faltantes/dañados y ${extra.size} archivo(s) extra o no reconocidos."
      extra.isNotEmpty() ->
        "Se detectaron ${extra.size} archivo(s) extra o no reconocidos en las carpetas del juego u OBB."
      else ->
        "Se han detectado modificaciones o archivos dañados en el juego."
    }

    IntegrityReport(
      isOk = false,
      missingFiles = missing,
      corruptedFiles = corrupted,
      extraFiles = extra,
      errorMessage = errorMsg
    )
  }

  /**
   * Repairs the game by restoring missing/corrupted files and undoing/removing extra files.
   * "y si es un error el mismo va a ir a las funciones de soluciones al deshacerlo, no nosotros automáticamente"
   */
  suspend fun repairFiles(report: IntegrityReport, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    val storageRoot = Environment.getExternalStorageDirectory()
    val obbDir = File(storageRoot, "Android/obb/$GTASA_PACKAGE")
    val dataDir = File(storageRoot, "Android/data/$GTASA_PACKAGE")
    
    val primaryBackupDir = File(storageRoot, "Modstudio/Backups/$GTASA_PACKAGE")
    val fallbackBackupDir = File(context.getExternalFilesDir(null), "backups/$GTASA_PACKAGE")

    // 1. Undo and remove extra / foreign / malicious files
    for (record in report.extraFiles) {
      onProgress("Deshaciendo cambios: eliminando ${record.fileName}...")
      val targetFile = File(storageRoot, record.relativePath)
      if (targetFile.exists() && targetFile.isFile) {
        targetFile.delete()
      }
    }
    // Clean any empty directories left inside dataDir
    cleanEmptyDirectories(dataDir)

    // 2. Restore missing and corrupted files from backup
    val allToRestore = report.missingFiles + report.corruptedFiles
    for (record in allToRestore) {
      onProgress("Restaurando ${record.fileName}...")
      var backupSource = File(primaryBackupDir, record.fileName)
      if (!backupSource.exists()) {
        backupSource = File(primaryBackupDir, record.relativePath)
      }
      if (!backupSource.exists()) {
        backupSource = File(fallbackBackupDir, record.fileName)
      }
      if (!backupSource.exists()) {
        backupSource = File(fallbackBackupDir, record.relativePath)
      }

      if (backupSource.exists() && backupSource.isFile) {
        val targetFile = if (record.relativePath.startsWith("Android/")) {
          File(storageRoot, record.relativePath)
        } else if (record.category == "obb" || record.fileName.endsWith(".obb", ignoreCase = true)) {
          File(obbDir, record.fileName)
        } else {
          File(dataDir, record.relativePath)
        }

        targetFile.parentFile?.mkdirs()
        backupSource.copyTo(targetFile, overwrite = true)
      }
    }
    
    return@withContext true
  }

  private fun cleanEmptyDirectories(dir: File) {
    if (!dir.exists() || !dir.isDirectory) return
    val children = dir.listFiles() ?: return
    for (child in children) {
      if (child.isDirectory && child.name != "cache") {
        cleanEmptyDirectories(child)
        val remaining = child.listFiles()
        if (remaining != null && remaining.isEmpty()) {
          child.delete()
        }
      }
    }
  }
}
