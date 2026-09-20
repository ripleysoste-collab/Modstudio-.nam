package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.analyzer.TextureBackupManager
import com.example.data.decoder.TxdTextureDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Floating clean modal dialog to view and inspect authentic RenderWare GTA San Andreas textures.
 * Reads real texture blocks from disk / raw GPU bytes and presents the decoded texture with
 * a transparency checkerboard background.
 *
 * If the physical texture block is not yet extracted to disk, displays an honest, technical
 * status explaining that the texture is indexed in the game manifest but requires data sync.
 * NEVER displays simulated or fake procedural images.
 */
@Composable
fun TxdTextureViewerDialog(
  textureName: String,
  isInterior: Boolean,
  onDismiss: () -> Unit,
  isDark: Boolean = true
) {
  val context = LocalContext.current
  var isLoading by remember { mutableStateOf(true) }
  var loadingMessage by remember { mutableStateOf("Localizando ruta del archivo...") }
  var decodeResult by remember { mutableStateOf<TxdTextureDecoder.DecodeResult?>(null) }

  // Checkerboard colors for authentic alpha transparency preview
  val checkerLight = if (isDark) Color(0xFF2C2F36) else Color(0xFFE9ECF0)
  val checkerDark = if (isDark) Color(0xFF212328) else Color(0xFFD4D8DF)

  val containerLocationLabel = if (isInterior) "texdb/gta_int (Interiores)" else "texdb/gta3 (Exteriores)"

  LaunchedEffect(textureName, isInterior) {
    isLoading = true
    decodeResult = null
    withContext(Dispatchers.IO) {
      val backupManager = TextureBackupManager(context)
      val targetDir = if (isInterior) {
        backupManager.getInteriorTexturesDir()
      } else {
        backupManager.getExteriorTexturesDir()
      }

      val sourceFileName = if (isInterior) "gta_int" else "gta3"
      loadingMessage = "Buscando $textureName en $sourceFileName..."
      delay(120)

      loadingMessage = "Extrayendo bloques de textura RenderWare..."
      val result = TxdTextureDecoder.decodeTexture(
        context = context,
        textureName = textureName,
        texdbDir = targetDir.parentFile,
        isInterior = isInterior
      )
      delay(120)

      decodeResult = result
      isLoading = false
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.72f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onDismiss
        )
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      // Main Floating Card
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 420.dp)
          .clip(RoundedCornerShape(22.dp))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {} // Consume click inside
          )
          .border(
            width = 1.dp,
            color = if (isDark) Color(0xFF333842) else Color(0xFFE0E3EA),
            shape = RoundedCornerShape(22.dp)
          )
          .testTag("txd_texture_viewer_dialog"),
        color = if (isDark) Color(0xFF16181D) else Color.White,
        shadowElevation = 16.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Header Bar
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = textureName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF1F3F5) else Color(0xFF1E2024),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = containerLocationLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = if (isDark) Color(0xFF8E95A5) else Color(0xFF6B7280)
              )
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(34.dp)
                .background(
                  color = if (isDark) Color(0xFF262930) else Color(0xFFF0F2F5),
                  shape = CircleShape
                )
                .testTag("txd_viewer_close_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = if (isDark) Color(0xFFC0C5D0) else Color(0xFF4B5563),
                modifier = Modifier.size(18.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // Texture Display Canvas Box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(if (isDark) Color(0xFF1C1E24) else Color(0xFFF5F6F8))
              .border(
                width = 1.dp,
                color = if (isDark) Color(0xFF2E323B) else Color(0xFFE2E5EB),
                shape = RoundedCornerShape(14.dp)
              )
              // Transparency checkerboard pattern
              .drawBehind {
                val tileSize = 20.dp.toPx()
                val numCols = (size.width / tileSize).toInt() + 1
                val numRows = (size.height / tileSize).toInt() + 1
                for (row in 0 until numRows) {
                  for (col in 0 until numCols) {
                    val color = if ((row + col) % 2 == 0) checkerLight else checkerDark
                    drawRect(
                      color = color,
                      topLeft = Offset(col * tileSize, row * tileSize),
                      size = Size(tileSize, tileSize)
                    )
                  }
                }
              },
            contentAlignment = Alignment.Center
          ) {
            if (isLoading) {
              // Smooth pulsing loading state
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(38.dp),
                  color = Color(0xFF00A2ED),
                  strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = loadingMessage,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = if (isDark) Color(0xFFD0D5DD) else Color(0xFF374151),
                  textAlign = TextAlign.Center
                )
              }
            } else if (decodeResult?.bitmap != null) {
              // Authentic Decoded Texture from game files
              Image(
                bitmap = decodeResult!!.bitmap!!.asImageBitmap(),
                contentDescription = textureName,
                modifier = Modifier
                  .fillMaxSize()
                  .padding(8.dp)
              )
            } else {
              // Texture is indexed in game TOC/manifest but binary bytes not yet present on disk
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = null,
                  tint = Color(0xFF00A2ED),
                  modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Bloques binarios sin extraer",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isDark) Color(0xFFF1F3F5) else Color(0xFF1E2024),
                  textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "La textura '$textureName' está indexada en el catálogo de San Andreas, pero sus píxeles comprimidos (.dat / .txd) no se encuentran volcados en el almacenamiento local.",
                  fontSize = 11.sp,
                  color = if (isDark) Color(0xFF9EABB8) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center,
                  lineHeight = 15.sp
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Technical Metadata Specs Pill / Grid
          decodeResult?.let { res ->
            val meta = res.meta
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  color = if (isDark) Color(0xFF1D2027) else Color(0xFFF7F8FA),
                  shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
              if (res.isDecoded && res.bitmap != null) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetaItem(
                    label = "Resolución",
                    value = "${meta.width} × ${meta.height} px",
                    isDark = isDark
                  )
                  MetaItem(
                    label = "Formato",
                    value = meta.format,
                    isDark = isDark
                  )
                  MetaItem(
                    label = "Canal Alfa",
                    value = if (meta.hasAlpha) "Activo (32-bit)" else "Opaco (24-bit)",
                    isDark = isDark
                  )
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetaItem(
                    label = "Catálogo",
                    value = if (isInterior) "gta_int.toc" else "gta3.toc",
                    isDark = isDark
                  )
                  MetaItem(
                    label = "Estado",
                    value = "Indexado",
                    isDark = isDark
                  )
                  MetaItem(
                    label = "Ubicación",
                    value = if (isInterior) "texdb/gta_int" else "texdb/gta3",
                    isDark = isDark
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Bottom Close Action Button
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF00A2ED))
              .clickable(onClick = onDismiss)
              .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Cerrar Vista",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetaItem(
  label: String,
  value: String,
  isDark: Boolean
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label.uppercase(),
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = if (isDark) Color(0xFF7A8292) else Color(0xFF9CA3AF),
      letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (isDark) Color(0xFFE2E5EB) else Color(0xFF1F2937),
      fontFamily = FontFamily.Monospace
    )
  }
}
