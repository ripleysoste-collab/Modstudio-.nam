@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.parser.ArchiveDirectoryContent
import com.example.data.parser.ArchiveExplorerHelper
import com.example.data.parser.ArchiveFileItem
import com.example.data.parser.ArchiveFolderItem
import com.example.data.repository.ModstudioRepository
import com.example.ui.components.DeviceStoragePickerModal
import com.example.ui.components.ExplorerFileActionDropdownMenu
import com.example.ui.theme.GoldButtonColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Temporary file explorer for inspecting files and folders inside a backup archive (OBB / APK).
 * Features:
 * - Clean title "Explorador" with back navigation.
 * - Deep folder navigation down to the bottom.
 * - File icons matching Modstudio's established design language.
 * - Long-press or tap contextual menu with "Mover" action.
 * - Device storage directory picker to move files directly to chosen destination.
 */
@Composable
fun GameBackupExplorerScreen(
  archiveFile: File,
  isDarkMode: Boolean = false,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val repo = remember { ModstudioRepository.getInstance(context) }

  // Current folder path inside the archive ("" represents root)
  var currentRelativePath by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }

  // State for the "Mover" workflow
  var selectedFileForMoveMenu by remember { mutableStateOf<ArchiveFileItem?>(null) }
  var isMoveMenuExpanded by remember { mutableStateOf(false) }
  var activeFileForStoragePicker by remember { mutableStateOf<ArchiveFileItem?>(null) }

  // Handle back navigation: go up one folder if inside subfolder; otherwise exit screen
  fun handleBackNavigation() {
    if (currentRelativePath.isNotEmpty()) {
      val lastSlash = currentRelativePath.lastIndexOf('/')
      currentRelativePath = if (lastSlash != -1) {
        currentRelativePath.substring(0, lastSlash)
      } else {
        ""
      }
    } else {
      onBack()
    }
  }

  BackHandler {
    handleBackNavigation()
  }

  // Load directory entries reactively
  val directoryContentState by produceState<ArchiveDirectoryContent?>(
    initialValue = null,
    key1 = archiveFile.absolutePath,
    key2 = currentRelativePath
  ) {
    value = withContext(Dispatchers.IO) {
      ArchiveExplorerHelper.readDirectory(archiveFile, currentRelativePath)
    }
  }

  // Theme colors
  val bg = if (isDarkMode) Color(0xFF141518) else Color(0xFFF9FAFB)
  val headerBg = if (isDarkMode) Color(0xFF1E1F25) else Color.White
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val subtitleColor = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
  val dividerColor = if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)
  val searchBg = if (isDarkMode) Color(0xFF282A33) else Color(0xFFF3F4F6)

  // Filter items based on search query
  val filteredFolders by remember(directoryContentState, searchQuery) {
    derivedStateOf {
      val folders = directoryContentState?.folders ?: emptyList()
      if (searchQuery.isBlank()) folders else folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
  }

  val filteredFiles by remember(directoryContentState, searchQuery) {
    derivedStateOf {
      val files = directoryContentState?.files ?: emptyList()
      if (searchQuery.isBlank()) files else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(bg)
      .statusBarsPadding()
      .testTag("game_backup_explorer_screen")
  ) {
    // 1. Header Bar: Back button and "Explorador" title
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(headerBg)
        .padding(horizontal = 4.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = { handleBackNavigation() },
        modifier = Modifier
          .size(48.dp)
          .testTag("game_backup_explorer_back_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.action_back),
          tint = textColor
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.explorer_title),
          fontSize = 16.5.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = textColor,
          modifier = Modifier.testTag("game_backup_explorer_title")
        )
        Text(
          text = archiveFile.name,
          fontSize = 11.5.sp,
          color = subtitleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HorizontalDivider(color = dividerColor, thickness = 1.dp)

    // 2. Breadcrumb path indicator
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(if (isDarkMode) Color(0xFF191A20) else Color.White)
        .padding(horizontal = 16.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val pathSegments = remember(currentRelativePath) {
        if (currentRelativePath.isEmpty()) emptyList() else currentRelativePath.split('/')
      }

      Text(
        text = "/",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (pathSegments.isEmpty()) textColor else subtitleColor,
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .combinedClickable(
            onClick = { currentRelativePath = "" }
          )
          .padding(horizontal = 4.dp, vertical = 2.dp)
      )

      pathSegments.forEachIndexed { index, segment ->
        Text(
          text = " > ",
          fontSize = 12.sp,
          color = subtitleColor.copy(alpha = 0.6f)
        )
        val isLast = index == pathSegments.lastIndex
        Text(
          text = segment,
          fontSize = 12.5.sp,
          fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
          color = if (isLast) textColor else subtitleColor,
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
              onClick = {
                currentRelativePath = pathSegments.take(index + 1).joinToString("/")
              }
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
        )
      }
    }

    HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

    // 3. Search Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(headerBg)
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(searchBg, RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = subtitleColor,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          singleLine = true,
          textStyle = TextStyle(
            fontSize = 13.5.sp,
            color = textColor
          ),
          cursorBrush = SolidColor(if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22)),
          modifier = Modifier.weight(1f),
          decorationBox = { innerTextField ->
            if (searchQuery.isEmpty()) {
              Text(
                text = stringResource(R.string.container_viewer_search_hint),
                fontSize = 13.sp,
                color = subtitleColor
              )
            }
            innerTextField()
          }
        )
        if (searchQuery.isNotEmpty()) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Limpiar",
            tint = subtitleColor,
            modifier = Modifier
              .size(17.dp)
              .clip(CircleShape)
              .combinedClickable(onClick = { searchQuery = "" })
          )
        }
      }
    }

    HorizontalDivider(color = dividerColor, thickness = 1.dp)

    // 4. Explorer Item List: Folders first, then Files
    val content = directoryContentState
    if (content == null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(
            color = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22),
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.container_viewer_loading),
            fontSize = 13.sp,
            color = subtitleColor
          )
        }
      }
    } else if (filteredFolders.isEmpty() && filteredFiles.isEmpty()) {
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
            tint = subtitleColor.copy(alpha = 0.4f),
            modifier = Modifier.size(46.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = stringResource(R.string.game_backup_explorer_empty),
            fontSize = 13.5.sp,
            color = subtitleColor
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .background(headerBg)
          .testTag("backup_explorer_lazy_column"),
        contentPadding = PaddingValues(vertical = 4.dp)
      ) {
        // Folders list
        items(
          items = filteredFolders,
          key = { "folder_${it.fullPath}" }
        ) { folder ->
          ExplorerFolderRow(
            folder = folder,
            isDarkMode = isDarkMode,
            onClick = {
              currentRelativePath = folder.fullPath
              searchQuery = ""
            }
          )
          HorizontalDivider(
            color = dividerColor.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }

        // Files list
        items(
          items = filteredFiles,
          key = { "file_${it.fullPath}" }
        ) { file ->
          Box {
            ExplorerFileRow(
              file = file,
              isDarkMode = isDarkMode,
              formattedSize = repo.formatFileSize(file.sizeBytes),
              onClick = {
                selectedFileForMoveMenu = file
                isMoveMenuExpanded = true
              },
              onLongClick = {
                selectedFileForMoveMenu = file
                isMoveMenuExpanded = true
              }
            )

            if (selectedFileForMoveMenu == file) {
              ExplorerFileActionDropdownMenu(
                expanded = isMoveMenuExpanded,
                onDismissRequest = {
                  isMoveMenuExpanded = false
                  selectedFileForMoveMenu = null
                },
                fileName = file.name,
                onMove = {
                  isMoveMenuExpanded = false
                  activeFileForStoragePicker = file
                },
                isDarkMode = isDarkMode
              )
            }
          }

          HorizontalDivider(
            color = dividerColor.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      }
    }
  }

  // 5. Device Storage Directory Picker Modal ("Mover")
  val fileToMove = activeFileForStoragePicker
  if (fileToMove != null) {
    DeviceStoragePickerModal(
      fileNameToMove = fileToMove.name,
      isDarkMode = isDarkMode,
      onDismiss = { activeFileForStoragePicker = null },
      onConfirmMoveTo = { targetDir ->
        activeFileForStoragePicker = null
        coroutineScope.launch {
          withContext(Dispatchers.IO) {
            val result = ArchiveExplorerHelper.extractSingleFile(
              archiveFile = archiveFile,
              entryPath = fileToMove.fullPath,
              targetDir = targetDir
            )
            withContext(Dispatchers.Main) {
              if (result.isSuccess) {
                Toast.makeText(
                  context,
                  context.getString(R.string.game_backup_move_success, targetDir.name),
                  Toast.LENGTH_LONG
                ).show()
              } else {
                Toast.makeText(
                  context,
                  "Error al mover archivo",
                  Toast.LENGTH_SHORT
                ).show()
              }
            }
          }
        }
      }
    )
  }
}

/**
 * Clean folder row inside the archive explorer.
 */
@Composable
private fun ExplorerFolderRow(
  folder: ArchiveFolderItem,
  isDarkMode: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val subtitleColor = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
  val folderTint = if (isDarkMode) GoldButtonColor else Color(0xFFF59E0B)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 11.dp)
      .testTag("explorer_folder_${folder.name}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Outlined.Folder,
      contentDescription = null,
      tint = folderTint,
      modifier = Modifier.size(24.dp)
    )

    Spacer(modifier = Modifier.width(14.dp))

    Text(
      text = folder.name,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = textColor,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Icon(
      imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
      contentDescription = null,
      tint = subtitleColor.copy(alpha = 0.45f),
      modifier = Modifier.size(13.dp)
    )
  }
}

/**
 * Clean file row inside the archive explorer.
 * Supports both single tap and long press to trigger the contextual "Mover" action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerFileRow(
  file: ArchiveFileItem,
  isDarkMode: Boolean,
  formattedSize: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val textColor = if (isDarkMode) Color.White else Color(0xFF1E1F22)
  val subtitleColor = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)

  Row(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
      )
      .padding(horizontal = 16.dp, vertical = 11.dp)
      .testTag("explorer_file_${file.name}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Exact file icon matching Modstudio's visual system
    ArchiveFileIcon(fileName = file.name, isDarkMode = isDarkMode)

    Spacer(modifier = Modifier.width(14.dp))

    Text(
      text = file.name,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
      text = formattedSize,
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Normal,
      color = subtitleColor
    )
  }
}

/**
 * Renders the accurate icon for any file inside an archive according to Modstudio's established design.
 */
@Composable
private fun ArchiveFileIcon(
  fileName: String,
  isDarkMode: Boolean,
  modifier: Modifier = Modifier
) {
  val lower = fileName.lowercase()
  when {
    lower.endsWith(".apk") -> {
      Image(
        painter = painterResource(id = R.drawable.ic_gtasa_game_logo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
          .size(24.dp)
          .clip(RoundedCornerShape(6.dp))
      )
    }
    lower.endsWith(".img") -> {
      // Same container disk icon in miniature (24.dp height)
      com.example.ui.components.GtaFileDocumentIcon(
        modifier = modifier.size(width = 20.dp, height = 24.dp)
      )
    }
    lower.endsWith(".obb") || lower.endsWith(".col") -> {
      Image(
        painter = painterResource(id = R.drawable.ic_obb_file),
        contentDescription = null,
        modifier = modifier.size(24.dp)
      )
    }
    lower.endsWith(".txd") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> {
      Icon(
        imageVector = Icons.Outlined.Image,
        contentDescription = null,
        tint = if (isDarkMode) GoldButtonColor else Color(0xFF3B82F6),
        modifier = modifier.size(22.dp)
      )
    }
    lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg") ||
      lower == "feet" || lower == "genrl" || lower == "pain_a" || lower == "script" || lower == "weapons" ||
      lower == "beats" || lower == "ch" || lower == "co" || lower == "ds" || lower == "rg" -> {
      Icon(
        imageVector = Icons.Outlined.AudioFile,
        contentDescription = null,
        tint = if (isDarkMode) GoldButtonColor else Color(0xFF8B5CF6),
        modifier = modifier.size(22.dp)
      )
    }
    lower.endsWith(".scm") || lower.endsWith(".dat") || lower.endsWith(".ide") ||
      lower.endsWith(".ipl") || lower.endsWith(".cfg") || lower.endsWith(".txt") ||
      lower.endsWith(".csa") || lower.endsWith(".csi") || lower.endsWith(".zon") -> {
      Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        tint = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
        modifier = modifier.size(22.dp)
      )
    }
    lower.endsWith(".so") || lower.endsWith(".dex") -> {
      Icon(
        imageVector = Icons.Outlined.Code,
        contentDescription = null,
        tint = if (isDarkMode) GoldButtonColor else Color(0xFF10B981),
        modifier = modifier.size(22.dp)
      )
    }
    else -> {
      Icon(
        imageVector = Icons.Outlined.InsertDriveFile,
        contentDescription = null,
        tint = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF9CA3AF),
        modifier = modifier.size(22.dp)
      )
    }
  }
}
