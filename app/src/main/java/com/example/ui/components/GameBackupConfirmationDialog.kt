package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import com.example.ui.theme.LightButtonColor
import com.example.ui.theme.LightButtonContentColor

/**
 * Clean, minimalist confirmation dialog shown before creating or completing a game backup.
 * Displays the Modstudio application logo, informs about storage requirements and safety benefits
 * using clean bullet points without boxes, and provides "Cancelar" and "Continuar" actions.
 */
@Composable
fun GameBackupConfirmationDialog(
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  isDarkMode: Boolean = false,
  modifier: Modifier = Modifier
) {
  val surfaceBg = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val subtitleColor = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
  val buttonBg = if (isDarkMode) GoldButtonColor else LightButtonColor
  val buttonContent = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = surfaceBg,
      shadowElevation = 8.dp,
      modifier = modifier
        .padding(horizontal = 24.dp, vertical = 24.dp)
        .fillMaxWidth()
        .testTag("game_backup_confirmation_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Modstudio Application Logo
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDarkMode) Color(0xFF262830) else Color(0xFFF3F4F6)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_modstudio_fg),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(14.dp))
              .testTag("game_backup_dialog_game_logo")
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title
        Text(
          text = stringResource(R.string.game_backup_dialog_title),
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = textColor,
          textAlign = TextAlign.Center,
          letterSpacing = 0.1.sp,
          modifier = Modifier.testTag("game_backup_dialog_title")
        )

        Spacer(modifier = Modifier.height(18.dp))

        val bulletColor = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22)

        // Point 1: File size & storage explanation (Clean bullet point, no card/box)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.Top
        ) {
          Box(
            modifier = Modifier
              .padding(top = 7.dp)
              .size(5.dp)
              .clip(CircleShape)
              .background(bulletColor)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.game_backup_dialog_size_title),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = textColor
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = stringResource(R.string.game_backup_dialog_size_desc),
              fontSize = 12.5.sp,
              lineHeight = 18.sp,
              color = subtitleColor
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Point 2: Benefits and safety explanation (Clean bullet point, no card/box)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.Top
        ) {
          Box(
            modifier = Modifier
              .padding(top = 7.dp)
              .size(5.dp)
              .clip(CircleShape)
              .background(bulletColor)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.game_backup_dialog_safety_title),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = textColor
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = stringResource(R.string.game_backup_dialog_safety_desc),
              fontSize = 12.5.sp,
              lineHeight = 18.sp,
              color = subtitleColor
            )
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Action Buttons: Cancelar & Continuar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = CircleShape,
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("game_backup_dialog_cancel_button")
          ) {
            Text(
              text = stringResource(R.string.permission_action_cancel),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium,
              color = if (isDarkMode) Color.White else Color(0xFF4B5563)
            )
          }

          Button(
            onClick = onConfirm,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = buttonBg,
              contentColor = buttonContent
            ),
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("game_backup_dialog_confirm_button")
          ) {
            Text(
              text = stringResource(R.string.game_backup_dialog_action_continue),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = buttonContent
            )
          }
        }
      }
    }
  }
}
