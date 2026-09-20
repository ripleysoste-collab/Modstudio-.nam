package com.example.data.analyzer

/**
 * An individual script (.csa, .csi, .fxt) discovered and installed into the game.
 */
data class ScriptInstallItem(
  val name: String,
  val type: String, // "CSA", "CSI", "FXT"
  val sizeBytes: Long,
  val deployedPath: String
)

/**
 * Consolidated summary of the mod implementation process (Phase 0.3):
 * - DFF models replaced and injected into GTA3 / GTA_INT containers, deployed to files/texdb/
 * - CLEO scripts (.csa, .csi, .fxt) discovered in folders/subfolders and deployed to Android/data/com.rockstargames.gtasa/
 * - Clean status reporting for the user interface
 */
data class ModImplementationSummary(
  val success: Boolean,
  val modName: String,
  val dffReplacedCount: Int,
  val dffInjectedCount: Int,
  val totalDffCount: Int,
  val affectsGta3: Boolean,
  val affectsGtaInt: Boolean,
  val dffContainerPath: String = "Android/data/com.rockstargames.gtasa/files/texdb/",
  val scriptsFound: List<ScriptInstallItem> = emptyList(),
  val scriptsSearched: Boolean = true,
  val scriptsDeployedPath: String = "Android/data/com.rockstargames.gtasa/",
  val rawTexturesFound: List<RawTextureEntry> = emptyList(),
  val rawTexturesExteriorCount: Int = 0,
  val rawTexturesInteriorCount: Int = 0,
  val rawTexturesWithAlphaCount: Int = 0,
  val rawTexturesWithoutAlphaCount: Int = 0,
  val texturesDeployedPath: String = "Android/data/com.rockstargames.gtasa/files/texdb/",
  val errorMessage: String? = null
)
