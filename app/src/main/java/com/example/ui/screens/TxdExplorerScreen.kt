package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.ui.viewmodel.ModstudioViewModel

/**
 * Screen for "Explorador TXD":
 * Clean table-like interface split equally into two halves (50% / 50%):
 * - Left half: Exteriores (textures from texdb/gta3)
 * - Right half: Interiores (textures from texdb/gta_int)
 */
@Composable
fun TxdExplorerScreen(
  onBack: () -> Unit,
  viewModel: ModstudioViewModel = viewModel(),
  exteriorTextures: List<String>? = null,
  interiorTextures: List<String>? = null,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val gta3TxdFiles by viewModel.gta3TxdFiles.collectAsStateWithLifecycle()
  val gtaIntTxdFiles by viewModel.gtaIntTxdFiles.collectAsStateWithLifecycle()

  val effectiveExteriorTextures = remember(exteriorTextures, gta3TxdFiles) {
    exteriorTextures ?: gta3TxdFiles.map { it.fileName }
  }
  val effectiveInteriorTextures = remember(interiorTextures, gtaIntTxdFiles) {
    interiorTextures ?: gtaIntTxdFiles.map { it.fileName }
  }

  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val screenBg = MaterialTheme.colorScheme.background
  val onBg = MaterialTheme.colorScheme.onBackground
  val headerBg = if (isDark) Color(0xFF1B1C22) else Color(0xFFFAFAFB)
  val dividerColor = if (isDark) Color(0xFF262830) else Color(0xFFE5E7EB)
  val subtleDividerColor = if (isDark) Color(0xFF22242C) else Color(0xFFF2F4F7)
  val emptyTextColor = if (isDark) Color(0xFF6B7280) else Color(0xFFBBBBBB)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(screenBg)
      .statusBarsPadding()
      .testTag("txd_explorer_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar: back button and "Explorador TXD" title
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
            .testTag("txd_explorer_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBg
          )
        }

        Text(
          text = stringResource(R.string.txd_explorer_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBg,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("txd_explorer_title")
        )
      }

      HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

      // Table Header: split 50% / 50% between Exteriores and Interiores
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(headerBg)
          .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Column Header: Exteriores
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.Center
        ) {
          val exteriorsTitle = if (effectiveExteriorTextures.isNotEmpty()) {
            "${stringResource(R.string.txd_explorer_col_exteriors)} (${effectiveExteriorTextures.size})"
          } else {
            stringResource(R.string.txd_explorer_col_exteriors)
          }
          Text(
            text = exteriorsTitle,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = onBg,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("txd_explorer_header_exteriors")
          )
        }

        // Vertical divider separating the two column headers
        VerticalDivider(
          color = dividerColor,
          modifier = Modifier.height(18.dp),
          thickness = 1.dp
        )

        // Right Column Header: Interiores
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.Center
        ) {
          val interiorsTitle = if (effectiveInteriorTextures.isNotEmpty()) {
            "${stringResource(R.string.txd_explorer_col_interiors)} (${effectiveInteriorTextures.size})"
          } else {
            stringResource(R.string.txd_explorer_col_interiors)
          }
          Text(
            text = interiorsTitle,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = onBg,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("txd_explorer_header_interiors")
          )
        }
      }

      HorizontalDivider(color = dividerColor, thickness = 1.dp)

      // Table Content Area: split down the middle (50% / 50%)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        // Left Section: Exteriores
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("txd_explorer_column_exteriors"),
          contentAlignment = Alignment.Center
        ) {
          if (effectiveExteriorTextures.isEmpty()) {
            Text(
              text = stringResource(R.string.txd_explorer_empty),
              fontSize = 12.sp,
              color = emptyTextColor,
              letterSpacing = 0.3.sp
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
              items(effectiveExteriorTextures) { textureName ->
                TxdFileItem(textureName = textureName, onBg = onBg, isDark = isDark)
              }
            }
          }
        }

        // Center vertical divider splitting the two halves
        VerticalDivider(
          color = subtleDividerColor,
          modifier = Modifier.fillMaxHeight(),
          thickness = 1.dp
        )

        // Right Section: Interiores
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("txd_explorer_column_interiors"),
          contentAlignment = Alignment.Center
        ) {
          if (effectiveInteriorTextures.isEmpty()) {
            Text(
              text = stringResource(R.string.txd_explorer_empty),
              fontSize = 12.sp,
              color = emptyTextColor,
              letterSpacing = 0.3.sp
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
              items(effectiveInteriorTextures) { textureName ->
                TxdFileItem(textureName = textureName, onBg = onBg, isDark = isDark)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TxdFileItem(
  textureName: String,
  onBg: Color,
  isDark: Boolean
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 6.dp, vertical = 5.dp)
  ) {
    Text(
      text = textureName,
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
      color = onBg,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
      color = if (isDark) Color(0xFF22242C) else Color(0xFFF2F3F5),
      thickness = 0.5.dp
    )
  }
}
