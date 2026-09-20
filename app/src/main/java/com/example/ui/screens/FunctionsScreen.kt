package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import com.example.ui.theme.LightButtonColor
import com.example.ui.theme.LightButtonContentColor

/**
 * Screen displaying application functions and configurations.
 * Arranged in a clean, vertical layout with generous spacing and pure white/dark styling.
 * 1. "Apariencia" (Theme toggle with golden buttons in Dark Mode).
 * 2. "Copias" (Container backup toggle with explanation mini-dialog on tap).
 * 3. "Copia de seguridad" (Game OBB and APK backup interface).
 */
@Composable
fun FunctionsScreen(
  onBack: () -> Unit,
  isDarkMode: Boolean,
  onToggleDarkMode: (Boolean) -> Unit,
  isBackupEnabled: Boolean = true,
  onToggleBackupEnabled: (Boolean) -> Unit = {},
  onOpenGameBackup: () -> Unit = {},
  onOpenSolution: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)
  val scrollState = rememberScrollState()

  var showBackupExplanationDialog by remember { mutableStateOf(false) }

  val backgroundColor = MaterialTheme.colorScheme.background
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onBackgroundColor = MaterialTheme.colorScheme.onBackground
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val outlineColor = MaterialTheme.colorScheme.outline
  val dividerColor = if (isDarkMode) Color(0xFF262830) else Color(0xFFF2F4F7)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
      .statusBarsPadding()
      .testTag("functions_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("functions_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBackgroundColor
          )
        }

        Text(
          text = stringResource(R.string.functions_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBackgroundColor,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("functions_title")
        )
      }

      HorizontalDivider(color = dividerColor, thickness = 1.dp)

      val switchColors = SwitchDefaults.colors(
        checkedThumbColor = if (isDarkMode) GoldButtonContentColor else Color.White,
        checkedTrackColor = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22),
        checkedBorderColor = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22),
        uncheckedThumbColor = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF6B7280),
        uncheckedTrackColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB),
        uncheckedBorderColor = if (isDarkMode) Color(0xFF3F4252) else Color(0xFFD1D5DB)
      )

      // Content: Clean, compact list of functions one below the other without cards or boxes
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 12.dp)
      ) {
        // Function 1: Modo oscuro / Apariencia (Prender / Apagar)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { onToggleDarkMode(!isDarkMode) }
            )
            .padding(vertical = 12.dp)
            .testTag("function_item_appearance"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isDarkMode) {
                stringResource(R.string.functions_dark_mode)
              } else {
                stringResource(R.string.functions_light_mode)
              },
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = onSurfaceColor,
              modifier = Modifier.testTag("functions_theme_mode_label")
            )
            Text(
              text = if (isDarkMode) {
                stringResource(R.string.functions_dark_mode_desc)
              } else {
                stringResource(R.string.functions_light_mode_desc)
              },
              fontSize = 12.sp,
              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Switch(
            checked = isDarkMode,
            onCheckedChange = onToggleDarkMode,
            colors = switchColors,
            modifier = Modifier.testTag("functions_theme_switch")
          )
        }

        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("function_item_backups"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showBackupExplanationDialog = true }
              )
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(R.string.functions_backups_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = onSurfaceColor,
                modifier = Modifier.testTag("functions_backups_label")
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info",
                tint = if (isDarkMode) GoldButtonColor.copy(alpha = 0.7f) else Color(0xFF9CA3AF),
                modifier = Modifier.size(14.dp)
              )
            }
            Text(
              text = if (isBackupEnabled) {
                stringResource(R.string.functions_backups_status_active)
              } else {
                stringResource(R.string.functions_backups_status_inactive)
              },
              fontSize = 12.sp,
              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Switch(
            checked = isBackupEnabled,
            onCheckedChange = onToggleBackupEnabled,
            colors = switchColors,
            modifier = Modifier.testTag("functions_backups_switch")
          )
        }

        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

        // Function 3: Copia de seguridad (Entrar a la interfaz)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onOpenGameBackup
            )
            .padding(vertical = 12.dp)
            .testTag("functions_game_backup_row"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.functions_game_backup_title),
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = onSurfaceColor
            )
            Text(
              text = stringResource(R.string.functions_game_backup_subtitle),
              fontSize = 12.sp,
              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = if (isDarkMode) GoldButtonColor else Color(0xFF9CA3AF),
            modifier = Modifier.size(14.dp)
          )
        }

        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
        // Function 4: Solución (Entrar a la interfaz, deshabilitado si no hay copia)
        val context = LocalContext.current
        val hasGameBackup = com.example.data.GameBackupManager.getInstance(context).hasGameBackup()
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              enabled = hasGameBackup,
              onClick = onOpenSolution
            )
            .padding(vertical = 12.dp)
            .alpha(if (hasGameBackup) 1f else 0.4f)
            .testTag("functions_solution_row"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.functions_solution_title),
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = onSurfaceColor
            )
            Text(
              text = stringResource(R.string.functions_solution_subtitle),
              fontSize = 12.sp,
              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = if (isDarkMode) GoldButtonColor else Color(0xFF9CA3AF),
            modifier = Modifier.size(14.dp)
          )
        }
        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
      }
    }

    // Mini-interface explanation dialog for "Copias"
    if (showBackupExplanationDialog) {
      AlertDialog(
        onDismissRequest = { showBackupExplanationDialog = false },
        containerColor = if (isDarkMode) Color(0xFF1C1D24) else Color.White,
        shape = RoundedCornerShape(20.dp),
        icon = {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(if (isDarkMode) Color(0xFF262832) else Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = null,
              tint = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22),
              modifier = Modifier.size(28.dp)
            )
          }
        },
        title = {
          Text(
            text = stringResource(R.string.functions_backups_dialog_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = stringResource(R.string.functions_backups_dialog_desc),
              fontSize = 13.5.sp,
              color = if (isDarkMode) Color(0xFFD1D5DB) else Color(0xFF4B5563),
              lineHeight = 19.sp,
              textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Current status indicator inside dialog
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDarkMode) Color(0xFF262832) else Color(0xFFF3F4F6))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(
                    if (isBackupEnabled) {
                      if (isDarkMode) GoldButtonColor else Color(0xFF16A34A)
                    } else {
                      Color(0xFF9CA3AF)
                    }
                  )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isBackupEnabled) {
                  stringResource(R.string.functions_backups_status_active)
                } else {
                  stringResource(R.string.functions_backups_status_inactive)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isBackupEnabled) {
                  if (isDarkMode) GoldButtonColor else Color(0xFF15803D)
                } else {
                  if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
                }
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { showBackupExplanationDialog = false },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isDarkMode) GoldButtonColor else LightButtonColor,
              contentColor = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("backup_dialog_confirm_button")
          ) {
            Text(
              text = stringResource(R.string.dialog_action_understood),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor
            )
          }
        }
      )
    }
  }
}

