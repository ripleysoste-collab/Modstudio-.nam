package com.example.data.analyzer

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * High-performance, crash-proof parser for modern RAR 5.0+ archive headers.
 * Reads file entries, paths, and unpacked sizes directly from archive headers
 * without decompressing or allocating large byte arrays.
 */
object Rar5Reader {

  data class Rar5Entry(
    val name: String,
    val fullPath: String,
    val unpackedSize: Long,
    val isDirectory: Boolean
  )

  // RAR 5.0 8-byte magic signature: 52 61 72 21 1A 07 01 00
  private val RAR5_MAGIC = byteArrayOf(
    0x52.toByte(), 0x61.toByte(), 0x72.toByte(), 0x21.toByte(),
    0x1A.toByte(), 0x07.toByte(), 0x01.toByte(), 0x00.toByte()
  )

  fun isRar5(file: File): Boolean {
    if (file.length() < 8) return false
    try {
      FileInputStream(file).use { fis ->
        val header = ByteArray(8)
        val read = fis.read(header)
        if (read < 8) return false
        return header.indices.all { header[it] == RAR5_MAGIC[it] }
      }
    } catch (_: Throwable) {
      return false
    }
  }

  fun readEntries(file: File): List<Rar5Entry> {
    val entries = mutableListOf<Rar5Entry>()
    try {
      FileInputStream(file).use { fis ->
        // 1. Verify 8-byte signature
        val header = ByteArray(8)
        if (fis.read(header) < 8 || !header.indices.all { header[it] == RAR5_MAGIC[it] }) {
          return emptyList()
        }

        // 2. Read blocks until EOF or EndArc
        while (true) {
          // Read 4 bytes CRC32
          val crcBytes = ByteArray(4)
          val crcRead = fis.read(crcBytes)
          if (crcRead < 4) break

          val (headerSize, _) = readVintWithLength(fis) ?: break
          if (headerSize <= 0) break

          // We will read `headerSize` bytes into a stream tracker or memory slice
          val headerContent = ByteArray(headerSize.toInt().coerceAtMost(65536))
          val hRead = fis.read(headerContent)
          if (hRead < headerContent.size) break

          var offset = 0

          fun readVintFromBuf(): Long? {
            var result = 0L
            var shift = 0
            while (offset < hRead) {
              val b = headerContent[offset++].toInt() and 0xFF
              result = result or ((b.toLong() and 0x7FL) shl shift)
              shift += 7
              if ((b and 0x80) == 0) return result
            }
            return null
          }

          val headerType = readVintFromBuf() ?: break
          val headerFlags = readVintFromBuf() ?: break

          val hasExtra = (headerFlags and 0x0001L) != 0L
          val hasData = (headerFlags and 0x0002L) != 0L

          if (hasExtra) {
            readVintFromBuf() // extra area size
          }

          val dataSize = if (hasData) {
            readVintFromBuf() ?: 0L
          } else {
            0L
          }

          // Header Type 2 = File or Service header
          // Header Type 5 = End of Archive
          if (headerType == 5L) {
            break
          }

          if (headerType == 2L) {
            val fileFlags = readVintFromBuf() ?: 0L
            val unpackedSize = readVintFromBuf() ?: 0L
            val isDir = (fileFlags and 0x0001L) != 0L

            readVintFromBuf() // attributes

            if ((fileFlags and 0x0002L) != 0L) {
              offset += 4 // mtime 4 bytes
            }
            if ((fileFlags and 0x0004L) != 0L) {
              offset += 4 // data CRC32 4 bytes
            }

            readVintFromBuf() // compression info
            readVintFromBuf() // host OS

            val nameLength = readVintFromBuf()?.toInt() ?: 0
            if (nameLength in 1..4096 && offset + nameLength <= hRead) {
              val nameBytes = headerContent.copyOfRange(offset, offset + nameLength)
              val path = String(nameBytes, StandardCharsets.UTF_8).replace('\\', '/')
              val cleanName = path.substringAfterLast('/')
              entries.add(
                Rar5Entry(
                  name = cleanName,
                  fullPath = path,
                  unpackedSize = unpackedSize,
                  isDirectory = isDir
                )
              )
            }
          }

          // Skip remainder of header bytes if header was larger than 65536
          val extraHeaderToSkip = headerSize - hRead
          if (extraHeaderToSkip > 0) {
            skipFully(fis, extraHeaderToSkip)
          }

          // Skip compressed data payload
          if (dataSize > 0) {
            skipFully(fis, dataSize)
          }
        }
      }
    } catch (_: Throwable) {
      // Graceful termination without crashing
    }

    return entries
  }

  private fun readVintWithLength(stream: InputStream): Pair<Long, Int>? {
    var result = 0L
    var shift = 0
    var count = 0
    while (count < 10) {
      val b = stream.read()
      if (b == -1) return null
      count++
      result = result or ((b.toLong() and 0x7FL) shl shift)
      shift += 7
      if ((b and 0x80) == 0) {
        return result to count
      }
    }
    return result to count
  }

  private fun skipFully(stream: InputStream, bytes: Long) {
    var remaining = bytes
    val skipBuf = ByteArray(8192)
    while (remaining > 0) {
      val toRead = remaining.coerceAtMost(skipBuf.size.toLong()).toInt()
      val read = stream.read(skipBuf, 0, toRead)
      if (read <= 0) break
      remaining -= read
    }
  }
}
