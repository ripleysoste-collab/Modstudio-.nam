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
    val storageRoot = Environment.getExternalStorageDirectory()
    val dataDir = File(storageRoot, "Android/data/com.rockstargames.gtasa/files/texdb")
    val obbDir = File(storageRoot, "Android/obb/com.rockstargames.gtasa")

    val gpu = GpuDetector.detectGpuFormat(dataDir)
    onProgress?.invoke("GPU detectada: ${gpu.displayName} (${gpu.extension.uppercase(Locale.ROOT)})")

    val exteriorDir = getExteriorTexturesDir()
    val interiorDir = getInteriorTexturesDir()

    var exteriorCopied = 0
    var interiorCopied = 0

    val allKnownGpus = listOf("dxt", "etc", "pvr", "unc")

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

    // Purge files belonging to other GPUs to prevent filling storage
    fun purgeOtherGpuFiles(dir: File, effectiveGpu: String) {
      if (!dir.exists() || !dir.isDirectory) return
      val otherGpus = allKnownGpus.filter { it != effectiveGpu }
      dir.listFiles()?.forEach { file ->
        val lower = file.name.lowercase(Locale.ROOT)
        val isOtherGpu = otherGpus.any { other -> lower.contains(".$other.") || lower.endsWith(".$other") }
        if (isOtherGpu) {
          try {
            file.delete()
          } catch (_: Exception) {}
        }
      }
    }

    // Resolves whether candidates contain files for preferred GPU, or another single GPU
    fun resolveEffectiveGpu(candidates: Collection<String>, targetPrefix: String, preferredGpu: String): String {
      val prefixCandidates = candidates.filter { it.lowercase(Locale.ROOT).startsWith(targetPrefix) }
      val preferredMatch = prefixCandidates.any { candidate ->
        val lower = candidate.lowercase(Locale.ROOT)
        lower.contains(".$preferredGpu.") || lower.endsWith(".$preferredGpu")
      }
      if (preferredMatch) return preferredGpu

      for (other in allKnownGpus) {
        if (other != preferredGpu && prefixCandidates.any { candidate ->
          val lower = candidate.lowercase(Locale.ROOT)
          lower.contains(".$other.") || lower.endsWith(".$other")
        }) {
          return other
        }
      }
      return preferredGpu
    }

    // Helper: Strictly matches ONLY the 3 files of the specific GPU (.dat, .txd, .toc) and manifest (.txt)
    fun isSelectedGpuFile(fileName: String, targetPrefix: String, effectiveGpu: String): Boolean {
      val lower = fileName.lowercase(Locale.ROOT)
      if (!lower.startsWith(targetPrefix)) return false

      // Strictly reject any file belonging to other GPUs
      val otherGpus = allKnownGpus.filter { it != effectiveGpu }
      if (otherGpus.any { other -> lower.contains(".$other.") || lower.endsWith(".$other") }) {
        return false
      }

      // Check for the 3 specific files of this GPU: .dat, .txd, .toc
      val expectedExtensions = listOf(".dat", ".txd", ".toc")
      for (ext in expectedExtensions) {
        if (lower == "$targetPrefix.$effectiveGpu$ext" || (lower.startsWith("$targetPrefix.$effectiveGpu.") && lower.endsWith(ext))) {
          return true
        }
      }

      // Manifest .txt (optional tiny text file)
      if (lower == "$targetPrefix.txt" || lower == "$targetPrefix.$effectiveGpu.txt") {
        return true
      }

      // Generic files if no GPU infix exists (e.g. gta3.dat, gta3.txd, gta3.toc)
      if (lower == "$targetPrefix.dat" || lower == "$targetPrefix.txd" || lower == "$targetPrefix.toc") {
        return true
      }

      return false
    }

    // 1. Scan external disk folders first (Android/data/.../texdb/gta3 and texdb/gta_int)
    val diskGta3Dir = File(dataDir, "gta3")
    val diskGtaIntDir = File(dataDir, "gta_int")

    if (diskGta3Dir.exists() && diskGta3Dir.canRead()) {
      val candidateFiles = diskGta3Dir.listFiles()?.filter { it.isFile } ?: emptyList()
      val effectiveGpu = resolveEffectiveGpu(candidateFiles.map { it.name }, "gta3", gpu.extension)
      purgeOtherGpuFiles(exteriorDir, effectiveGpu)
      onProgress?.invoke("Respaldando 3 archivos de texturas exteriores ($effectiveGpu)...")

      candidateFiles.forEach { file ->
        if (isSelectedGpuFile(file.name, "gta3", effectiveGpu)) {
          if (copyFileIfNeeded(file, exteriorDir)) exteriorCopied++
        }
      }
    }

    if (diskGtaIntDir.exists() && diskGtaIntDir.canRead()) {
      val candidateFiles = diskGtaIntDir.listFiles()?.filter { it.isFile } ?: emptyList()
      val effectiveGpu = resolveEffectiveGpu(candidateFiles.map { it.name }, "gta_int", gpu.extension)
      purgeOtherGpuFiles(interiorDir, effectiveGpu)
      onProgress?.invoke("Respaldando 3 archivos de texturas interiores ($effectiveGpu)...")

      candidateFiles.forEach { file ->
        if (isSelectedGpuFile(file.name, "gta_int", effectiveGpu)) {
          if (copyFileIfNeeded(file, interiorDir)) interiorCopied++
        }
      }
    }

    // Also check root of texdb if gta3/gta_int files were stored flat
    if ((exteriorCopied == 0 || interiorCopied == 0) && dataDir.exists() && dataDir.canRead()) {
      val flatFiles = dataDir.listFiles()?.filter { it.isFile } ?: emptyList()
      val gta3EffectiveGpu = resolveEffectiveGpu(flatFiles.map { it.name }, "gta3", gpu.extension)
      val gtaIntEffectiveGpu = resolveEffectiveGpu(flatFiles.map { it.name }, "gta_int", gpu.extension)

      purgeOtherGpuFiles(exteriorDir, gta3EffectiveGpu)
      purgeOtherGpuFiles(interiorDir, gtaIntEffectiveGpu)

      flatFiles.forEach { file ->
        if (exteriorCopied == 0 && isSelectedGpuFile(file.name, "gta3", gta3EffectiveGpu)) {
          if (copyFileIfNeeded(file, exteriorDir)) exteriorCopied++
        } else if (interiorCopied == 0 && isSelectedGpuFile(file.name, "gta_int", gtaIntEffectiveGpu)) {
          if (copyFileIfNeeded(file, interiorDir)) interiorCopied++
        }
      }
    }

    // 2. Scan OBB zip archives if disk files were not found
    if ((exteriorCopied == 0 || interiorCopied == 0) && obbDir.exists() && obbDir.canRead()) {
      onProgress?.invoke("Examinando OBB para los 3 archivos de texturas de ${gpu.extension}...")
      val obbFiles = obbDir.listFiles { _, name -> name.endsWith(".obb", ignoreCase = true) } ?: emptyArray()

      for (obb in obbFiles) {
        try {
          ZipFile(obb).use { zip ->
            val entriesList = mutableListOf<java.util.zip.ZipEntry>()
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
              val entry = enumEntries.nextElement()
              if (!entry.isDirectory) {
                entriesList.add(entry)
              }
            }

            val gta3Entries = entriesList.filter { entry ->
              val lower = entry.name.lowercase(Locale.ROOT)
              lower.contains("texdb/gta3/") || lower.startsWith("gta3/") ||
                (lower.startsWith("texdb/") && lower.contains("gta3."))
            }
            val gtaIntEntries = entriesList.filter { entry ->
              val lower = entry.name.lowercase(Locale.ROOT)
              lower.contains("texdb/gta_int/") || lower.startsWith("gta_int/") ||
                (lower.startsWith("texdb/") && lower.contains("gta_int."))
            }

            val gta3EffectiveGpu = resolveEffectiveGpu(gta3Entries.map { File(it.name).name }, "gta3", gpu.extension)
            val gtaIntEffectiveGpu = resolveEffectiveGpu(gtaIntEntries.map { File(it.name).name }, "gta_int", gpu.extension)

            purgeOtherGpuFiles(exteriorDir, gta3EffectiveGpu)
            purgeOtherGpuFiles(interiorDir, gtaIntEffectiveGpu)

            if (exteriorCopied == 0) {
              for (entry in gta3Entries) {
                val simpleName = File(entry.name).name
                if (isSelectedGpuFile(simpleName, "gta3", gta3EffectiveGpu)) {
                  val targetFile = File(exteriorDir, simpleName)
                  if (!targetFile.exists() || targetFile.length() != entry.size) {
                    zip.getInputStream(entry).use { inStream ->
                      FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                      }
                    }
                  }
                  exteriorCopied++
                }
              }
            }

            if (interiorCopied == 0) {
              for (entry in gtaIntEntries) {
                val simpleName = File(entry.name).name
                if (isSelectedGpuFile(simpleName, "gta_int", gtaIntEffectiveGpu)) {
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
          }
        } catch (_: Exception) {
          // Gracefully continue
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
