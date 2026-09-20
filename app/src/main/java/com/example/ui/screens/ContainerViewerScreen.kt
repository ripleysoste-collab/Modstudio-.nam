package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.parser.ImgArchiveReader
import com.example.data.parser.ImgItemEntry
import com.example.data.repository.ModstudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Clean, minimalist, temporary inspector screen for viewing .dff model contents
 * of GTA containers (gta3.img or gta_int.img).
 *
 * Characteristics:
 * - Temporary view: does not cache heavy buffers; releases memory when closed.
 * - Minimalist presentation: displays the .dff filename on the left and its file weight on the right.
 * - No bulky cards or extraneous logos for max cleanliness and performance.
 * - Single search input to easily locate models among thousands of entries.
 * - Read-only: does not modify file locations or contents.
 */
@Composable
fun ContainerViewerScreen(
  containerName: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val repository = remember { ModstudioRepository.getInstance(context) }

  // Clean exit when pressing system back button
  BackHandler {
    onBack()
  }

  // Load entries asynchronously and release when screen is dismissed
  val entriesState by produceState<List<ImgItemEntry>?>(initialValue = null, key1 = containerName) {
    value = withContext(Dispatchers.IO) {
      val containerFile = repository.getContainerFile(containerName)
      ImgArchiveReader.readEntries(containerFile)
    }
  }

  var searchQuery by remember { mutableStateOf("") }

  val filteredEntries by remember(entriesState, searchQuery) {
    derivedStateOf {
      val list = entriesState ?: emptyList()
      if (searchQuery.isBlank()) {
        list
      } else {
        val q = searchQuery.trim()
        list.filter { it.name.contains(q, ignoreCase = true) }
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF9FAFB))
      .statusBarsPadding()
      .testTag("container_viewer_screen")
  ) {
    // 1. Header Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color.White)
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("container_viewer_back_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.action_back),
          tint = Color(0xFF1E1F22)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = containerName,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1F22),
            modifier = Modifier.testTag("container_viewer_title")
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "IMG",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFB45309)
            )
          }
        }
        val totalCount = entriesState?.size ?: 0
        Text(
          text = if (entriesState == null) {
            stringResource(R.string.container_viewer_loading)
          } else {
            stringResource(R.string.container_viewer_total_items, totalCount)
          },
          fontSize = 12.sp,
          color = Color(0xFF6B7280)
        )
      }
    }

    HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

    // 2. Search Input Bar (No category chips, clean and sleek)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color.White)
        .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = Color(0xFF9CA3AF),
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          singleLine = true,
          textStyle = TextStyle(
            fontSize = 14.sp,
            color = Color(0xFF1F2937)
          ),
          cursorBrush = SolidColor(Color(0xFF3B82F6)),
          modifier = Modifier
            .weight(1f)
            .testTag("container_viewer_search_input"),
          decorationBox = { innerTextField ->
            if (searchQuery.isEmpty()) {
              Text(
                text = stringResource(R.string.container_viewer_search_hint),
                fontSize = 13.sp,
                color = Color(0xFF9CA3AF)
              )
            }
            innerTextField()
          }
        )
        if (searchQuery.isNotEmpty()) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Clear",
            tint = Color(0xFF6B7280),
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .clickable { searchQuery = "" }
          )
        }
      }
    }

    HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

    // Read-only advisory indicator bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFFF9FAFB))
        .padding(horizontal = 16.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.container_viewer_readonly_notice),
        fontSize = 11.5.sp,
        color = Color(0xFF6B7280),
        modifier = Modifier.weight(1f)
      )
      Text(
        text = "${filteredEntries.size} / ${entriesState?.size ?: 0}",
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF4B5563)
      )
    }

    HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.8.dp)

    // 3. Content List: Clean, high-performance LazyColumn showing files one below another
    val entries = entriesState
    if (entries == null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(
            color = Color(0xFF3B82F6),
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.container_viewer_loading),
            fontSize = 13.sp,
            color = Color(0xFF6B7280)
          )
        }
      }
    } else if (filteredEntries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(horizontal = 32.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.FolderZip,
            contentDescription = null,
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(44.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = stringResource(R.string.container_viewer_empty),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4B5563)
          )
        }
      }
    } else {
      // Clean white list container for high readability and professional layout
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .background(Color.White)
          .testTag("container_entries_lazy_column"),
        contentPadding = PaddingValues(vertical = 4.dp)
      ) {
        items(
          items = filteredEntries,
          key = { it.name }
        ) { item ->
          CleanDffRowItem(
            entry = item,
            formattedSize = formatBytes(item.sizeBytes)
          )
          HorizontalDivider(
            color = Color(0xFFF3F4F6),
            thickness = 0.8.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      }
    }
  }
}

/**
 * Clean and professional row item:
 * Displays the .dff filename directly on the left and the file weight on the right.
 * No bulky surrounding cards, no extra logos before the name.
 */
@Composable
private fun CleanDffRowItem(
  entry: ImgItemEntry,
  formattedSize: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag("container_item_${entry.name}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Model name on the left
    Text(
      text = entry.name,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = Color(0xFF1E1F22),
      modifier = Modifier.weight(1f)
    )

    // Weight/size on the right
    Text(
      text = formattedSize,
      fontSize = 13.sp,
      fontWeight = FontWeight.Normal,
      color = Color(0xFF6B7280)
    )
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return when {
    mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
    kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
    else -> "$bytes B"
  }
}
