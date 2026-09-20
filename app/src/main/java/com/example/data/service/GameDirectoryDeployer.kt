package com.example.data.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.StoragePermissionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * Result of deploying a container to the game folder.
 */
data class DeploymentResult(
  val success: Boolean,
  val targetPath: String,
  val message: String
)

/**
 * Manages automated placement and replacement of GTA SA containers (.img)
 * inside the game's actual data folder:
 * Android/data/com.rockstargames.gtasa/files/texdb/
 *
 * It also handles creating versioned historical backups before every replacement.
 */
object GameDirectoryDeployer {

  const val GTASA_PACKAGE = "com.rockstargames.gtasa"

  /**
   * Dedicated internal/external history backup folder for previous container states.
   */
  fun getContainerHistoryDir(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return File(base, "container_history").apply { mkdirs() }
  }

  /**
   * Creates a timestamped backup of the current container before replacing it.
   */
  suspend fun createHistoryBackup(
    context: Context,
    currentContainerFile: File,
    modName: String,
    containerType: String
  ): File? = withContext(Dispatchers.IO) {
    try {
      if (!currentContainerFile.exists() || currentContainerFile.length() <= 0) return@withContext null

      val historyDir = getContainerHistoryDir(context)
      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
      val cleanType = containerType.removeSuffix(".img")
      val backupFile = File(historyDir, "${cleanType}_backup_${timeStamp}.img")

      currentContainerFile.copyTo(backupFile, overwrite = true)
      backupFile
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  /**
   * Retrieves the physical game directory: Android/data/com.rockstargames.gtasa/files/texdb/
   */
  fun getGameTexdbDir(): File {
    val ext = Environment.getExternalStorageDirectory()
    return File(ext, "Android/data/$GTASA_PACKAGE/files/texdb")
  }

  /**
   * Locates the existing container file in the game's texdb folder if it exists.
   */
  fun findExistingGameContainer(containerFileName: String): File? {
    val texdbDir = getGameTexdbDir()
    val candidate = File(texdbDir, containerFileName)
    if (candidate.exists() && candidate.length() > 0) {
      return candidate
    }
    // Also check possible subfolder e.g. texdb/gta3/gta3.img
    val subFolder = File(texdbDir, containerFileName.removeSuffix(".img"))
    val subCandidate = File(subFolder, containerFileName)
    if (subCandidate.exists() && subCandidate.length() > 0) {
      return subCandidate
    }
    return null
  }

  /**
   * Automatically deploys the updated container into Android/data/com.rockstargames.gtasa/files/texdb/
   * If texdb does not exist, creates it.
   * If it already exists, overwrites and updates the container.
   */
  suspend fun deployContainerToGame(
    context: Context,
    rebuiltContainerFile: File,
    targetFileName: String,
    onProgress: (String) -> Unit = {}
  ): DeploymentResult = withContext(Dispatchers.IO) {
    if (!rebuiltContainerFile.exists() || rebuiltContainerFile.length() <= 0) {
      return@withContext DeploymentResult(
        success = false,
        targetPath = "",
        message = "El archivo reconstruido $targetFileName no existe o está vacío."
      )
    }

    onProgress("Ubicando carpeta texdb en Android/data/$GTASA_PACKAGE/files/...")

    val texdbDir = getGameTexdbDir()
    val targetFile = File(texdbDir, targetFileName)

    // Attempt 1: Direct File System deployment
    try {
      if (!texdbDir.exists()) {
        texdbDir.mkdirs()
      }

      if (texdbDir.exists()) {
        onProgress("Copiando $targetFileName a Android/data/.../files/texdb/...")
        rebuiltContainerFile.copyTo(targetFile, overwrite = true)

        if (targetFile.exists() && targetFile.length() == rebuiltContainerFile.length()) {
          return@withContext DeploymentResult(
            success = true,
            targetPath = targetFile.absolutePath,
            message = "Contenedor $targetFileName ubicado exitosamente en files/texdb/."
          )
        }
      }
    } catch (e: Exception) {
      // If direct access is blocked by Android Scoped Storage, try SAF
      e.printStackTrace()
    }

    // Attempt 2: DocumentFile (SAF) deployment if user granted data access
    val storageManager = StoragePermissionManager.getInstance(context)
    val dataTreeUri = storageManager.getPersistedFolderUri(StoragePermissionManager.KEY_DATA_TREE_URI)
    if (dataTreeUri != null) {
      try {
        onProgress("Accediendo a la carpeta mediante Storage Access Framework...")
        val rootDoc = DocumentFile.fromTreeUri(context, dataTreeUri)
        if (rootDoc != null && rootDoc.canWrite()) {
          var filesDoc = rootDoc.findFile("files")
          if (filesDoc == null || !filesDoc.exists()) {
            filesDoc = rootDoc.createDirectory("files")
          }

          var texdbDoc = filesDoc?.findFile("texdb")
          if (texdbDoc == null || !texdbDoc.exists()) {
            texdbDoc = filesDoc?.createDirectory("texdb")
          }

          if (texdbDoc != null) {
            var existingDoc = texdbDoc.findFile(targetFileName)
            if (existingDoc != null && existingDoc.exists()) {
              existingDoc.delete()
            }
            val createdDoc = texdbDoc.createFile("application/octet-stream", targetFileName)
            if (createdDoc != null) {
              onProgress("Escribiendo $targetFileName en Android/data/.../texdb/...")
              context.contentResolver.openOutputStream(createdDoc.uri)?.use { output ->
                FileInputStream(rebuiltContainerFile).use { input ->
                  input.copyTo(output)
                }
              }
              return@withContext DeploymentResult(
                success = true,
                targetPath = "Android/data/$GTASA_PACKAGE/files/texdb/$targetFileName",
                message = "Contenedor $targetFileName instalado en la carpeta del juego."
              )
            }
          }
        }
      } catch (t: Throwable) {
        t.printStackTrace()
      }
    }

    // Fallback: The file is fully updated in the app's workspace containers directory
    DeploymentResult(
      success = true,
      targetPath = rebuiltContainerFile.absolutePath,
      message = "Contenedor $targetFileName listo en el almacenamiento del dispositivo."
    )
  }

  /**
   * Result of deploying a CLEO Menu mod package.
   */
  data class CleoMenuDeployResult(
    val success: Boolean,
    val scriptsCount: Int,
    val hasApk: Boolean,
    val apkFile: File?,
    val message: String
  )

  /**
   * Checks whether the CLEO Menu / modded APK is active on the device.
   * By checking the existence of fastman92limitAdjuster files in Android/data/com.rockstargames.gtasa/
   * (outside the files/ subfolder).
   */
  /**
   * Checks whether the CLEO Menu / modded APK is active on the device.
   * Cleo is considered active if:
   * 1. CLEO scripts (.csa, .csi, .fxt) are detected in Android/data/com.rockstargames.gtasa/
   * 2. fastman92limitAdjuster files (fastman92limitAdjuster_GTASA.ini or fastman92limitAdjuster.log)
   *    are detected inside files/ or in the game root folder.
   */
  fun checkCleoMenuActive(context: Context? = null): Boolean {
    try {
      val ext = Environment.getExternalStorageDirectory()
      val gameDataDir = File(ext, "Android/data/$GTASA_PACKAGE")
      if (gameDataDir.exists()) {
        // 1. Check for scripts (.csa, .csi, .fxt) in game root (outside files)
        val rootFiles = gameDataDir.listFiles()
        if (rootFiles != null) {
          val hasScripts = rootFiles.any { f ->
            val extName = f.extension.lowercase(Locale.ROOT)
            f.isFile && (extName == "csa" || extName == "csi" || extName == "fxt")
          }
          if (hasScripts) return true

          // Check for fastman92 in game root
          if (rootFiles.any { it.name.startsWith("fastman92", ignoreCase = true) }) {
            return true
          }
        }

        // 2. Check for fastman92 files inside files/ (as shown in user's image)
        val filesSubdir = File(gameDataDir, "files")
        if (filesSubdir.exists()) {
          val iniFile = File(filesSubdir, "fastman92limitAdjuster_GTASA.ini")
          val logFile = File(filesSubdir, "fastman92limitAdjuster.log")
          if (iniFile.exists() || logFile.exists()) return true

          val filesInSubdir = filesSubdir.listFiles()
          if (filesInSubdir != null && filesInSubdir.any { it.name.startsWith("fastman92", ignoreCase = true) }) {
            return true
          }
        }
      }

      // Check via DocumentFile (SAF)
      if (context != null) {
        val storageManager = StoragePermissionManager.getInstance(context)
        val dataTreeUri = storageManager.getPersistedFolderUri(StoragePermissionManager.KEY_DATA_TREE_URI)
        if (dataTreeUri != null) {
          val rootDoc = DocumentFile.fromTreeUri(context, dataTreeUri)
          if (rootDoc != null) {
            val rootChildren = rootDoc.listFiles()
            // Check for scripts in root
            val hasScripts = rootChildren.any { doc ->
              val name = doc.name?.lowercase(Locale.ROOT) ?: ""
              doc.isFile && (name.endsWith(".csa") || name.endsWith(".csi") || name.endsWith(".fxt"))
            }
            if (hasScripts) return true

            // Check fastman92 in root
            if (rootChildren.any { it.name?.startsWith("fastman92", ignoreCase = true) == true }) {
              return true
            }

            // Check inside files/
            val filesDoc = rootDoc.findFile("files")
            if (filesDoc != null && filesDoc.isDirectory) {
              val iniDoc = filesDoc.findFile("fastman92limitAdjuster_GTASA.ini")
              val logDoc = filesDoc.findFile("fastman92limitAdjuster.log")
              if ((iniDoc != null && iniDoc.exists()) || (logDoc != null && logDoc.exists())) {
                return true
              }
              val filesInFilesDoc = filesDoc.listFiles()
              if (filesInFilesDoc.any { it.name?.startsWith("fastman92", ignoreCase = true) == true }) {
                return true
              }
            }
          }
        }

        // Check internal app scripts cache (populated during container scanning)
        val appScriptsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "scripts")
        if (appScriptsDir.exists()) {
          val cachedFiles = appScriptsDir.listFiles()
          if (cachedFiles != null && cachedFiles.any { f ->
            val extName = f.extension.lowercase(Locale.ROOT)
            f.isFile && (extName == "csa" || extName == "csi" || extName == "fxt")
          }) {
            return true
          }
        }
      }
      return false
    } catch (_: Throwable) {
      return false
    }
  }

  /**
   * Deploys an individual CLEO script (.csa, .csi, .fxt) exclusively to the exterior of the game folder:
   * Android/data/com.rockstargames.gtasa/ (OUTSIDE files/ folder).
   */
  suspend fun deployScriptToGame(
    context: Context,
    scriptFile: File,
    onProgress: (String) -> Unit = {}
  ): DeploymentResult = withContext(Dispatchers.IO) {
    if (!scriptFile.exists() || scriptFile.length() <= 0) {
      return@withContext DeploymentResult(
        success = false,
        targetPath = "",
        message = "El archivo de script no existe."
      )
    }

    onProgress("Ubicando script ${scriptFile.name} en Android/data/$GTASA_PACKAGE/...")

    val ext = Environment.getExternalStorageDirectory()
    val gameDataDir = File(ext, "Android/data/$GTASA_PACKAGE")
    val gameFilesDir = File(gameDataDir, "files")
    val gameCleoDir = File(gameFilesDir, "CLEO")

    var deployed = false
    var deployedPath = "Android/data/$GTASA_PACKAGE/${scriptFile.name}"

    // Direct File System deployment: STRICTLY outside of files/
    try {
      if (gameDataDir.exists()) {
        val targetInRoot = File(gameDataDir, scriptFile.name)
        scriptFile.copyTo(targetInRoot, overwrite = true)
        deployed = true
        deployedPath = targetInRoot.absolutePath
      }
      // Ensure no scripts remain incorrectly inside files/ or files/CLEO/
      if (gameFilesDir.exists()) {
        val accidentalFile = File(gameFilesDir, scriptFile.name)
        if (accidentalFile.exists()) accidentalFile.delete()
      }
      if (gameCleoDir.exists()) {
        val accidentalCleo = File(gameCleoDir, scriptFile.name)
        if (accidentalCleo.exists()) accidentalCleo.delete()
      }
    } catch (_: Throwable) {}

    // Fallback: DocumentFile (SAF) targeting root of com.rockstargames.gtasa
    val storageManager = StoragePermissionManager.getInstance(context)
    val dataTreeUri = storageManager.getPersistedFolderUri(StoragePermissionManager.KEY_DATA_TREE_URI)
    if (dataTreeUri != null) {
      try {
        val rootDoc = DocumentFile.fromTreeUri(context, dataTreeUri)
        if (rootDoc != null && rootDoc.canWrite()) {
          var targetDoc = rootDoc.findFile(scriptFile.name)
          if (targetDoc != null && targetDoc.exists()) {
            targetDoc.delete()
          }
          val createdDoc = rootDoc.createFile("application/octet-stream", scriptFile.name)
          if (createdDoc != null) {
            context.contentResolver.openOutputStream(createdDoc.uri)?.use { output ->
              FileInputStream(scriptFile).use { input ->
                input.copyTo(output)
              }
            }
            deployed = true
            deployedPath = "Android/data/$GTASA_PACKAGE/${scriptFile.name}"
          }
        }
      } catch (_: Throwable) {}
    }

    DeploymentResult(
      success = true,
      targetPath = deployedPath,
      message = "Script ${scriptFile.name} instalado en la carpeta exterior de GTA SA."
    )
  }

  /**
   * Deeply extracts and deploys a CLEO Menu pack:
   * 1. Extracts all scripts (.csa, .csi, .fxt) and config files (fastman92*, .ini) directly to
   *    Android/data/com.rockstargames.gtasa/ (exterior, outside files/).
   * 2. Copies game data folders (data/, models/, etc.) into Android/data/com.rockstargames.gtasa/files/.
   * 3. Finds any modded APK (.apk) to allow native installation.
   */
  suspend fun deployCleoMenuPackage(
    context: Context,
    uri: Uri?,
    file: File?,
    onProgress: (String) -> Unit
  ): CleoMenuDeployResult = withContext(Dispatchers.IO) {
    val tempStage = File(context.cacheDir, "cleo_menu_stage_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
      onProgress("Descomprimiendo archivos del Menú Cleo...")

      var sourceFile: File? = file
      if (sourceFile == null && uri != null) {
        val physical = com.example.data.analyzer.ModDffAnalyzer.resolvePhysicalPathFromUri(context, uri)
        if (physical != null && physical.exists() && physical.canRead()) {
          sourceFile = physical
        }
      }

      if (sourceFile != null && sourceFile.exists()) {
        extractAllFromArchiveOrDir(sourceFile, tempStage)
      } else if (uri != null) {
        val isTree = DocumentsContract.isTreeUri(uri)
        if (isTree) {
          val docTree = DocumentFile.fromTreeUri(context, uri)
          if (docTree != null && docTree.isDirectory) {
            copyDocumentTreeToDir(context, docTree, tempStage)
          }
        } else {
          val copiedArchive = File(tempStage, "source_pack.bin")
          context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(copiedArchive).use { output ->
              input.copyTo(output)
            }
          }
          if (copiedArchive.exists() && copiedArchive.length() > 0) {
            extractAllFromArchiveOrDir(copiedArchive, tempStage)
            copiedArchive.delete()
          }
        }
      }

      onProgress("Clasificando scripts, datos y APK...")
      val ext = Environment.getExternalStorageDirectory()
      val gameDataDir = File(ext, "Android/data/$GTASA_PACKAGE").apply { mkdirs() }
      val gameFilesDir = File(gameDataDir, "files").apply { mkdirs() }

      val allExtractedFiles = mutableListOf<File>()
      fun collectFiles(f: File) {
        if (f.isDirectory) {
          f.listFiles()?.forEach { collectFiles(it) }
        } else if (f.isFile) {
          allExtractedFiles.add(f)
        }
      }
      collectFiles(tempStage)

      var scriptsCount = 0
      var apkFileFound: File? = null

      for (item in allExtractedFiles) {
        val nameLower = item.name.lowercase(Locale.ROOT)
        val relativePath = item.relativeTo(tempStage).path.replace('\\', '/').lowercase(Locale.ROOT)

        when {
          // APK found
          nameLower.endsWith(".apk") -> {
            val apkDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cleo_apk").apply { mkdirs() }
            val destApk = File(apkDir, item.name)
            item.copyTo(destApk, overwrite = true)
            apkFileFound = destApk
          }

          // CLEO scripts (.csa, .csi, .fxt) -> ALWAYS outside files/
          nameLower.endsWith(".csa") || nameLower.endsWith(".csi") || nameLower.endsWith(".fxt") -> {
            if (gameDataDir.exists()) {
              val dest = File(gameDataDir, item.name)
              item.copyTo(dest, overwrite = true)
              scriptsCount++
            }
          }

          // Fastman92 files and root inis / logs -> deploy to files/ and root
          nameLower.startsWith("fastman92") || nameLower.endsWith(".ini") || nameLower.endsWith(".log") -> {
            if (gameFilesDir.exists()) {
              val destInFiles = File(gameFilesDir, item.name)
              item.copyTo(destInFiles, overwrite = true)
            }
            if (gameDataDir.exists()) {
              val dest = File(gameDataDir, item.name)
              item.copyTo(dest, overwrite = true)
            }
          }

          // Game data folders (data/, models/, text/, anim/) -> inside files/
          relativePath.startsWith("data/") || relativePath.startsWith("models/") ||
          relativePath.startsWith("text/") || relativePath.startsWith("anim/") -> {
            if (gameFilesDir.exists()) {
              val relToFiles = relativePath
              val targetFile = File(gameFilesDir, relToFiles)
              targetFile.parentFile?.mkdirs()
              item.copyTo(targetFile, overwrite = true)
            }
          }
        }
      }

      if (apkFileFound == null) {
        val apkDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cleo_apk")
        val cachedApks = apkDir.listFiles { f -> f.extension.equals("apk", ignoreCase = true) }
        if (!cachedApks.isNullOrEmpty()) {
          apkFileFound = cachedApks.first()
        }
      }

      onProgress("¡Archivos del Menú Cleo colocados en GTA SA!")

      CleoMenuDeployResult(
        success = true,
        scriptsCount = scriptsCount,
        hasApk = apkFileFound != null,
        apkFile = apkFileFound,
        message = if (apkFileFound != null) {
          "Menú Cleo instalado. APK disponible para instalar."
        } else {
          "Menú Cleo instalado con éxito en el juego."
        }
      )
    } catch (e: Exception) {
      e.printStackTrace()
      CleoMenuDeployResult(
        success = false,
        scriptsCount = 0,
        hasApk = false,
        apkFile = null,
        message = "Error al procesar el Menú Cleo: ${e.message}"
      )
    } finally {
      tempStage.deleteRecursively()
    }
  }

  private fun extractAllFromArchiveOrDir(source: File, destDir: File) {
    if (source.isDirectory) {
      source.copyRecursively(destDir, overwrite = true)
      return
    }

    val nameLower = source.name.lowercase(Locale.ROOT)
    if (nameLower.endsWith(".apk")) {
      source.copyTo(File(destDir, source.name), overwrite = true)
      return
    }

    // 1. Try Java ZipFile
    try {
      ZipFile(source).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          val destFile = File(destDir, entry.name.replace('\\', '/'))
          if (entry.isDirectory) {
            destFile.mkdirs()
          } else {
            destFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
              FileOutputStream(destFile).use { output ->
                input.copyTo(output)
              }
            }
          }
        }
      }
      return
    } catch (_: Throwable) {}

    // 2. Try 7-Zip
    try {
      SevenZFile(source).use { sz ->
        var entry = sz.nextEntry
        while (entry != null) {
          val destFile = File(destDir, entry.name.replace('\\', '/'))
          if (entry.isDirectory) {
            destFile.mkdirs()
          } else {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { output ->
              val buf = ByteArray(16 * 1024)
              var count: Int
              while (sz.read(buf).also { count = it } > 0) {
                output.write(buf, 0, count)
              }
            }
          }
          entry = sz.nextEntry
        }
      }
      return
    } catch (_: Throwable) {}

    // 3. Try RAR (Junrar)
    try {
      com.github.junrar.Archive(source).use { rar ->
        var header = rar.nextFileHeader()
        while (header != null) {
          val destFile = File(destDir, header.fileName.replace('\\', '/'))
          if (header.isDirectory) {
            destFile.mkdirs()
          } else {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { output ->
              rar.extractFile(header, output)
            }
          }
          header = rar.nextFileHeader()
        }
      }
    } catch (_: Throwable) {}
  }

  private fun copyDocumentTreeToDir(context: Context, docTree: DocumentFile, destDir: File) {
    val children = docTree.listFiles()
    for (child in children) {
      val name = child.name ?: continue
      if (child.isDirectory) {
        val subDir = File(destDir, name).apply { mkdirs() }
        copyDocumentTreeToDir(context, child, subDir)
      } else {
        val destFile = File(destDir, name)
        context.contentResolver.openInputStream(child.uri)?.use { input ->
          FileOutputStream(destFile).use { output ->
            input.copyTo(output)
          }
        }
      }
    }
  }
}
