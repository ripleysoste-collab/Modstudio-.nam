package com.example.data.analyzer

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

data class TextureCopyResult(
  val gpuFormat: GpuFormat,
  val exteriorFilesCount: Int,
  val interiorFilesCount: Int,
  val totalFilesCopied: Int,
  val exteriorDir: File,
  val interiorDir: File
)

/**
 * Manages detection of device CPU/GPU texture format and copies exterior (gta3)
 * and interior (gta_int) texture database files from GTA San Andreas.
 */
class TextureBackupManager(private val context: Context) {

  fun getTexturesBaseDir(): File {
    return File(context.getExternalFilesDir(null) ?: context.filesDir, "textures").apply {
      mkdirs()
    }
  }

  fun getExteriorTexturesDir(): File {
    return File(getTexturesBaseDir(), "gta3").apply {
      mkdirs()
    }
  }

  fun getInteriorTexturesDir(): File {
    return File(getTexturesBaseDir(), "gta_int").apply {
      mkdirs()
    }
  }

  /**
   * Discovers and copies texture database files for both Exteriores (gta3) and Interiores (gta_int)
   * based on the detected device GPU architecture.
   */
  suspend fun copyTextures(
    onProgress: (suspend (String) -> Unit)? = null
  ): TextureCopyResult = withContext(Dispatchers.IO) {
    val gpu = GpuDetector.detectGpuFormat()
    onProgress?.invoke("Detectando GPU: ${gpu.displayName}...")

    val exteriorDir = getExteriorTexturesDir()
    val interiorDir = getInteriorTexturesDir()

    val storageRoot = Environment.getExternalStorageDirectory()
    val dataDir = File(storageRoot, "Android/data/com.rockstargames.gtasa/files/texdb")
    val obbDir = File(storageRoot, "Android/obb/com.rockstargames.gtasa")

    var exteriorCopied = 0
    var interiorCopied = 0

    // Helper: copy disk file if newer or different size
    fun copyFileIfNeeded(source: File, destDir: File): Boolean {
      try {
        val target = File(destDir, source.name)
        if (!target.exists() || target.length() != source.length() || target.lastModified() < source.lastModified()) {
          source.inputStream().use { input ->
            FileOutputStream(target).use { output ->
              input.copyTo(output)
            }
          }
          target.setLastModified(source.lastModified())
          return true
        }
        return true
      } catch (_: Exception) {
        return false
      }
    }

    // Helper: is texture file of interest?
    fun isRelevantTextureFile(fileName: String, targetPrefix: String, preferredGpu: String): Boolean {
      val lower = fileName.lowercase(Locale.ROOT)
      // Check prefix (gta3 or gta_int)
      if (!lower.startsWith(targetPrefix)) return false

      // Always include .txt list
      if (lower == "$targetPrefix.txt") return true

      // Any matching GPU format (.dxt., .etc., .pvr., .unc.)
      if (lower.contains(".$preferredGpu.") || lower.endsWith(".$preferredGpu")) return true

      // Include general texture extensions
      if (lower.endsWith(".dat") || lower.endsWith(".toc") || lower.endsWith(".tmb") || lower.endsWith(".txd")) {
        return true
      }

      return false
    }

    // 1. Scan external disk folders first (Android/data/.../texdb/gta3 and texdb/gta_int)
    onProgress?.invoke("Examinando carpetas de texturas en Android/data...")
    val diskGta3Dir = File(dataDir, "gta3")
    val diskGtaIntDir = File(dataDir, "gta_int")

    if (diskGta3Dir.exists() && diskGta3Dir.canRead()) {
      diskGta3Dir.listFiles()?.forEach { file ->
        if (file.isFile && isRelevantTextureFile(file.name, "gta3", gpu.extension)) {
          if (copyFileIfNeeded(file, exteriorDir)) exteriorCopied++
        }
      }
    }

    if (diskGtaIntDir.exists() && diskGtaIntDir.canRead()) {
      diskGtaIntDir.listFiles()?.forEach { file ->
        if (file.isFile && isRelevantTextureFile(file.name, "gta_int", gpu.extension)) {
          if (copyFileIfNeeded(file, interiorDir)) interiorCopied++
        }
      }
    }

    // Also check root of texdb if gta3/gta_int files were stored flat
    if (dataDir.exists() && dataDir.canRead()) {
      dataDir.listFiles()?.forEach { file ->
        if (file.isFile) {
          if (isRelevantTextureFile(file.name, "gta3", gpu.extension)) {
            if (copyFileIfNeeded(file, exteriorDir)) exteriorCopied++
          } else if (isRelevantTextureFile(file.name, "gta_int", gpu.extension)) {
            if (copyFileIfNeeded(file, interiorDir)) interiorCopied++
          }
        }
      }
    }

    // 2. Scan OBB zip archives if disk files are empty or incomplete
    if (obbDir.exists() && obbDir.canRead()) {
      onProgress?.invoke("Examinando paquetes OBB para texturas de ${gpu.extension}...")
      val obbFiles = obbDir.listFiles { _, name -> name.endsWith(".obb", ignoreCase = true) } ?: emptyArray()

      for (obb in obbFiles) {
        try {
          ZipFile(obb).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
              val entry = entries.nextElement()
              if (entry.isDirectory) continue
              val entryName = entry.name.lowercase(Locale.ROOT)

              // Check if entry is in texdb/gta3 or texdb/gta_int
              val isGta3 = entryName.contains("texdb/gta3/") || entryName.startsWith("gta3/") ||
                (entryName.startsWith("texdb/") && entryName.contains("gta3."))
              val isGtaInt = entryName.contains("texdb/gta_int/") || entryName.startsWith("gta_int/") ||
                (entryName.startsWith("texdb/") && entryName.contains("gta_int."))

              val simpleName = File(entry.name).name

              if (isGta3 && isRelevantTextureFile(simpleName, "gta3", gpu.extension)) {
                val targetFile = File(exteriorDir, simpleName)
                if (!targetFile.exists() || targetFile.length() != entry.size) {
                  zip.getInputStream(entry).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                      inStream.copyTo(outStream)
                    }
                  }
                }
                exteriorCopied++
              } else if (isGtaInt && isRelevantTextureFile(simpleName, "gta_int", gpu.extension)) {
                val targetFile = File(interiorDir, simpleName)
                if (!targetFile.exists() || targetFile.length() != entry.size) {
                  zip.getInputStream(entry).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                      inStream.copyTo(outStream)
                    }
                  }
                }
                interiorCopied++
              }
            }
          }
        } catch (_: Exception) {
          // Gracefully continue searching
        }
      }
    }

    onProgress?.invoke("Copia de texturas finalizada: $exteriorCopied exteriores, $interiorCopied interiores.")

    TextureCopyResult(
      gpuFormat = gpu,
      exteriorFilesCount = exteriorDir.listFiles()?.size ?: 0,
      interiorFilesCount = interiorDir.listFiles()?.size ?: 0,
      totalFilesCopied = (exteriorDir.listFiles()?.size ?: 0) + (interiorDir.listFiles()?.size ?: 0),
      exteriorDir = exteriorDir,
      interiorDir = interiorDir
    )
  }

  companion object {
    @Volatile
    private var INSTANCE: TextureBackupManager? = null

    fun getInstance(context: Context): TextureBackupManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: TextureBackupManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
