package com.example.data.analyzer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.parser.TxdArchiveReader
import com.github.junrar.Archive as RarArchive
import com.github.junrar.rarfile.FileHeader
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Deep scanner and classifier for raw textures (.png, .jpg, .bmp) inside mod packages.
 */
object ModTextureAnalyzer {

  fun analyzeFromUri(
    context: Context,
    uri: Uri,
    archiveName: String? = null,
    dffExteriorTextures: Set<String> = emptySet(),
    dffInteriorTextures: Set<String> = emptySet(),
    gameWorkingDir: File? = null
  ): List<RawTextureEntry> {
    // 1. Try physical file resolution first
    val physicalFile = ModDffAnalyzer.resolvePhysicalPathFromUri(context, uri)
    if (physicalFile != null && physicalFile.exists()) {
      return analyzeFromFile(
        file = physicalFile,
        dffExteriorTextures = dffExteriorTextures,
        dffInteriorTextures = dffInteriorTextures,
        gameWorkingDir = gameWorkingDir
      )
    }

    // 2. Try DocumentFile directory tree
    if (uri.scheme == "content") {
      try {
        val docDir = DocumentFile.fromTreeUri(context, uri)
        if (docDir != null && docDir.isDirectory) {
          val knownExt = getKnownTextureNames("gta3", gameWorkingDir)
          val knownInt = getKnownTextureNames("gta_int", gameWorkingDir)
          val rawEntries = mutableListOf<RawScannedTexture>()
          scanDocumentTree(context, docDir, "", rawEntries, maxDepth = 12)
          return rawEntries.map { raw ->
            val (hasAlpha, alphaFolder) = RawTextureClassifier.detectAlpha(raw.relativePath, raw.previewBytes)
            val (target, reason) = RawTextureClassifier.classify(
              fileName = raw.name,
              relativePath = raw.relativePath,
              archiveOrModName = archiveName ?: docDir.name,
              dffExteriorTextures = dffExteriorTextures,
              dffInteriorTextures = dffInteriorTextures,
              knownExteriorTextures = knownExt,
              knownInteriorTextures = knownInt
            )
            RawTextureEntry(
              name = raw.name,
              baseName = raw.name.substringBeforeLast('.'),
              sizeBytes = raw.sizeBytes,
              relativePath = raw.relativePath,
              folderName = if (raw.relativePath.contains('/')) raw.relativePath.substringBeforeLast('/').substringAfterLast('/') else "",
              hasAlpha = hasAlpha,
              alphaFolderName = alphaFolder,
              targetContainer = target,
              matchReason = reason
            )
          }
        }
      } catch (_: Throwable) {}
    }

    // 3. Fallback: Copy single archive to temp
    val tempDir = File(context.cacheDir, "tex_scan_${System.currentTimeMillis()}").apply { mkdirs() }
    val tempFile = File(tempDir, "temp_mod_archive")
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
          input.copyTo(output)
        }
      }
      return analyzeFromFile(
        file = tempFile,
        archiveDisplayName = archiveName,
        dffExteriorTextures = dffExteriorTextures,
        dffInteriorTextures = dffInteriorTextures,
        gameWorkingDir = gameWorkingDir
      )
    } catch (_: Throwable) {
      return emptyList()
    } finally {
      tempFile.delete()
      tempDir.delete()
    }
  }

  fun analyzeFromFile(
    file: File,
    archiveDisplayName: String? = null,
    dffExteriorTextures: Set<String> = emptySet(),
    dffInteriorTextures: Set<String> = emptySet(),
    gameWorkingDir: File? = null
  ): List<RawTextureEntry> {
    val knownExt = getKnownTextureNames("gta3", gameWorkingDir)
    val knownInt = getKnownTextureNames("gta_int", gameWorkingDir)
    val modName = archiveDisplayName ?: file.name

    val rawEntries = if (file.isDirectory) {
      scanLocalDirectory(file)
    } else {
      scanArchive(file)
    }

    return rawEntries.map { raw ->
      val (hasAlpha, alphaFolder) = RawTextureClassifier.detectAlpha(raw.relativePath, raw.previewBytes)
      val (target, reason) = RawTextureClassifier.classify(
        fileName = raw.name,
        relativePath = raw.relativePath,
        archiveOrModName = modName,
        dffExteriorTextures = dffExteriorTextures,
        dffInteriorTextures = dffInteriorTextures,
        knownExteriorTextures = knownExt,
        knownInteriorTextures = knownInt
      )
      RawTextureEntry(
        name = raw.name,
        baseName = raw.name.substringBeforeLast('.'),
        sizeBytes = raw.sizeBytes,
        relativePath = raw.relativePath,
        folderName = if (raw.relativePath.contains('/')) raw.relativePath.substringBeforeLast('/').substringAfterLast('/') else "",
        hasAlpha = hasAlpha,
        alphaFolderName = alphaFolder,
        targetContainer = target,
        matchReason = reason
      )
    }
  }

  private fun scanLocalDirectory(dir: File): List<RawScannedTexture> {
    val result = mutableListOf<RawScannedTexture>()
    val baseUri = dir.toURI()

    dir.walkTopDown().forEach { f ->
      if (f.isFile && RawTextureClassifier.isRawTextureFile(f.name)) {
        val rel = baseUri.relativize(f.toURI()).path.replace('\\', '/')
        val preview = try {
          f.inputStream().use { it.readNBytes(32) }
        } catch (_: Throwable) { null }
        result.add(
          RawScannedTexture(
            name = f.name,
            relativePath = rel,
            sizeBytes = f.length(),
            previewBytes = preview
          )
        )
      }
    }
    return result
  }

  private fun scanArchive(archive: File): List<RawScannedTexture> {
    val result = mutableListOf<RawScannedTexture>()

    // 1. Try ZipFile
    try {
      ZipFile(archive).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          val path = entry.name.replace('\\', '/')
          val fileName = path.substringAfterLast('/')
          if (!entry.isDirectory && RawTextureClassifier.isRawTextureFile(fileName)) {
            val preview = try {
              zip.getInputStream(entry).use { it.readNBytes(32) }
            } catch (_: Throwable) { null }
            result.add(
              RawScannedTexture(
                name = fileName,
                relativePath = path,
                sizeBytes = entry.size,
                previewBytes = preview
              )
            )
          }
        }
      }
      if (result.isNotEmpty()) return result
    } catch (_: Throwable) {}

    // 2. Try RAR5
    try {
      val rar5Entries = Rar5Reader.readEntries(archive)
      if (rar5Entries.isNotEmpty()) {
        for (e in rar5Entries) {
          if (!e.isDirectory && RawTextureClassifier.isRawTextureFile(e.name)) {
            result.add(
              RawScannedTexture(
                name = e.name,
                relativePath = e.fullPath,
                sizeBytes = e.unpackedSize,
                previewBytes = null
              )
            )
          }
        }
        if (result.isNotEmpty()) return result
      }
    } catch (_: Throwable) {}

    // 3. Try RAR4 (Junrar)
    try {
      RarArchive(archive).use { rar ->
        var header: FileHeader? = rar.nextFileHeader()
        while (header != null) {
          if (!header.isDirectory) {
            val fullPath = (header.fileNameW ?: header.fileNameString ?: "").replace('\\', '/')
            val fileName = fullPath.substringAfterLast('/')
            if (RawTextureClassifier.isRawTextureFile(fileName)) {
              result.add(
                RawScannedTexture(
                  name = fileName,
                  relativePath = fullPath,
                  sizeBytes = header.fullUnpackSize,
                  previewBytes = null
                )
              )
            }
          }
          header = rar.nextFileHeader()
        }
      }
      if (result.isNotEmpty()) return result
    } catch (_: Throwable) {}

    // 4. Try 7-Zip
    try {
      SevenZFile(archive).use { sz ->
        var entry = sz.nextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            val path = entry.name.replace('\\', '/')
            val fileName = path.substringAfterLast('/')
            if (RawTextureClassifier.isRawTextureFile(fileName)) {
              result.add(
                RawScannedTexture(
                  name = fileName,
                  relativePath = path,
                  sizeBytes = entry.size,
                  previewBytes = null
                )
              )
            }
          }
          entry = sz.nextEntry
        }
      }
    } catch (_: Throwable) {}

    return result
  }

  private fun scanDocumentTree(
    context: Context,
    dir: DocumentFile,
    currentPath: String,
    outList: MutableList<RawScannedTexture>,
    maxDepth: Int
  ) {
    if (maxDepth <= 0) return
    try {
      val files = dir.listFiles()
      for (f in files) {
        val name = f.name ?: continue
        val childPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
        if (f.isDirectory) {
          scanDocumentTree(context, f, childPath, outList, maxDepth - 1)
        } else if (RawTextureClassifier.isRawTextureFile(name)) {
          val preview = try {
            context.contentResolver.openInputStream(f.uri)?.use { it.readNBytes(32) }
          } catch (_: Throwable) { null }
          outList.add(
            RawScannedTexture(
              name = name,
              relativePath = childPath,
              sizeBytes = f.length(),
              previewBytes = preview
            )
          )
        }
      }
    } catch (_: Throwable) {}
  }

  private fun getKnownTextureNames(containerName: String, gameWorkingDir: File?): Set<String> {
    val results = mutableSetOf<String>()
    try {
      if (gameWorkingDir != null) {
        val manifestTxt = File(File(gameWorkingDir, containerName), "$containerName.txt")
        if (manifestTxt.exists()) {
          results.addAll(TxdArchiveReader.extractFromManifestTxt(manifestTxt).map { it.lowercase(Locale.ROOT) })
        }
        val tocFile = File(gameWorkingDir, "$containerName.toc")
        if (tocFile.exists()) {
          results.addAll(TxdArchiveReader.extractFromBinaryToc(tocFile).map { it.lowercase(Locale.ROOT) })
        }
      }
    } catch (_: Throwable) {}

    if (containerName.contains("int")) {
      results.addAll(TxdArchiveReader.DEFAULT_INTERIOR_TEXTURES.map { it.lowercase(Locale.ROOT) })
    } else {
      results.addAll(TxdArchiveReader.DEFAULT_EXTERIOR_TEXTURES.map { it.lowercase(Locale.ROOT) })
    }
    return results
  }

  /**
   * Helper to safely read n bytes from an InputStream across older and newer Android runtimes.
   */
  private fun InputStream.readNBytes(n: Int): ByteArray {
    val buf = ByteArray(n)
    var total = 0
    while (total < n) {
      val count = read(buf, total, n - total)
      if (count <= 0) break
      total += count
    }
    return if (total == n) buf else buf.copyOf(total)
  }

  private data class RawScannedTexture(
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val previewBytes: ByteArray?
  )
}
