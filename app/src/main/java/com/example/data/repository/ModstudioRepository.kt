package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConfigEntry
import com.example.data.local.entity.HistoryEntry
import com.example.data.local.entity.ModFileEntry
import com.example.data.analyzer.DffMatchEngine
import com.example.data.analyzer.MatchPlan
import com.example.data.analyzer.ModAnalysisResult
import com.example.data.analyzer.ModImplementationSummary
import com.example.data.analyzer.ModStagingManager
import com.example.data.analyzer.ModTextureAnalyzer
import com.example.data.analyzer.RawTextureEntry
import com.example.data.analyzer.ScriptInstallItem
import com.example.data.analyzer.TargetContainer
import com.example.data.parser.ContainerRebuildResult
import com.example.data.parser.ImgArchiveWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

sealed class ContainerSearchResult {
  data class Success(
    val gta3File: File,
    val gtaIntFile: File,
    val gta3Size: Long,
    val gtaIntSize: Long
  ) : ContainerSearchResult()

  data class NotFound(
    val reason: String,
    val searchedPaths: List<String>
  ) : ContainerSearchResult()
}

sealed class ContainerUpdateResult {
  data class Success(
    val changed: Boolean,
    val gta3Size: Long,
    val gtaIntSize: Long,
    val message: String
  ) : ContainerUpdateResult()

  data class Error(val message: String) : ContainerUpdateResult()
}

data class ScriptCopyResult(
  val csaCount: Int,
  val csiCount: Int,
  val fxtCount: Int,
  val totalFound: Int
)

/**
 * Single source of truth for all local offline database operations in Modstudio.
 * Abstracted via the Repository Pattern using Room Database.
 */
class ModstudioRepository(
  private val database: AppDatabase,
  private val context: Context
) {
  private val historyDao = database.historyDao()
  private val modFileDao = database.modFileDao()
  private val configDao = database.configDao()

  val allHistory: Flow<List<HistoryEntry>> = historyDao.getAllHistory()
  val historyCount: Flow<Int> = historyDao.getCount()

  val allFiles: Flow<List<ModFileEntry>> = modFileDao.getAllFiles()
  val imgFiles: Flow<List<ModFileEntry>> = modFileDao.getImgDffFiles()
  val gta3DffFiles: Flow<List<ModFileEntry>> = modFileDao.getGta3DffFiles()
  val gtaIntDffFiles: Flow<List<ModFileEntry>> = modFileDao.getGtaIntDffFiles()
  val gta3TxdFiles: Flow<List<ModFileEntry>> = modFileDao.getGta3TxdFiles()
  val gtaIntTxdFiles: Flow<List<ModFileEntry>> = modFileDao.getGtaIntTxdFiles()
  val txdFiles: Flow<List<ModFileEntry>> = modFileDao.getFilesByType("TXD")
  val gtaContainers: Flow<List<ModFileEntry>> = modFileDao.getGtaContainers()
  val csaFiles: Flow<List<ModFileEntry>> = modFileDao.getCsaFiles()
  val csiFiles: Flow<List<ModFileEntry>> = modFileDao.getCsiFiles()
  val fxtFiles: Flow<List<ModFileEntry>> = modFileDao.getFxtFiles()

  suspend fun getGtaContainersDirect(): List<ModFileEntry> = withContext(Dispatchers.IO) {
    modFileDao.getGtaContainersDirect()
  }

  suspend fun addHistoryEntry(
    title: String,
    description: String,
    category: String = "SCAN",
    backupFilePath: String? = null,
    containerType: String? = null,
    dffCount: Int = 0
  ): Long = withContext(Dispatchers.IO) {
    historyDao.insert(
      HistoryEntry(
        title = title,
        description = description,
        category = category,
        timestamp = System.currentTimeMillis(),
        backupFilePath = backupFilePath,
        containerType = containerType,
        dffCount = dffCount
      )
    )
  }

  suspend fun clearHistory() = withContext(Dispatchers.IO) {
    historyDao.clearAll()
  }

  suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) {
    historyDao.deleteById(id)
  }

  suspend fun addModFile(file: ModFileEntry): Long = withContext(Dispatchers.IO) {
    modFileDao.insert(file)
  }

  suspend fun addModFiles(files: List<ModFileEntry>) = withContext(Dispatchers.IO) {
    modFileDao.insertAll(files)
  }

  suspend fun clearModFiles() = withContext(Dispatchers.IO) {
    modFileDao.clearAll()
  }

  suspend fun deleteModFile(id: Long) = withContext(Dispatchers.IO) {
    modFileDao.deleteById(id)
  }

  fun getConfig(key: String): Flow<String?> = configDao.getValue(key)

  suspend fun setConfig(key: String, value: String) = withContext(Dispatchers.IO) {
    configDao.setConfig(ConfigEntry(key, value))
  }

  /**
   * Scans local game directories (Android/data and Android/obb) on device storage.
   * Inserts discovered IMG and TXD assets, CSI/CSA scripts, and saves scan audit into Historial.
   */
  suspend fun performLocalScan(): Int = withContext(Dispatchers.IO) {
    var detectedCount = 0
    val detectedFiles = mutableListOf<ModFileEntry>()

    val storageRoot = Environment.getExternalStorageDirectory()
    val candidatePaths = listOf(
      File(storageRoot, "Android/data/com.rockstargames.gtasa/files"),
      File(storageRoot, "Android/data/com.rockstargames.gtasa"),
      File(storageRoot, "Android/obb/com.rockstargames.gtasa")
    )

    for (dir in candidatePaths) {
      if (dir.exists() && dir.canRead()) {
        val files = dir.listFiles()
        if (files != null) {
          for (f in files) {
            val name = f.name.lowercase()
            val type = when {
              name.endsWith(".img") -> "IMG"
              name.endsWith(".txd") -> "TXD"
              name.endsWith(".csi") -> "CSI"
              name.endsWith(".csa") -> "CSA"
              else -> null
            }
            if (type != null) {
              detectedFiles.add(
                ModFileEntry(
                  fileName = f.name,
                  fileType = type,
                  relativePath = f.absolutePath,
                  sizeBytes = f.length(),
                  isEnabled = true,
                  lastModified = f.lastModified()
                )
              )
              detectedCount++
            }
          }
        }
      }
    }

    if (detectedFiles.isNotEmpty()) {
      modFileDao.insertAll(detectedFiles)
      addHistoryEntry(
        title = "Exploración completada",
        description = "Se detectaron y catalogaron $detectedCount archivos del juego en la base de datos local.",
        category = "SCAN"
      )
    } else {
      // Record scan in local database even if directory is pristine/empty
      addHistoryEntry(
        title = "Búsqueda local ejecutada",
        description = "Exploración de directorios Android/data y Android/obb finalizada. Base de datos sincronizada.",
        category = "SCAN"
      )
    }

    detectedCount
  }

  /**
   * Performs real detection, extraction and non-destructive backup copying of
   * gta3.img and gta_int.img from GTA San Andreas obb/data folders.
   */
  suspend fun findAndBackupGtaContainers(
    onProgress: suspend (String) -> Unit
  ): ContainerSearchResult = withContext(Dispatchers.IO) {
    val searchedPaths = mutableListOf<String>()

    onProgress("Verificando almacenamiento y permisos...")
    delay(400)

    val storageRoot = Environment.getExternalStorageDirectory()
    val obbDir = File(storageRoot, "Android/obb/com.rockstargames.gtasa")
    val dataDir = File(storageRoot, "Android/data/com.rockstargames.gtasa")
    val dataTexdbDir = File(dataDir, "files/texdb")
    val dataFilesDir = File(dataDir, "files")

    searchedPaths.add(obbDir.absolutePath)
    searchedPaths.add(dataTexdbDir.absolutePath)

    val containersDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "containers").apply {
      mkdirs()
    }
    val destGta3 = File(containersDir, "gta3.img")
    val destGtaInt = File(containersDir, "gta_int.img")

    // Helper to find file recursively in directory
    fun findFileIgnoreCase(dir: File, targetName: String, maxDepth: Int = 3): File? {
      if (!dir.exists() || !dir.canRead() || maxDepth <= 0) return null
      val files = dir.listFiles() ?: return null
      for (f in files) {
        if (f.isFile && f.name.equals(targetName, ignoreCase = true)) {
          return f
        } else if (f.isDirectory) {
          val found = findFileIgnoreCase(f, targetName, maxDepth - 1)
          if (found != null) return found
        }
      }
      return null
    }

    onProgress("Buscando carpeta Android/obb/com.rockstargames.gtasa...")
    delay(500)

    var sourceGta3File: File? = null
    var sourceGtaIntFile: File? = null

    // 1. Check direct disk files in obb and data
    val candidateDirs = listOf(obbDir, dataTexdbDir, dataFilesDir, dataDir)
    for (dir in candidateDirs) {
      if (sourceGta3File == null) sourceGta3File = findFileIgnoreCase(dir, "gta3.img")
      if (sourceGtaIntFile == null) sourceGtaIntFile = findFileIgnoreCase(dir, "gta_int.img")
    }

    // 2. Check inside .obb archive zip packages (main and patch)
    onProgress("Examinando paquetes OBB y carpeta texdb...")
    delay(500)

    var gta3FromZipBytes: ByteArray? = null
    var gtaIntFromZipBytes: ByteArray? = null

    if (obbDir.exists()) {
      val obbFiles = obbDir.listFiles { _, name -> name.endsWith(".obb", ignoreCase = true) } ?: emptyArray()
      for (obb in obbFiles) {
        searchedPaths.add(obb.absolutePath)
        try {
          ZipFile(obb).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
              val entry = entries.nextElement()
              val entryName = entry.name.lowercase(Locale.ROOT)
              if (sourceGta3File == null && gta3FromZipBytes == null && (entryName.endsWith("gta3.img") || entryName == "gta3.img")) {
                onProgress("Extrayendo gta3.img desde ${obb.name}...")
                zip.getInputStream(entry).use { input ->
                  FileOutputStream(destGta3).use { output ->
                    input.copyTo(output)
                  }
                }
                sourceGta3File = destGta3
              }
              if (sourceGtaIntFile == null && gtaIntFromZipBytes == null && (entryName.endsWith("gta_int.img") || entryName == "gta_int.img")) {
                onProgress("Extrayendo gta_int.img desde ${obb.name}...")
                zip.getInputStream(entry).use { input ->
                  FileOutputStream(destGtaInt).use { output ->
                    input.copyTo(output)
                  }
                }
                sourceGtaIntFile = destGtaInt
              }
            }
          }
        } catch (_: Exception) {
          // Continue searching other files gracefully
        }
      }
    }

    // 3. If files were found directly on disk, copy them to containers backup
    if (sourceGta3File != null && sourceGta3File != destGta3) {
      onProgress("Copiando contenedor gta3.img...")
      sourceGta3File?.inputStream()?.use { input ->
        FileOutputStream(destGta3).use { output ->
          input.copyTo(output)
        }
      }
    }

    if (sourceGtaIntFile != null && sourceGtaIntFile != destGtaInt) {
      onProgress("Copiando contenedor gta_int.img...")
      sourceGtaIntFile?.inputStream()?.use { input ->
        FileOutputStream(destGtaInt).use { output ->
          input.copyTo(output)
        }
      }
    }

    // 4. Verify if both containers are successfully backed up
    val gta3Ready = destGta3.exists() && destGta3.length() > 0
    val gtaIntReady = destGtaInt.exists() && destGtaInt.length() > 0

    if (gta3Ready && gtaIntReady) {
      onProgress("Contenedores listos y verificados.")
      delay(300)

      val gta3Size = destGta3.length()
      val gtaIntSize = destGtaInt.length()

      // Register both in Room DB with CONTAINER type so they don't appear as loose files in Explorador
      modFileDao.deleteByFileName("gta3.img")
      modFileDao.deleteByFileName("gta_int.img")
      modFileDao.insert(
        ModFileEntry(
          fileName = "gta3.img",
          fileType = "CONTAINER",
          relativePath = destGta3.absolutePath,
          sizeBytes = gta3Size,
          isEnabled = true,
          lastModified = destGta3.lastModified()
        )
      )
      modFileDao.insert(
        ModFileEntry(
          fileName = "gta_int.img",
          fileType = "CONTAINER",
          relativePath = destGtaInt.absolutePath,
          sizeBytes = gtaIntSize,
          isEnabled = true,
          lastModified = destGtaInt.lastModified()
        )
      )

      // Populate all internal container DFFs into the Explorador table
      populateContainerDffEntries()

      // Phase 0.1 Texture Explorer: Detect CPU/GPU and copy exterior (gta3) and interior (gta_int) texture database files
      val textureManager = com.example.data.analyzer.TextureBackupManager.getInstance(context)
      textureManager.copyTextures { msg -> onProgress(msg) }
      populateTextureEntries()

      // Phase 0.1: Copy all CLEO scripts (.csa, .csi, .fxt) found in Android/data into internal scripts cache
      val scriptResult = copyAndClassifyScripts(onProgress)
      val scriptMsg = if (scriptResult.totalFound > 0) {
        " y ${scriptResult.totalFound} scripts guardados (${scriptResult.csaCount} CSA, ${scriptResult.csiCount} CSI, ${scriptResult.fxtCount} FXT)"
      } else {
        ""
      }

      addHistoryEntry(
        title = "Contenedores y scripts respaldados",
        description = "gta3.img (${formatFileSize(gta3Size)}) y gta_int.img (${formatFileSize(gtaIntSize)}) copiados con éxito$scriptMsg.",
        category = "BACKUP"
      )

      ContainerSearchResult.Success(
        gta3File = destGta3,
        gtaIntFile = destGtaInt,
        gta3Size = gta3Size,
        gtaIntSize = gtaIntSize
      )
    } else {
      // Also inspect and copy scripts even if containers were not found in standard paths
      val scriptResult = copyAndClassifyScripts(onProgress)
      val scriptMsg = if (scriptResult.totalFound > 0) {
        " Se respaldaron ${scriptResult.totalFound} scripts (${scriptResult.csaCount} CSA, ${scriptResult.csiCount} CSI, ${scriptResult.fxtCount} FXT)."
      } else {
        ""
      }

      addHistoryEntry(
        title = "Búsqueda finalizada",
        description = "No se localizaron los dos contenedores (gta3.img y gta_int.img) en Android/obb ni Android/data.$scriptMsg",
        category = "INFO"
      )

      ContainerSearchResult.NotFound(
        reason = "No se encontraron los contenedores gta3.img y gta_int.img en las rutas de GTA San Andreas.",
        searchedPaths = searchedPaths
      )
    }
  }

  /**
   * Checks for external modifications in GTA San Andreas folders and refreshes local backups.
   * If no external changes occurred, reports that containers are already up-to-date.
   */
  suspend fun updateOrRefreshGtaContainers(
    onProgress: suspend (String) -> Unit
  ): ContainerUpdateResult = withContext(Dispatchers.IO) {
    onProgress("Buscando actualizaciones externas...")
    delay(400)

    val storageRoot = Environment.getExternalStorageDirectory()
    val obbDir = File(storageRoot, "Android/obb/com.rockstargames.gtasa")
    val dataDir = File(storageRoot, "Android/data/com.rockstargames.gtasa")
    val dataTexdbDir = File(dataDir, "files/texdb")
    val dataFilesDir = File(dataDir, "files")

    val containersDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "containers").apply {
      mkdirs()
    }
    val destGta3 = File(containersDir, "gta3.img")
    val destGtaInt = File(containersDir, "gta_int.img")

    // If local copies don't exist yet, run full backup search
    if (!destGta3.exists() || !destGtaInt.exists() || destGta3.length() == 0L || destGtaInt.length() == 0L) {
      val fullSearch = findAndBackupGtaContainers(onProgress)
      return@withContext when (fullSearch) {
        is ContainerSearchResult.Success -> ContainerUpdateResult.Success(
          changed = true,
          gta3Size = fullSearch.gta3Size,
          gtaIntSize = fullSearch.gtaIntSize,
          message = "Contenedores encontrados y respaldados."
        )
        is ContainerSearchResult.NotFound -> ContainerUpdateResult.Error(fullSearch.reason)
      }
    }

    onProgress("Comprobando cambios en archivos del juego...")
    delay(400)

    fun findFileIgnoreCase(dir: File, targetName: String, maxDepth: Int = 3): File? {
      if (!dir.exists() || !dir.canRead() || maxDepth <= 0) return null
      val files = dir.listFiles() ?: return null
      for (f in files) {
        if (f.isFile && f.name.equals(targetName, ignoreCase = true)) {
          return f
        } else if (f.isDirectory) {
          val found = findFileIgnoreCase(f, targetName, maxDepth - 1)
          if (found != null) return found
        }
      }
      return null
    }

    var extGta3: File? = null
    var extGtaInt: File? = null
    val candidateDirs = listOf(dataTexdbDir, dataFilesDir, dataDir, obbDir)
    for (dir in candidateDirs) {
      if (extGta3 == null) extGta3 = findFileIgnoreCase(dir, "gta3.img")
      if (extGtaInt == null) extGtaInt = findFileIgnoreCase(dir, "gta_int.img")
    }

    var changesDetected = false

    // Check if external gta3.img changed
    if (extGta3 != null && extGta3 != destGta3) {
      val extLen = extGta3.length()
      val extModified = extGta3.lastModified()
      if (extLen != destGta3.length() || (extModified > destGta3.lastModified() + 2000L)) {
        onProgress("Actualizando copia de gta3.img...")
        extGta3.inputStream().use { input ->
          FileOutputStream(destGta3).use { output ->
            input.copyTo(output)
          }
        }
        changesDetected = true
      }
    }

    // Check if external gta_int.img changed
    if (extGtaInt != null && extGtaInt != destGtaInt) {
      val extLen = extGtaInt.length()
      val extModified = extGtaInt.lastModified()
      if (extLen != destGtaInt.length() || (extModified > destGtaInt.lastModified() + 2000L)) {
        onProgress("Actualizando copia de gta_int.img...")
        extGtaInt.inputStream().use { input ->
          FileOutputStream(destGtaInt).use { output ->
            input.copyTo(output)
          }
        }
        changesDetected = true
      }
    }

    val currentGta3Size = destGta3.length()
    val currentGtaIntSize = destGtaInt.length()

    // Sincronizar también scripts en cada actualización
    copyAndClassifyScripts(onProgress)

    // Sincronizar también texturas según GPU
    val textureManager = com.example.data.analyzer.TextureBackupManager.getInstance(context)
    textureManager.copyTextures { msg -> onProgress(msg) }
    populateTextureEntries()

    if (changesDetected) {
      modFileDao.deleteByFileName("gta3.img")
      modFileDao.deleteByFileName("gta_int.img")
      modFileDao.insert(
        ModFileEntry(
          fileName = "gta3.img",
          fileType = "IMG",
          relativePath = destGta3.absolutePath,
          sizeBytes = currentGta3Size,
          isEnabled = true,
          lastModified = destGta3.lastModified()
        )
      )
      modFileDao.insert(
        ModFileEntry(
          fileName = "gta_int.img",
          fileType = "IMG",
          relativePath = destGtaInt.absolutePath,
          sizeBytes = currentGtaIntSize,
          isEnabled = true,
          lastModified = destGtaInt.lastModified()
        )
      )
      addHistoryEntry(
        title = "Contenedores actualizados",
        description = "Se sincronizaron cambios externos de gta3.img (${formatFileSize(currentGta3Size)}) y gta_int.img (${formatFileSize(currentGtaIntSize)}).",
        category = "UPDATE"
      )
      ContainerUpdateResult.Success(
        changed = true,
        gta3Size = currentGta3Size,
        gtaIntSize = currentGtaIntSize,
        message = "¡Contenedores actualizados con éxito!"
      )
    } else {
      addHistoryEntry(
        title = "Verificación de actualización",
        description = "No se detectaron cambios externos en los archivos del juego. Los contenedores están al día.",
        category = "INFO"
      )
      ContainerUpdateResult.Success(
        changed = false,
        gta3Size = currentGta3Size,
        gtaIntSize = currentGtaIntSize,
        message = "Sin cambios externos. Los contenedores están al día."
      )
    }
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
      gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
      mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
      kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
      else -> "$bytes B"
    }
  }

  fun getWorkingContainersDir(): File {
    return File(context.getExternalFilesDir(null) ?: context.filesDir, "containers").apply {
      mkdirs()
    }
  }

  /**
   * Retrieves the physical File reference for a GTA container (e.g. gta3.img, gta_int.img)
   * in the app-specific containers directory. If it doesn't exist yet, creates a placeholder
   * archive file so it can be cleanly shared or inspected.
   */
  fun getContainerFile(fileName: String): File {
    val containersDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "containers").apply {
      mkdirs()
    }
    val file = File(containersDir, fileName)
    if (!file.exists() || file.length() == 0L) {
      try {
        file.outputStream().use { out ->
          out.write("GTA SAN ANDREAS CONTAINER ARCHIVE: $fileName\nModstudio backup.\n".toByteArray())
        }
      } catch (_: Exception) {}
    }
    return file
  }

  /**
   * Reads all .dff models contained inside the game's actual containers (gta3.img & gta_int.img)
   * and populates them into the Room database for the Explorador screen.
   */
  suspend fun populateContainerDffEntries() = withContext(Dispatchers.IO) {
    try {
      val workingDir = getWorkingContainersDir()
      val gta3File = File(workingDir, "gta3.img")
      val gtaIntFile = File(workingDir, "gta_int.img")

      val dffItems = mutableListOf<ModFileEntry>()

      if (gta3File.exists()) {
        val gta3Entries = com.example.data.parser.ImgArchiveReader.readEntries(gta3File)
        for (entry in gta3Entries) {
          if (entry.name.endsWith(".dff", ignoreCase = true) && !entry.name.equals("gta3.img", ignoreCase = true)) {
            dffItems.add(
              ModFileEntry(
                fileName = entry.name,
                fileType = "GTA3_DFF",
                relativePath = "gta3.img",
                sizeBytes = entry.sizeBytes,
                isEnabled = true
              )
            )
          }
        }
      }

      if (gtaIntFile.exists()) {
        val gtaIntEntries = com.example.data.parser.ImgArchiveReader.readEntries(gtaIntFile)
        for (entry in gtaIntEntries) {
          if (entry.name.endsWith(".dff", ignoreCase = true) && !entry.name.equals("gta_int.img", ignoreCase = true)) {
            dffItems.add(
              ModFileEntry(
                fileName = entry.name,
                fileType = "GTA_INT_DFF",
                relativePath = "gta_int.img",
                sizeBytes = entry.sizeBytes,
                isEnabled = true
              )
            )
          }
        }
      }

      if (dffItems.isNotEmpty()) {
        modFileDao.clearDffFiles()
        modFileDao.insertAll(dffItems)
      }
    } catch (_: Throwable) {}
  }

  /**
   * Phase 0.1 Texture Explorer:
   * Extracts all exterior (gta3) and interior (gta_int) texture names from copied texture files
   * (.txt, .toc, .dat, .txd) and stores them in the local Room database for the Explorador TXD screen.
   */
  suspend fun populateTextureEntries() = withContext(Dispatchers.IO) {
    try {
      val textureManager = com.example.data.analyzer.TextureBackupManager.getInstance(context)
      val extDir = textureManager.getExteriorTexturesDir()
      val intDir = textureManager.getInteriorTexturesDir()

      val exteriorNames = com.example.data.parser.TxdArchiveReader.extractTextureNamesFromDir(
        dir = extDir,
        fallbackList = com.example.data.parser.TxdArchiveReader.DEFAULT_EXTERIOR_TEXTURES
      )

      val interiorNames = com.example.data.parser.TxdArchiveReader.extractTextureNamesFromDir(
        dir = intDir,
        fallbackList = com.example.data.parser.TxdArchiveReader.DEFAULT_INTERIOR_TEXTURES
      )

      val entries = mutableListOf<ModFileEntry>()
      for (name in exteriorNames) {
        entries.add(
          ModFileEntry(
            fileName = name,
            fileType = "GTA3_TXD",
            relativePath = "texdb/gta3",
            sizeBytes = 0L,
            isEnabled = true
          )
        )
      }
      for (name in interiorNames) {
        entries.add(
          ModFileEntry(
            fileName = name,
            fileType = "GTA_INT_TXD",
            relativePath = "texdb/gta_int",
            sizeBytes = 0L,
            isEnabled = true
          )
        )
      }

      if (entries.isNotEmpty()) {
        modFileDao.clearTxdFiles()
        modFileDao.insertAll(entries)
      }
    } catch (_: Throwable) {}
  }

  /**
   * Phase 0.1 Script Copy & Classification:
   * Scans Android/data/com.rockstargames.gtasa for all CLEO/script files (.csa, .csi, .fxt),
   * copies them into the application's dedicated "scripts" storage folder (separate from "containers"),
   * and classifies each file type into the Room database for the Explorador de Scripts screen.
   * If no scripts are found in game data or app storage, clears the entries and shows empty state.
   */
  suspend fun copyAndClassifyScripts(
    onProgress: (suspend (String) -> Unit)? = null
  ): ScriptCopyResult = withContext(Dispatchers.IO) {
    val scriptsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts").apply {
      mkdirs()
    }

    onProgress?.invoke("Buscando scripts en Android/data/com.rockstargames.gtasa...")

    val storageRoot = Environment.getExternalStorageDirectory()
    val dataDir = File(storageRoot, "Android/data/com.rockstargames.gtasa")
    val candidateDirs = listOf(
      dataDir,
      File(dataDir, "files"),
      File(dataDir, "files/CLEO")
    )

    val foundScripts = mutableListOf<File>()
    val visitedCanonicalPaths = mutableSetOf<String>()

    fun scanRecursively(dir: File, depth: Int = 0) {
      if (!dir.exists() || !dir.canRead() || depth > 4) return
      val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
      if (!visitedCanonicalPaths.add(canonical)) return

      val files = dir.listFiles() ?: return
      for (f in files) {
        if (f.isFile) {
          val nameLower = f.name.lowercase(Locale.ROOT)
          if (nameLower.endsWith(".csa") || nameLower.endsWith(".csi") || nameLower.endsWith(".fxt")) {
            foundScripts.add(f)
          }
        } else if (f.isDirectory && !f.name.startsWith(".")) {
          scanRecursively(f, depth + 1)
        }
      }
    }

    for (dir in candidateDirs) {
      scanRecursively(dir)
    }

    val dbEntries = mutableListOf<ModFileEntry>()
    var csaCount = 0
    var csiCount = 0
    var fxtCount = 0

    if (foundScripts.isNotEmpty()) {
      onProgress?.invoke("Copiando y clasificando scripts (${foundScripts.size} encontrados)...")

      for (srcFile in foundScripts) {
        try {
          val destFile = File(scriptsDir, srcFile.name)
          if (!destFile.exists() || destFile.length() != srcFile.length() || destFile.lastModified() != srcFile.lastModified()) {
            srcFile.inputStream().use { input ->
              FileOutputStream(destFile).use { output ->
                input.copyTo(output)
              }
            }
            destFile.setLastModified(srcFile.lastModified())
          }

          val nameLower = destFile.name.lowercase(Locale.ROOT)
          val type = when {
            nameLower.endsWith(".csa") -> {
              csaCount++
              "CSA"
            }
            nameLower.endsWith(".csi") -> {
              csiCount++
              "CSI"
            }
            nameLower.endsWith(".fxt") -> {
              fxtCount++
              "FXT"
            }
            else -> null
          }

          if (type != null) {
            dbEntries.add(
              ModFileEntry(
                fileName = destFile.name,
                fileType = type,
                relativePath = destFile.absolutePath,
                sizeBytes = destFile.length(),
                isEnabled = true,
                lastModified = destFile.lastModified()
              )
            )
          }
        } catch (_: Exception) {
          // Gracefully continue with other scripts
        }
      }
    } else {
      // If game data had no scripts or was unreadable, inspect what we already have saved in scriptsDir
      val cachedScripts = scriptsDir.listFiles() ?: emptyArray()
      for (destFile in cachedScripts) {
        if (destFile.isFile) {
          val nameLower = destFile.name.lowercase(Locale.ROOT)
          val type = when {
            nameLower.endsWith(".csa") -> {
              csaCount++
              "CSA"
            }
            nameLower.endsWith(".csi") -> {
              csiCount++
              "CSI"
            }
            nameLower.endsWith(".fxt") -> {
              fxtCount++
              "FXT"
            }
            else -> null
          }
          if (type != null) {
            dbEntries.add(
              ModFileEntry(
                fileName = destFile.name,
                fileType = type,
                relativePath = destFile.absolutePath,
                sizeBytes = destFile.length(),
                isEnabled = true,
                lastModified = destFile.lastModified()
              )
            )
          }
        }
      }
    }

    // Update Room DB classification
    modFileDao.clearScriptFiles()
    if (dbEntries.isNotEmpty()) {
      modFileDao.insertAll(dbEntries)
    }

    ScriptCopyResult(
      csaCount = csaCount,
      csiCount = csiCount,
      fxtCount = fxtCount,
      totalFound = dbEntries.size
    )
  }

  /**
   * Scans for installed CLEO / Script files (.csa, .csi, .fxt) in game directories
   * and registers them in the Room database for the Explorador de Scripts screen.
   */
  suspend fun populateScriptEntries() = withContext(Dispatchers.IO) {
    try {
      copyAndClassifyScripts()
    } catch (_: Throwable) {}
  }

  /**
   * Implements the mod into the game (Phase 0.3):
   * 1. Rebuilds affected IMG containers (GTA3 / GTA_INT) with DFF models and deploys them to files/texdb/.
   * 2. Deeply inspects the same mod (folders, subfolders, archives) for CLEO scripts (.csa, .csi, .fxt).
   * 3. Registers found scripts into the scripts table (Room DB) and deploys them into Android/data/com.rockstargames.gtasa/.
   * 4. Returns a clean ModImplementationSummary.
   */
  suspend fun executeRebuildPlan(
    plan: MatchPlan,
    modUri: Uri? = null,
    modFile: File? = null,
    textureOverrides: Map<String, TargetContainer> = emptyMap(),
    onProgress: (stepMessage: String, progressRatio: Float) -> Unit
  ): ModImplementationSummary = withContext(Dispatchers.IO) {
    try {
      val workingDir = getWorkingContainersDir()
      val gta3File = File(workingDir, "gta3.img")
      val gtaIntFile = File(workingDir, "gta_int.img")
      val stagingDir = ModStagingManager.getStagingDir(context)

      val dffBytesProvider: suspend (String) -> ByteArray? = { name ->
        val staged = File(stagingDir, name.lowercase(Locale.ROOT))
        if (staged.exists()) {
          staged.readBytes()
        } else {
          null
        }
      }

      var gta3Success = true
      var gtaIntSuccess = true

      val backupSettings = com.example.data.BackupSettingsManager.getInstance(context)
      val shouldSaveBackups = backupSettings.isSavedBackupEnabled()

      // 1. REBUILD GTA3.IMG
      if (plan.affectsGta3 && gta3File.exists()) {
        if (shouldSaveBackups) {
          onProgress("Creando copia de respaldo de gta3.img en historial...", 0.05f)
          val existingGameGta3 = com.example.data.service.GameDirectoryDeployer.findExistingGameContainer("gta3.img")
          val sourceToBackup = existingGameGta3 ?: gta3File
          val backupGta3 = com.example.data.service.GameDirectoryDeployer.createHistoryBackup(
            context = context,
            currentContainerFile = sourceToBackup,
            modName = plan.modName,
            containerType = "gta3.img"
          )
          if (backupGta3 != null) {
            val origCount = com.example.data.parser.ImgArchiveReader.readEntries(backupGta3).size
            addHistoryEntry(
              title = "Copia de seguridad: gta3.img",
              description = "Versión anterior guardada antes de instalar ${plan.modName} (${plan.totalDffCount} cambios).",
              category = "CONTAINER_BACKUP",
              backupFilePath = backupGta3.absolutePath,
              containerType = "gta3.img",
              dffCount = origCount
            )
          }
        }

        onProgress("Reconstruyendo gta3.img milimétricamente...", 0.15f)
        val result = ImgArchiveWriter.rebuildContainer(
          baseImgFile = gta3File,
          targetContainer = TargetContainer.GTA3,
          matchPlan = plan,
          dffBytesProvider = dffBytesProvider,
          onProgress = { msg, prog -> onProgress("GTA3: $msg", 0.15f + (prog * 0.40f)) }
        )
        gta3Success = result.success

        if (gta3Success) {
          onProgress("Ubicando gta3.img en Android/data/.../files/texdb/...", 0.58f)
          com.example.data.service.GameDirectoryDeployer.deployContainerToGame(
            context = context,
            rebuiltContainerFile = gta3File,
            targetFileName = "gta3.img",
            onProgress = { msg -> onProgress(msg, 0.60f) }
          )
        }
      }

      // 2. REBUILD GTA_INT.IMG
      if (plan.affectsGtaInt && gtaIntFile.exists() && gta3Success) {
        if (shouldSaveBackups) {
          onProgress("Creando copia de respaldo de gta_int.img en historial...", 0.65f)
          val existingGameGtaInt = com.example.data.service.GameDirectoryDeployer.findExistingGameContainer("gta_int.img")
          val sourceToBackup = existingGameGtaInt ?: gtaIntFile
          val backupGtaInt = com.example.data.service.GameDirectoryDeployer.createHistoryBackup(
            context = context,
            currentContainerFile = sourceToBackup,
            modName = plan.modName,
            containerType = "gta_int.img"
          )
          if (backupGtaInt != null) {
            val origCount = com.example.data.parser.ImgArchiveReader.readEntries(backupGtaInt).size
            addHistoryEntry(
              title = "Copia de seguridad: gta_int.img",
              description = "Versión anterior guardada antes de instalar ${plan.modName}.",
              category = "CONTAINER_BACKUP",
              backupFilePath = backupGtaInt.absolutePath,
              containerType = "gta_int.img",
              dffCount = origCount
            )
          }
        }

        onProgress("Reconstruyendo gta_int.img milimétricamente...", 0.72f)
        val result = ImgArchiveWriter.rebuildContainer(
          baseImgFile = gtaIntFile,
          targetContainer = TargetContainer.GTA_INT,
          matchPlan = plan,
          dffBytesProvider = dffBytesProvider,
          onProgress = { msg, prog -> onProgress("GTA_INT: $msg", 0.72f + (prog * 0.20f)) }
        )
        gtaIntSuccess = result.success

        if (gtaIntSuccess) {
          onProgress("Ubicando gta_int.img en Android/data/.../files/texdb/...", 0.93f)
          com.example.data.service.GameDirectoryDeployer.deployContainerToGame(
            context = context,
            rebuiltContainerFile = gtaIntFile,
            targetFileName = "gta_int.img",
            onProgress = { msg -> onProgress(msg, 0.94f) }
          )
        }
      }

      if (gta3Success && gtaIntSuccess) {
        onProgress("Modelos DFF listos. Buscando scripts CLEO en carpetas y subcarpetas...", 0.94f)
        populateContainerDffEntries()

        // 3. Deep search for scripts in the same mod
        val extractedScripts = ModStagingManager.extractScriptsFromMod(context, modUri, modFile)
        val scriptsInstalled = mutableListOf<ScriptInstallItem>()

        if (extractedScripts.isNotEmpty()) {
          onProgress("Se encontraron ${extractedScripts.size} scripts. Desplegando en Android/data...", 0.96f)
          val scriptsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts").apply { mkdirs() }

          for (scriptFile in extractedScripts) {
            val nameLower = scriptFile.name.lowercase(Locale.ROOT)
            val type = when {
              nameLower.endsWith(".csa") -> "CSA"
              nameLower.endsWith(".csi") -> "CSI"
              nameLower.endsWith(".fxt") -> "FXT"
              else -> "SCRIPT"
            }

            // Save to persistent app scripts folder
            val localScript = File(scriptsDir, scriptFile.name)
            scriptFile.copyTo(localScript, overwrite = true)

            // Deploy into actual game data directory
            val deployResult = com.example.data.service.GameDirectoryDeployer.deployScriptToGame(
              context = context,
              scriptFile = localScript,
              onProgress = { msg -> onProgress(msg, 0.97f) }
            )

            scriptsInstalled.add(
              ScriptInstallItem(
                name = scriptFile.name,
                type = type,
                sizeBytes = scriptFile.length(),
                deployedPath = deployResult.targetPath
              )
            )
          }

          // Register in Room DB scripts table (Explorador de Scripts)
          populateScriptEntries()
          onProgress("${scriptsInstalled.size} scripts CLEO ubicados y registrados.", 0.98f)
        } else {
          onProgress("Búsqueda finalizada: No se encontraron scripts (.csa, .csi, .fxt)", 0.98f)
        }

        // 4. Raw Textures Analysis & Classification (PNG, With Alpha / Without Alpha)
        onProgress("Analizando y clasificando texturas crudas (.png)...", 0.98f)
        val rawTextures: List<RawTextureEntry> = try {
          if (modUri != null) {
            ModTextureAnalyzer.analyzeFromUri(
              context = context,
              uri = modUri,
              archiveName = plan.modName,
              gameWorkingDir = workingDir
            )
          } else if (modFile != null) {
            ModTextureAnalyzer.analyzeFromFile(
              file = modFile,
              archiveDisplayName = plan.modName,
              gameWorkingDir = workingDir
            )
          } else {
            emptyList()
          }
        } catch (_: Throwable) {
          emptyList()
        }

        val effectiveTextures = rawTextures.map { t ->
          val override = textureOverrides[t.name.lowercase(Locale.ROOT)]
          if (override != null) {
            t.copy(targetContainer = override)
          } else {
            t
          }
        }

        val gta3Textures = effectiveTextures.filter { it.targetContainer == TargetContainer.GTA3 }
        val gtaIntTextures = effectiveTextures.filter { it.targetContainer == TargetContainer.GTA_INT }
        val withAlpha = effectiveTextures.count { it.hasAlpha }
        val withoutAlpha = effectiveTextures.count { !it.hasAlpha }

        if (effectiveTextures.isNotEmpty()) {
          onProgress("${effectiveTextures.size} texturas clasificadas (${gta3Textures.size} gta3, ${gtaIntTextures.size} gta_int). Sincronizando...", 0.99f)

          // Register in Room DB for Explorador TXD
          try {
            val textureDbEntries = mutableListOf<ModFileEntry>()
            for (t in gta3Textures) {
              textureDbEntries.add(
                ModFileEntry(
                  fileName = t.name,
                  fileType = "GTA3_TXD",
                  relativePath = "texdb/gta3/${t.alphaFolderName}/${t.name}",
                  sizeBytes = t.sizeBytes,
                  isEnabled = true
                )
              )
            }
            for (t in gtaIntTextures) {
              textureDbEntries.add(
                ModFileEntry(
                  fileName = t.name,
                  fileType = "GTA_INT_TXD",
                  relativePath = "texdb/gta_int/${t.alphaFolderName}/${t.name}",
                  sizeBytes = t.sizeBytes,
                  isEnabled = true
                )
              )
            }
            if (textureDbEntries.isNotEmpty()) {
              modFileDao.insertAll(textureDbEntries)
            }
          } catch (_: Throwable) {}
        }

        val scriptSummaryDesc = if (scriptsInstalled.isNotEmpty()) {
          "${scriptsInstalled.size} scripts CLEO instalados en data"
        } else {
          "Sin scripts en mod"
        }

        val textureSummaryDesc = if (rawTextures.isNotEmpty()) {
          "${rawTextures.size} texturas crudas clasificadas (${gta3Textures.size} exteriores, ${gtaIntTextures.size} interiores)"
        } else {
          "Sin texturas crudas"
        }

        addHistoryEntry(
          title = "Mod implementado: ${plan.modName}",
          description = "Contenedor en files/texdb/ con ${plan.totalDffCount} modelos DFF (${plan.replaceCount} reemplazos, ${plan.injectCount} inyecciones). $scriptSummaryDesc. $textureSummaryDesc.",
          category = "MOD"
        )
        onProgress("¡Mod implementado con éxito!", 1.0f)

        ModImplementationSummary(
          success = true,
          modName = plan.modName,
          dffReplacedCount = plan.replaceCount,
          dffInjectedCount = plan.injectCount,
          totalDffCount = plan.totalDffCount,
          affectsGta3 = plan.affectsGta3,
          affectsGtaInt = plan.affectsGtaInt,
          scriptsFound = scriptsInstalled,
          scriptsSearched = true,
          scriptsDeployedPath = "Android/data/com.rockstargames.gtasa/",
          rawTexturesFound = rawTextures,
          rawTexturesExteriorCount = gta3Textures.size,
          rawTexturesInteriorCount = gtaIntTextures.size,
          rawTexturesWithAlphaCount = withAlpha,
          rawTexturesWithoutAlphaCount = withoutAlpha,
          texturesDeployedPath = "Android/data/com.rockstargames.gtasa/files/texdb/"
        )
      } else {
        ModImplementationSummary(
          success = false,
          modName = plan.modName,
          dffReplacedCount = 0,
          dffInjectedCount = 0,
          totalDffCount = plan.totalDffCount,
          affectsGta3 = plan.affectsGta3,
          affectsGtaInt = plan.affectsGtaInt,
          errorMessage = "No se pudo completar la reconstrucción de los contenedores DFF."
        )
      }
    } catch (t: Throwable) {
      t.printStackTrace()
      ModImplementationSummary(
        success = false,
        modName = plan.modName,
        dffReplacedCount = 0,
        dffInjectedCount = 0,
        totalDffCount = plan.totalDffCount,
        affectsGta3 = plan.affectsGta3,
        affectsGtaInt = plan.affectsGtaInt,
        errorMessage = t.localizedMessage ?: "Error desconocido al implementar mod"
      )
    }
  }

  companion object {
    @Volatile
    private var INSTANCE: ModstudioRepository? = null

    fun getInstance(context: Context): ModstudioRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: ModstudioRepository(
          AppDatabase.getInstance(context.applicationContext),
          context.applicationContext
        ).also { INSTANCE = it }
      }
    }
  }
}
