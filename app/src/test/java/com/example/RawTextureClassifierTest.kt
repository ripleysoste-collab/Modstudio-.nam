package com.example

import com.example.data.analyzer.ModTextureAnalyzer
import com.example.data.analyzer.RawTextureClassifier
import com.example.data.analyzer.TargetContainer
import com.example.data.analyzer.TextureMatchReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RawTextureClassifierTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testExplicitFolderExteriorAndInterior() {
    // 1. Interior folder
    val intEntry = RawTextureClassifier.classifyTexture(
      name = "furniture_couch.png",
      relativePath = "Casa_Mod/Interiores/With Alpha/furniture_couch.png",
      sizeBytes = 2048,
      dffExteriorTextures = emptySet(),
      dffInteriorTextures = emptySet()
    )
    assertEquals(TargetContainer.GTA_INT, intEntry.targetContainer)
    assertEquals(TextureMatchReason.FOLDER_EXPLICIT, intEntry.matchReason)
    assertTrue(intEntry.hasAlpha)
    assertEquals("With Alpha", intEntry.alphaFolderName)

    // 2. Exterior folder
    val extEntry = RawTextureClassifier.classifyTexture(
      name = "car_door.png",
      relativePath = "Car_Mod/gta3/Without Alpha/car_door.png",
      sizeBytes = 4096,
      dffExteriorTextures = emptySet(),
      dffInteriorTextures = emptySet()
    )
    assertEquals(TargetContainer.GTA3, extEntry.targetContainer)
    assertEquals(TextureMatchReason.FOLDER_EXPLICIT, extEntry.matchReason)
    assertFalse(extEntry.hasAlpha)
    assertEquals("Without Alpha", extEntry.alphaFolderName)
  }

  @Test
  fun testDffCrossReferenceLink() {
    // Texture name matches interior DFF material
    val intDffTextures = setOf("office_desk", "wood_floor")
    val extDffTextures = setOf("infernus_body", "wheel_rim")

    val intResult = RawTextureClassifier.classifyTexture(
      name = "wood_floor.png",
      relativePath = "textures/wood_floor.png",
      sizeBytes = 1024,
      dffExteriorTextures = extDffTextures,
      dffInteriorTextures = intDffTextures
    )
    assertEquals(TargetContainer.GTA_INT, intResult.targetContainer)
    assertEquals(TextureMatchReason.DFF_MODEL_LINK, intResult.matchReason)

    val extResult = RawTextureClassifier.classifyTexture(
      name = "infernus_body.png",
      relativePath = "textures/infernus_body.png",
      sizeBytes = 8192,
      dffExteriorTextures = extDffTextures,
      dffInteriorTextures = intDffTextures
    )
    assertEquals(TargetContainer.GTA3, extResult.targetContainer)
    assertEquals(TextureMatchReason.DFF_MODEL_LINK, extResult.matchReason)
  }

  @Test
  fun testSemanticNameTokens() {
    // Texture with "int_" or "cj_" prefix without folder indicator
    val intTokenResult = RawTextureClassifier.classifyTexture(
      name = "int_sofa_red.png",
      relativePath = "sofa_red.png",
      sizeBytes = 512,
      dffExteriorTextures = emptySet(),
      dffInteriorTextures = emptySet()
    )
    assertEquals(TargetContainer.GTA_INT, intTokenResult.targetContainer)
    assertEquals(TextureMatchReason.TEXTURE_NAME_SEMANTICS, intTokenResult.matchReason)

    // Exterior vehicle/road token
    val vehTokenResult = RawTextureClassifier.classifyTexture(
      name = "veh_headlight.png",
      relativePath = "veh_headlight.png",
      sizeBytes = 512,
      dffExteriorTextures = emptySet(),
      dffInteriorTextures = emptySet()
    )
    assertEquals(TargetContainer.GTA3, vehTokenResult.targetContainer)
    assertEquals(TextureMatchReason.TEXTURE_NAME_SEMANTICS, vehTokenResult.matchReason)
  }

  @Test
  fun testDefaultExteriorProbability() {
    // Ambiguous texture with no hints
    val result = RawTextureClassifier.classifyTexture(
      name = "custom_decal_xyz.png",
      relativePath = "custom_decal_xyz.png",
      sizeBytes = 1024,
      dffExteriorTextures = emptySet(),
      dffInteriorTextures = emptySet()
    )
    assertEquals(TargetContainer.GTA3, result.targetContainer)
    assertEquals(TextureMatchReason.DEFAULT_EXTERIOR_PROBABILITY, result.matchReason)
  }

  @Test
  fun testArchiveDeepScanning() = runBlocking {
    val zipFile = tempFolder.newFile("SampleMod.zip")
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
      // Interior texture
      zos.putNextEntry(ZipEntry("Interior/With Alpha/wardrobe.png"))
      zos.write(ByteArray(64))
      zos.closeEntry()

      // Exterior texture
      zos.putNextEntry(ZipEntry("Exterior/Without Alpha/tarmac.png"))
      zos.write(ByteArray(128))
      zos.closeEntry()

      // Non-texture file (should be ignored)
      zos.putNextEntry(ZipEntry("readme.txt"))
      zos.write("install guide".toByteArray())
      zos.closeEntry()
    }

    val entries = ModTextureAnalyzer.analyzeFromFile(zipFile, "SampleMod.zip")
    assertEquals(2, entries.size)

    val intEntry = entries.first { it.name == "wardrobe.png" }
    assertEquals(TargetContainer.GTA_INT, intEntry.targetContainer)
    assertTrue(intEntry.hasAlpha)

    val extEntry = entries.first { it.name == "tarmac.png" }
    assertEquals(TargetContainer.GTA3, extEntry.targetContainer)
    assertFalse(extEntry.hasAlpha)
  }
}
