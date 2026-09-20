package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LanguageManager

/**
 * Clean, minimalist language selection interface.
 * Shows supported languages one below the other without flags, only clean names.
 * Changes are instantly applied and persisted to local storage.
 */
@Composable
fun LanguagesScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val languageManager = remember { LanguageManager.getInstance(context) }
  val currentLanguageCode by languageManager.currentLanguage.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .statusBarsPadding()
      .testTag("languages_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("languages_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = Color(0xFF1E1F22)
          )
        }

        Text(
          text = stringResource(R.string.languages_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = Color(0xFF1E1F22),
          modifier = Modifier
            .padding(start = 6.dp)
            .testTag("languages_top_title")
        )
      }

      HorizontalDivider(
        color = Color(0xFFF0F2F5),
        thickness = 1.dp
      )

      // Main content: clean list of languages placed one below the other
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = stringResource(R.string.languages_subtitle),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF6B7280),
          modifier = Modifier.padding(bottom = 6.dp)
        )

        LanguageManager.SUPPORTED_LANGUAGES.forEach { lang ->
          val isSelected = lang.code == currentLanguageCode

          val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) Color(0xFFF8F9FA) else Color.White,
            label = "lang_bg"
          )
          val borderColor by animateColorAsState(
            targetValue = if (isSelected) Color(0xFF1E1F22) else Color(0xFFE5E7EB),
            label = "lang_border"
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
              )
              .background(backgroundColor)
              .clickable {
                if (!isSelected) {
                  languageManager.setLanguage(lang.code)
                  Toast.makeText(
                    context,
                    context.getString(R.string.language_saved_toast),
                    Toast.LENGTH_SHORT
                  ).show()
                }
              }
              .padding(horizontal = 18.dp, vertical = 16.dp)
              .testTag("language_item_${lang.code}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Clean native name without flag
            Text(
              text = lang.displayName,
              fontSize = 15.5.sp,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
              color = Color(0xFF1E1F22),
              letterSpacing = 0.2.sp
            )

            // Selection indicator
            if (isSelected) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF1E1F22)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Seleccionado",
                  tint = Color.White,
                  modifier = Modifier.size(14.dp)
                )
              }
            } else {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .border(1.5.dp, Color(0xFFD1D5DB), CircleShape)
              )
            }
          }
        }
      }
    }
  }
}
