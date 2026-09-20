package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.BackgroundManager
import com.example.data.local.entity.HistoryEntry
import com.example.data.local.entity.ModFileEntry
import com.example.ui.components.ContainerActionDropdownMenu
import com.example.ui.components.GtaContainerCardsRow
import com.example.ui.components.SingleContainerCard
import com.example.ui.viewmodel.ModstudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for "Historial": clean, reactive interface backed by the local Room database.
 * Displays audit logs, mod scans, and operations persisted in device storage.
 * If a background is set via "Fondo", it is displayed behind the content.
 */
@Composable
fun HistoryScreen(
  onBack: () -> Unit,
  onOpenInformation: () -> Unit = {},
  onOpenBackground: () -> Unit = {},
  onOpenExplorer: () -> Unit = {},
  onOpenTxdExplorer: () -> Unit = {},
  onOpenScriptExplorer: () -> Unit = {},
  onOpenCleoMenu: () -> Unit = {},
  onOpenLanguages: () -> Unit = {},
  onOpenFunctions: () -> Unit = {},
  onViewContainer: (String) -> Unit = {},
  viewModel: ModstudioViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  var isMenuOpen by remember { mutableStateOf(false) }
  val historyList by viewModel.historyList.collectAsStateWithLifecycle()
  val gtaContainers by viewModel.gtaContainers.collectAsStateWithLifecycle()
  var selectedContainerFile by remember { mutableStateOf<java.io.File?>(null) }
  var selectedContainerTitle by remember { mutableStateOf("") }
  var containerDffs by remember { mutableStateOf<List<com.example.data.parser.ImgItemEntry>>(emptyList()) }

  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val screenBgColor = MaterialTheme.colorScheme.background
  val onBgColor = MaterialTheme.colorScheme.onBackground
  val topDividerColor = if (isDark) Color(0xFF262830) else Color(0xFFF2F4F7)

  BackHandler {
    if (isMenuOpen) {
      isMenuOpen = false
    } else {
      onBack()
    }
  }

  val context = LocalContext.current
  val bgManager = remember { BackgroundManager.getInstance(context) }
  val customBgFile by bgManager.backgroundImageFile.collectAsState()

  // Asynchronously decode bitmap on IO thread - never blocks UI thread or causes lag
  val customBitmap by produceState<ImageBitmap?>(initialValue = null, customBgFile) {
    value = withContext(Dispatchers.IO) {
      if (customBgFile != null && customBgFile!!.exists()) {
        try {
          BitmapFactory.decodeFile(customBgFile!!.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
          null
        }
      } else null
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(screenBgColor)
      .testTag("history_screen")
  ) {
    // If a custom background has been set in local storage, display it
    val bgBitmap = customBitmap
    if (bgBitmap != null) {
      Image(
        bitmap = bgBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .testTag("history_custom_background_image")
      )
      // Soft translucent protective overlay so titles remain clean and readable
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f))
      )
    }

    // Main content layout: Top bar and history canvas from Room SQLite database
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
      // Top bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { onBack() },
          modifier = Modifier
            .size(48.dp)
            .testTag("history_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBgColor
          )
        }

        Text(
          text = stringResource(R.string.history_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBgColor,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("history_title")
        )

        Spacer(modifier = Modifier.weight(1f))

        // 3 vertical dots menu icon in the top right corner
        IconButton(
          onClick = { isMenuOpen = true },
          modifier = Modifier
            .size(48.dp)
            .testTag("history_overflow_menu_button")
        ) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.action_more_options),
            tint = onBgColor
          )
        }
      }

      HorizontalDivider(color = topDividerColor, thickness = 1.dp)

      // Reactive content: reads real local Room database entries and GTA containers
      val hasContent = historyList.isNotEmpty() || gtaContainers.isNotEmpty()
      if (!hasContent) {
        // Pristine empty state (ideal for custom backgrounds)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("history_empty_state"),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.History,
              contentDescription = null,
              tint = Color(0xFFB0B3B8),
              modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = stringResource(R.string.history_empty_title),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF7A7D85)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.history_empty_desc),
              fontSize = 12.sp,
              color = Color(0xFF9E9E9E),
              lineHeight = 17.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        // List of entries from local database with GTA containers placed side-by-side below
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("history_list"),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // 1. Text history logs (audits, operations, timestamps) and backup containers
          items(historyList, key = { it.id }) { entry ->
            val isBackup = !entry.backupFilePath.isNullOrBlank()
            if (isBackup) {
              BackupHistoryItem(
                entry = entry,
                onDelete = { viewModel.deleteHistory(entry.id) },
                onViewDffs = { file, title ->
                  selectedContainerFile = file
                  selectedContainerTitle = title
                  containerDffs = com.example.data.parser.ImgArchiveReader.readEntries(file)
                    .filter { it.name.endsWith(".dff", ignoreCase = true) }
                },
                onShare = { file ->
                  shareContainerFile(context, file)
                }
              )
            } else {
              StandardHistoryItemCard(
                entry = entry,
                onDelete = { viewModel.deleteHistory(entry.id) }
              )
            }
          }

          // 2. Working copy containers side by side below any history text
          if (gtaContainers.isNotEmpty()) {
            item(key = "gta_containers_side_by_side") {
              GtaContainerCardsRow(
                containers = gtaContainers,
                modifier = Modifier.padding(top = if (historyList.isNotEmpty()) 8.dp else 0.dp, bottom = 10.dp),
                onViewContainer = { container ->
                  onViewContainer(container.fileName)
                }
              )
            }
          }
        }
      }
    }

    // Modal dialog to inspect DFF files in a backup container
    if (selectedContainerFile != null) {
      androidx.compose.material3.AlertDialog(
        onDismissRequest = { selectedContainerFile = null },
        title = {
          Text(
            text = selectedContainerTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
          )
        },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .height(350.dp)
          ) {
            Text(
              text = "${containerDffs.size} modelos .dff contenidos:",
              fontSize = 12.5.sp,
              color = Color(0xFF6B7280),
              modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(containerDffs, key = { it.name }) { dff ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = dff.name,
                    fontSize = 13.5.sp,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Medium
                  )
                  Text(
                    text = com.example.data.parser.ImgArchiveReader.formatFileSize(dff.sizeBytes),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                  )
                }
                HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.5.dp)
              }
            }
          }
        },
        confirmButton = {
          androidx.compose.material3.Button(
            onClick = { selectedContainerFile = null },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = Color(0xFF1E1F22),
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Cerrar")
          }
        }
      )
    }

    // Clean in-composition Application Menu
    if (isMenuOpen) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) {
            isMenuOpen = false
          },
        contentAlignment = Alignment.TopEnd
      ) {
        val menuBg = if (isDark) Color(0xFF1E2026) else Color.White
        val menuBorderColor = if (isDark) Color(0xFF2E313A) else Color(0xFFE5E7EB)
        val menuItemText = if (isDark) Color(0xFFEDEDED) else Color(0xFF1E1F22)
        val menuItemIconTint = if (isDark) Color(0xFFEDEDED) else Color(0xFF1E1F22)
        val menuDivider = if (isDark) Color(0xFF2B2E37) else Color(0xFFF0F2F5)

        Column(
          modifier = Modifier
            .statusBarsPadding()
            .padding(top = 50.dp, end = 16.dp)
            .width(230.dp)
            .background(menuBg, RoundedCornerShape(14.dp))
            .border(1.dp, menuBorderColor, RoundedCornerShape(14.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) { /* prevent clicking through to dismiss */ }
            .padding(vertical = 6.dp)
            .testTag("history_custom_menu_panel")
        ) {
          // Option 1: Information
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenInformation()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_information"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_information),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option 2: Fondo
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenBackground()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_background"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.Image,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_background),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option 3: Explorador
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenExplorer()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_explorer"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_explorer),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option 3.5: Explorador TXD
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenTxdExplorer()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_txd_explorer"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_txd_explorer),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option 3.6: Explorador de Scripts
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenScriptExplorer()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_script_explorer"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_script_explorer),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option: Menú Cleo (Hidden when active or unverified; ONLY appears if game was checked and lacks Cleo)
          val showCleoMenuInMenu by viewModel.showCleoMenuInMenu.collectAsStateWithLifecycle()
          val isCleoMenuActive by viewModel.isCleoMenuActive.collectAsStateWithLifecycle()

          if (showCleoMenuInMenu && !isCleoMenuActive) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (showCleoMenuInMenu && !isCleoMenuActive) {
                    isMenuOpen = false
                    onOpenCleoMenu()
                  }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("menu_item_cleo_menu"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Outlined.Widgets,
                contentDescription = null,
                tint = menuItemIconTint,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Menú Cleo",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = menuItemText,
                modifier = Modifier.weight(1f)
              )
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF9E9E9E),
                modifier = Modifier.size(12.dp)
              )
            }

            HorizontalDivider(
              color = menuDivider,
              thickness = 1.dp,
              modifier = Modifier.padding(horizontal = 12.dp)
            )
          }

          // Option 4: Idiomas
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenLanguages()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_languages"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.Translate,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_languages),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          HorizontalDivider(
            color = menuDivider,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 12.dp)
          )

          // Option 5: Funciones
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                isMenuOpen = false
                onOpenFunctions()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("menu_item_functions"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Outlined.Tune,
              contentDescription = null,
              tint = menuItemIconTint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_functions),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = menuItemText,
              modifier = Modifier.weight(1f)
            )
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
              contentDescription = null,
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(12.dp)
            )
          }

          // Option: Clear history if any exists
          if (historyList.isNotEmpty()) {
            HorizontalDivider(
              color = menuDivider,
              thickness = 1.dp,
              modifier = Modifier.padding(horizontal = 12.dp)
            )

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  isMenuOpen = false
                  viewModel.clearAllHistory()
                  Toast.makeText(
                    context,
                    context.getString(R.string.history_cleared_toast),
                    Toast.LENGTH_SHORT
                  ).show()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("menu_item_clear_history"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = Color(0xFFBA1A1A),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = stringResource(R.string.history_clear_action),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFBA1A1A),
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BackupHistoryItem(
  entry: HistoryEntry,
  onDelete: () -> Unit,
  onViewDffs: (File, String) -> Unit,
  onShare: (File) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
  val formattedDate = remember(entry.timestamp) { dateFormatter.format(Date(entry.timestamp)) }
  val file = remember(entry.backupFilePath) { entry.backupFilePath?.let { File(it) } }

  val containerFileName = remember(entry.containerType, entry.title, file) {
    when {
      !entry.containerType.isNullOrBlank() -> entry.containerType
      entry.title.contains("gta_int", ignoreCase = true) -> "gta_int.img"
      entry.title.contains("gta3", ignoreCase = true) -> "gta3.img"
      file != null && file.name.contains("gta_int", ignoreCase = true) -> "gta_int.img"
      file != null && file.name.contains("gta3", ignoreCase = true) -> "gta3.img"
      file != null -> file.name
      else -> "gta3.img"
    }
  }

  val containerSizeBytes = remember(file, containerFileName) {
    if (file != null && file.exists() && file.length() > 0) {
      file.length()
    } else {
      if (containerFileName.contains("int", ignoreCase = true)) 44826624L else 283516928L
    }
  }

  val backupModEntry = remember(containerFileName, containerSizeBytes, file) {
    ModFileEntry(
      fileName = containerFileName,
      fileType = "IMG",
      relativePath = file?.absolutePath ?: "",
      sizeBytes = containerSizeBytes
    )
  }

  var menuOpen by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("history_backup_item_${entry.id}")
  ) {
    // Header row: Badge, Date, Delete button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFDBEAFE))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "RESPALDO",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D4ED8)
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = formattedDate,
          fontSize = 11.5.sp,
          color = Color(0xFF8C9099),
          fontWeight = FontWeight.Normal
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier
          .size(32.dp)
          .testTag("delete_backup_history_${entry.id}")
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = "Borrar",
          tint = Color(0xFF9E9E9E),
          modifier = Modifier.size(18.dp)
        )
      }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Explanatory message placed right above the container card
    Text(
      text = entry.description.ifBlank { entry.title },
      fontSize = 12.5.sp,
      color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
      lineHeight = 17.sp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 3.dp, bottom = 8.dp)
    )

    // Identical container card matching the official containers row in dimensions and look
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(modifier = Modifier.weight(1f)) {
        SingleContainerCard(
          entry = backupModEntry,
          modifier = Modifier.fillMaxWidth(),
          testTag = "backup_container_card_${entry.id}",
          onClick = { menuOpen = true }
        )

        ContainerActionDropdownMenu(
          expanded = menuOpen,
          onDismissRequest = { menuOpen = false },
          containerName = backupModEntry.fileName,
          onShare = {
            menuOpen = false
            if (file != null && file.exists()) {
              onShare(file)
            } else {
              Toast.makeText(context, "Archivo de respaldo no disponible", Toast.LENGTH_SHORT).show()
            }
          },
          onView = {
            menuOpen = false
            if (file != null && file.exists()) {
              onViewDffs(file, entry.title)
            } else {
              Toast.makeText(context, "Archivo de respaldo no disponible", Toast.LENGTH_SHORT).show()
            }
          }
        )
      }

      // Counterweight spacer to preserve exact side-by-side card dimensions
      Spacer(modifier = Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = Color(0xFFF0F2F5), thickness = 1.dp)
  }
}

@Composable
private fun StandardHistoryItemCard(
  entry: HistoryEntry,
  onDelete: () -> Unit
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
  val formattedDate = remember(entry.timestamp) { dateFormatter.format(Date(entry.timestamp)) }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isDark) Color(0xFF1B1C22) else Color(0xFFF8F9FA)
    ),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isDark) Color(0xFF2B2E37) else Color(0xFFE5E7EB)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("history_item_${entry.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color(0xFFEDEDED) else Color(0xFF1E1F22)
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = formattedDate,
            fontSize = 11.sp,
            color = Color(0xFF8C9099)
          )
          if (entry.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = entry.description,
              fontSize = 12.sp,
              color = if (isDark) Color(0xFFA0A4AD) else Color(0xFF555555),
              lineHeight = 17.sp
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(36.dp)
            .testTag("delete_history_${entry.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Borrar",
            tint = Color(0xFF9E9E9E),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

private fun shareContainerFile(context: android.content.Context, file: File) {
  try {
    val uri = androidx.core.content.FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/octet-stream"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir contenedor IMG"))
  } catch (e: Exception) {
    Toast.makeText(context, "Error al compartir: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
  }
}
