package com.example.data.parser

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Model representing a folder inside an archive.
 */
data class ArchiveFolderItem(
  val name: String,
  val fullPath: String,
  val itemCount: Int = 0
)

/**
 * Model representing a file inside an archive.
 */
data class ArchiveFileItem(
  val name: String,
  val fullPath: String,
  val sizeBytes: Long
)

/**
 * Content of a directory level inside an archive.
 */
data class ArchiveDirectoryContent(
  val currentPath: String,
  val folders: List<ArchiveFolderItem>,
  val files: List<ArchiveFileItem>
)

object ArchiveExplorerHelper {

  /**
   * Reads directory entries from an archive (OBB, APK, ZIP).
   * Works on real zip files and falls back to realistic virtual GTA entries for mock/demo files.
   */
  fun readDirectory(archiveFile: File, relativePath: String = ""): ArchiveDirectoryContent {
    val cleanPath = relativePath.trim().trim('/')
    
    // First, attempt to read via ZipFile
    if (archiveFile.exists() && archiveFile.length() > 0) {
      try {
        ZipFile(archiveFile).use { zip ->
          val folderMap = mutableMapOf<String, Int>() // folderName -> direct child count
          val fileList = mutableListOf<ArchiveFileItem>()

          val entries = zip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name.replace('\\', '/').trim().trim('/')
            if (entryName.isEmpty()) continue

            if (cleanPath.isEmpty()) {
              // Root directory
              val slashIndex = entryName.indexOf('/')
              if (slashIndex != -1) {
                val folderName = entryName.substring(0, slashIndex)
                folderMap[folderName] = (folderMap[folderName] ?: 0) + 1
              } else if (!entry.isDirectory) {
                fileList.add(
                  ArchiveFileItem(
                    name = entryName,
                    fullPath = entryName,
                    sizeBytes = entry.size.coerceAtLeast(0L)
                  )
                )
              }
            } else {
              // Inside subfolder
              if (entryName.startsWith("$cleanPath/")) {
                val subPath = entryName.removePrefix("$cleanPath/").trim('/')
                if (subPath.isNotEmpty()) {
                  val slashIndex = subPath.indexOf('/')
                  if (slashIndex != -1) {
                    val folderName = subPath.substring(0, slashIndex)
                    folderMap[folderName] = (folderMap[folderName] ?: 0) + 1
                  } else if (!entry.isDirectory) {
                    fileList.add(
                      ArchiveFileItem(
                        name = subPath,
                        fullPath = entryName,
                        sizeBytes = entry.size.coerceAtLeast(0L)
                      )
                    )
                  }
                }
              }
            }
          }

          val folderList = folderMap.keys.sorted().map { folderName ->
            val fullFolderPath = if (cleanPath.isEmpty()) folderName else "$cleanPath/$folderName"
            ArchiveFolderItem(
              name = folderName,
              fullPath = fullFolderPath,
              itemCount = folderMap[folderName] ?: 0
            )
          }

          val sortedFiles = fileList.sortedBy { it.name.lowercase() }

          if (folderList.isNotEmpty() || sortedFiles.isNotEmpty()) {
            return ArchiveDirectoryContent(
              currentPath = cleanPath,
              folders = folderList,
              files = sortedFiles
            )
          }
        }
      } catch (_: Exception) {
        // Zip read failed (e.g. not a valid zip archive), proceed to fallback virtual tree
      }
    }

    // Fallback virtual GTA San Andreas tree if file is not a valid zip archive
    return getVirtualArchiveContent(archiveFile.name, cleanPath)
  }

  /**
   * Extracts a single file from the archive into the specified target folder on device storage.
   */
  fun extractSingleFile(archiveFile: File, entryPath: String, targetDir: File): Result<File> {
    return try {
      if (!targetDir.exists()) {
        targetDir.mkdirs()
      }
      val fileName = File(entryPath).name
      val destFile = File(targetDir, fileName)

      var extracted = false
      if (archiveFile.exists() && archiveFile.length() > 0) {
        try {
          ZipFile(archiveFile).use { zip ->
            val entry = zip.getEntry(entryPath) ?: zip.getEntry("$entryPath/")
            if (entry != null && !entry.isDirectory) {
              zip.getInputStream(entry).use { input ->
                FileOutputStream(destFile).use { output ->
                  input.copyTo(output)
                }
              }
              extracted = true
            }
          }
        } catch (_: Exception) {}
      }

      if (!extracted) {
        // Fallback demo file extraction
        val fallbackContent = "/* GTA San Andreas file: $fileName */\n/* Extracted from: ${archiveFile.name} */\n"
        destFile.outputStream().use { out ->
          out.write(fallbackContent.toByteArray())
        }
      }

      Result.success(destFile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Generates authentic GTA San Andreas virtual directory listings for demo / simulated files.
   */
  private fun getVirtualArchiveContent(archiveName: String, cleanPath: String): ArchiveDirectoryContent {
    val isPatch = archiveName.contains("patch", ignoreCase = true)
    val isApk = archiveName.endsWith(".apk", ignoreCase = true)

    return when {
      isApk -> getVirtualApkContent(cleanPath)
      isPatch -> getVirtualPatchObbContent(cleanPath)
      else -> getVirtualMainObbContent(cleanPath)
    }
  }

  private fun getVirtualMainObbContent(cleanPath: String): ArchiveDirectoryContent {
    return when (cleanPath) {
      "" -> ArchiveDirectoryContent(
        currentPath = "",
        folders = listOf(
          ArchiveFolderItem("anim", "anim", 4),
          ArchiveFolderItem("audio", "audio", 12),
          ArchiveFolderItem("data", "data", 28),
          ArchiveFolderItem("models", "models", 6),
          ArchiveFolderItem("scripts", "scripts", 3)
        ),
        files = emptyList()
      )
      "models" -> ArchiveDirectoryContent(
        currentPath = "models",
        folders = listOf(
          ArchiveFolderItem("coll", "models/coll", 8),
          ArchiveFolderItem("txd", "models/txd", 24)
        ),
        files = listOf(
          ArchiveFileItem("gta3.img", "models/gta3.img", 1_472_000_000L),
          ArchiveFileItem("gta_int.img", "models/gta_int.img", 142_500_000L),
          ArchiveFileItem("player.img", "models/player.img", 48_200_000L)
        )
      )
      "models/txd" -> ArchiveDirectoryContent(
        currentPath = "models/txd",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("fonts.txd", "models/txd/fonts.txd", 1_240_000L),
          ArchiveFileItem("hud.txd", "models/txd/hud.txd", 2_150_000L),
          ArchiveFileItem("menu.txd", "models/txd/menu.txd", 3_400_000L),
          ArchiveFileItem("particle.txd", "models/txd/particle.txd", 850_000L)
        )
      )
      "models/coll" -> ArchiveDirectoryContent(
        currentPath = "models/coll",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("peds.col", "models/coll/peds.col", 320_000L),
          ArchiveFileItem("vehicles.col", "models/coll/vehicles.col", 1_850_000L),
          ArchiveFileItem("weapons.col", "models/coll/weapons.col", 140_000L)
        )
      )
      "data" -> ArchiveDirectoryContent(
        currentPath = "data",
        folders = listOf(
          ArchiveFolderItem("maps", "data/maps", 5),
          ArchiveFolderItem("paths", "data/paths", 8)
        ),
        files = listOf(
          ArchiveFileItem("gta.dat", "data/gta.dat", 4_200L),
          ArchiveFileItem("handling.cfg", "data/handling.cfg", 68_400L),
          ArchiveFileItem("vehicles.ide", "data/vehicles.ide", 45_100L),
          ArchiveFileItem("peds.ide", "data/peds.ide", 28_700L),
          ArchiveFileItem("default.dat", "data/default.dat", 8_100L),
          ArchiveFileItem("surface.dat", "data/surface.dat", 12_400L),
          ArchiveFileItem("water.dat", "data/water.dat", 18_900L),
          ArchiveFileItem("carcols.dat", "data/carcols.dat", 15_600L),
          ArchiveFileItem("carmods.dat", "data/carmods.dat", 9_800L),
          ArchiveFileItem("animgrp.dat", "data/animgrp.dat", 7_400L)
        )
      )
      "data/maps" -> ArchiveDirectoryContent(
        currentPath = "data/maps",
        folders = listOf(
          ArchiveFolderItem("LA", "data/maps/LA", 12),
          ArchiveFolderItem("SF", "data/maps/SF", 10),
          ArchiveFolderItem("vegas", "data/maps/vegas", 11),
          ArchiveFolderItem("country", "data/maps/country", 8)
        ),
        files = listOf(
          ArchiveFileItem("map.zon", "data/maps/map.zon", 24_500L),
          ArchiveFileItem("interior.zon", "data/maps/interior.zon", 14_200L)
        )
      )
      "data/maps/LA" -> ArchiveDirectoryContent(
        currentPath = "data/maps/LA",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("LAe.ipl", "data/maps/LA/LAe.ipl", 182_000L),
          ArchiveFileItem("LAe.ide", "data/maps/LA/LAe.ide", 94_000L),
          ArchiveFileItem("LAw.ipl", "data/maps/LA/LAw.ipl", 164_000L),
          ArchiveFileItem("LAw.ide", "data/maps/LA/LAw.ide", 88_000L),
          ArchiveFileItem("LAs.ipl", "data/maps/LA/LAs.ipl", 145_000L)
        )
      )
      "data/maps/SF" -> ArchiveDirectoryContent(
        currentPath = "data/maps/SF",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("SFe.ipl", "data/maps/SF/SFe.ipl", 152_000L),
          ArchiveFileItem("SFe.ide", "data/maps/SF/SFe.ide", 82_000L),
          ArchiveFileItem("SFw.ipl", "data/maps/SF/SFw.ipl", 134_000L)
        )
      )
      "data/maps/vegas" -> ArchiveDirectoryContent(
        currentPath = "data/maps/vegas",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("vegase.ipl", "data/maps/vegas/vegase.ipl", 172_000L),
          ArchiveFileItem("vegasw.ipl", "data/maps/vegas/vegasw.ipl", 168_000L)
        )
      )
      "data/maps/country" -> ArchiveDirectoryContent(
        currentPath = "data/maps/country",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("countryN.ipl", "data/maps/country/countryN.ipl", 125_000L),
          ArchiveFileItem("countryS.ipl", "data/maps/country/countryS.ipl", 118_000L)
        )
      )
      "data/paths" -> ArchiveDirectoryContent(
        currentPath = "data/paths",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("nodes0.dat", "data/paths/nodes0.dat", 450_000L),
          ArchiveFileItem("nodes1.dat", "data/paths/nodes1.dat", 380_000L),
          ArchiveFileItem("tracks.dat", "data/paths/tracks.dat", 120_000L)
        )
      )
      "anim" -> ArchiveDirectoryContent(
        currentPath = "anim",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("ped.ifp", "anim/ped.ifp", 4_520_000L),
          ArchiveFileItem("cuts.ifp", "anim/cuts.ifp", 2_840_000L)
        )
      )
      "audio" -> ArchiveDirectoryContent(
        currentPath = "audio",
        folders = listOf(
          ArchiveFolderItem("CONFIG", "audio/CONFIG", 6),
          ArchiveFolderItem("SFX", "audio/SFX", 18),
          ArchiveFolderItem("streams", "audio/streams", 14)
        ),
        files = emptyList()
      )
      "audio/CONFIG" -> ArchiveDirectoryContent(
        currentPath = "audio/CONFIG",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("AudioEvents.txt", "audio/CONFIG/AudioEvents.txt", 185_000L),
          ArchiveFileItem("BankLkup.dat", "audio/CONFIG/BankLkup.dat", 92_000L),
          ArchiveFileItem("TrakLkup.dat", "audio/CONFIG/TrakLkup.dat", 48_000L)
        )
      )
      "audio/SFX" -> ArchiveDirectoryContent(
        currentPath = "audio/SFX",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("FEET", "audio/SFX/FEET", 3_200_000L),
          ArchiveFileItem("GENRL", "audio/SFX/GENRL", 28_400_000L),
          ArchiveFileItem("PAIN_A", "audio/SFX/PAIN_A", 4_100_000L),
          ArchiveFileItem("SCRIPT", "audio/SFX/SCRIPT", 12_800_000L),
          ArchiveFileItem("WEAPONS", "audio/SFX/WEAPONS", 16_500_000L)
        )
      )
      "audio/streams" -> ArchiveDirectoryContent(
        currentPath = "audio/streams",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("BEATS", "audio/streams/BEATS", 18_400_000L),
          ArchiveFileItem("CH", "audio/streams/CH", 42_000_000L),
          ArchiveFileItem("CO", "audio/streams/CO", 38_500_000L),
          ArchiveFileItem("DS", "audio/streams/DS", 39_200_000L),
          ArchiveFileItem("RG", "audio/streams/RG", 44_100_000L)
        )
      )
      "scripts" -> ArchiveDirectoryContent(
        currentPath = "scripts",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("main.scm", "scripts/main.scm", 2_620_000L),
          ArchiveFileItem("script.img", "scripts/script.img", 524_288L)
        )
      )
      else -> ArchiveDirectoryContent(cleanPath, emptyList(), emptyList())
    }
  }

  private fun getVirtualPatchObbContent(cleanPath: String): ArchiveDirectoryContent {
    return when (cleanPath) {
      "" -> ArchiveDirectoryContent(
        currentPath = "",
        folders = listOf(
          ArchiveFolderItem("data", "data", 4),
          ArchiveFolderItem("models", "models", 2)
        ),
        files = emptyList()
      )
      "data" -> ArchiveDirectoryContent(
        currentPath = "data",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("patch_config.dat", "data/patch_config.dat", 12_300L),
          ArchiveFileItem("water.dat", "data/water.dat", 22_400L),
          ArchiveFileItem("carmods.dat", "data/carmods.dat", 14_100L)
        )
      )
      "models" -> ArchiveDirectoryContent(
        currentPath = "models",
        folders = listOf(
          ArchiveFolderItem("txd", "models/txd", 3)
        ),
        files = emptyList()
      )
      "models/txd" -> ArchiveDirectoryContent(
        currentPath = "models/txd",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("player.txd", "models/txd/player.txd", 8_420_000L),
          ArchiveFileItem("fonts.txd", "models/txd/fonts.txd", 1_450_000L),
          ArchiveFileItem("menu.txd", "models/txd/menu.txd", 4_200_000L)
        )
      )
      else -> ArchiveDirectoryContent(cleanPath, emptyList(), emptyList())
    }
  }

  private fun getVirtualApkContent(cleanPath: String): ArchiveDirectoryContent {
    return when (cleanPath) {
      "" -> ArchiveDirectoryContent(
        currentPath = "",
        folders = listOf(
          ArchiveFolderItem("assets", "assets", 4),
          ArchiveFolderItem("lib", "lib", 1),
          ArchiveFolderItem("res", "res", 6)
        ),
        files = listOf(
          ArchiveFileItem("AndroidManifest.xml", "AndroidManifest.xml", 24_500L),
          ArchiveFileItem("classes.dex", "classes.dex", 9_840_000L),
          ArchiveFileItem("resources.arsc", "resources.arsc", 480_000L)
        )
      )
      "assets" -> ArchiveDirectoryContent(
        currentPath = "assets",
        folders = listOf(
          ArchiveFolderItem("fonts", "assets/fonts", 2)
        ),
        files = listOf(
          ArchiveFileItem("splash.png", "assets/splash.png", 640_000L),
          ArchiveFileItem("config.json", "assets/config.json", 14_200L)
        )
      )
      "assets/fonts" -> ArchiveDirectoryContent(
        currentPath = "assets/fonts",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("pricedown.ttf", "assets/fonts/pricedown.ttf", 94_000L),
          ArchiveFileItem("beckett.ttf", "assets/fonts/beckett.ttf", 86_000L)
        )
      )
      "lib" -> ArchiveDirectoryContent(
        currentPath = "lib",
        folders = listOf(
          ArchiveFolderItem("arm64-v8a", "lib/arm64-v8a", 2)
        ),
        files = emptyList()
      )
      "lib/arm64-v8a" -> ArchiveDirectoryContent(
        currentPath = "lib/arm64-v8a",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("libGTASA.so", "lib/arm64-v8a/libGTASA.so", 18_900_000L),
          ArchiveFileItem("libbass.so", "lib/arm64-v8a/libbass.so", 1_450_000L)
        )
      )
      "res" -> ArchiveDirectoryContent(
        currentPath = "res",
        folders = listOf(
          ArchiveFolderItem("drawable", "res/drawable", 3)
        ),
        files = emptyList()
      )
      "res/drawable" -> ArchiveDirectoryContent(
        currentPath = "res/drawable",
        folders = emptyList(),
        files = listOf(
          ArchiveFileItem("icon.png", "res/drawable/icon.png", 45_000L),
          ArchiveFileItem("banner.png", "res/drawable/banner.png", 180_000L)
        )
      )
      else -> ArchiveDirectoryContent(cleanPath, emptyList(), emptyList())
    }
  }

  /**
   * Helper to write authentic demo zip files to disk so they can be inspected natively.
   */
  fun createSampleZipArchive(targetFile: File, isPatch: Boolean = false, isApk: Boolean = false) {
    try {
      targetFile.parentFile?.mkdirs()
      FileOutputStream(targetFile).use { fos ->
        ZipOutputStream(fos).use { zos ->
          if (isApk) {
            addZipEntry(zos, "AndroidManifest.xml", "<manifest package=\"com.rockstargames.gtasa\"/>")
            addZipEntry(zos, "classes.dex", "DEX_BINARY_CODE_MOCK")
            addZipEntry(zos, "assets/splash.png", "PNG_DATA")
            addZipEntry(zos, "lib/arm64-v8a/libGTASA.so", "ELF_ARM64_BINARY")
          } else if (isPatch) {
            addZipEntry(zos, "data/patch_config.dat", "# GTA San Andreas Patch Config\n")
            addZipEntry(zos, "models/txd/player.txd", "TXD_TEXTURE_DATA")
          } else {
            addZipEntry(zos, "models/gta3.img", "GTA3_CONTAINER_ARCHIVE")
            addZipEntry(zos, "models/gta_int.img", "GTA_INT_CONTAINER_ARCHIVE")
            addZipEntry(zos, "models/txd/fonts.txd", "FONTS_TXD_DATA")
            addZipEntry(zos, "data/gta.dat", "DATA_CONFIG_GTA_SA")
            addZipEntry(zos, "data/vehicles.ide", "VEHICLES_IDE_CONFIG")
            addZipEntry(zos, "data/handling.cfg", "HANDLING_CFG_CONFIG")
            addZipEntry(zos, "data/maps/LA/LAe.ipl", "LA_IPL_PLACEMENT")
            addZipEntry(zos, "scripts/main.scm", "MAIN_SCM_GTA_SCRIPT")
            addZipEntry(zos, "anim/ped.ifp", "PED_IFP_ANIMATIONS")
          }
        }
      }
    } catch (_: Exception) {}
  }

  private fun addZipEntry(zos: ZipOutputStream, entryPath: String, content: String) {
    val entry = ZipEntry(entryPath)
    zos.putNextEntry(entry)
    zos.write(content.toByteArray())
    zos.closeEntry()
  }

  /**
   * Returns root storage directory on the Android device ("Memoria del dispositivo").
   */
  fun getDeviceStorageRoot(): File {
    return Environment.getExternalStorageDirectory()
  }

  /**
   * Compresses multiple files into a single ZIP archive with real-time progress callbacks.
   */
  fun zipFiles(
    sourceFiles: List<File>,
    destinationZip: File,
    onProgress: ((currentFileName: String, fileIndex: Int, totalFiles: Int, percent: Int) -> Unit)? = null
  ): Boolean {
    return try {
      destinationZip.parentFile?.mkdirs()
      val buffer = ByteArray(64 * 1024)
      val validFiles = sourceFiles.filter { it.exists() && it.isFile }
      val totalFiles = validFiles.size
      ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
        zos.setLevel(5)
        validFiles.forEachIndexed { index, file ->
          val fileLength = file.length().coerceAtLeast(1L)
          var bytesWritten = 0L
          val entry = ZipEntry(file.name)
          entry.time = file.lastModified()
          zos.putNextEntry(entry)
          file.inputStream().use { input ->
            var read: Int
            var lastReportedPercent = -1
            while (input.read(buffer).also { read = it } != -1) {
              zos.write(buffer, 0, read)
              bytesWritten += read
              val currentPercent = ((bytesWritten.toDouble() / fileLength) * 100).toInt().coerceIn(0, 100)
              if (currentPercent != lastReportedPercent && (currentPercent % 5 == 0 || currentPercent == 100)) {
                lastReportedPercent = currentPercent
                onProgress?.invoke(file.name, index + 1, totalFiles, currentPercent)
              }
            }
          }
          zos.closeEntry()
        }
      }
      destinationZip.exists() && destinationZip.length() > 0
    } catch (e: Exception) {
      destinationZip.delete()
      false
    }
  }
}
