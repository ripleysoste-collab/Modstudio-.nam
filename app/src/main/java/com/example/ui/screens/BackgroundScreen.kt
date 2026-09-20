package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BackgroundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for setting a local custom background image for Historial.
 * Works 100% offline, persistent on device storage.
 */
@Composable
fun BackgroundScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bgManager = remember { BackgroundManager.getInstance(context) }
  val currentBgFile by bgManager.backgroundImageFile.collectAsState()

  // Asynchronously decode bitmap on IO thread - never blocks UI thread or causes lag
  val currentBitmap by produceState<ImageBitmap?>(initialValue = null, currentBgFile) {
    value = withContext(Dispatchers.IO) {
      if (currentBgFile != null && currentBgFile!!.exists()) {
        try {
          BitmapFactory.decodeFile(currentBgFile!!.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
          null
        }
      } else null
    }
  }

  // Google Play zero-permission Photo Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val success = bgManager.saveBackgroundFromUri(uri)
        if (success) {
          Toast.makeText(
            context,
            context.getString(R.string.background_saved_success),
            Toast.LENGTH_SHORT
          ).show()
        } else {
          Toast.makeText(
            context,
            context.getString(R.string.background_error_loading),
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .statusBarsPadding()
      .testTag("background_screen")
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
            .testTag("bg_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = Color(0xFF1E1F22)
          )
        }

        Text(
          text = stringResource(R.string.background_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = Color(0xFF1E1F22),
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("bg_top_title")
        )
      }

      HorizontalDivider(
        color = Color(0xFFF0F2F5),
        thickness = 1.dp
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.background_empty_desc),
          fontSize = 13.sp,
          lineHeight = 20.sp,
          color = Color(0xFF555555),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("background_description")
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Visual Preview Container
        val previewBitmap = currentBitmap
        if (previewBitmap != null) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = stringResource(R.string.background_current_preview),
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF1E1F22),
              modifier = Modifier
                .align(Alignment.Start)
                .testTag("preview_label")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
              modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE2E4E8), RoundedCornerShape(16.dp))
                .testTag("background_preview_box")
            ) {
              Image(
                bitmap = previewBitmap,
                contentDescription = stringResource(R.string.background_current_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons: Change or Remove
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Button(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF141416),
                  contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier.testTag("change_background_button")
              ) {
                Icon(
                  imageVector = Icons.Outlined.AddPhotoAlternate,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.background_change_action),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              OutlinedButton(
                onClick = {
                  scope.launch {
                    val removed = bgManager.removeBackground()
                    if (removed) {
                      Toast.makeText(
                        context,
                        context.getString(R.string.background_removed_success),
                        Toast.LENGTH_SHORT
                      ).show()
                    }
                  }
                },
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = Color(0xFFBA1A1A)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("remove_background_button")
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = stringResource(R.string.background_remove_action),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        } else {
          // Empty placeholder container
          Box(
            modifier = Modifier
              .fillMaxWidth(0.82f)
              .aspectRatio(9f / 14f)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFFFAFAFB))
              .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
              .testTag("empty_preview_box"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(44.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = stringResource(R.string.background_no_image),
                fontSize = 13.sp,
                color = Color(0xFF888888)
              )
            }
          }

          Spacer(modifier = Modifier.height(28.dp))

          // Primary "Insertar fondo" Button
          Button(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF141416),
              contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
              defaultElevation = 2.dp,
              pressedElevation = 4.dp
            ),
            modifier = Modifier.testTag("insert_background_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.AddPhotoAlternate,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.background_insert_action),
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 0.2.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  }
}
