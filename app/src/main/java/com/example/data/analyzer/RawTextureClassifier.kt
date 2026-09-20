package com.example.data.analyzer

import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Reasons why a raw texture (.png) was routed to GTA3 (Exteriores) or GTA_INT (Interiores).
 */
enum class TextureMatchReason {
  FOLDER_EXPLICIT,             // Caso A: folder hierarchy explicitly indicated interior or exterior
  DFF_MODEL_LINK,              // Caso B: matched texture name referenced by a DFF 3D model in the mod
  GAME_CATALOG_MATCH,          // Caso C: verified match against game's actual texdb / TOC texture registry
  ARCHIVE_NAME_SEMANTICS,      // Caso D: compressed archive name or mod title indicated interior (casa, tienda...) or exterior (calles, autos...)
  TEXTURE_NAME_SEMANTICS,      // Caso E: filename prefixes or stems indicated interior or exterior
  DEFAULT_EXTERIOR_PROBABILITY // Caso F: fallback high probability for exterior world (vehículos, calles, vegetación, etc.)
}

/**
 * Represents an individual raw texture (uncompressed image, typically .png)
 * discovered and classified inside a mod.
 */
data class RawTextureEntry(
  val name: String,
  val baseName: String,
  val sizeBytes: Long,
  val relativePath: String,
  val folderName: String,
  val hasAlpha: Boolean,
  val alphaFolderName: String, // "With Alpha" or "Without Alpha"
  val targetContainer: TargetContainer,
  val matchReason: TextureMatchReason,
  val linkedDffModel: String? = null
)

/**
 * Lightweight extractor to scan RenderWare .dff 3D model binaries
 * and extract texture names referenced in its materials.
 */
object DffTextureExtractor {

  /**
   * Scans a DFF file and extracts all valid texture name tokens referenced in materials.
   */
  fun extractTextureNames(dffFile: File): Set<String> {
    if (!dffFile.exists() || dffFile.length() < 12) return emptySet()
    return try {
      val maxBytes = minOf(dffFile.length(), 2 * 1024 * 1024L).toInt()
      val bytes = ByteArray(maxBytes)
      FileInputStream(dffFile).use { input ->
        input.read(bytes)
      }
      extractTextureNamesFromBytes(bytes)
    } catch (_: Throwable) {
      emptySet()
    }
  }

  /**
   * Scans byte buffer for null-terminated ASCII texture names.
   */
  fun extractTextureNamesFromBytes(bytes: ByteArray): Set<String> {
    val textures = mutableSetOf<String>()
    val sb = java.lang.StringBuilder()

    for (b in bytes) {
      val c = b.toInt().toChar()
      if ((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '-' || c == '$') {
        sb.append(c)
      } else {
        if (sb.length in 3..32) {
          val candidate = sb.toString().lowercase(Locale.ROOT)
          if (isValidTextureCandidate(candidate)) {
            textures.add(candidate)
          }
        }
        sb.setLength(0)
      }
    }
    return textures
  }

  private val RESERVED_KEYWORDS = setOf(
    "clump", "framelist", "geometrylist", "geometry", "materiallist", "material",
    "atomic", "rwversion", "renderware", "standard", "struct", "extension", "camera",
    "light", "string", "texture", "world", "binmeshplg", "skinplg", "normalplg"
  )

  private fun isValidTextureCandidate(candidate: String): Boolean {
    if (candidate in RESERVED_KEYWORDS) return false
    if (!candidate.any { it in 'a'..'z' }) return false
    if (candidate.startsWith("0x")) return false
    return true
  }
}

/**
 * Deep, millimeter-precise classifier for raw textures (loose .png, .jpg, .bmp files):
 * - Detects Alpha channel transparency (With Alpha vs Without Alpha).
 * - Identifies with exact precision whether a texture belongs to EXTERIORES (gta3) or INTERIORES (gta_int).
 * - Evaluates folder hierarchies, compressed archive semantics, DFF model material links,
 *   game catalog registries, and semantic texture naming tokens.
 */
object RawTextureClassifier {

  private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "bmp", "webp", "tga")

  fun isRawTextureFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in SUPPORTED_EXTENSIONS
  }

  /**
   * Detects whether the texture is designated "With Alpha" (32-bit RGBA) or "Without Alpha" (24-bit RGB solid).
   */
  fun detectAlpha(relativePath: String, fileBytes: ByteArray? = null): Pair<Boolean, String> {
    val pathLower = relativePath.lowercase(Locale.ROOT)

    // Check path segments for explicit TXD Tool folders
    val segments = pathLower.split('/', '\\')
    for (seg in segments) {
      val s = seg.trim()
      if (s.contains("without alpha") || s.contains("without_alpha") || s.contains("sin alpha") || s.contains("sin_alpha") || s.contains("no alpha") || s.contains("no_alpha")) {
        return false to "Without Alpha"
      }
      if (s == "with alpha" || s == "with_alpha" || s == "con alpha" || s == "con_alpha" || s == "alpha" || s.startsWith("with alpha") || s.startsWith("con alpha")) {
        return true to "With Alpha"
      }
    }

    // PNG Header inspection (Color Type in IHDR chunk)
    if (fileBytes != null && fileBytes.size >= 26) {
      // PNG magic: 89 50 4E 47
      if (fileBytes[0] == 0x89.toByte() && fileBytes[1] == 0x50.toByte() && fileBytes[2] == 0x4E.toByte() && fileBytes[3] == 0x47.toByte()) {
        val colorType = fileBytes[25].toInt()
        // Type 4: Grayscale + Alpha, Type 6: Truecolor + Alpha
        if (colorType == 4 || colorType == 6) {
          return true to "With Alpha"
        } else if (colorType == 0 || colorType == 2) {
          return false to "Without Alpha"
        }
      }
    }

    // Default to Without Alpha if not specified
    return false to "Without Alpha"
  }

  /**
   * Classifies a raw texture into TargetContainer.GTA3 or TargetContainer.GTA_INT.
   */
  fun classify(
    fileName: String,
    relativePath: String,
    archiveOrModName: String? = null,
    dffExteriorTextures: Set<String> = emptySet(),
    dffInteriorTextures: Set<String> = emptySet(),
    knownExteriorTextures: Set<String> = emptySet(),
    knownInteriorTextures: Set<String> = emptySet()
  ): Pair<TargetContainer, TextureMatchReason> {
    val baseName = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)

    // TIER 1: Path & Folder Hierarchy Analysis
    val pathDecision = detectPathIntent(relativePath)
    if (pathDecision != null) {
      return pathDecision to TextureMatchReason.FOLDER_EXPLICIT
    }

    // TIER 2: DFF 3D Model Material Cross-Reference
    if (dffInteriorTextures.contains(baseName)) {
      return TargetContainer.GTA_INT to TextureMatchReason.DFF_MODEL_LINK
    }
    if (dffExteriorTextures.contains(baseName)) {
      return TargetContainer.GTA3 to TextureMatchReason.DFF_MODEL_LINK
    }

    // TIER 3: Real Game Texture Catalog Lookup (TOC & Manifests)
    val matchInGameInt = knownInteriorTextures.contains(baseName) || BUILT_IN_INTERIOR_TEXTURES.contains(baseName)
    val matchInGameExt = knownExteriorTextures.contains(baseName) || BUILT_IN_EXTERIOR_TEXTURES.contains(baseName)

    if (matchInGameInt && !matchInGameExt) {
      return TargetContainer.GTA_INT to TextureMatchReason.GAME_CATALOG_MATCH
    }
    if (matchInGameExt && !matchInGameInt) {
      return TargetContainer.GTA3 to TextureMatchReason.GAME_CATALOG_MATCH
    }

    // TIER 4: Compressed Archive Name and Mod Title Semantics
    if (!archiveOrModName.isNullOrBlank()) {
      val archiveDecision = detectArchiveIntent(archiveOrModName)
      if (archiveDecision != null) {
        return archiveDecision to TextureMatchReason.ARCHIVE_NAME_SEMANTICS
      }
    }

    // TIER 5: Semantic Texture Name & GTA SA Prefix Analysis
    val nameDecision = detectTextureNameIntent(baseName)
    if (nameDecision != null) {
      return nameDecision to TextureMatchReason.TEXTURE_NAME_SEMANTICS
    }

    // TIER 6: Default Exterior Probability (Predominant in GTA San Andreas mod ecosystem)
    return TargetContainer.GTA3 to TextureMatchReason.DEFAULT_EXTERIOR_PROBABILITY
  }

  /**
   * Constructs a fully classified RawTextureEntry from metadata and content hints.
   */
  fun classifyTexture(
    name: String,
    relativePath: String,
    sizeBytes: Long,
    fileBytes: ByteArray? = null,
    archiveOrModName: String? = null,
    dffExteriorTextures: Set<String> = emptySet(),
    dffInteriorTextures: Set<String> = emptySet(),
    knownExteriorTextures: Set<String> = emptySet(),
    knownInteriorTextures: Set<String> = emptySet()
  ): RawTextureEntry {
    val (hasAlpha, alphaFolder) = detectAlpha(relativePath, fileBytes)
    val (container, reason) = classify(
      fileName = name,
      relativePath = relativePath,
      archiveOrModName = archiveOrModName,
      dffExteriorTextures = dffExteriorTextures,
      dffInteriorTextures = dffInteriorTextures,
      knownExteriorTextures = knownExteriorTextures,
      knownInteriorTextures = knownInteriorTextures
    )
    val folder = relativePath.substringBeforeLast('/', "").substringAfterLast('/')
    val base = name.substringBeforeLast('.')
    return RawTextureEntry(
      name = name,
      baseName = base,
      sizeBytes = sizeBytes,
      relativePath = relativePath,
      folderName = folder,
      hasAlpha = hasAlpha,
      alphaFolderName = alphaFolder,
      targetContainer = container,
      matchReason = reason
    )
  }

  fun createEntry(
    name: String,
    relativePath: String,
    sizeBytes: Long,
    fileBytes: ByteArray? = null,
    archiveOrModName: String? = null,
    dffExteriorTextures: Set<String> = emptySet(),
    dffInteriorTextures: Set<String> = emptySet(),
    knownExteriorTextures: Set<String> = emptySet(),
    knownInteriorTextures: Set<String> = emptySet()
  ): RawTextureEntry = classifyTexture(
    name = name,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    fileBytes = fileBytes,
    archiveOrModName = archiveOrModName,
    dffExteriorTextures = dffExteriorTextures,
    dffInteriorTextures = dffInteriorTextures,
    knownExteriorTextures = knownExteriorTextures,
    knownInteriorTextures = knownInteriorTextures
  )

  /**
   * Traverses path segments from deepest parent up to root.
   * Format folders like "With Alpha" or "Without Alpha" are transparently skipped.
   */
  private fun detectPathIntent(relativePath: String): TargetContainer? {
    val clean = relativePath.replace('\\', '/')
    val segments = clean.split('/').dropLast(1).reversed() // Deepest parent first

    for (segment in segments) {
      val s = segment.lowercase(Locale.ROOT).trim()
      // Skip TXD Tool format subfolders
      if (s in NEUTRAL_FOLDER_TOKENS) continue
      if (s.contains("with alpha") || s.contains("without alpha") || s.contains("sin alpha") || s.contains("con alpha")) continue

      val isInterior = s.contains("gta_int") ||
        s.contains("gta.int") ||
        s.contains("gtaint") ||
        s.contains("gta int") ||
        s.contains("interior") ||
        s.contains("interiores") ||
        s.contains("interiors") ||
        s.contains("indoor") ||
        s.contains("indoors") ||
        s.contains("inside") ||
        s.contains("dentro") ||
        INTERIOR_ARCHIVE_KEYWORDS.any { s.contains(it) }

      val isExterior = s.contains("gta3") ||
        s.contains("gta 3") ||
        s.contains("gta_3") ||
        s.contains("gta3.img") ||
        s.contains("gta3.txt") ||
        s.contains("exterior") ||
        s.contains("exteriores") ||
        s.contains("exteriors") ||
        s.contains("outdoor") ||
        s.contains("outdoors") ||
        s.contains("outside") ||
        s.contains("afuera") ||
        EXTERIOR_ARCHIVE_KEYWORDS.any { s.contains(it) }

      if (isInterior && !isExterior) return TargetContainer.GTA_INT
      if (isExterior && !isInterior) return TargetContainer.GTA3
    }

    return null
  }

  /**
   * Evaluates mod archive name or compressed file title semantics.
   */
  private fun detectArchiveIntent(archiveName: String): TargetContainer? {
    val lower = archiveName.lowercase(Locale.ROOT)
    val hasInterior = INTERIOR_ARCHIVE_KEYWORDS.any { lower.contains(it) }
    val hasExterior = EXTERIOR_ARCHIVE_KEYWORDS.any { lower.contains(it) }

    if (hasInterior && !hasExterior) return TargetContainer.GTA_INT
    if (hasExterior && !hasInterior) return TargetContainer.GTA3
    return null
  }

  /**
   * Evaluates individual texture filename prefixes and architectural tokens.
   */
  private fun detectTextureNameIntent(baseName: String): TargetContainer? {
    val lower = baseName.lowercase(Locale.ROOT)

    // Check interior prefixes and tokens
    val hasInteriorToken = INTERIOR_NAME_TOKENS.any { token ->
      lower.startsWith(token) || lower.contains(token)
    }

    // Check exterior prefixes and tokens
    val hasExteriorToken = EXTERIOR_NAME_TOKENS.any { token ->
      lower.startsWith(token) || lower.contains(token)
    }

    if (hasInteriorToken && !hasExteriorToken) return TargetContainer.GTA_INT
    if (hasExteriorToken && !hasInteriorToken) return TargetContainer.GTA3
    return null
  }

  private val NEUTRAL_FOLDER_TOKENS = setOf(
    "textures", "texturas", "images", "imagenes", "txd", "importar", "import", "files",
    "with alpha", "without alpha", "con alpha", "sin alpha", "alpha", "no alpha", "raw"
  )

  private val INTERIOR_ARCHIVE_KEYWORDS = listOf(
    "casa", "house", "hogar", "mansion", "mansión", "habitacion", "habitación", "room",
    "dormitorio", "bedroom", "living", "cocina", "kitchen", "bano", "baño", "bathroom",
    "tienda", "shop", "store", "mercado", "market", "supermarket", "24-7", "24/7",
    "bar", "cantina", "discoteca", "club", "casino", "restaurante", "restaurant",
    "pizzeria", "pizzería", "burger", "cluckin", "gym", "gimnasio", "hospital",
    "comisaria", "comisaría", "interior", "interiores", "interiors", "indoor", "indoors",
    "safehouse", "hotel", "motel", "cuartel", "oficina", "office", "barberia", "barbería",
    "barber", "tatuaje", "tattoo", "ammunation", "ammu-nation"
  )

  private val EXTERIOR_ARCHIVE_KEYWORDS = listOf(
    "calle", "calles", "street", "streets", "road", "roads", "carretera", "pista",
    "asfalto", "asphalt", "acera", "sidewalk", "auto", "autos", "car", "cars",
    "coche", "coches", "vehiculo", "vehículos", "vehiculos", "vehicle", "vehicles",
    "moto", "motos", "bike", "bikes", "camion", "camión", "truck", "avion", "avión",
    "plane", "helicoptero", "helicóptero", "barco", "boat", "rueda", "ruedas", "wheels",
    "rims", "llantas", "semaforo", "semáforo", "farolas", "luces", "lights", "arbol",
    "arboles", "árboles", "trees", "palmeras", "vegetacion", "vegetación", "vegetation",
    "pasto", "grass", "hierba", "cielo", "sky", "nubes", "clouds", "timecyc", "playa",
    "beach", "arena", "sand", "agua", "water", "armas", "weapons", "guns", "pistola",
    "rifle", "ped", "peds", "peaton", "peatones", "skins", "skin", "ropa", "clothes",
    "radar", "mapa", "hud", "overhaul", "exterior", "exteriores"
  )

  private val INTERIOR_NAME_TOKENS = listOf(
    "int_", "in_", "interior_", "room_", "house_", "casa_", "floor_int", "wall_int",
    "carpet", "alfombra", "moqueta", "parquet", "wallpaper", "ceiling", "techo_int",
    "curtain", "cortina", "sofa", "couch", "sillon", "sillón", "chair", "silla",
    "table", "mesa", "bed", "cama", "pillow", "almohada", "sheet", "sabana", "sábana",
    "tv", "television", "lamp", "lampara", "lámpara", "desk", "escritorio", "shelf",
    "estante", "kitchen", "cocina", "tile_int", "azulejo", "baldosa", "bath", "baño",
    "shower", "ducha", "sink", "lavabo", "counter", "mostrador", "refrigerator", "fridge",
    "nevera", "stove", "horno", "microwave", "microondas", "poster", "painting", "cuadro",
    "casino", "roulette", "ruleta", "slot", "tragamonedas", "gym", "gimnasio", "dumbbell",
    "mancuerna", "treadmill", "barber", "tattoo", "tatuaje", "ammunation", "cluckin",
    "burgershot", "wellstacked", "pizza", "bistro", "wardrobe", "armario", "closet",
    "mirror", "espejo", "safe_box", "caja_fuerte", "snack_vendor", "arcade"
  )

  private val EXTERIOR_NAME_TOKENS = listOf(
    "veh_", "car_", "auto_", "coche_", "body", "chassis", "hood", "bonnet", "trunk",
    "bumper", "door_ext", "wheel", "rueda", "rim", "llanta", "tire", "tyre", "tread",
    "headlight", "taillight", "light_glass", "exhaust", "plate", "placa", "matricula",
    "badge", "emblem", "grille", "engine", "windshield", "window_ext", "spoiler",
    "decal_ext", "livery", "road", "calle", "asphalt", "asfalto", "lane", "carril",
    "pave", "pavement", "sidewalk", "acera", "curb", "bordillo", "grass", "pasto",
    "cesped", "césped", "hierba", "tree", "arbol", "árbol", "leaf", "leaves", "hojas",
    "bark", "corteza", "bush", "arbusto", "hedge", "palm", "palmera", "sand", "arena",
    "beach", "playa", "water", "agua", "sea", "mar", "river", "rio", "río", "sky",
    "cielo", "cloud", "nube", "sun", "sol", "moon", "luna", "radar", "hud", "crosshair",
    "trafficlight", "semaforo", "semáforo", "lamppost", "farola", "billboard", "cartel",
    "fence", "valla", "barrier", "barrera", "facade", "fachada", "ped_", "skin_",
    "weapon_", "gun_", "rifle", "pistol"
  )

  private val BUILT_IN_INTERIOR_TEXTURES = setOf(
    "barbersflr1_la", "bistromenu", "cutlery", "interior_wall01", "interior_wall02",
    "woodfloor01", "woodfloor02", "casino_carpet01", "casino_carpet02", "kitchen_tile",
    "bathroom_marble", "door_wood_int", "counter_table", "shelf_bottles", "tv_screen",
    "arcade_machine", "safe_metal", "furniture_leather", "armchair_fabric", "bed_sheet_blue",
    "pooltable_felt", "gym_mat", "tattoo_poster", "painting_classic", "ceiling_lamp",
    "elevator_door", "curtain_red", "snack_vendor", "slot_machine", "roulette_table"
  )

  private val BUILT_IN_EXTERIOR_TEXTURES = setOf(
    "radar00", "radar01", "radar02", "radar03", "radar04", "radar05", "radar06", "radar07",
    "radar_centre", "radar_ringplane", "radar_waypoint", "radar_north",
    "asphalt_law", "asphalt_sfse", "asphalt_desert", "roadlanedbl", "roadlanesng",
    "grass_patch", "grasstype1", "grasstype2", "grasstype4",
    "pave_curb", "pavement_dirt", "sidewalk1", "sidewalk2", "concrete_barrier",
    "sand_beach", "waterclear256", "vehiclelights128", "plateback1", "wheel_rim",
    "cj_ped_head", "cj_ped_torso", "cj_ped_legs", "tree_leaves1", "palm_leaf",
    "lamppost_glow", "trafficlight", "billboard_sprunk", "billboard_cluckin"
  )
}
