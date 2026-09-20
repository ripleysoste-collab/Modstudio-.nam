package com.example.data.parser

import com.example.data.analyzer.DffActionType
import com.example.data.analyzer.DffMatchItem
import com.example.data.analyzer.MatchPlan
import com.example.data.analyzer.TargetContainer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result report for an IMG container rebuild operation.
 */
data class ContainerRebuildResult(
  val success: Boolean,
  val containerFileName: String,
  val totalEntries: Int,
  val replacedEntries: Int,
  val injectedEntries: Int,
  val finalSizeBytes: Long,
  val errorMessage: String? = null
)

/**
 * Millimeter-accurate IMG v2 (VER2) Builder and Reconstructor for GTA San Andreas (Android / PC).
 *
 * CRITICAL ENGINE REQUIREMENTS:
 * 1. MUST preserve EVERY single entry in the container (DFF, TXD, COL, IPL, IFP, SCM, etc.).
 * 2. Header format:
 *    - 4 bytes: "VER2" (ASCII)
 *    - 4 bytes: Int32 Little-Endian entry count
 *    - N * 32 bytes:
 *        * offsetInSectors: UInt32 LE
 *        * streamingSize: UInt16 LE
 *        * streamingSize2: UInt16 LE (0x0000)
 *        * name: 24 bytes ASCII null-padded (0x00)
 * 3. Header Sector Padding:
 *    - The header table MUST be zero-padded until the full 2048-byte sector boundary.
 * 4. Data Offsets:
 *    - The first entry data begins exactly at sector (totalHeaderBytes / 2048).
 *    - Every file data is padded with 0x00 to complete 2048-byte sector alignment.
 */
object ImgArchiveWriter {

  const val SECTOR_SIZE = 2048
  const val ENTRY_SIZE = 32

  suspend fun rebuildContainer(
    baseImgFile: File,
    targetContainer: TargetContainer,
    matchPlan: MatchPlan,
    dffBytesProvider: suspend (fileName: String) -> ByteArray?,
    onProgress: (stepMessage: String, progressRatio: Float) -> Unit = { _, _ -> }
  ): ContainerRebuildResult = withContext(Dispatchers.IO) {
    if (!baseImgFile.exists() || baseImgFile.length() < 8) {
      return@withContext ContainerRebuildResult(
        success = false,
        containerFileName = baseImgFile.name,
        totalEntries = 0,
        replacedEntries = 0,
        injectedEntries = 0,
        finalSizeBytes = 0L,
        errorMessage = "El contenedor ${baseImgFile.name} no existe o no es válido"
      )
    }

    val containerItems = matchPlan.items.filter { it.targetContainer == targetContainer }
    if (containerItems.isEmpty()) {
      return@withContext ContainerRebuildResult(
        success = true,
        containerFileName = baseImgFile.name,
        totalEntries = 0,
        replacedEntries = 0,
        injectedEntries = 0,
        finalSizeBytes = baseImgFile.length()
      )
    }

    val replacementMap = containerItems
      .filter { it.actionType == DffActionType.REPLACE }
      .associateBy { it.fileName.lowercase(Locale.ROOT) }

    val injectionItems = containerItems
      .filter { it.actionType == DffActionType.INJECT }

    onProgress("Leyendo todas las entradas de ${baseImgFile.name}...", 0.05f)

    // 1. Read ALL raw entries (DFF, TXD, COL, etc.)
    val allOriginalEntries = ImgArchiveReader.readAllRawEntries(baseImgFile)
    if (allOriginalEntries.isEmpty()) {
      return@withContext ContainerRebuildResult(
        success = false,
        containerFileName = baseImgFile.name,
        totalEntries = 0,
        replacedEntries = 0,
        injectedEntries = 0,
        finalSizeBytes = 0L,
        errorMessage = "No se pudieron leer las entradas de ${baseImgFile.name}"
      )
    }

    class OutputEntryDef(
      val name: String,
      var offsetSectors: Int = 0,
      var sizeSectors: Int = 0,
      val isNewDff: Boolean = false,
      val originalOffsetSectors: Int = 0,
      val originalSizeSectors: Int = 0
    )

    val outputList = ArrayList<OutputEntryDef>(allOriginalEntries.size + injectionItems.size)

    for (orig in allOriginalEntries) {
      val lowerName = orig.name.lowercase(Locale.ROOT)
      val isReplaced = lowerName in replacementMap
      val origSectors = if (orig.streamingSizeSectors > 0) orig.streamingSizeSectors else orig.size2Sectors
      outputList.add(
        OutputEntryDef(
          name = orig.name,
          isNewDff = isReplaced,
          originalOffsetSectors = orig.offsetSectors,
          originalSizeSectors = origSectors
        )
      )
    }

    for (inj in injectionItems) {
      outputList.add(
        OutputEntryDef(
          name = inj.fileName,
          isNewDff = true,
          originalOffsetSectors = 0,
          originalSizeSectors = 0
        )
      )
    }

    val totalEntries = outputList.size
    val rawHeaderSize = 8 + (totalEntries * ENTRY_SIZE)
    val headerSectors = (rawHeaderSize + SECTOR_SIZE - 1) / SECTOR_SIZE
    val totalHeaderSizeBytes = headerSectors * SECTOR_SIZE

    onProgress("Reconstruyendo sectores y alineación en ${baseImgFile.name}...", 0.15f)

    val tempFile = File(baseImgFile.parentFile ?: File("."), "${baseImgFile.name}.tmp")
    if (tempFile.exists()) tempFile.delete()

    var replacedCount = 0
    var injectedCount = 0
    var currentSector = headerSectors

    try {
      RandomAccessFile(tempFile, "rw").use { outRaf ->
        // Reserve space for header
        outRaf.seek(totalHeaderSizeBytes.toLong())

        RandomAccessFile(baseImgFile, "r").use { origRaf ->
          val copyBuffer = ByteArray(64 * 1024)
          val zeroPadding = ByteArray(SECTOR_SIZE)

          for ((index, item) in outputList.withIndex()) {
            if (index % 400 == 0) {
              val prog = 0.2f + (0.65f * (index.toFloat() / totalEntries.toFloat()))
              onProgress("Escribiendo ${item.name} ($index de $totalEntries)...", prog)
            }

            item.offsetSectors = currentSector

            if (item.isNewDff) {
              val dffBytes = dffBytesProvider(item.name)
              if (dffBytes != null && dffBytes.isNotEmpty()) {
                val sectorsNeeded = (dffBytes.size + SECTOR_SIZE - 1) / SECTOR_SIZE
                item.sizeSectors = sectorsNeeded

                outRaf.write(dffBytes)

                val remainder = dffBytes.size % SECTOR_SIZE
                if (remainder > 0) {
                  val paddingLen = SECTOR_SIZE - remainder
                  outRaf.write(zeroPadding, 0, paddingLen)
                }

                currentSector += sectorsNeeded
                if (item.originalOffsetSectors > 0) {
                  replacedCount++
                } else {
                  injectedCount++
                }
              } else {
                // Fallback copy original verbatim
                val origOffsetBytes = item.originalOffsetSectors.toLong() * SECTOR_SIZE
                val origSectors = item.originalSizeSectors
                val origSizeBytes = origSectors.toLong() * SECTOR_SIZE
                item.sizeSectors = origSectors

                origRaf.seek(origOffsetBytes)
                var bytesLeft = origSizeBytes
                while (bytesLeft > 0) {
                  val toRead = minOf(copyBuffer.size.toLong(), bytesLeft).toInt()
                  val read = origRaf.read(copyBuffer, 0, toRead)
                  if (read <= 0) break
                  outRaf.write(copyBuffer, 0, read)
                  bytesLeft -= read
                }
                currentSector += origSectors
              }
            } else {
              // Copy unchanged entry verbatim (all original sectors including textures/col)
              val origOffsetBytes = item.originalOffsetSectors.toLong() * SECTOR_SIZE
              val origSectors = item.originalSizeSectors
              val origSizeBytes = origSectors.toLong() * SECTOR_SIZE
              item.sizeSectors = origSectors

              origRaf.seek(origOffsetBytes)
              var bytesLeft = origSizeBytes
              while (bytesLeft > 0) {
                val toRead = minOf(copyBuffer.size.toLong(), bytesLeft).toInt()
                val read = origRaf.read(copyBuffer, 0, toRead)
                if (read <= 0) break
                outRaf.write(copyBuffer, 0, read)
                bytesLeft -= read
              }
              currentSector += origSectors
            }
          }
        }

        onProgress("Escribiendo tabla de directorios (TOC) y firma VER2...", 0.90f)

        // 3. Write TOC at offset 0
        outRaf.seek(0)
        val headerBuffer = ByteBuffer.allocate(totalHeaderSizeBytes)
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // Magic "VER2"
        headerBuffer.put('V'.code.toByte())
        headerBuffer.put('E'.code.toByte())
        headerBuffer.put('R'.code.toByte())
        headerBuffer.put('2'.code.toByte())

        // Entry count
        headerBuffer.putInt(totalEntries)

        // Write entries
        for (item in outputList) {
          headerBuffer.putInt(item.offsetSectors)
          headerBuffer.putShort((item.sizeSectors and 0xFFFF).toShort())
          headerBuffer.putShort(0.toShort()) // 0x0000

          val nameBytes = item.name.toByteArray(Charsets.US_ASCII)
          val namePadded = ByteArray(24)
          val copyLen = minOf(nameBytes.size, 23)
          System.arraycopy(nameBytes, 0, namePadded, 0, copyLen)
          headerBuffer.put(namePadded)
        }

        outRaf.write(headerBuffer.array(), 0, totalHeaderSizeBytes)
        outRaf.fd.sync()
      }

      onProgress("Cerrando y reemplazando contenedor...", 0.98f)

      // 4. Safe replacement
      val backupFile = File(baseImgFile.parentFile ?: File("."), "${baseImgFile.name}.bak")
      if (baseImgFile.exists()) {
        if (!backupFile.exists()) {
          baseImgFile.copyTo(backupFile, overwrite = true)
        }
        baseImgFile.delete()
      }

      val replaced = tempFile.renameTo(baseImgFile)
      if (!replaced) {
        tempFile.inputStream().use { input ->
          baseImgFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
        tempFile.delete()
      }

      onProgress("Reconstrucción completada con éxito", 1.0f)

      return@withContext ContainerRebuildResult(
        success = true,
        containerFileName = baseImgFile.name,
        totalEntries = totalEntries,
        replacedEntries = replacedCount,
        injectedEntries = injectedCount,
        finalSizeBytes = baseImgFile.length()
      )
    } catch (e: Throwable) {
      e.printStackTrace()
      if (tempFile.exists()) tempFile.delete()
      return@withContext ContainerRebuildResult(
        success = false,
        containerFileName = baseImgFile.name,
        totalEntries = 0,
        replacedEntries = 0,
        injectedEntries = 0,
        finalSizeBytes = 0L,
        errorMessage = e.localizedMessage ?: "Error desconocido durante la reconstrucción"
      )
    }
  }
}
