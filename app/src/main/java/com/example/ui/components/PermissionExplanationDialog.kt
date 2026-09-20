package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Pre-permission explanation dialog displayed before triggering system permission flows.
 * Displays the Modstudio logo, title, bullet list of required accesses (OBB, DATA, Storage),
 * and "Cancelar" / "Aceptar" action buttons.
 */
@Composable
fun PermissionExplanationDialog(
  onDismiss: () -> Unit,
  onAccept: () -> Unit,
  modifier: Modifier = Modifier
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      shadowElevation = 8.dp,
      modifier = modifier
        .padding(horizontal = 24.dp, vertical = 24.dp)
        .fillMaxWidth()
        .testTag("permission_explanation_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // App Logo Badge at top
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0xFFF6F7F9))
            .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            .testTag("permission_dialog_logo"),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_modstudio_fg),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape),
            contentScale = ContentScale.Crop
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dialog Title
        Text(
          text = stringResource(R.string.permission_dialog_title),
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF1E1F22),
          textAlign = TextAlign.Center,
          letterSpacing = 0.1.sp,
          modifier = Modifier.testTag("permission_dialog_title")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle description
        Text(
          text = stringResource(R.string.permission_dialog_subtitle),
          fontSize = 13.sp,
          lineHeight = 19.sp,
          color = Color(0xFF616161),
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("permission_dialog_subtitle")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bullet points with clean dots
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFB), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          PermissionBulletItem(
            text = stringResource(R.string.permission_point_obb),
            testTag = "bullet_obb"
          )
          PermissionBulletItem(
            text = stringResource(R.string.permission_point_data),
            testTag = "bullet_data"
          )
          PermissionBulletItem(
            text = stringResource(R.string.permission_point_storage),
            testTag = "bullet_storage"
          )
          PermissionBulletItem(
            text = stringResource(R.string.permission_point_local_safe),
            testTag = "bullet_local_safe"
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Two action buttons: Cancelar and Aceptar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = Color(0xFF555555)
            ),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("permission_dialog_cancel_button")
          ) {
            Text(
              text = stringResource(R.string.permission_action_cancel),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
          }

          Button(
            onClick = onAccept,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF141416),
              contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
              defaultElevation = 2.dp,
              pressedElevation = 4.dp
            ),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("permission_dialog_accept_button")
          ) {
            Text(
              text = stringResource(R.string.permission_action_accept),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionBulletItem(
  text: String,
  testTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag),
    verticalAlignment = Alignment.Top
  ) {
    // Elegant small dot
    Box(
      modifier = Modifier
        .padding(top = 7.dp, end = 10.dp)
        .size(6.dp)
        .clip(CircleShape)
        .background(Color(0xFF1E1F22))
    )
    Text(
      text = text,
      fontSize = 12.5.sp,
      lineHeight = 18.sp,
      fontWeight = FontWeight.Normal,
      color = Color(0xFF333333),
      modifier = Modifier.weight(1f)
    )
  }
}

/**
 * Pre-permission explanation dialog specifically for background notifications.
 * Shown after storage permissions are granted, before triggering system POST_NOTIFICATIONS dialog.
 */
@Composable
fun NotificationPermissionDialog(
  onDismiss: () -> Unit,
  onAccept: () -> Unit,
  modifier: Modifier = Modifier
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      shadowElevation = 8.dp,
      modifier = modifier
        .fillMaxWidth(0.90f)
        .padding(16.dp)
        .testTag("notification_permission_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // App icon with notification badge
        Box(
          modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_modstudio_fg),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = stringResource(R.string.permission_notifications_title),
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF1E1F22),
          textAlign = TextAlign.Center,
          letterSpacing = (-0.3).sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = stringResource(R.string.permission_notifications_desc),
          fontSize = 13.5.sp,
          lineHeight = 20.sp,
          fontWeight = FontWeight.Normal,
          color = Color(0xFF555555),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = Color(0xFF4B5563)
            ),
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .testTag("notification_permission_skip_button")
          ) {
            Text(
              text = stringResource(R.string.permission_notifications_skip),
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1
            )
          }

          Button(
            onClick = onAccept,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF141416),
              contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
              defaultElevation = 2.dp,
              pressedElevation = 4.dp
            ),
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .testTag("notification_permission_accept_button")
          ) {
            Text(
              text = stringResource(R.string.permission_notifications_allow),
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

