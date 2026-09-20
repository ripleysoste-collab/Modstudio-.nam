package com.example.ui.components

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import com.example.ui.theme.LightButtonColor
import com.example.ui.theme.LightButtonContentColor
import java.io.File

/**
 * Non-full-screen modal interface replicating a clean native-style directory picker.
 * Starts from the device root storage ("Memoria del dispositivo") and displays the live path,
 * allowing navigation through directories to select a destination folder for moving a file.
 */
@Composable
fun DeviceStoragePickerModal(
  fileNameToMove: String,
  isDarkMode: Boolean = false,
  onDismiss: () -> Unit,
  onConfirmMoveTo: (targetDirectory: File) -> Unit
) {
  val rootStorage = remember {
    Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
  }

  var currentDir by remember { mutableStateOf(rootStorage) }

  // Detect directories in current directory
  val subDirectories = remember(currentDir) {
    try {
      currentDir.listFiles { file ->
        file.isDirectory && !file.name.startsWith(".")
      }?.sortedBy { it.name.lowercase() }?.toList() ?: emptyList()
    } catch (_: Exception) {
      emptyList()
    }
  }

  val canGoUp = currentDir.absolutePath != rootStorage.absolutePath && currentDir.parentFile != null

  val surfaceBg = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val subtitleColor = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
  val dividerColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)
  val folderColor = if (isDarkMode) GoldButtonColor else Color(0xFFF59E0B)
  val buttonBg = if (isDarkMode) GoldButtonColor else LightButtonColor
  val buttonContent = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor

  // Human-readable path representation starting with "Memoria del dispositivo"
  val displayPath = remember(currentDir) {
    val relPath = currentDir.absolutePath.removePrefix(rootStorage.absolutePath).trim('/')
    if (relPath.isEmpty()) {
      "Memoria del dispositivo"
    } else {
      "Memoria del dispositivo > " + relPath.replace('/', '>')
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = surfaceBg,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .fillMaxHeight(0.78f)
        .testTag("device_storage_picker_dialog")
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Header: Back/up, title & path, and close button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              if (canGoUp) {
                currentDir = currentDir.parentFile ?: rootStorage
              }
            },
            enabled = canGoUp,
            modifier = Modifier.size(38.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Subir nivel",
              tint = if (canGoUp) textColor else subtitleColor.copy(alpha = 0.35f),
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(6.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.game_backup_move_title) + ": $fileNameToMove",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = textColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = displayPath,
              fontSize = 11.5.sp,
              color = subtitleColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Cerrar",
              tint = subtitleColor,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // 2. Folder List: clean rows without heavy boxes
        if (subDirectories.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = subtitleColor.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = stringResource(R.string.game_backup_explorer_empty),
                fontSize = 13.sp,
                color = subtitleColor
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp)
          ) {
            items(
              items = subDirectories,
              key = { it.absolutePath }
            ) { folder ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    currentDir = folder
                  }
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Outlined.Folder,
                  contentDescription = null,
                  tint = folderColor,
                  modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                  text = folder.name,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  color = textColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )

                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                  contentDescription = null,
                  tint = subtitleColor.copy(alpha = 0.4f),
                  modifier = Modifier.size(14.dp)
                )
              }

              HorizontalDivider(
                color = dividerColor.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
              )
            }
          }
        }

        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // 3. Action Buttons: Cancel and "Mover aquí"
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(42.dp)
          ) {
            Text(
              text = stringResource(R.string.permission_action_cancel),
              color = textColor,
              fontSize = 13.5.sp
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Button(
            onClick = {
              onConfirmMoveTo(currentDir)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = buttonBg,
              contentColor = buttonContent
            ),
            modifier = Modifier
              .height(42.dp)
              .testTag("device_storage_confirm_move_button")
          ) {
            Text(
              text = stringResource(R.string.game_backup_move_here),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}
