package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ModFileEntry
import com.example.util.FileSharingHelper
import java.util.Locale

/**
 * Renders the two GTA San Andreas containers (gta3.img and gta_int.img) side-by-side
 * in compact, clean cards with the real game container archive icon (amber sheet with white CD),
 * and the exact size in normal text underneath.
 *
 * When tapping either container, a minimalist dropdown menu appears with:
 * 1. "Compartir" - Launches standard Android share sheet (WhatsApp, Drive, etc.)
 * 2. "Ver" - Shows view container action (marked as coming soon)
 */
@Composable
fun GtaContainerCardsRow(
  containers: List<ModFileEntry>,
  modifier: Modifier = Modifier,
  onContainerClick: ((ModFileEntry) -> Unit)? = null,
  onViewContainer: ((ModFileEntry) -> Unit)? = null
) {
  val context = LocalContext.current

  val gta3 = containers.firstOrNull { it.fileName.equals("gta3.img", ignoreCase = true) }
    ?: ModFileEntry(
      fileName = "gta3.img",
      fileType = "IMG",
      relativePath = "",
      sizeBytes = 283516928L // ~270.38 MB default GTA SA size reference if reading
    )

  val gtaInt = containers.firstOrNull { it.fileName.equals("gta_int.img", ignoreCase = true) }
    ?: ModFileEntry(
      fileName = "gta_int.img",
      fileType = "IMG",
      relativePath = "",
      sizeBytes = 44826624L // ~42.75 MB default GTA SA size reference if reading
    )

  var menuGta3Open by remember { mutableStateOf(false) }
  var menuGtaIntOpen by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.containers_section_title),
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.5.sp,
      color = Color(0xFF6B7280),
      modifier = Modifier
        .padding(bottom = 8.dp)
        .testTag("containers_section_label")
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("containers_side_by_side_row"),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(modifier = Modifier.weight(1f)) {
        SingleContainerCard(
          entry = gta3,
          modifier = Modifier.fillMaxWidth(),
          testTag = "container_card_gta3",
          onClick = {
            onContainerClick?.invoke(gta3)
            menuGta3Open = true
          }
        )

        ContainerActionDropdownMenu(
          expanded = menuGta3Open,
          onDismissRequest = { menuGta3Open = false },
          containerName = gta3.fileName,
          onShare = {
            menuGta3Open = false
            FileSharingHelper.shareContainer(context, gta3)
          },
          onView = {
            menuGta3Open = false
            onViewContainer?.invoke(gta3)
          }
        )
      }

      Box(modifier = Modifier.weight(1f)) {
        SingleContainerCard(
          entry = gtaInt,
          modifier = Modifier.fillMaxWidth(),
          testTag = "container_card_gta_int",
          onClick = {
            onContainerClick?.invoke(gtaInt)
            menuGtaIntOpen = true
          }
        )

        ContainerActionDropdownMenu(
          expanded = menuGtaIntOpen,
          onDismissRequest = { menuGtaIntOpen = false },
          containerName = gtaInt.fileName,
          onShare = {
            menuGtaIntOpen = false
            FileSharingHelper.shareContainer(context, gtaInt)
          },
          onView = {
            menuGtaIntOpen = false
            onViewContainer?.invoke(gtaInt)
          }
        )
      }
    }
  }
}

/**
 * Minimalist dropdown menu matching the clean aesthetic of the main application menu:
 * Pure white card, 14.dp rounded corners, subtle border, neat vertical options one below the other:
 * 1. "Compartir"
 * 2. "Ver"
 */
@Composable
fun ContainerActionDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  containerName: String,
  onShare: () -> Unit,
  onView: () -> Unit,
  modifier: Modifier = Modifier
) {
  MaterialTheme(
    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
  ) {
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = onDismissRequest,
      modifier = modifier
        .width(185.dp)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        .padding(vertical = 4.dp)
        .testTag("container_dropdown_${containerName}")
    ) {
      // Option 1: Compartir
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onShare
          )
          .padding(horizontal = 14.dp, vertical = 11.dp)
          .testTag("container_menu_item_share"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Share,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.container_action_share),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
      }

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 10.dp)
      )

      // Option 2: Ver (one below the other, clean)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onView
          )
          .padding(horizontal = 14.dp, vertical = 11.dp)
          .testTag("container_menu_item_view"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Visibility,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.container_action_view),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

/**
 * Compact container card matching the reference image layout:
 * - Golden document icon with folded top-right corner and white CD disc
 * - Container name (e.g. gta3.img)
 * - Container size in normal letters right underneath
 * Approximately half the height and size of the previous card without clutter.
 */
@Composable
fun SingleContainerCard(
  entry: ModFileEntry,
  modifier: Modifier = Modifier,
  testTag: String = "container_card",
  onClick: (() -> Unit)? = null
) {
  Card(
    onClick = { onClick?.invoke() },
    enabled = onClick != null,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier.testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Golden document file icon with white CD disc (exact replica of game container archive)
      GtaFileDocumentIcon(
        modifier = Modifier
          .size(width = 27.dp, height = 33.dp)
          .testTag("${testTag}_icon")
      )

      Spacer(modifier = Modifier.width(9.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        // File name (e.g. gta3.img, gta_int.img)
        Text(
          text = entry.fileName,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("${testTag}_name")
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Formatted size in normal letters right underneath (e.g. 270.38 MB)
        Text(
          text = formatBytes(entry.sizeBytes),
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.testTag("${testTag}_size")
        )
      }
    }
  }
}

/**
 * Authentic GTA San Andreas game container archive icon matching the reference image:
 * Golden amber document sheet with folded corner at top-right and a white CD/DVD disc with center spindle hole.
 */
@Composable
fun GtaFileDocumentIcon(
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val foldSize = w * 0.32f
    val cornerRadius = 3.dp.toPx()

    // 1. Document body path with folded top-right corner
    val docPath = Path().apply {
      moveTo(0f, cornerRadius)
      quadraticTo(0f, 0f, cornerRadius, 0f)
      lineTo(w - foldSize, 0f)
      lineTo(w, foldSize)
      lineTo(w, h - cornerRadius)
      quadraticTo(w, h, w - cornerRadius, h)
      lineTo(cornerRadius, h)
      quadraticTo(0f, h, 0f, h - cornerRadius)
      close()
    }

    // Rich golden amber color from GTA SA / ZArchiver container format
    val bodyColor = Color(0xFFEAA002)
    drawPath(path = docPath, color = bodyColor)

    // 2. Dog-ear folded corner flap
    val foldPath = Path().apply {
      moveTo(w - foldSize, 0f)
      lineTo(w, foldSize)
      lineTo(w - foldSize, foldSize)
      close()
    }
    val foldColor = Color(0xFFFFC83B)
    drawPath(path = foldPath, color = foldColor)

    // 3. White CD / DVD disc centered in the lower portion of the sheet
    val discCenter = Offset(w * 0.48f, h * 0.58f)
    val discRadius = w * 0.30f

    // White disc body
    drawCircle(
      color = Color.White,
      radius = discRadius,
      center = discCenter
    )

    // Center spindle hole showing through the document body
    val holeRadius = discRadius * 0.34f
    drawCircle(
      color = bodyColor,
      radius = holeRadius,
      center = discCenter
    )
  }
}

/**
 * Retained for backward compatibility if referenced in tests or previews.
 */
@Composable
fun GoldenDiscIcon(
  modifier: Modifier = Modifier
) {
  GtaFileDocumentIcon(modifier = modifier)
}

fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  val gb = mb / 1024.0
  return when {
    gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
    mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
    kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
    else -> "$bytes B"
  }
}
