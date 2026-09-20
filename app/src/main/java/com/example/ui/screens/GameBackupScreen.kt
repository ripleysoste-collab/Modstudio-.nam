@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.R
import com.example.data.BackupActionType
import com.example.data.GameBackupFileType
import com.example.data.GameBackupItem
import com.example.data.GameBackupManager
import com.example.data.NotificationHelper
import com.example.data.StoragePermissionManager
import com.example.data.repository.ModstudioRepository
import com.example.ui.components.BackupFileActionDropdownMenu
import com.example.ui.components.DeviceStoragePickerModal
import com.example.ui.components.MultiBackupActionDropdownMenu
import com.example.util.FileSharingHelper
import java.io.File
import com.example.service.GtaSyncService
import com.example.service.SyncServiceState
import com.example.ui.components.GameBackupConfirmationDialog
import com.example.ui.components.NotificationPermissionDialog
import com.example.ui.components.PermissionExplanationDialog
import com.example.ui.components.RotatingBallIndicator
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import com.example.ui.theme.LightButtonColor
import com.example.ui.theme.LightButtonContentColor

/**
 * Clean, minimalist screen for GTA San Andreas game backup:
 * - Simple top bar with title "Copia de seguridad".
 * - Only displays files that ACTUALLY exist in the backup folder (empty until backup is created).
 * - Pre-backup clean dialog with game logo, file weight details, and safety benefits.
 * - Dynamic button states: "Crear copia de seguridad", "Completar archivos", "Creado", or "No encontrados".
 * - While backing up: Rotating ball indicator with step message in center (isolated from Home screen).
 * - Automatic real-time rescan on resume when user modifies files externally.
 */
@Composable
fun GameBackupScreen(
  onBack: () -> Unit,
  isDarkMode: Boolean,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val permissionManager = remember { StoragePermissionManager.getInstance(context) }
  val backupManager = remember { GameBackupManager.getInstance(context) }
  val backupState by backupManager.state.collectAsStateWithLifecycle()
  val syncServiceState by GtaSyncService.serviceState.collectAsStateWithLifecycle()
  val repo = remember { ModstudioRepository.getInstance(context) }
  val coroutineScope = rememberCoroutineScope()

  var showPreBackupDialog by rememberSaveable { mutableStateOf(false) }
  var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
  var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
  var waitingForPermissionResult by rememberSaveable { mutableStateOf(false) }
  var pendingOnlyMissing by rememberSaveable { mutableStateOf(false) }

  // State for pending compressed backup (valid for 15 minutes)
  var pendingCompressedZip by remember { mutableStateOf<File?>(null) }
  var zipFileToMove by remember { mutableStateOf<File?>(null) }
  var showMovePickerForZip by remember { mutableStateOf(false) }

  val checkPendingCompression: () -> Unit = {
    backupManager.checkAndPurgeExpiredCompression()
    val pending = backupManager.getPendingCompressedBackup()
    pendingCompressedZip = pending
    if (pending != null && !showMovePickerForZip) {
      zipFileToMove = pending
      showMovePickerForZip = true
    }
  }

  // Initial scan of game files, backup directory & check pending compression
  LaunchedEffect(Unit) {
    backupManager.scanGameFiles()
    checkPendingCompression()
  }

  // Periodic 15-minute expiration timer check while user has the screen open
  LaunchedEffect(Unit) {
    while (true) {
      delay(4000)
      val purged = backupManager.checkAndPurgeExpiredCompression()
      if (purged) {
        pendingCompressedZip = null
        if (showMovePickerForZip) {
          showMovePickerForZip = false
          zipFileToMove = null
          Toast.makeText(
            context,
            context.getString(R.string.game_backup_compress_timeout_expired),
            Toast.LENGTH_LONG
          ).show()
        }
      } else {
        pendingCompressedZip = backupManager.getPendingCompressedBackup()
      }
    }
  }

  // React to GtaSyncService finishing compression
  LaunchedEffect(syncServiceState) {
    if (syncServiceState is SyncServiceState.Finished) {
      val finished = syncServiceState as SyncServiceState.Finished
      if (finished.action == GtaSyncService.ACTION_COMPRESS_BACKUP) {
        if (finished.success) {
          checkPendingCompression()
        } else {
          Toast.makeText(context, finished.message, Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  // Auto-rescan on lifecycle ON_RESUME so any external changes (e.g. user deleting files in ZArchiver)
  // or background compression completion are detected immediately
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch {
          backupManager.scanGameFiles()
          checkPendingCompression()
        }
        if (waitingForPermissionResult) {
          waitingForPermissionResult = false
          if (permissionManager.hasStorageAccess()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasNotificationPermission(context)) {
              showNotificationPermissionDialog = true
            } else {
              GtaSyncService.startGameBackup(context, onlyMissing = pendingOnlyMissing)
            }
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val isSyncRunning = syncServiceState is SyncServiceState.Running &&
      (syncServiceState as SyncServiceState.Running).action == GtaSyncService.ACTION_GAME_BACKUP
  val isCompressRunning = syncServiceState is SyncServiceState.Running &&
      (syncServiceState as SyncServiceState.Running).action == GtaSyncService.ACTION_COMPRESS_BACKUP
  val compressProgressMsg = if (isCompressRunning) {
    (syncServiceState as SyncServiceState.Running).message
  } else ""

  val isAnyBackingUp = backupState.isBackingUp || isSyncRunning

  val currentStepMessage = when {
    isSyncRunning -> (syncServiceState as SyncServiceState.Running).message
    backupState.isBackingUp -> backupState.statusMessage
    backupState.isSuccess -> stringResource(R.string.game_backup_success_title)
    else -> ""
  }

  // Execution trigger
  val startBackupFlow: (Boolean) -> Unit = { onlyMissing ->
    GtaSyncService.startGameBackup(context, onlyMissing = onlyMissing)
  }

  // Permission handling sequence after confirming in pre-backup dialog
  val proceedWithPermissions: (Boolean) -> Unit = { onlyMissing ->
    pendingOnlyMissing = onlyMissing
    if (!permissionManager.hasStorageAccess()) {
      showPermissionDialog = true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !NotificationHelper.hasNotificationPermission(context)) {
      showNotificationPermissionDialog = true
    } else {
      startBackupFlow(onlyMissing)
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    startBackupFlow(pendingOnlyMissing)
  }

  val manageAllFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) {
    if (permissionManager.hasStorageAccess()) {
      coroutineScope.launch { backupManager.scanGameFiles() }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          !NotificationHelper.hasNotificationPermission(context)) {
        showNotificationPermissionDialog = true
      } else {
        startBackupFlow(pendingOnlyMissing)
      }
    } else {
      Toast.makeText(
        context,
        context.getString(R.string.permission_denied_warning),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  val legacyPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      coroutineScope.launch { backupManager.scanGameFiles() }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          !NotificationHelper.hasNotificationPermission(context)) {
        showNotificationPermissionDialog = true
      } else {
        startBackupFlow(pendingOnlyMissing)
      }
    } else {
      Toast.makeText(
        context,
        context.getString(R.string.permission_denied_warning),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  // Pre-backup clean explanation dialog (with game logo and weight info)
  if (showPreBackupDialog) {
    GameBackupConfirmationDialog(
      onDismiss = { showPreBackupDialog = false },
      onConfirm = {
        showPreBackupDialog = false
        val onlyMissing = (backupState.actionType == BackupActionType.COMPLETE)
        proceedWithPermissions(onlyMissing)
      },
      isDarkMode = isDarkMode
    )
  }

  // State for multi-selection, contextual menus and explorer
  val selectedItems = remember { mutableStateListOf<GameBackupItem>() }
  var isSingleMenuExpanded by remember { mutableStateOf(false) }
  var isMultiMenuExpanded by remember { mutableStateOf(false) }
  var activeFileForExplorer by remember { mutableStateOf<File?>(null) }

  // When user taps "Ver", open the temporary archive explorer
  val currentExplorerFile = activeFileForExplorer
  if (currentExplorerFile != null) {
    GameBackupExplorerScreen(
      archiveFile = currentExplorerFile,
      isDarkMode = isDarkMode,
      onBack = { activeFileForExplorer = null }
    )
    return
  }

  // Device Storage Picker modal after compression to cut/move zip directly to device storage
  val activeZipToMove = zipFileToMove
  if (showMovePickerForZip && activeZipToMove != null) {
    DeviceStoragePickerModal(
      fileNameToMove = activeZipToMove.name,
      isDarkMode = isDarkMode,
      onDismiss = {
        showMovePickerForZip = false
      },
      onConfirmMoveTo = { targetDir: File ->
        val currentZip = zipFileToMove
        if (currentZip != null && currentZip.exists()) {
          val destinationFile = File(targetDir, currentZip.name)
          val moved = try {
            currentZip.copyTo(destinationFile, overwrite = true)
            currentZip.delete()
            true
          } catch (_: Exception) {
            false
          }
          if (moved) {
            backupManager.clearPendingCompressedBackup(deleteFile = false)
            pendingCompressedZip = null
            Toast.makeText(
              context,
              context.getString(R.string.game_backup_move_success, targetDir.name),
              Toast.LENGTH_LONG
            ).show()
          } else {
            Toast.makeText(context, "Error al mover archivo", Toast.LENGTH_SHORT).show()
          }
        }
        showMovePickerForZip = false
        zipFileToMove = null
        selectedItems.clear()
      }
    )
  }

  // Storage permission dialog
  if (showPermissionDialog) {
    PermissionExplanationDialog(
      onDismiss = { showPermissionDialog = false },
      onAccept = {
        showPermissionDialog = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          waitingForPermissionResult = true
          manageAllFilesLauncher.launch(permissionManager.createManageAllFilesIntent())
        } else {
          legacyPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.READ_EXTERNAL_STORAGE,
              Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
          )
        }
      }
    )
  }

  // Notification permission dialog
  if (showNotificationPermissionDialog) {
    NotificationPermissionDialog(
      onDismiss = {
        showNotificationPermissionDialog = false
        startBackupFlow(pendingOnlyMissing)
      },
      onAccept = {
        showNotificationPermissionDialog = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          startBackupFlow(pendingOnlyMissing)
        }
      }
    )
  }

  val backgroundColor = MaterialTheme.colorScheme.background
  val onBackgroundColor = MaterialTheme.colorScheme.onBackground
  val dividerColor = if (isDarkMode) Color(0xFF262830) else Color(0xFFF0F2F5)
  val buttonBg = if (isDarkMode) GoldButtonColor else LightButtonColor
  val buttonContent = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("game_backup_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top Bar: Clean, small, standard
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
            .testTag("game_backup_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBackgroundColor
          )
        }

        Text(
          text = stringResource(R.string.game_backup_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = onBackgroundColor,
          modifier = Modifier
            .padding(start = 4.dp)
            .testTag("game_backup_title_text")
        )
      }

      HorizontalDivider(color = dividerColor, thickness = 1.dp)

      // Main content: either Rotating ball indicator while copying, or the clean list of files
      if (isAnyBackingUp) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          RotatingBallIndicator(
            statusText = currentStepMessage.ifEmpty { stringResource(R.string.game_backup_status_in_progress) }
          )
        }
      } else {
        // Files list: ONLY displays files that ACTUALLY exist in the backup directory
        val backedUpList = backupState.backedUpItems.sortedWith(
          compareBy<GameBackupItem> { item ->
            when (item.type) {
              GameBackupFileType.APK -> 0
              GameBackupFileType.MAIN_OBB -> 1
              GameBackupFileType.PATCH_OBB -> 2
              GameBackupFileType.SCRIPT -> 3
            }
          }.thenBy { it.fileName.lowercase(java.util.Locale.ROOT) }
        )

        if (backedUpList.isEmpty()) {
          // Empty state: clean, uncluttered layout when no backup exists yet
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (backupState.actionType == BackupActionType.NOT_FOUND) {
                stringResource(R.string.game_backup_status_not_found)
              } else {
                stringResource(R.string.game_backup_empty_subtitle)
              },
              fontSize = 13.5.sp,
              lineHeight = 20.sp,
              color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("game_backup_empty_message")
            )
          }
        } else {
          // Clean list of files with peaceful highlight on multi-select (scrollable for many items)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 12.dp)
          ) {
            backedUpList.forEach { item ->
              val isSelected = selectedItems.any { it.fileName == item.fileName }

              Box(modifier = Modifier.fillMaxWidth()) {
                CleanGameFileRow(
                  item = item,
                  isSelected = isSelected,
                  isDarkMode = isDarkMode,
                  formatFileSize = { repo.formatFileSize(it) },
                  onClick = {
                    if (isSelected) {
                      selectedItems.removeAll { it.fileName == item.fileName }
                    } else {
                      selectedItems.add(item)
                    }

                    if (selectedItems.size >= 2) {
                      isSingleMenuExpanded = false
                      isMultiMenuExpanded = true
                    } else if (selectedItems.size == 1) {
                      isMultiMenuExpanded = false
                      isSingleMenuExpanded = true
                    } else {
                      isSingleMenuExpanded = false
                      isMultiMenuExpanded = false
                    }
                  },
                  onLongClick = {
                    if (isSelected) {
                      selectedItems.removeAll { it.fileName == item.fileName }
                    } else {
                      selectedItems.add(item)
                    }
                    if (selectedItems.size >= 2) {
                      isSingleMenuExpanded = false
                      isMultiMenuExpanded = true
                    } else if (selectedItems.size == 1) {
                      isMultiMenuExpanded = false
                      isSingleMenuExpanded = true
                    }
                  }
                )

                // Single-item contextual menu (Compartir / Ver)
                if (selectedItems.size == 1 && selectedItems.first().fileName == item.fileName) {
                  BackupFileActionDropdownMenu(
                    expanded = isSingleMenuExpanded,
                    onDismissRequest = {
                      isSingleMenuExpanded = false
                    },
                    fileName = item.fileName,
                    onShare = {
                      isSingleMenuExpanded = false
                      val targetFile = item.sourceFile ?: File(backupManager.getPrimaryBackupDir(), item.fileName)
                      FileSharingHelper.shareFile(context, targetFile, item.fileName)
                      selectedItems.clear()
                    },
                    onView = {
                      isSingleMenuExpanded = false
                      val targetFile = item.sourceFile ?: File(backupManager.getPrimaryBackupDir(), item.fileName)
                      activeFileForExplorer = targetFile
                      selectedItems.clear()
                    },
                    isDarkMode = isDarkMode
                  )
                }

                // Multi-item contextual menu (Compartir / Comprimir)
                if (selectedItems.size >= 2 && selectedItems.lastOrNull()?.fileName == item.fileName) {
                  MultiBackupActionDropdownMenu(
                    expanded = isMultiMenuExpanded,
                    onDismissRequest = {
                      isMultiMenuExpanded = false
                    },
                    selectedCount = selectedItems.size,
                    onShare = {
                      isMultiMenuExpanded = false
                      val filesToShare = selectedItems.mapNotNull { sel ->
                        sel.sourceFile ?: File(backupManager.getPrimaryBackupDir(), sel.fileName).takeIf { it.exists() }
                      }
                      FileSharingHelper.shareMultipleFiles(
                        context = context,
                        files = filesToShare,
                        title = context.getString(R.string.game_backup_share_all)
                      )
                      selectedItems.clear()
                    },
                    onCompress = {
                      isMultiMenuExpanded = false
                      val filesToCompress = selectedItems.mapNotNull { sel ->
                        sel.sourceFile ?: File(backupManager.getPrimaryBackupDir(), sel.fileName).takeIf { it.exists() }
                      }
                      selectedItems.clear()
                      if (filesToCompress.isNotEmpty()) {
                        val paths = ArrayList(filesToCompress.map { it.absolutePath })
                        GtaSyncService.startCompressBackup(context, paths)
                      }
                    },
                    isDarkMode = isDarkMode
                  )
                }
              }
              HorizontalDivider(
                color = dividerColor.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 48.dp)
              )
            }
          }
        }

        // Bottom action: compression progress / move compressed prompt / backup action button
        if (isCompressRunning) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            RotatingBallIndicator(
              statusText = compressProgressMsg.ifEmpty { stringResource(R.string.game_backup_compressing) }
            )
          }
        } else if (pendingCompressedZip != null && pendingCompressedZip!!.exists()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
          ) {
            Button(
              onClick = {
                zipFileToMove = pendingCompressedZip
                showMovePickerForZip = true
              },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = buttonBg,
                contentColor = buttonContent
              ),
              elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
              ),
              modifier = Modifier.testTag("move_compressed_backup_button")
            ) {
              Text(
                text = stringResource(R.string.game_backup_compress_move_prompt),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
              )
            }
          }
        } else {
          // Bottom action button: dynamic text and behavior based on backup status
          val buttonLabel = when (backupState.actionType) {
            BackupActionType.CREATED -> stringResource(R.string.game_backup_action_created)
            BackupActionType.COMPLETE -> stringResource(R.string.game_backup_action_complete_missing)
            BackupActionType.NOT_FOUND -> stringResource(R.string.game_backup_status_not_found)
            BackupActionType.CREATE -> stringResource(R.string.game_backup_action_create)
          }

          val isButtonActionable = backupState.actionType == BackupActionType.CREATE ||
              backupState.actionType == BackupActionType.COMPLETE

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
          ) {
            Button(
              onClick = {
                if (backupState.actionType == BackupActionType.CREATED) {
                  Toast.makeText(
                    context,
                    context.getString(R.string.game_backup_already_created_toast),
                    Toast.LENGTH_SHORT
                  ).show()
                } else if (isButtonActionable) {
                  showPreBackupDialog = true
                }
              },
              enabled = backupState.actionType != BackupActionType.NOT_FOUND,
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = if (backupState.actionType == BackupActionType.CREATED) {
                  if (isDarkMode) Color(0xFF2C2D35) else Color(0xFFE5E7EB)
                } else {
                  buttonBg
                },
                contentColor = if (backupState.actionType == BackupActionType.CREATED) {
                  if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF374151)
                } else {
                  buttonContent
                },
                disabledContainerColor = if (isDarkMode) Color(0xFF23242A) else Color(0xFFF3F4F6),
                disabledContentColor = if (isDarkMode) Color(0xFF6B7280) else Color(0xFF9CA3AF)
              ),
              elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
              ),
              modifier = Modifier.testTag("create_game_backup_button")
            ) {
              Text(
                text = buttonLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Clean file row representing an APK or OBB file in ZArchiver style:
 * - Small square icon on the left (game logo for APK, orange document for OBB).
 * - File name in medium weight, single line.
 * - Small file size / status underneath.
 * - Zero cards, zero borders.
 */
@Composable
private fun CleanGameFileRow(
  item: GameBackupItem,
  isSelected: Boolean,
  isDarkMode: Boolean,
  formatFileSize: (Long) -> String,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val onBackgroundColor = MaterialTheme.colorScheme.onBackground
  val highlightBg = if (isSelected) {
    if (isDarkMode) Color(0xFF282A33) else Color(0xFFF3F4F6)
  } else {
    Color.Transparent
  }
  val highlightBorder = if (isSelected) {
    if (isDarkMode) GoldButtonColor.copy(alpha = 0.45f) else GoldButtonColor.copy(alpha = 0.5f)
  } else {
    Color.Transparent
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(highlightBg)
      .border(1.dp, highlightBorder, RoundedCornerShape(12.dp))
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
      )
      .padding(horizontal = 8.dp, vertical = 8.dp)
      .testTag("game_backup_item_${item.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Small icon: Game logo for APK, Blue/teal code doc for Scripts, Orange document for OBB
    when (item.type) {
      GameBackupFileType.APK -> {
        Image(
          painter = painterResource(id = R.drawable.ic_gtasa_game_logo),
          contentDescription = "GTA San Andreas",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .testTag("game_backup_apk_game_logo")
        )
      }
      GameBackupFileType.SCRIPT -> {
        Image(
          painter = painterResource(id = R.drawable.ic_script_file),
          contentDescription = "Script CLEO",
          modifier = Modifier
            .size(36.dp)
            .testTag("game_backup_script_icon_${item.id}")
        )
      }
      else -> {
        Image(
          painter = painterResource(id = R.drawable.ic_obb_file),
          contentDescription = "OBB File",
          modifier = Modifier
            .size(36.dp)
            .testTag("game_backup_obb_icon_${item.id}")
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    // File details: name and size
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.fileName,
        fontSize = 13.5.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        color = onBackgroundColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(2.dp))

      val sizeText = if (item.exists) {
        formatFileSize(item.sizeBytes)
      } else {
        stringResource(R.string.game_backup_status_not_found)
      }

      Text(
        text = sizeText,
        fontSize = 11.5.sp,
        color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
      )
    }

    if (isSelected) {
      Spacer(modifier = Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(GoldButtonColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = Color(0xFF1E1F22),
          modifier = Modifier.size(13.dp)
        )
      }
    }
  }
}
