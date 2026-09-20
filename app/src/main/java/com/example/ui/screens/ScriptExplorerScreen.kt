package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.local.entity.ModFileEntry
import com.example.ui.viewmodel.ModstudioViewModel
import kotlinx.coroutines.launch

/**
 * Screen for "Explorador de Scripts":
 * Displays game scripts divided into 3 distinct parts:
 * - 1. CSA (.csa CLEO Scripts)
 * - 2. CSI (.csi CLEO Menu / Invoked Scripts)
 * - 3. FXT (.fxt CLEO Text translation tables)
 *
 * Each column has an exact width of 50% of the screen width (maxWidth / 2),
 * preserving the exact 2-column aesthetic of the DFF & TXD explorers.
 * Users can swipe or slide horizontally with a gesture to reveal the 3rd part.
 */
@Composable
fun ScriptExplorerScreen(
  onBack: () -> Unit,
  viewModel: ModstudioViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  LaunchedEffect(Unit) {
    viewModel.refreshScripts()
  }

  val csaFiles by viewModel.csaFiles.collectAsStateWithLifecycle()
  val csiFiles by viewModel.csiFiles.collectAsStateWithLifecycle()
  val fxtFiles by viewModel.fxtFiles.collectAsStateWithLifecycle()

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

  val horizontalScrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(screenBg)
      .statusBarsPadding()
      .testTag("script_explorer_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar: back button and small "Explorador de Scripts" title
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
            .testTag("script_explorer_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBg
          )
        }

        Text(
          text = stringResource(R.string.script_explorer_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBg,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("script_explorer_title")
        )
      }

      HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

      // Content area with 3 columns, each exact 50% screen width
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        val columnWidth = maxWidth / 2

        Row(
          modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
            .testTag("script_explorer_horizontal_row")
        ) {
          // ================= COLUMN 1: CSA =================
          Column(
            modifier = Modifier
              .width(columnWidth)
              .fillMaxHeight()
              .testTag("script_column_csa")
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              val csaTitle = if (csaFiles.isNotEmpty()) {
                "${stringResource(R.string.script_explorer_col_csa)} (${csaFiles.size})"
              } else {
                stringResource(R.string.script_explorer_col_csa)
              }
              Text(
                text = csaTitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = onBg,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("script_header_csa")
              )
            }

            HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentAlignment = Alignment.Center
            ) {
              if (csaFiles.isEmpty()) {
                Text(
                  text = stringResource(R.string.script_explorer_empty),
                  fontSize = 12.sp,
                  color = emptyTextColor,
                  letterSpacing = 0.3.sp,
                  modifier = Modifier.testTag("script_empty_csa")
                )
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                  items(csaFiles, key = { it.id }) { file ->
                    ScriptFileItem(
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

          // Divider 1: Between CSA and CSI
          VerticalDivider(
            color = dividerColor,
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp
          )

          // ================= COLUMN 2: CSI =================
          Column(
            modifier = Modifier
              .width(columnWidth)
              .fillMaxHeight()
              .testTag("script_column_csi")
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              val csiTitle = if (csiFiles.isNotEmpty()) {
                "${stringResource(R.string.script_explorer_col_csi)} (${csiFiles.size})"
              } else {
                stringResource(R.string.script_explorer_col_csi)
              }
              Text(
                text = csiTitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = onBg,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("script_header_csi")
              )
            }

            HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentAlignment = Alignment.Center
            ) {
              if (csiFiles.isEmpty()) {
                Text(
                  text = stringResource(R.string.script_explorer_empty),
                  fontSize = 12.sp,
                  color = emptyTextColor,
                  letterSpacing = 0.3.sp,
                  modifier = Modifier.testTag("script_empty_csi")
                )
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                  items(csiFiles, key = { it.id }) { file ->
                    ScriptFileItem(
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

          // Divider 2: Between CSI and FXT
          VerticalDivider(
            color = dividerColor,
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp
          )

          // ================= COLUMN 3: FXT =================
          Column(
            modifier = Modifier
              .width(columnWidth)
              .fillMaxHeight()
              .testTag("script_column_fxt")
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              val fxtTitle = if (fxtFiles.isNotEmpty()) {
                "${stringResource(R.string.script_explorer_col_fxt)} (${fxtFiles.size})"
              } else {
                stringResource(R.string.script_explorer_col_fxt)
              }
              Text(
                text = fxtTitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = onBg,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("script_header_fxt")
              )
            }

            HorizontalDivider(color = subtleDividerColor, thickness = 1.dp)

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentAlignment = Alignment.Center
            ) {
              if (fxtFiles.isEmpty()) {
                Text(
                  text = stringResource(R.string.script_explorer_empty),
                  fontSize = 12.sp,
                  color = emptyTextColor,
                  letterSpacing = 0.3.sp,
                  modifier = Modifier.testTag("script_empty_fxt")
                )
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                  items(fxtFiles, key = { it.id }) { file ->
                    ScriptFileItem(
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
  }
}

@Composable
private fun ScriptFileItem(
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
      .testTag("script_file_${file.id}")
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
