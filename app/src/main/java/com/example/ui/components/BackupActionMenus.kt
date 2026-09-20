package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Contextual popup menu for backup files:
 * 14.dp rounded corners, clean border, with:
 * 1. "Compartir"
 * 2. "Ver"
 */
@Composable
fun BackupFileActionDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  fileName: String,
  onShare: () -> Unit,
  onView: () -> Unit,
  isDarkMode: Boolean = false,
  modifier: Modifier = Modifier
) {
  val surfaceColor = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val borderColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)
  val dividerColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFF3F4F6)

  MaterialTheme(
    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
  ) {
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = onDismissRequest,
      modifier = modifier
        .width(185.dp)
        .background(surfaceColor)
        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
        .padding(vertical = 4.dp)
        .testTag("backup_dropdown_${fileName}")
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
          .testTag("backup_menu_item_share"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Share,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.container_action_share),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = textColor,
          modifier = Modifier.weight(1f)
        )
      }

      HorizontalDivider(
        color = dividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 10.dp)
      )

      // Option 2: Ver
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onView
          )
          .padding(horizontal = 14.dp, vertical = 11.dp)
          .testTag("backup_menu_item_view"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Visibility,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.container_action_view),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = textColor,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

/**
 * Contextual popup menu for an individual file inside the archive explorer:
 * Shows the "Mover" action with the same minimalist style.
 */
@Composable
fun ExplorerFileActionDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  fileName: String,
  onMove: () -> Unit,
  isDarkMode: Boolean = false,
  modifier: Modifier = Modifier
) {
  val surfaceColor = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val borderColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)

  MaterialTheme(
    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
  ) {
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = onDismissRequest,
      modifier = modifier
        .width(170.dp)
        .background(surfaceColor)
        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
        .padding(vertical = 4.dp)
        .testTag("explorer_dropdown_${fileName}")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onMove
          )
          .padding(horizontal = 14.dp, vertical = 11.dp)
          .testTag("explorer_menu_item_move"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.DriveFileMove,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.game_backup_action_move),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = textColor,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

/**
 * Contextual popup menu when multiple backup files are highlighted:
 * Displays:
 * 1. "Compartir" (shares all selected files at once)
 * 2. "Comprimir" (zips the selected files and directly opens moving interface)
 */
@Composable
fun MultiBackupActionDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  selectedCount: Int,
  onShare: () -> Unit,
  onCompress: () -> Unit,
  isDarkMode: Boolean = false,
  modifier: Modifier = Modifier
) {
  val surfaceColor = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val borderColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)
  val dividerColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFF3F4F6)

  MaterialTheme(
    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
  ) {
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = onDismissRequest,
      modifier = modifier
        .width(185.dp)
        .background(surfaceColor)
        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
        .padding(vertical = 4.dp)
        .testTag("multi_backup_dropdown")
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
          .testTag("multi_backup_menu_item_share"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Share,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.container_action_share),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = textColor,
          modifier = Modifier.weight(1f)
        )
      }

      HorizontalDivider(
        color = dividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 10.dp)
      )

      // Option 2: Comprimir
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onCompress
          )
          .padding(horizontal = 14.dp, vertical = 11.dp)
          .testTag("multi_backup_menu_item_compress"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.FolderZip,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
          text = stringResource(R.string.game_backup_action_compress),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = textColor,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}
