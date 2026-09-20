package com.example.data.analyzer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages extraction and caching of DFF models from mods for seamless injection into IMG containers.
 */
object ModStagingManager {

  fun getStagingDir(context: Context): File {
    return File(context.filesDir, "active_mod_staging").apply { mkdirs() }
  }

  fun clearStagingDir(context: Context) {
    try {
      val dir = getStagingDir(context)
      if (dir.exists()) {
        dir.deleteRecursively()
        dir.mkdirs()
      }
    } catch (_: Throwable) {}
  }

  /**
   * Stages all DFF models from a mod source (Uri or File) into the staging directory.
   */
  suspend fun stageDffFiles(
    context: Context,
    uri: Uri
  ): Map<String, File> = withContext(Dispatchers.IO) {
    val stagingDir = getStagingDir(context)
    clearStagingDir(context)
    val map = mutableMapOf<String, File>()

    try {
      val physical = ModDffAnalyzer.resolvePhysicalPathFromUri(context, uri)
      if (physical != null && physical.exists() && physical.canRead()) {
        if (physical.isDirectory) {
          physical.walkTopDown().maxDepth(15).forEach { f ->
            if (f.isFile && f.name.endsWith(".dff", ignoreCase = true)) {
              val dest = File(stagingDir, f.name.lowercase(Locale.ROOT))
              f.copyTo(dest, overwrite = true)
              map[f.name.lowercase(Locale.ROOT)] = dest
            }
          }
        } else {
          stageFromArchiveFile(physical, stagingDir, map)
        }
      } else {
        val isTree = DocumentsContract.isTreeUri(uri)
        if (isTree) {
          val treeDoc = DocumentFile.fromTreeUri(context, uri)
          if (treeDoc != null && treeDoc.isDirectory) {
            stageFromDocumentTree(context, treeDoc, stagingDir, map, 15)
          }
        } else {
          val tempCache = File(context.cacheDir, "temp_stage_${System.currentTimeMillis()}").apply { mkdirs() }
          val tempFile = File(tempCache, "mod_archive")
          context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
              input.copyTo(output)
            }
          }
          if (tempFile.exists() && tempFile.length() > 0) {
            stageFromArchiveFile(tempFile, stagingDir, map)
          }
          tempCache.deleteRecursively()
        }
      }
    } catch (t: Throwable) {
      t.printStackTrace()
    }

    map
  }

  private fun stageFromArchiveFile(archive: File, stagingDir: File, map: MutableMap<String, File>) {
    val name = archive.name.lowercase(Locale.ROOT)
    if (name.endsWith(".dff")) {
      val dest = File(stagingDir, archive.name.lowercase(Locale.ROOT))
      archive.copyTo(dest, overwrite = true)
      map[archive.name.lowercase(Locale.ROOT)] = dest
      return
    }

    // Try standard ZipFile
    try {
      ZipFile(archive).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          val entryName = entry.name.replace('\\', '/')
          val fileName = File(entryName).name
          if (!entry.isDirectory && fileName.endsWith(".dff", ignoreCase = true)) {
            val dest = File(stagingDir, fileName.lowercase(Locale.ROOT))
            zip.getInputStream(entry).use { input ->
              FileOutputStream(dest).use { output ->
                input.copyTo(output)
              }
            }
            map[fileName.lowercase(Locale.ROOT)] = dest
          }
        }
      }
    } catch (_: Throwable) {
      // If zip fails, try Commons Compress / 7z
      try {
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { sz ->
          var entry = sz.nextEntry
          while (entry != null) {
            val fileName = File(entry.name.replace('\\', '/')).name
            if (!entry.isDirectory && fileName.endsWith(".dff", ignoreCase = true)) {
              val dest = File(stagingDir, fileName.lowercase(Locale.ROOT))
              FileOutputStream(dest).use { output ->
                val buf = ByteArray(16 * 1024)
                var count: Int
                while (sz.read(buf).also { count = it } > 0) {
                  output.write(buf, 0, count)
                }
              }
              map[fileName.lowercase(Locale.ROOT)] = dest
            }
            entry = sz.nextEntry
          }
        }
      } catch (_: Throwable) {}
    }
  }

  private fun stageFromDocumentTree(
    context: Context,
    docDir: DocumentFile,
    stagingDir: File,
    map: MutableMap<String, File>,
    maxDepth: Int
  ) {
    if (maxDepth <= 0) return
    try {
      val children = docDir.listFiles()
      for (child in children) {
        val name = child.name ?: continue
        if (child.isDirectory) {
          stageFromDocumentTree(context, child, stagingDir, map, maxDepth - 1)
        } else if (name.endsWith(".dff", ignoreCase = true)) {
          val dest = File(stagingDir, name.lowercase(Locale.ROOT))
          context.contentResolver.openInputStream(child.uri)?.use { input ->
            FileOutputStream(dest).use { output ->
              input.copyTo(output)
            }
          }
          map[name.lowercase(Locale.ROOT)] = dest
        }
      }
    } catch (_: Throwable) {}
  }

  fun isScriptFile(name: String): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    return lower.endsWith(".csa") || lower.endsWith(".csi") || lower.endsWith(".fxt")
  }

  fun getScriptsStagingDir(context: Context): File {
    return File(context.filesDir, "active_mod_scripts").apply { mkdirs() }
  }

  /**
   * Deeply scans the mod source (folders, subfolders, nested archives) for CLEO scripts
   * (.csa, .csi, .fxt) and extracts them cleanly into the scripts staging directory.
   */
  suspend fun extractScriptsFromMod(
    context: Context,
    uri: Uri?,
    file: File?
  ): List<File> = withContext(Dispatchers.IO) {
    val scriptsDir = getScriptsStagingDir(context)
    try {
      if (scriptsDir.exists()) {
        scriptsDir.deleteRecursively()
      }
      scriptsDir.mkdirs()
    } catch (_: Throwable) {}

    val extractedScripts = mutableListOf<File>()

    try {
      if (file != null && file.exists()) {
        extractScriptsFromFileOrDir(file, scriptsDir, extractedScripts)
      } else if (uri != null) {
        val physical = ModDffAnalyzer.resolvePhysicalPathFromUri(context, uri)
        if (physical != null && physical.exists() && physical.canRead()) {
          extractScriptsFromFileOrDir(physical, scriptsDir, extractedScripts)
        } else {
          val isTree = DocumentsContract.isTreeUri(uri)
          if (isTree) {
            val treeDoc = DocumentFile.fromTreeUri(context, uri)
            if (treeDoc != null && treeDoc.isDirectory) {
              extractScriptsFromDocumentTree(context, treeDoc, scriptsDir, extractedScripts, maxDepth = 25)
            }
          } else {
            val tempCache = File(context.cacheDir, "temp_scripts_stage_${System.currentTimeMillis()}").apply { mkdirs() }
            val tempFile = File(tempCache, "mod_archive")
            context.contentResolver.openInputStream(uri)?.use { input ->
              FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
              }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
              extractScriptsFromFileOrDir(tempFile, scriptsDir, extractedScripts)
            }
            tempCache.deleteRecursively()
          }
        }
      }
    } catch (t: Throwable) {
      t.printStackTrace()
    }

    extractedScripts
  }

  private fun extractScriptsFromFileOrDir(
    source: File,
    scriptsDir: File,
    outList: MutableList<File>
  ) {
    if (source.isDirectory) {
      source.walkTopDown().maxDepth(25).forEach { f ->
        if (f.isFile && isScriptFile(f.name)) {
          val dest = File(scriptsDir, f.name)
          f.copyTo(dest, overwrite = true)
          if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
            outList.add(dest)
          }
        }
      }
    } else if (isScriptFile(source.name)) {
      val dest = File(scriptsDir, source.name)
      source.copyTo(dest, overwrite = true)
      if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
        outList.add(dest)
      }
    } else {
      extractScriptsFromArchive(source, scriptsDir, outList)
    }
  }

  private fun extractScriptsFromArchive(
    archive: File,
    scriptsDir: File,
    outList: MutableList<File>
  ) {
    // 1. Try Java ZipFile
    try {
      ZipFile(archive).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          val fileName = File(entry.name.replace('\\', '/')).name
          if (!entry.isDirectory && isScriptFile(fileName)) {
            val dest = File(scriptsDir, fileName)
            zip.getInputStream(entry).use { input ->
              FileOutputStream(dest).use { output ->
                input.copyTo(output)
              }
            }
            if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
              outList.add(dest)
            }
          }
        }
      }
    } catch (_: Throwable) {
      // 2. Try 7-Zip
      try {
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { sz ->
          var entry = sz.nextEntry
          while (entry != null) {
            val fileName = File(entry.name.replace('\\', '/')).name
            if (!entry.isDirectory && isScriptFile(fileName)) {
              val dest = File(scriptsDir, fileName)
              FileOutputStream(dest).use { output ->
                val buf = ByteArray(16 * 1024)
                var count: Int
                while (sz.read(buf).also { count = it } > 0) {
                  output.write(buf, 0, count)
                }
              }
              if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
                outList.add(dest)
              }
            }
            entry = sz.nextEntry
          }
        }
      } catch (_: Throwable) {
        // 3. Try RAR (Junrar)
        try {
          com.github.junrar.Archive(archive).use { rar ->
            var header = rar.nextFileHeader()
            while (header != null) {
              val fileName = File(header.fileName.replace('\\', '/')).name
              if (!header.isDirectory && isScriptFile(fileName)) {
                val dest = File(scriptsDir, fileName)
                FileOutputStream(dest).use { output ->
                  rar.extractFile(header, output)
                }
                if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
                  outList.add(dest)
                }
              }
              header = rar.nextFileHeader()
            }
          }
        } catch (_: Throwable) {}
      }
    }
  }

  private fun extractScriptsFromDocumentTree(
    context: Context,
    docDir: DocumentFile,
    scriptsDir: File,
    outList: MutableList<File>,
    maxDepth: Int
  ) {
    if (maxDepth <= 0) return
    try {
      val children = docDir.listFiles()
      for (child in children) {
        val name = child.name ?: continue
        if (child.isDirectory) {
          extractScriptsFromDocumentTree(context, child, scriptsDir, outList, maxDepth - 1)
        } else if (isScriptFile(name)) {
          val dest = File(scriptsDir, name)
          context.contentResolver.openInputStream(child.uri)?.use { input ->
            FileOutputStream(dest).use { output ->
              input.copyTo(output)
            }
          }
          if (!outList.any { it.name.equals(dest.name, ignoreCase = true) }) {
            outList.add(dest)
          }
        }
      }
    } catch (_: Throwable) {}
  }
}
