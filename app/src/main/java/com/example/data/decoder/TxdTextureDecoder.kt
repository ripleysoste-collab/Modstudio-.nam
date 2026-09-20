package com.example.data.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.ETC1
import android.os.Environment
import com.example.data.parser.ImgArchiveReader
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile

/**
 * High-performance, authentic RenderWare texture decoder for GTA San Andreas mobile textures.
 * Performs deep extraction across:
 * 1. Loose texture assets (PNG, JPG, BMP, DDS)
 * 2. RenderWare .TXD archives (inside gta3.img, gta_int.img or loose in texdb)
 * 3. Mobile texdb cache (.dat + .toc + .txt)
 *
 * Strictly NO SIMULATION: if real binary texture bytes cannot be read,
 * returns null so the UI can honestly inform the user.
 */
object TxdTextureDecoder {

  data class TextureMeta(
    val name: String,
    val width: Int,
    val height: Int,
    val format: String,
    val hasAlpha: Boolean,
    val sourceFile: String,
    val sizeBytes: Long
  )

  data class DecodeResult(
    val bitmap: Bitmap?,
    val meta: TextureMeta,
    val isDecoded: Boolean
  )

  /**
   * Attempts to locate the binary texture within the texdb directory, img archives,
   * or obb/data folders, and decodes it into an authentic Android Bitmap.
   */
  fun decodeTexture(
    context: Context,
    textureName: String,
    texdbDir: File?,
    isInterior: Boolean = false
  ): DecodeResult {
    val cleanName = textureName.trim().removeSuffix(".png").removeSuffix(".bmp").removeSuffix(".jpg")
    val subDirName = if (isInterior) "gta_int" else "gta3"

    // Search candidate directories
    val storageRoot = Environment.getExternalStorageDirectory()
    val candidateDirs = mutableListOf<File>()

    // 1. App-specific copied textures folder
    if (texdbDir != null) {
      candidateDirs.add(File(texdbDir, subDirName))
      candidateDirs.add(texdbDir)
    }

    // 2. Direct external game data directory
    val gameTexdbDir = File(storageRoot, "Android/data/com.rockstargames.gtasa/files/texdb")
    candidateDirs.add(File(gameTexdbDir, subDirName))
    candidateDirs.add(gameTexdbDir)

    // 3. Search for loose images or .dat/.toc in candidate directories
    for (dir in candidateDirs) {
      if (dir.exists() && dir.isDirectory) {
        val extracted = scanAndExtractFromFolder(cleanName, dir, subDirName)
        if (extracted != null && extracted.bitmap != null) {
          return extracted
        }
      }
    }

    // 4. Search in containers (containers/gta3.img, containers/gta_int.img, or in Android/data, obb)
    val containerImg = findAndExtractFromImgContainers(context, cleanName, isInterior)
    if (containerImg != null && containerImg.bitmap != null) {
      return containerImg
    }

    // 5. Search inside OBB files directly if not yet copied to disk
    val obbResult = searchInsideObb(cleanName, subDirName)
    if (obbResult != null && obbResult.bitmap != null) {
      return obbResult
    }

    // Honest result: Texture is indexed in game catalog, but binary data is not available on disk
    return DecodeResult(
      bitmap = null,
      meta = TextureMeta(
        name = cleanName,
        width = 0,
        height = 0,
        format = "RenderWare (Sin extraer)",
        hasAlpha = false,
        sourceFile = "texdb/$subDirName",
        sizeBytes = 0L
      ),
      isDecoded = false
    )
  }

  private fun scanAndExtractFromFolder(
    textureName: String,
    folder: File,
    containerLabel: String
  ): DecodeResult? {
    val files = folder.listFiles() ?: return null

    // 1. Direct image files (PNG, JPG, WEBP, BMP)
    val imageFile = files.firstOrNull {
      it.isFile && it.nameWithoutExtension.equals(textureName, ignoreCase = true) &&
        (it.name.endsWith(".png", true) || it.name.endsWith(".jpg", true) || it.name.endsWith(".webp", true) || it.name.endsWith(".bmp", true))
    }
    if (imageFile != null) {
      try {
        val bmp = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bmp != null) {
          return DecodeResult(
            bitmap = bmp,
            meta = TextureMeta(
              name = textureName,
              width = bmp.width,
              height = bmp.height,
              format = "PNG/Bitmap",
              hasAlpha = bmp.hasAlpha(),
              sourceFile = "${containerLabel}/${imageFile.name}",
              sizeBytes = imageFile.length()
            ),
            isDecoded = true
          )
        }
      } catch (_: Exception) {}
    }

    // 2. Loose RenderWare .TXD file matching texture name
    val txdFile = files.firstOrNull {
      it.isFile && (it.nameWithoutExtension.equals(textureName, ignoreCase = true) || it.name.equals("$containerLabel.txd", ignoreCase = true)) &&
        it.name.endsWith(".txd", ignoreCase = true)
    }
    if (txdFile != null && txdFile.length() > 0) {
      try {
        val rawTxd = txdFile.readBytes()
        val parsed = parseRenderWareTxd(rawTxd, textureName)
        if (parsed != null) {
          return DecodeResult(
            bitmap = parsed.first,
            meta = parsed.second.copy(sourceFile = "${containerLabel}/${txdFile.name}"),
            isDecoded = true
          )
        }
      } catch (_: Exception) {}
    }

    // 3. Mobile texdb .dat + .toc archive pair
    val tocFiles = files.filter { it.name.endsWith(".toc", ignoreCase = true) }
    val datFiles = files.filter { it.name.endsWith(".dat", ignoreCase = true) }

    for (toc in tocFiles) {
      val baseName = toc.nameWithoutExtension.substringBeforeLast(".")
      val dat = datFiles.firstOrNull { it.nameWithoutExtension.startsWith(baseName) } ?: datFiles.firstOrNull()
      if (dat != null && dat.length() > 0) {
        val entry = findEntryInToc(toc, dat, textureName)
        if (entry != null) {
          try {
            val rawBytes = ByteArray(entry.size)
            RandomAccessFile(dat, "r").use { raf ->
              raf.seek(entry.offset)
              raf.readFully(rawBytes)
            }

            // Check if rawBytes is a standard DDS or PNG or raw DXT
            val decoded = decodeImageBytes(rawBytes, entry.width, entry.height, entry.format)
            if (decoded != null) {
              return DecodeResult(
                bitmap = decoded,
                meta = TextureMeta(
                  name = textureName,
                  width = decoded.width,
                  height = decoded.height,
                  format = entry.format,
                  hasAlpha = decoded.hasAlpha(),
                  sourceFile = "${containerLabel}/${dat.name}",
                  sizeBytes = entry.size.toLong()
                ),
                isDecoded = true
              )
            }
          } catch (_: Exception) {}
        }
      }
    }

    return null
  }

  private data class TocEntry(
    val name: String,
    val offset: Long,
    val size: Int,
    val width: Int,
    val height: Int,
    val format: String
  )

  /**
   * Scans a .toc file (either text or binary index) to locate offset and size in .dat
   */
  private fun findEntryInToc(tocFile: File, datFile: File, targetName: String): TocEntry? {
    val datLength = datFile.length()
    if (datLength <= 0) return null

    // A. Check if .toc is text-based (manifest style)
    try {
      tocFile.bufferedReader().useLines { lines ->
        for (line in lines) {
          val trimmed = line.trim()
          if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue
          if (trimmed.contains(targetName, ignoreCase = true)) {
            val parts = trimmed.split(Regex("[,\\s\\t=]+")).filter { it.isNotBlank() }
            val offset = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val size = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val width = parts.getOrNull(3)?.toIntOrNull() ?: 256
            val height = parts.getOrNull(4)?.toIntOrNull() ?: 256
            val format = parts.getOrNull(5) ?: "DXT1"
            if (size > 0 && offset + size <= datLength) {
              return TocEntry(targetName, offset, size, width, height, format)
            }
          }
        }
      }
    } catch (_: Exception) {}

    // B. Check binary .toc index
    try {
      val maxRead = minOf(tocFile.length(), 8 * 1024 * 1024L).toInt()
      val buffer = ByteArray(maxRead)
      FileInputStream(tocFile).use { it.read(buffer) }

      val nameBytes = targetName.toByteArray(Charsets.US_ASCII)
      val nameLen = nameBytes.size
      val lowerTarget = targetName.lowercase(Locale.ROOT)

      for (i in 0 until buffer.size - nameLen - 16) {
        // Fast case-insensitive match
        var match = true
        for (k in 0 until nameLen) {
          if (buffer[i + k].toInt().toChar().lowercaseChar() != lowerTarget[k]) {
            match = false
            break
          }
        }

        if (match) {
          val termByte = buffer[i + nameLen]
          if (termByte == 0.toByte() || termByte == ' '.code.toByte()) {
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            // Inspect 16 bytes after name boundary (32-byte fixed name slot)
            val offsetsToCheck = listOf(i + 32, i + nameLen + 1, i + nameLen + 4, i + 24)
            for (pos in offsetsToCheck) {
              if (pos + 8 <= buffer.size) {
                val off = bb.getInt(pos).toLong() and 0xFFFFFFFFL
                val sz = bb.getInt(pos + 4)
                if (off in 0 until datLength && sz in 16..(16 * 1024 * 1024) && off + sz <= datLength) {
                  val w = if (pos + 12 <= buffer.size) bb.getShort(pos + 8).toInt() and 0xFFFF else 256
                  val h = if (pos + 14 <= buffer.size) bb.getShort(pos + 10).toInt() and 0xFFFF else 256
                  return TocEntry(targetName, off, sz, if (w in 4..4096) w else 256, if (h in 4..4096) h else 256, "DXT1")
                }
              }
            }
          }
        }
      }
    } catch (_: Exception) {}

    return null
  }

  /**
   * Searches for textures embedded in RenderWare .TXD dictionaries inside .IMG containers.
   */
  private fun findAndExtractFromImgContainers(
    context: Context,
    textureName: String,
    isInterior: Boolean
  ): DecodeResult? {
    val containersDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "containers")
    val imgName = if (isInterior) "gta_int.img" else "gta3.img"
    val containerFile = File(containersDir, imgName)

    if (!containerFile.exists() || containerFile.length() < 2048) return null

    try {
      val rawEntries = ImgArchiveReader.readAllRawEntries(containerFile)
      val txdEntries = rawEntries.filter { it.name.endsWith(".txd", ignoreCase = true) }

      RandomAccessFile(containerFile, "r").use { raf ->
        for (txdEntry in txdEntries) {
          // If TXD name closely matches or is generic.txd
          val isCandidate = txdEntry.name.contains(textureName, ignoreCase = true) ||
            txdEntry.name.equals("generic.txd", ignoreCase = true) ||
            txdEntry.name.equals("vehicle.txd", ignoreCase = true)

          if (isCandidate && txdEntry.sizeBytes in 16..(32 * 1024 * 1024L)) {
            val txdBytes = ByteArray(txdEntry.sizeBytes.toInt())
            raf.seek(txdEntry.offsetSectors.toLong() * ImgArchiveReader.SECTOR_SIZE)
            raf.readFully(txdBytes)

            val parsed = parseRenderWareTxd(txdBytes, textureName)
            if (parsed != null) {
              return DecodeResult(
                bitmap = parsed.first,
                meta = parsed.second.copy(sourceFile = "$imgName / ${txdEntry.name}"),
                isDecoded = true
              )
            }
          }
        }
      }
    } catch (_: Exception) {}

    return null
  }

  /**
   * Searches directly inside OBB zip archives for the texture.
   */
  private fun searchInsideObb(textureName: String, subDirName: String): DecodeResult? {
    val storageRoot = Environment.getExternalStorageDirectory()
    val obbDir = File(storageRoot, "Android/obb/com.rockstargames.gtasa")
    if (!obbDir.exists() || !obbDir.canRead()) return null

    val obbFiles = obbDir.listFiles { _, name -> name.endsWith(".obb", ignoreCase = true) } ?: return null

    for (obb in obbFiles) {
      try {
        ZipFile(obb).use { zip ->
          val entries = zip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val lower = entry.name.lowercase(Locale.ROOT)

            if (lower.contains(textureName.lowercase(Locale.ROOT)) &&
              (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".bmp"))
            ) {
              val stream = zip.getInputStream(entry)
              val bmp = BitmapFactory.decodeStream(stream)
              if (bmp != null) {
                return DecodeResult(
                  bitmap = bmp,
                  meta = TextureMeta(
                    name = textureName,
                    width = bmp.width,
                    height = bmp.height,
                    format = "PNG/Texture (OBB)",
                    hasAlpha = bmp.hasAlpha(),
                    sourceFile = "${obb.name}/${entry.name}",
                    sizeBytes = entry.size
                  ),
                  isDecoded = true
                )
              }
            }
          }
        }
      } catch (_: Exception) {}
    }
    return null
  }

  /**
   * Parses a RenderWare stream containing rwID_TEXTURENATIVE (0x15) chunks
   * to extract and decode the texture.
   */
  fun parseRenderWareTxd(bytes: ByteArray, targetName: String): Pair<Bitmap, TextureMeta>? {
    if (bytes.size < 64) return null
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val nameBytes = targetName.toByteArray(Charsets.US_ASCII)
    val lowerTarget = targetName.lowercase(Locale.ROOT)

    for (i in 0 until bytes.size - 64) {
      var match = true
      for (k in nameBytes.indices) {
        if (bytes[i + k].toInt().toChar().lowercaseChar() != lowerTarget[k]) {
          match = false
          break
        }
      }

      if (match && (bytes[i + nameBytes.size] == 0.toByte() || bytes[i + nameBytes.size] == ' '.code.toByte())) {
        val formatOffset = i + 64
        if (formatOffset + 24 <= bytes.size) {
          try {
            val d3dFormat = bb.getInt(formatOffset + 4)
            val width = bb.getShort(formatOffset + 8).toInt() and 0xFFFF
            val height = bb.getShort(formatOffset + 10).toInt() and 0xFFFF
            val depth = bytes[formatOffset + 12].toInt() and 0xFF
            val dataSize = bb.getInt(formatOffset + 16)

            if (width in 4..4096 && height in 4..4096 && dataSize in 16..(16 * 1024 * 1024) && formatOffset + 20 + dataSize <= bytes.size) {
              val rawData = ByteArray(dataSize)
              System.arraycopy(bytes, formatOffset + 20, rawData, 0, dataSize)

              val formatStr = when (d3dFormat) {
                0x31545844 -> "DXT1"
                0x33545844 -> "DXT3"
                0x35545844 -> "DXT5"
                else -> if (depth == 32) "RGBA32" else "DXT1"
              }

              val decodedBmp = decodeImageBytes(rawData, width, height, formatStr)
              if (decodedBmp != null) {
                return Pair(
                  decodedBmp,
                  TextureMeta(
                    name = targetName,
                    width = width,
                    height = height,
                    format = formatStr,
                    hasAlpha = formatStr.contains("DXT5") || formatStr.contains("RGBA") || depth == 32,
                    sourceFile = "RenderWare .TXD",
                    sizeBytes = dataSize.toLong()
                  )
                )
              }
            }
          } catch (_: Exception) {}
        }
      }
    }
    return null
  }

  /**
   * Decodes image bytes whether they are wrapped with DDS header, PNG header, BMP header, or raw blocks.
   */
  fun decodeImageBytes(data: ByteArray, width: Int, height: Int, format: String): Bitmap? {
    if (data.isEmpty()) return null

    // 1. Direct BitmapFactory for PNG, JPG, BMP
    if (data.size >= 4 && (data[0] == 0x89.toByte() && data[1] == 0x50.toByte()) ||
      (data[0] == 'B'.code.toByte() && data[1] == 'M'.code.toByte())
    ) {
      try {
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
        if (bmp != null) return bmp
      } catch (_: Exception) {}
    }

    // 2. Microsoft DirectDraw Surface (DDS)
    if (data.size > 128 && data[0] == 'D'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == 'S'.code.toByte() && data[3] == ' '.code.toByte()) {
      val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
      val ddsHeight = bb.getInt(12)
      val ddsWidth = bb.getInt(16)
      val fourCC = bb.getInt(84)
      val pixelDataOffset = 128
      val rawPixels = ByteArray(data.size - pixelDataOffset)
      System.arraycopy(data, pixelDataOffset, rawPixels, 0, rawPixels.size)

      val ddsFormat = when (fourCC) {
        0x31545844 -> "DXT1"
        0x33545844 -> "DXT3"
        0x35545844 -> "DXT5"
        else -> "DXT1"
      }
      return decodeRawBlock(rawPixels, ddsWidth, ddsHeight, ddsFormat)
    }

    // 3. Raw blocks (DXT1, DXT5, ETC1, RGBA32)
    val effectiveW = if (width > 0) width else 256
    val effectiveH = if (height > 0) height else 256
    return decodeRawBlock(data, effectiveW, effectiveH, format)
  }

  private fun decodeRawBlock(data: ByteArray, width: Int, height: Int, format: String): Bitmap? {
    if (width <= 0 || height <= 0 || data.isEmpty()) return null
    return try {
      when {
        format.contains("DXT1", ignoreCase = true) -> decodeDxt1(data, width, height)
        format.contains("DXT5", ignoreCase = true) -> decodeDxt5(data, width, height)
        format.contains("ETC", ignoreCase = true) -> decodeEtc1(data, width, height)
        format.contains("RGBA", ignoreCase = true) || format.contains("32", ignoreCase = true) -> decodeRgba32(data, width, height)
        else -> decodeDxt1(data, width, height)
      }
    } catch (_: Exception) {
      null
    }
  }

  // --- DXT1 DECODER ---
  private fun decodeDxt1(data: ByteArray, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    var bufferOffset = 0
    val numBlocksX = (width + 3) / 4
    val numBlocksY = (height + 3) / 4

    for (by in 0 until numBlocksY) {
      for (bx in 0 until numBlocksX) {
        if (bufferOffset + 8 > data.size) break

        val c0 = (data[bufferOffset].toInt() and 0xFF) or ((data[bufferOffset + 1].toInt() and 0xFF) shl 8)
        val c1 = (data[bufferOffset + 2].toInt() and 0xFF) or ((data[bufferOffset + 3].toInt() and 0xFF) shl 8)
        bufferOffset += 4

        val colorTable = IntArray(4)
        colorTable[0] = rgb565ToArgb(c0)
        colorTable[1] = rgb565ToArgb(c1)

        val r0 = (c0 shr 11) and 0x1F
        val g0 = (c0 shr 5) and 0x3F
        val b0 = c0 and 0x1F

        val r1 = (c1 shr 11) and 0x1F
        val g1 = (c1 shr 5) and 0x3F
        val b1 = c1 and 0x1F

        if (c0 > c1) {
          colorTable[2] = Color.rgb((2 * r0 + r1) * 255 / (3 * 31), (2 * g0 + g1) * 255 / (3 * 63), (2 * b0 + b1) * 255 / (3 * 31))
          colorTable[3] = Color.rgb((r0 + 2 * r1) * 255 / (3 * 31), (g0 + 2 * g1) * 255 / (3 * 63), (b0 + 2 * b1) * 255 / (3 * 31))
        } else {
          colorTable[2] = Color.rgb((r0 + r1) * 255 / (2 * 31), (g0 + g1) * 255 / (2 * 63), (b0 + b1) * 255 / (2 * 31))
          colorTable[3] = Color.TRANSPARENT
        }

        var lookup = (data[bufferOffset].toInt() and 0xFF) or
          ((data[bufferOffset + 1].toInt() and 0xFF) shl 8) or
          ((data[bufferOffset + 2].toInt() and 0xFF) shl 16) or
          ((data[bufferOffset + 3].toInt() and 0xFF) shl 24)
        bufferOffset += 4

        for (py in 0 until 4) {
          for (px in 0 until 4) {
            val x = bx * 4 + px
            val y = by * 4 + py
            val code = lookup and 3
            lookup = lookup ushr 2
            if (x < width && y < height) {
              pixels[y * width + x] = colorTable[code]
            }
          }
        }
      }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }

  // --- DXT5 DECODER ---
  private fun decodeDxt5(data: ByteArray, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    var bufferOffset = 0
    val numBlocksX = (width + 3) / 4
    val numBlocksY = (height + 3) / 4

    for (by in 0 until numBlocksY) {
      for (bx in 0 until numBlocksX) {
        if (bufferOffset + 16 > data.size) break

        val a0 = data[bufferOffset].toInt() and 0xFF
        val a1 = data[bufferOffset + 1].toInt() and 0xFF
        val alphaTable = IntArray(8)
        alphaTable[0] = a0
        alphaTable[1] = a1

        if (a0 > a1) {
          for (i in 1..6) alphaTable[i + 1] = ((7 - i) * a0 + i * a1) / 7
        } else {
          for (i in 1..4) alphaTable[i + 1] = ((5 - i) * a0 + i * a1) / 5
          alphaTable[6] = 0
          alphaTable[7] = 255
        }

        var aBits = 0L
        for (i in 0 until 6) {
          aBits = aBits or ((data[bufferOffset + 2 + i].toLong() and 0xFFL) shl (i * 8))
        }
        bufferOffset += 8

        val c0 = (data[bufferOffset].toInt() and 0xFF) or ((data[bufferOffset + 1].toInt() and 0xFF) shl 8)
        val c1 = (data[bufferOffset + 2].toInt() and 0xFF) or ((data[bufferOffset + 3].toInt() and 0xFF) shl 8)
        bufferOffset += 4

        val colorTable = IntArray(4)
        colorTable[0] = rgb565ToArgb(c0)
        colorTable[1] = rgb565ToArgb(c1)

        val r0 = (c0 shr 11) and 0x1F
        val g0 = (c0 shr 5) and 0x3F
        val b0 = c0 and 0x1F

        val r1 = (c1 shr 11) and 0x1F
        val g1 = (c1 shr 5) and 0x3F
        val b1 = c1 and 0x1F

        colorTable[2] = Color.rgb((2 * r0 + r1) * 255 / (3 * 31), (2 * g0 + g1) * 255 / (3 * 63), (2 * b0 + b1) * 255 / (3 * 31))
        colorTable[3] = Color.rgb((r0 + 2 * r1) * 255 / (3 * 31), (g0 + 2 * g1) * 255 / (3 * 63), (b0 + 2 * b1) * 255 / (3 * 31))

        var lookup = (data[bufferOffset].toInt() and 0xFF) or
          ((data[bufferOffset + 1].toInt() and 0xFF) shl 8) or
          ((data[bufferOffset + 2].toInt() and 0xFF) shl 16) or
          ((data[bufferOffset + 3].toInt() and 0xFF) shl 24)
        bufferOffset += 4

        for (py in 0 until 4) {
          for (px in 0 until 4) {
            val x = bx * 4 + px
            val y = by * 4 + py
            val code = lookup and 3
            lookup = lookup ushr 2

            val alphaIdx = (aBits and 7L).toInt()
            aBits = aBits ushr 3

            if (x < width && y < height) {
              val baseColor = colorTable[code]
              val a = alphaTable[alphaIdx]
              pixels[y * width + x] = Color.argb(a, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            }
          }
        }
      }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }

  // --- ETC1 DECODER ---
  private fun decodeEtc1(data: ByteArray, width: Int, height: Int): Bitmap? {
    return try {
      val inBuffer = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
      inBuffer.put(data).position(0)

      val pixelSize = 2 // 565 format
      val stride = width * pixelSize
      val outBuffer = ByteBuffer.allocateDirect(height * stride).order(ByteOrder.nativeOrder())

      ETC1.decodeImage(inBuffer, outBuffer, width, height, pixelSize, stride)
      outBuffer.position(0)

      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
      bitmap.copyPixelsFromBuffer(outBuffer)
      bitmap
    } catch (_: Exception) {
      null
    }
  }

  // --- RGBA 32-BIT DECODER ---
  private fun decodeRgba32(data: ByteArray, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    var idx = 0
    for (i in pixels.indices) {
      if (idx + 4 <= data.size) {
        val r = data[idx].toInt() and 0xFF
        val g = data[idx + 1].toInt() and 0xFF
        val b = data[idx + 2].toInt() and 0xFF
        val a = data[idx + 3].toInt() and 0xFF
        pixels[i] = Color.argb(a, r, g, b)
        idx += 4
      }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }

  private fun rgb565ToArgb(c: Int): Int {
    val r = ((c shr 11) and 0x1F) * 255 / 31
    val g = ((c shr 5) and 0x3F) * 255 / 63
    val b = (c and 0x1F) * 255 / 31
    return Color.rgb(r, g, b)
  }
}
