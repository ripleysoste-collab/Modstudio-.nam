package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.local.entity.ModFileEntry
import com.example.ui.viewmodel.ModstudioViewModel

/**
 * Screen for "Explorador":
 * Clean table-like interface split equally into two halves:
 * - Left half: IMG (from local Room database)
 * - Right half: TXD (from local Room database)
 */
@Composable
fun ExplorerScreen(
  onBack: () -> Unit,
  viewModel: ModstudioViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val gta3Files by viewModel.gta3DffFiles.collectAsStateWithLifecycle()
  val gtaIntFiles by viewModel.gtaIntDffFiles.collectAsStateWithLifecycle()

  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val screenBg = MaterialTheme.colorScheme.background
  val onBg = MaterialTheme.colorScheme.onBackground
  val headerBg = if (isDark) Color(0xFF1B1C22) else Color(0xFFFAFAFB)
  val dividerColor = if (isDark) Color(0xFF262830) else Color(0xFFE5E7EB)
  val subtleDividerColor = if (isDark) Color(0xFF22242C) else Color(0xFFF2F4F7)
  val emptyTextColor = if (isDark) Color(0xFF6B7280) else Color(0xFFBBBBBB)
  val itemText = if (isDark) Color(0xFFEEEEEE) else Color(0xFF1E1F22)
  val itemSubtext = if (isDark) Color(0xFF9E9E9E) else Color(0xFF8C9199)
  val itemDivider = if (isDark) Color(0xFF262830) else Color(0xFFF2F3F5)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(screenBg)
      .statusBarsPadding()
      .testTag("explorer_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar: back button and small "Explorador de DFF" title
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
            .testTag("explorer_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBg
          )
        }

        Text(
          text = stringResource(R.string.explorer_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBg,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("explorer_title")
        )
      }

      HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

      // Table Header: split 50% / 50% between GTA3.IMG (Exteriores) and GTA_INT.IMG (Interiores)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(headerBg)
          .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Column Header: GTA3.IMG
        Box(
          modifier = Modifier
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          val gta3HeaderText = if (gta3Files.isNotEmpty()) {
            "${stringResource(R.string.explorer_col_gta3)} (${gta3Files.size})"
          } else {
            stringResource(R.string.explorer_col_gta3)
          }
          Text(
            text = gta3HeaderText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = onBg,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("explorer_header_gta3")
          )
        }

        // Vertical divider separating the two columns
        VerticalDivider(
          color = dividerColor,
          modifier = Modifier.height(18.dp),
          thickness = 1.dp
        )

        // Right Column Header: GTA_INT.IMG
        Box(
          modifier = Modifier
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          val gtaIntHeaderText = if (gtaIntFiles.isNotEmpty()) {
            "${stringResource(R.string.explorer_col_gta_int)} (${gtaIntFiles.size})"
          } else {
            stringResource(R.string.explorer_col_gta_int)
          }
          Text(
            text = gtaIntHeaderText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = onBg,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("explorer_header_gta_int")
          )
        }
      }

      HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

      // Table Content Area: split down the middle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        // Left Section: GTA3.IMG (.dff)
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("explorer_column_gta3"),
          contentAlignment = Alignment.Center
        ) {
          if (gta3Files.isEmpty()) {
            Text(
              text = stringResource(R.string.explorer_empty),
              fontSize = 12.sp,
              color = emptyTextColor,
              letterSpacing = 0.3.sp
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
              items(gta3Files, key = { it.id }) { file ->
                ExplorerFileItem(
                  file = file,
                  textColor = itemText,
                  subtextColor = itemSubtext,
                  dividerColor = itemDivider
                )
              }
            }
          }
        }

        // Center vertical divider splitting the two halves
        VerticalDivider(
          color = dividerColor,
          modifier = Modifier.fillMaxHeight(),
          thickness = 1.dp
        )

        // Right Section: GTA_INT.IMG (.dff)
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("explorer_column_gta_int"),
          contentAlignment = Alignment.Center
        ) {
          if (gtaIntFiles.isEmpty()) {
            Text(
              text = stringResource(R.string.explorer_empty),
              fontSize = 12.sp,
              color = emptyTextColor,
              letterSpacing = 0.3.sp
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
              items(gtaIntFiles, key = { it.id }) { file ->
                ExplorerFileItem(
                  file = file,
                  textColor = itemText,
                  subtextColor = itemSubtext,
                  dividerColor = itemDivider
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExplorerFileItem(
  file: ModFileEntry,
  textColor: Color,
  subtextColor: Color,
  dividerColor: Color
) {
  val formattedSize = when {
    file.sizeBytes > 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", file.sizeBytes / (1024.0 * 1024.0))
    file.sizeBytes > 1024 -> String.format(java.util.Locale.US, "%.0f KB", file.sizeBytes / 1024.0)
    else -> "${file.sizeBytes} B"
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 6.dp, vertical = 5.dp)
      .testTag("mod_file_${file.id}")
  ) {
    Text(
      text = file.fileName,
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(1.dp))
    Text(
      text = formattedSize,
      fontSize = 10.sp,
      color = subtextColor
    )
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
  }
}
