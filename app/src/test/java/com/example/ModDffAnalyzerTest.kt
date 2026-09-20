package com.example

import com.example.data.analyzer.MatchReason
import com.example.data.analyzer.ModDffAnalyzer
import com.example.data.analyzer.TargetContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModDffAnalyzerTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testCaseA_foldersWithExplicitInteriorAndExterior() = runBlocking {
    // Mimicking user's "Casa de Michael" mod:
    // contains "Replasar en Gta3.img" with dffs and "Replasar en Gta_int.img" with dffs
    val modDir = tempFolder.newFolder("CasaDeMichael")
    val gta3Dir = File(modDir, "Replasar en Gta3.img").apply { mkdirs() }
    val gtaIntDir = File(modDir, "Replasar en Gta_int.img").apply { mkdirs() }

    File(gta3Dir, "michael_exterior.dff").writeBytes(ByteArray(1024))
    File(gta3Dir, "countrys_7.col").writeBytes(ByteArray(512)) // Should be ignored!
    File(gtaIntDir, "michael_interior.dff").writeBytes(ByteArray(2048))

    val result = ModDffAnalyzer.analyzeFromFile(modDir)

    assertEquals(2, result.totalDffFound)
    assertEquals(1, result.ignoredNonDffCount) // col ignored
    assertEquals(1, result.gta3Entries.size)
    assertEquals("michael_exterior.dff", result.gta3Entries[0].name)
    assertEquals(TargetContainer.GTA3, result.gta3Entries[0].targetContainer)
    assertEquals(MatchReason.FOLDER_EXPLICIT, result.gta3Entries[0].matchReason)

    assertEquals(1, result.gtaIntEntries.size)
    assertEquals("michael_interior.dff", result.gtaIntEntries[0].name)
    assertEquals(TargetContainer.GTA_INT, result.gtaIntEntries[0].targetContainer)
    assertEquals(MatchReason.FOLDER_EXPLICIT, result.gtaIntEntries[0].matchReason)
  }

  @Test
  fun testCaseC_containerVerificationWithWeaponsPack() = runBlocking {
    // Mimicking user's "Armas De GTA SA Android.zip":
    // folder "ARMAS DFF NO INSTALADAS" with bat.dff, ak47.dff, etc.
    val zipFile = tempFolder.newFile("ArmasMod.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      // Non-DFF files that must be ignored
      zos.putNextEntry(ZipEntry("ARMAS DFF NO INSTALADAS/readme.txt"))
      zos.write("readme content".toByteArray())
      zos.closeEntry()

      // DFF files that match gta3.img
      zos.putNextEntry(ZipEntry("ARMAS DFF NO INSTALADAS/bat.dff"))
      zos.write(ByteArray(20_000))
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("ARMAS DFF NO INSTALADAS/ak47.dff"))
      zos.write(ByteArray(140_000))
      zos.closeEntry()
    }

    val result = ModDffAnalyzer.analyzeFromFile(zipFile)

    assertEquals(2, result.totalDffFound)
    assertEquals(1, result.ignoredNonDffCount) // readme ignored
    assertEquals(2, result.gta3Entries.size)
    assertTrue(result.gtaIntEntries.isEmpty())

    val batEntry = result.gta3Entries.first { it.name == "bat.dff" }
    assertEquals(TargetContainer.GTA3, batEntry.targetContainer)
    assertEquals(MatchReason.CONTAINER_MATCH, batEntry.matchReason)
  }

  @Test
  fun testIgnoresNonDffAssetsStrictly() = runBlocking {
    val modDir = tempFolder.newFolder("ModWithMultipleAssets")
    File(modDir, "car.dff").writeBytes(ByteArray(500))
    File(modDir, "car.txd").writeBytes(ByteArray(800))
    File(modDir, "car.col").writeBytes(ByteArray(300))
    File(modDir, "script.csa").writeBytes(ByteArray(100))

    val result = ModDffAnalyzer.analyzeFromFile(modDir)
    assertEquals(1, result.totalDffFound)
    assertEquals(3, result.ignoredNonDffCount)
    assertEquals("car.dff", result.gta3Entries[0].name)
  }

  @Test
  fun testDeepNestedSubfoldersDownToTheBottom() = runBlocking {
    // 6 levels deep subfolder hierarchy
    val modDir = tempFolder.newFolder("VeryDeepMod")
    val deepDir = File(modDir, "Level1/Level2/Level3/Level4/Level5/Models").apply { mkdirs() }

    File(deepDir, "infernus.dff").writeBytes(ByteArray(2048))
    File(deepDir, "tuning.col").writeBytes(ByteArray(1024))
    File(deepDir, "textures.txd").writeBytes(ByteArray(4096))

    val result = ModDffAnalyzer.analyzeFromFile(modDir)

    assertEquals(1, result.totalDffFound)
    assertEquals(2, result.ignoredNonDffCount)
    assertEquals("infernus.dff", result.gta3Entries[0].name)
    assertEquals(TargetContainer.GTA3, result.gta3Entries[0].targetContainer)
  }

  @Test
  fun testNestedArchiveInsideArchive() = runBlocking {
    // Main zip containing a nested inner zip with dff models
    val innerZipFile = File(tempFolder.root, "inner_weapons.zip")
    ZipOutputStream(FileOutputStream(innerZipFile)).use { innerZos ->
      innerZos.putNextEntry(ZipEntry("weapons/ak47.dff"))
      innerZos.write(ByteArray(5000))
      innerZos.closeEntry()
    }

    val outerZipFile = tempFolder.newFile("MasterPack.zip")
    ZipOutputStream(FileOutputStream(outerZipFile)).use { outerZos ->
      outerZos.putNextEntry(ZipEntry("packs/inner_weapons.zip"))
      outerZos.write(innerZipFile.readBytes())
      outerZos.closeEntry()

      outerZos.putNextEntry(ZipEntry("packs/readme.pdf"))
      outerZos.write("info".toByteArray())
      outerZos.closeEntry()
    }

    val result = ModDffAnalyzer.analyzeFromFile(outerZipFile)

    assertEquals(1, result.totalDffFound)
    assertEquals("ak47.dff", result.gta3Entries[0].name)
    assertEquals(TargetContainer.GTA3, result.gta3Entries[0].targetContainer)
  }

  @Test
  fun testDeepInteriorPathIntentInheritance() = runBlocking {
    // Explicit interior container at root, but dff is nested 3 levels inside
    val modDir = tempFolder.newFolder("InteriorPack")
    val targetDir = File(modDir, "Replasar en Gta_int.img/Habitacion/Muebles").apply { mkdirs() }
    File(targetDir, "cama.dff").writeBytes(ByteArray(1500))

    val result = ModDffAnalyzer.analyzeFromFile(modDir)

    assertEquals(1, result.totalDffFound)
    assertEquals(1, result.gtaIntEntries.size)
    assertEquals("cama.dff", result.gtaIntEntries[0].name)
    assertEquals(TargetContainer.GTA_INT, result.gtaIntEntries[0].targetContainer)
    assertEquals(MatchReason.FOLDER_EXPLICIT, result.gtaIntEntries[0].matchReason)
  }

  @Test
  fun testCorruptedOrEmptyArchiveDoesNotCrash() = runBlocking {
    // Malformed archive with random corrupt bytes
    val corruptZip = tempFolder.newFile("corrupt.zip")
    corruptZip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x99.toByte(), 0xFF.toByte(), 0x12))

    val result = ModDffAnalyzer.analyzeFromFile(corruptZip)
    assertEquals(0, result.totalDffFound)

    // Malformed rar
    val corruptRar = tempFolder.newFile("corrupt.rar")
    corruptRar.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x12, 0x34))
    val resultRar = ModDffAnalyzer.analyzeFromFile(corruptRar)
    assertEquals(0, resultRar.totalDffFound)
  }
}
