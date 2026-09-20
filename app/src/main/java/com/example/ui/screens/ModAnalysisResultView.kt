package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.analyzer.DffActionType
import com.example.data.analyzer.DffMatchItem
import com.example.data.analyzer.MatchPlan
import com.example.data.analyzer.ModAnalysisResult
import com.example.data.analyzer.ModDffEntry
import com.example.data.analyzer.ModImplementationSummary
import com.example.data.analyzer.RawTextureEntry
import com.example.data.analyzer.ScriptInstallItem
import com.example.data.analyzer.TargetContainer
import com.example.data.analyzer.TextureMatchReason
import com.example.data.parser.ImgArchiveReader
import com.example.ui.components.RotatingBallIndicator
import com.example.ui.viewmodel.MatchUiState
import com.example.ui.viewmodel.RebuildUiState
import kotlinx.coroutines.delay

/**
 * Clean, minimal results screen that presents analyzed and matched DFF models.
 * Displays "bola de carga" during matching and rebuild, and clean vertical lists without recuadros.
 */
@Composable
fun ModAnalysisResultView(
  result: ModAnalysisResult,
  matchState: MatchUiState = MatchUiState.Idle,
  rebuildState: RebuildUiState = RebuildUiState.Idle,
  onRebuild: (MatchPlan, Map<String, TargetContainer>) -> Unit = { _, _ -> },
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Manual overrides for textures: textureName (lowercase) -> TargetContainer
  val textureOverrides = remember { mutableStateMapOf<String, TargetContainer>() }
  var textureTargetingPickerFor by remember { mutableStateOf<TargetContainer?>(null) }

  // Effective texture lists taking user overrides into account
  val allTextures = remember(result.gta3TextureEntries, result.gtaIntTextureEntries) {
    result.gta3TextureEntries + result.gtaIntTextureEntries
  }

  val effectiveGta3Textures = allTextures.filter { entry ->
    val override = textureOverrides[entry.name.lowercase()]
    if (override != null) override == TargetContainer.GTA3 else entry.targetContainer == TargetContainer.GTA3
  }

  val effectiveGtaIntTextures = allTextures.filter { entry ->
    val override = textureOverrides[entry.name.lowercase()]
    if (override != null) override == TargetContainer.GTA_INT else entry.targetContainer == TargetContainer.GTA_INT
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .navigationBarsPadding()
      .testTag("mod_analysis_result_view")
  ) {
    // Header Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(48.dp)
          .testTag("close_mod_analysis_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Cerrar",
          tint = Color(0xFF1E1F22)
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 4.dp)
      ) {
        Text(
          text = result.modName,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF1E1F22),
          maxLines = 1
        )
        Text(
          text = when {
            rebuildState is RebuildUiState.Rebuilding -> rebuildState.statusMessage
            rebuildState is RebuildUiState.Success -> "Mod inyectado y ubicado en el juego"
            matchState is MatchUiState.Matched -> "Estructura clasificada • Revisa o confirma abajo"
            matchState is MatchUiState.Matching -> "Identificando coincidencias..."
            else -> {
              val dffText = if (result.totalDffFound > 0) "${result.totalDffFound} modelos .dff" else ""
              val texText = if (allTextures.isNotEmpty()) "${allTextures.size} texturas .png" else ""
              listOf(dffText, texText).filter { it.isNotEmpty() }.joinToString(" • ").ifEmpty { "Mod analizado" }
            }
          },
          fontSize = 12.sp,
          color = Color(0xFF6B7280)
        )
      }
    }

    HorizontalDivider(color = Color(0xFFF2F4F7), thickness = 1.dp)

    // Rebuilding State: "bola de carga"
    if (rebuildState is RebuildUiState.Rebuilding) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        RotatingBallIndicator(statusText = rebuildState.statusMessage)
      }
      return
    }

    // Success State
    if (rebuildState is RebuildUiState.Success) {
      ModImplementationSummaryView(
        summary = rebuildState.summary,
        fallbackMessage = rebuildState.message,
        onClose = onClose
      )
      return
    }

    // Matching State: "bola de carga"
    if (matchState is MatchUiState.Matching) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        RotatingBallIndicator(statusText = matchState.stepMessage)
      }
      return
    }

    val hasGta3 = result.gta3Entries.isNotEmpty()
    val hasGtaInt = result.gtaIntEntries.isNotEmpty()
    val hasGta3Tex = effectiveGta3Textures.isNotEmpty()
    val hasGtaIntTex = effectiveGtaIntTextures.isNotEmpty()

    if (!hasGta3 && !hasGtaInt && !hasGta3Tex && !hasGtaIntTex) {
      // Empty state
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No se encontraron modelos .dff ni texturas en el mod",
          fontSize = 14.sp,
          color = Color(0xFF6B7280)
        )
      }
    } else {
      val matchPlan = (matchState as? MatchUiState.Matched)?.plan
      val matchMap = matchPlan?.items?.associateBy { it.fileName.lowercase() } ?: emptyMap()

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .testTag("analyzed_dff_list"),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        // SECTION 1: gta3.img (DFF Models)
        if (hasGta3) {
          item(key = "header_gta3") {
            ContainerSectionHeader(
              containerName = "gta3.img",
              containerType = "EXTERIORES",
              count = result.gta3Entries.size,
              itemType = "dff"
            )
          }

          items(result.gta3Entries, key = { "gta3_${it.relativePath}_${it.name}" }) { entry ->
            val matchItem = matchMap[entry.name.lowercase()]
            CleanAnalyzedDffRow(entry = entry, matchItem = matchItem)
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp),
              color = Color(0xFFF3F4F6),
              thickness = 0.8.dp
            )
          }
        }

        // SECTION 2: gta_int.img (DFF Models - Interior)
        if (hasGtaInt) {
          item(key = "header_gta_int") {
            Spacer(modifier = Modifier.height(if (hasGta3) 16.dp else 0.dp))
            ContainerSectionHeader(
              containerName = "gta_int.img",
              containerType = "INTERIORES",
              count = result.gtaIntEntries.size,
              itemType = "dff"
            )
          }

          items(result.gtaIntEntries, key = { "gtaint_${it.relativePath}_${it.name}" }) { entry ->
            val matchItem = matchMap[entry.name.lowercase()]
            CleanAnalyzedDffRow(entry = entry, matchItem = matchItem)
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp),
              color = Color(0xFFF3F4F6),
              thickness = 0.8.dp
            )
          }
        }

        // SECTION 3: gta3 (Raw Textures - Exteriores)
        if (hasGta3Tex) {
          item(key = "header_tex_gta3") {
            Spacer(modifier = Modifier.height(if (hasGta3 || hasGtaInt) 16.dp else 0.dp))
            ContainerSectionHeader(
              containerName = "gta3 (Texturas)",
              containerType = "EXTERIORES",
              count = effectiveGta3Textures.size,
              itemType = "png"
            )
          }

          items(effectiveGta3Textures, key = { "tex_gta3_${it.relativePath}_${it.name}" }) { entry ->
            val isOverridden = textureOverrides.containsKey(entry.name.lowercase())
            CleanAnalyzedTextureRow(
              entry = entry,
              isManuallyOverridden = isOverridden,
              onMoveToOpposite = {
                textureOverrides[entry.name.lowercase()] = TargetContainer.GTA_INT
              }
            )
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp),
              color = Color(0xFFF3F4F6),
              thickness = 0.8.dp
            )
          }
        }

        // SECTION 4: gta_int (Raw Textures - Interiores)
        if (hasGtaIntTex) {
          item(key = "header_tex_gta_int") {
            Spacer(modifier = Modifier.height(if (hasGta3 || hasGtaInt || hasGta3Tex) 16.dp else 0.dp))
            ContainerSectionHeader(
              containerName = "gta_int (Texturas)",
              containerType = "INTERIORES",
              count = effectiveGtaIntTextures.size,
              itemType = "png"
            )
          }

          items(effectiveGtaIntTextures, key = { "tex_gtaint_${it.relativePath}_${it.name}" }) { entry ->
            val isOverridden = textureOverrides.containsKey(entry.name.lowercase())
            CleanAnalyzedTextureRow(
              entry = entry,
              isManuallyOverridden = isOverridden,
              onMoveToOpposite = {
                textureOverrides[entry.name.lowercase()] = TargetContainer.GTA3
              }
            )
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 16.dp),
              color = Color(0xFFF3F4F6),
              thickness = 0.8.dp
            )
          }
        }
      }

      // Bottom Control Panel: Manual reclassification buttons & Confirm button
      HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFFAFAFA))
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        // Small buttons for manual assignment (Exteriores & Interiores)
        if (allTextures.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Button 1: Exteriores
            Button(
              onClick = { textureTargetingPickerFor = TargetContainer.GTA3 },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF3F4F6),
                contentColor = Color(0xFF1E1F22)
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("btn_manual_exteriores")
            ) {
              Icon(
                imageVector = Icons.Outlined.FolderZip,
                contentDescription = null,
                tint = Color(0xFF1E1F22),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Exteriores (${effectiveGta3Textures.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
              )
            }

            // Button 2: Interiores
            Button(
              onClick = { textureTargetingPickerFor = TargetContainer.GTA_INT },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF3F4F6),
                contentColor = Color(0xFF1E1F22)
              ),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("btn_manual_interiores")
            ) {
              Icon(
                imageVector = Icons.Outlined.FolderZip,
                contentDescription = null,
                tint = Color(0xFF1E1F22),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Interiores (${effectiveGtaIntTextures.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
        }

        // Confirmation Button ("Confirmar") to proceed to the rebuild & success interface
        Button(
          onClick = {
            if (matchPlan != null) {
              onRebuild(matchPlan, textureOverrides.toMap())
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E1F22),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("btn_confirm_classification")
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Confirmar y aplicar",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }

  // Dialog to manually pick textures to assign to target container
  if (textureTargetingPickerFor != null) {
    val target = textureTargetingPickerFor!!
    val targetName = if (target == TargetContainer.GTA3) "Exteriores (gta3)" else "Interiores (gta_int)"

    Dialog(onDismissRequest = { textureTargetingPickerFor = null }) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp)
      ) {
        Column(
          modifier = Modifier
            .padding(16.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column {
              Text(
                text = "Asignar a $targetName",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1F22)
              )
              Text(
                text = "Selecciona o marca las texturas para este contenedor",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
              )
            }
            IconButton(
              onClick = { textureTargetingPickerFor = null },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Quick action: Assign ALL textures to this container
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                allTextures.forEach { entry ->
                  textureOverrides[entry.name.lowercase()] = target
                }
                textureTargetingPickerFor = null
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text(text = "Asignar todas (${allTextures.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          HorizontalDivider(color = Color(0xFFF3F4F6))

          // Individual Texture Selector List
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
          ) {
            items(allTextures, key = { it.name }) { tex ->
              val currentTarget = textureOverrides[tex.name.lowercase()] ?: tex.targetContainer
              val isAssignedToThis = currentTarget == target

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (isAssignedToThis) {
                      val opposite = if (target == TargetContainer.GTA3) TargetContainer.GTA_INT else TargetContainer.GTA3
                      textureOverrides[tex.name.lowercase()] = opposite
                    } else {
                      textureOverrides[tex.name.lowercase()] = target
                    }
                  }
                  .padding(vertical = 10.dp, horizontal = 4.dp)
              ) {
                Icon(
                  imageVector = if (isAssignedToThis) Icons.Default.CheckCircle else Icons.Default.Close,
                  contentDescription = null,
                  tint = if (isAssignedToThis) Color(0xFF16A34A) else Color(0xFFD1D5DB),
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = tex.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E1F22)
                  )
                  Text(
                    text = if (isAssignedToThis) "Asignado a $targetName" else "En ${if (currentTarget == TargetContainer.GTA3) "Exteriores" else "Interiores"}",
                    fontSize = 11.sp,
                    color = if (isAssignedToThis) Color(0xFF16A34A) else Color(0xFF9CA3AF)
                  )
                }
              }
              HorizontalDivider(color = Color(0xFFF9FAFB), thickness = 0.5.dp)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = { textureTargetingPickerFor = null },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E1F22),
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = "Listo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun ContainerSectionHeader(
  containerName: String,
  containerType: String,
  count: Int,
  itemType: String = "dff"
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFFF8F9FA))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = containerName,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E1F22)
      )

      Spacer(modifier = Modifier.size(8.dp))

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xFFE5E7EB))
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = containerType,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF374151),
          letterSpacing = 0.5.sp
        )
      }
    }

    Text(
      text = "$count $itemType",
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      color = Color(0xFF6B7280)
    )
  }
}

@Composable
private fun CleanAnalyzedTextureRow(
  entry: RawTextureEntry,
  isManuallyOverridden: Boolean = false,
  onMoveToOpposite: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .testTag("analyzed_texture_row_${entry.name}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = entry.name,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF1E1F22)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (entry.hasAlpha) Color(0xFFEDE9FE) else Color(0xFFF3F4F6))
            .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
          Text(
            text = entry.alphaFolderName,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.hasAlpha) Color(0xFF7C3AED) else Color(0xFF4B5563)
          )
        }

        if (isManuallyOverridden) {
          Spacer(modifier = Modifier.width(6.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0xFFFEF3C7))
              .padding(horizontal = 5.dp, vertical = 1.dp)
          ) {
            Text(
              text = "MANUAL",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFD97706)
            )
          }
        }
      }

      val reasonLabel = if (isManuallyOverridden) {
        "Selección manual del usuario"
      } else {
        when (entry.matchReason) {
          TextureMatchReason.FOLDER_EXPLICIT -> "Carpeta: ${entry.folderName.ifEmpty { "Ruta" }}"
          TextureMatchReason.DFF_MODEL_LINK -> "Modelo 3D DFF"
          TextureMatchReason.GAME_CATALOG_MATCH -> "Catálogo oficial del juego"
          TextureMatchReason.ARCHIVE_NAME_SEMANTICS -> "Nombre del archivo comprimido"
          TextureMatchReason.TEXTURE_NAME_SEMANTICS -> "Prefijo de textura"
          TextureMatchReason.DEFAULT_EXTERIOR_PROBABILITY -> "Probabilidad de exteriores"
        }
      }
      Text(
        text = reasonLabel,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Normal,
        color = if (isManuallyOverridden) Color(0xFFD97706) else Color(0xFF6B7280)
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = ImgArchiveReader.formatFileSize(entry.sizeBytes),
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Color(0xFF6B7280),
        modifier = Modifier.padding(end = 8.dp)
      )

      if (onMoveToOpposite != null) {
        IconButton(
          onClick = onMoveToOpposite,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Cambiar contenedor",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun CleanAnalyzedDffRow(
  entry: ModDffEntry,
  matchItem: DffMatchItem? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 13.dp)
      .testTag("analyzed_dff_row_${entry.name}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.name,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1E1F22)
      )
      if (matchItem != null) {
        val actionLabel = if (matchItem.actionType == DffActionType.REPLACE) "Reemplazo" else "Inyección"
        val actionColor = if (matchItem.actionType == DffActionType.REPLACE) Color(0xFF2563EB) else Color(0xFF059669)
        Text(
          text = actionLabel,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Normal,
          color = actionColor
        )
      }
    }

    Text(
      text = ImgArchiveReader.formatFileSize(entry.sizeBytes),
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Normal,
      color = Color(0xFF6B7280),
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@Composable
fun ModImplementationSummaryView(
  summary: ModImplementationSummary?,
  fallbackMessage: String,
  onClose: () -> Unit
) {
  // Remains on screen until user explicitly presses "Listo"
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 20.dp)
  ) {
    // 1. DFF Models
    val totalDff = summary?.totalDffCount ?: 0
    val replaced = summary?.dffReplacedCount ?: 0
    val injected = summary?.dffInjectedCount ?: 0
    val containers = buildList {
      if (summary?.affectsGta3 == true) add("gta3.img")
      if (summary?.affectsGtaInt == true) add("gta_int.img")
    }.joinToString(", ")

    Text(
      text = "MODELOS DFF",
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF6B7280),
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = when {
        replaced > 0 && injected > 0 -> "$replaced reemplazo(s) y $injected inyección(es) en $containers"
        replaced > 0 -> "$replaced reemplazo(s) en $containers"
        injected > 0 -> "$injected inyección(es) en $containers"
        else -> "$totalDff modelo(s) verificado(s)"
      },
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = Color(0xFF1E1F22)
    )
    Text(
      text = "Ubicación: Android/data/.../files/texdb/",
      fontSize = 12.sp,
      color = Color(0xFF9CA3AF)
    )

    Spacer(modifier = Modifier.height(22.dp))

    // 2. CLEO Scripts
    Text(
      text = "SCRIPTS CLEO",
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF6B7280),
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    val scripts = summary?.scriptsFound.orEmpty()
    if (scripts.isNotEmpty()) {
      scripts.forEach { script ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(vertical = 2.dp)
        ) {
          Text(
            text = "• ${script.name}",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1E1F22)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "(${script.type})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2563EB)
          )
        }
        Text(
          text = "Ubicación: Android/data/com.rockstargames.gtasa/files/",
          fontSize = 11.5.sp,
          color = Color(0xFF9CA3AF),
          modifier = Modifier.padding(start = 10.dp)
        )
      }
    } else {
      Text(
        text = "Sin scripts adicionales (.csa, .csi)",
        fontSize = 13.sp,
        color = Color(0xFF9CA3AF)
      )
    }

    Spacer(modifier = Modifier.height(22.dp))

    // 3. Raw Textures (.PNG)
    Text(
      text = "TEXTURAS CRUDAS (.PNG)",
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF6B7280),
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    val extCount = summary?.rawTexturesExteriorCount ?: 0
    val intCount = summary?.rawTexturesInteriorCount ?: 0
    val totalTex = extCount + intCount
    if (totalTex > 0) {
      Text(
        text = "$totalTex textura(s) clasificada(s): $extCount exteriores (gta3), $intCount interiores (gta_int)",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1E1F22)
      )
      val withAlpha = summary?.rawTexturesWithAlphaCount ?: 0
      val withoutAlpha = summary?.rawTexturesWithoutAlphaCount ?: 0
      Text(
        text = "$withAlpha con Alpha (transparencia) • $withoutAlpha sin Alpha (sólidas)",
        fontSize = 12.5.sp,
        color = Color(0xFF4B5563)
      )
      Text(
        text = "Ubicación: Android/data/.../files/texdb/ (listas para importación TXD)",
        fontSize = 12.sp,
        color = Color(0xFF9CA3AF)
      )
    } else {
      Text(
        text = "Sin texturas crudas (.png) adicionales",
        fontSize = 13.sp,
        color = Color(0xFF9CA3AF)
      )
    }

    Spacer(modifier = Modifier.height(22.dp))

    // 4. Status
    Text(
      text = "ESTADO",
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF6B7280),
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "Implementado en el juego y sincronizado",
      fontSize = 13.sp,
      color = Color(0xFF16A34A),
      fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.weight(1f))

    // Small, discreet button
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      contentAlignment = Alignment.Center
    ) {
      Button(
        onClick = onClose,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF1E1F22),
          contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
        modifier = Modifier
          .height(36.dp)
          .testTag("btn_finish_rebuild")
      ) {
        Text(
          text = "Listo",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}
