package com.example.data.parser

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * High-performance parser to extract individual texture names from GTA San Andreas Android
 * texture files:
 * - gta3.txt / gta_int.txt manifests
 * - Binary .toc (Table of Contents) files
 * - RenderWare .txd texture dictionaries
 */
object TxdArchiveReader {

  /**
   * Extracts texture names from a folder containing texture files (.txt, .toc, .dat, .txd).
   */
  fun extractTextureNamesFromDir(dir: File, fallbackList: List<String> = emptyList()): List<String> {
    if (!dir.exists() || !dir.isDirectory) {
      return fallbackList
    }

    val results = linkedSetOf<String>()

    // 1. Prioritize manifest .txt files (gta3.txt, gta_int.txt)
    val txtFiles = dir.listFiles { _, name -> name.endsWith(".txt", ignoreCase = true) } ?: emptyArray()
    for (txtFile in txtFiles) {
      val names = extractFromManifestTxt(txtFile)
      results.addAll(names)
    }

    // 2. If no textures found from txt, inspect binary .toc files
    if (results.isEmpty()) {
      val tocFiles = dir.listFiles { _, name -> name.endsWith(".toc", ignoreCase = true) } ?: emptyArray()
      for (tocFile in tocFiles) {
        val names = extractFromBinaryToc(tocFile)
        results.addAll(names)
      }
    }

    // 3. Inspect loose .txd files
    val txdFiles = dir.listFiles { _, name -> name.endsWith(".txd", ignoreCase = true) } ?: emptyArray()
    for (txd in txdFiles) {
      val baseName = txd.nameWithoutExtension
      if (baseName.isNotBlank()) {
        results.add(baseName)
      }
    }

    // 4. Inspect .dat or .tmb files for embedded null-terminated texture names
    if (results.isEmpty()) {
      val datFiles = dir.listFiles { _, name -> name.endsWith(".dat", ignoreCase = true) } ?: emptyArray()
      for (dat in datFiles) {
        val names = extractFromBinaryToc(dat)
        results.addAll(names)
      }
    }

    return if (results.isNotEmpty()) results.toList() else fallbackList
  }

  /**
   * Parses texture entries from gta3.txt / gta_int.txt:
   * e.g. "barbersflr1_LA" width=128 height=128 png=...
   * or "arrow" width=64 height=64 ...
   */
  fun extractFromManifestTxt(file: File): List<String> {
    if (!file.exists() || file.length() == 0L) return emptyList()

    val textures = linkedSetOf<String>()
    try {
      file.bufferedReader().useLines { lines ->
        for (rawLine in lines) {
          val line = rawLine.trim()
          if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue

          val name = if (line.startsWith("\"")) {
            line.substringAfter("\"").substringBefore("\"").trim()
          } else {
            // First token before space or =
            line.split(Regex("[\\s=]")).firstOrNull()?.trim() ?: ""
          }

          if (isValidTextureName(name)) {
            textures.add(name)
          }
        }
      }
    } catch (_: Exception) {}

    return textures.toList()
  }

  /**
   * Scans a binary .toc / table-of-contents file to extract texture entry names.
   */
  fun extractFromBinaryToc(file: File): List<String> {
    if (!file.exists() || file.length() < 16) return emptyList()

    val textures = linkedSetOf<String>()
    try {
      val maxBytesToRead = minOf(file.length(), 4 * 1024 * 1024L).toInt()
      val buffer = ByteArray(maxBytesToRead)
      FileInputStream(file).use { input ->
        input.read(buffer)
      }

      val sb = StringBuilder()
      for (b in buffer) {
        val ch = b.toInt().toChar()
        if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_' || ch == '-' || ch == '$' || ch == '#') {
          sb.append(ch)
        } else {
          if (sb.length in 3..32) {
            val candidate = sb.toString()
            if (isValidTextureName(candidate)) {
              textures.add(candidate)
            }
          }
          sb.setLength(0)
        }
      }
    } catch (_: Exception) {}

    return textures.toList()
  }

  private fun isValidTextureName(name: String): Boolean {
    if (name.length < 2 || name.length > 40) return false
    val lower = name.lowercase(Locale.ROOT)
    if (lower == "width" || lower == "height" || lower == "png" || lower == "img" || lower == "alphamode") return false
    if (lower.startsWith("0x")) return false
    return name.any { it in 'a'..'z' || it in 'A'..'Z' }
  }

  /**
   * Default exterior textures (GTA3) representative of GTA San Andreas outdoor world.
   */
  val DEFAULT_EXTERIOR_TEXTURES = listOf(
    "radar00", "radar01", "radar02", "radar03", "radar04", "radar05", "radar06", "radar07",
    "radar_centre", "radar_ringplane", "radar_waypoint", "radar_north",
    "asphalt_law", "asphalt_sfse", "asphalt_desert", "roadlanedbl", "roadlanesng",
    "grass_patch", "grasstype1", "grasstype2", "grasstype4",
    "pave_curb", "pavement_dirt", "sidewalk1", "sidewalk2", "concrete_barrier",
    "sand_beach", "waterclear256", "vehiclelights128", "plateback1", "wheel_rim",
    "cj_ped_head", "cj_ped_torso", "cj_ped_legs", "tree_leaves1", "palm_leaf",
    "lamppost_glow", "trafficlight", "billboard_sprunk", "billboard_cluckin"
  )

  /**
   * Default interior textures (GTA_INT) representative of GTA San Andreas building interiors.
   */
  val DEFAULT_INTERIOR_TEXTURES = listOf(
    "interior_wall01", "interior_wall02", "woodfloor01", "woodfloor02",
    "casino_carpet01", "casino_carpet02", "barbersflr1_LA", "BistroMenu", "Cutlery",
    "kitchen_tile", "bathroom_marble", "door_wood_int", "counter_table",
    "shelf_bottles", "tv_screen", "arcade_machine", "safe_metal",
    "furniture_leather", "armchair_fabric", "bed_sheet_blue", "pooltable_felt",
    "gym_mat", "tattoo_poster", "painting_classic", "ceiling_lamp",
    "elevator_door", "curtain_red", "snack_vendor"
  )
}
