package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.LanguageManager
import com.example.data.NotificationHelper
import com.example.data.StoragePermissionManager
import com.example.ui.components.NotificationPermissionDialog
import com.example.ui.components.PermissionExplanationDialog
import com.example.ui.components.RotatingBallIndicator
import com.example.ui.components.SplashScreen
import com.example.ui.screens.BackgroundScreen
import com.example.ui.screens.ContainerViewerScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.TxdExplorerScreen
import com.example.ui.screens.ScriptExplorerScreen
import com.example.ui.screens.GameBackupScreen
import com.example.ui.screens.CleoMenuScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.InformationScreen
import com.example.ui.screens.LanguagesScreen
import android.net.Uri
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.luminance
import com.example.data.ThemeManager
import com.example.ui.screens.FunctionsScreen
import com.example.ui.screens.ModAnalysisResultView
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import com.example.ui.theme.LightButtonColor
import com.example.ui.theme.LightButtonContentColor
import com.example.ui.viewmodel.ModAnalysisUiState
import kotlinx.coroutines.delay
import com.example.ui.theme.ModstudioTheme
import com.example.ui.viewmodel.ModstudioViewModel
import com.example.ui.viewmodel.SearchUiState
import com.example.data.AppShortcutManager

class LocalizedActivityContext(
  val activity: ComponentActivity,
  private val localizedContext: Context
) : ContextWrapper(activity), ActivityResultRegistryOwner {

  override val activityResultRegistry get() = activity.activityResultRegistry
  override fun getResources(): Resources = localizedContext.resources
  override fun getAssets(): AssetManager = localizedContext.assets
  override fun getTheme(): Resources.Theme = localizedContext.theme
  override fun getApplicationContext(): Context = activity.applicationContext
}

class MainActivity : ComponentActivity() {

  private val shortcutScreenState = mutableStateOf<ScreenState?>(null)

  override fun attachBaseContext(newBase: Context) {
    val langCode = LanguageManager.getInstance(newBase).getSavedLanguageCode()
    val localizedContext = LanguageManager.createLocalizedContext(newBase, langCode)
    super.attachBaseContext(localizedContext)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    AppShortcutManager.extractTargetScreen(intent)?.let { target ->
      shortcutScreenState.value = target
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize launcher shortcuts ("Abrir cachés" and "Abrir backup")
    AppShortcutManager.initShortcuts(this)

    // Check if launched from a shortcut
    AppShortcutManager.extractTargetScreen(intent)?.let { target ->
      shortcutScreenState.value = target
    }

    setContent {
      val context = LocalContext.current
      val languageManager = remember { LanguageManager.getInstance(context) }
      val currentLanguageCode by languageManager.currentLanguage.collectAsStateWithLifecycle()
      val themeManager = remember { ThemeManager.getInstance(this@MainActivity) }
      val isDarkMode by themeManager.isDarkMode.collectAsStateWithLifecycle()
      val backupSettingsManager = remember { com.example.data.BackupSettingsManager.getInstance(this@MainActivity) }
      val isBackupEnabled by backupSettingsManager.isBackupEnabled.collectAsStateWithLifecycle()
      val activeShortcutScreen by remember { shortcutScreenState }

      val localizedContext = remember(currentLanguageCode) {
        LanguageManager.createLocalizedContext(this@MainActivity, currentLanguageCode)
      }
      val localizedActivityContext = remember(localizedContext) {
        LocalizedActivityContext(this@MainActivity, localizedContext)
      }
      val localizedConfig = remember(currentLanguageCode) {
        localizedContext.resources.configuration
      }

      // If opened via shortcut, skip splash screen directly to the target view
      var showSplash by rememberSaveable { mutableStateOf(activeShortcutScreen == null) }

      CompositionLocalProvider(
        LocalContext provides localizedActivityContext,
        LocalConfiguration provides localizedConfig,
        LocalActivityResultRegistryOwner provides this@MainActivity,
        LocalLifecycleOwner provides this@MainActivity
      ) {
        if (showSplash) {
          SplashScreen(
            onAnimationFinished = { showSplash = false }
          )
        } else {
          ModstudioTheme(darkTheme = isDarkMode) {
            ModstudioApp(
              isDarkMode = isDarkMode,
              onToggleDarkMode = { themeManager.setDarkMode(it) },
              isBackupEnabled = isBackupEnabled,
              onToggleBackupEnabled = { backupSettingsManager.setBackupEnabled(it) },
              shortcutTargetScreen = activeShortcutScreen
            )
          }
        }
      }
    }
  }
}

enum class ScreenState {
  HOME,
  HISTORY,
  INFORMATION,
  BACKGROUND,
  EXPLORER,
  TXD_EXPLORER,
  SCRIPT_EXPLORER,
  CLEO_MENU,
  LANGUAGES,
  FUNCTIONS,
  CONTAINER_VIEWER,
  GAME_BACKUP,
  SOLUTION
}

@Composable
fun ModstudioApp(
  isDarkMode: Boolean = false,
  onToggleDarkMode: (Boolean) -> Unit = {},
  isBackupEnabled: Boolean = true,
  onToggleBackupEnabled: (Boolean) -> Unit = {},
  shortcutTargetScreen: ScreenState? = null,
  viewModel: ModstudioViewModel = viewModel()
) {
  // Always starts at HOME on launch, unless launched directly from App Shortcut
  var currentScreen by rememberSaveable { mutableStateOf(shortcutTargetScreen ?: ScreenState.HOME) }
  var openedViaShortcut by rememberSaveable { mutableStateOf(shortcutTargetScreen != null) }
  var activeContainerName by remember { mutableStateOf("gta3.img") }

  LaunchedEffect(shortcutTargetScreen) {
    if (shortcutTargetScreen != null) {
      currentScreen = shortcutTargetScreen
      openedViaShortcut = true
    }
  }

  // Direct, rock-solid screen routing backed by Room SQLite database
  when (currentScreen) {
    ScreenState.HOME -> {
      ModstudioHomeScreen(
        viewModel = viewModel,
        isDarkMode = isDarkMode,
        onOpenHistory = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.HISTORY -> {
      HistoryScreen(
        viewModel = viewModel,
        onBack = { currentScreen = ScreenState.HOME },
        onOpenInformation = { currentScreen = ScreenState.INFORMATION },
        onOpenBackground = { currentScreen = ScreenState.BACKGROUND },
        onOpenExplorer = { currentScreen = ScreenState.EXPLORER },
        onOpenTxdExplorer = { currentScreen = ScreenState.TXD_EXPLORER },
        onOpenScriptExplorer = { currentScreen = ScreenState.SCRIPT_EXPLORER },
        onOpenCleoMenu = {
          if (viewModel.showCleoMenuInMenu.value && !viewModel.isCleoMenuActive.value) {
            currentScreen = ScreenState.CLEO_MENU
          }
        },
        onOpenLanguages = { currentScreen = ScreenState.LANGUAGES },
        onOpenFunctions = { currentScreen = ScreenState.FUNCTIONS },
        onViewContainer = { containerName ->
          activeContainerName = containerName
          currentScreen = ScreenState.CONTAINER_VIEWER
        }
      )
    }
    ScreenState.FUNCTIONS -> {
      FunctionsScreen(
        onBack = { currentScreen = ScreenState.HISTORY },
        isDarkMode = isDarkMode,
        onToggleDarkMode = onToggleDarkMode,
        isBackupEnabled = isBackupEnabled,
        onToggleBackupEnabled = onToggleBackupEnabled,
        onOpenGameBackup = { currentScreen = ScreenState.GAME_BACKUP },
        onOpenSolution = { currentScreen = ScreenState.SOLUTION }
      )
    }
    ScreenState.GAME_BACKUP -> {
      GameBackupScreen(
        onBack = {
          if (openedViaShortcut) {
            currentScreen = ScreenState.HOME
            openedViaShortcut = false
          } else {
            currentScreen = ScreenState.FUNCTIONS
          }
        },
        isDarkMode = isDarkMode
      )
    }
    ScreenState.SOLUTION -> {
      com.example.ui.screens.SolutionScreen(
        onBack = { currentScreen = ScreenState.FUNCTIONS },
        isDarkMode = isDarkMode
      )
    }
    ScreenState.CONTAINER_VIEWER -> {
      ContainerViewerScreen(
        containerName = activeContainerName,
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.INFORMATION -> {
      InformationScreen(
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.BACKGROUND -> {
      BackgroundScreen(
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.EXPLORER -> {
      ExplorerScreen(
        viewModel = viewModel,
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.TXD_EXPLORER -> {
      TxdExplorerScreen(
        viewModel = viewModel,
        onBack = {
          if (openedViaShortcut) {
            currentScreen = ScreenState.HOME
            openedViaShortcut = false
          } else {
            currentScreen = ScreenState.HISTORY
          }
        }
      )
    }
    ScreenState.SCRIPT_EXPLORER -> {
      ScriptExplorerScreen(
        viewModel = viewModel,
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.CLEO_MENU -> {
      CleoMenuScreen(
        viewModel = viewModel,
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
    ScreenState.LANGUAGES -> {
      LanguagesScreen(
        onBack = { currentScreen = ScreenState.HISTORY }
      )
    }
  }
}

@Composable
fun ModstudioHomeScreen(
  viewModel: ModstudioViewModel = viewModel(),
  isDarkMode: Boolean = false,
  onOpenHistory: () -> Unit = {}
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val permissionManager = remember { StoragePermissionManager.getInstance(context) }
  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
  val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
  val modAnalysisState by viewModel.modAnalysisState.collectAsStateWithLifecycle()
  val matchUiState by viewModel.matchUiState.collectAsStateWithLifecycle()
  val rebuildUiState by viewModel.rebuildUiState.collectAsStateWithLifecycle()

  // 2-second delay after containers are found to transition to "Select Mod"
  var showModSelectionAfterSuccess by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(searchUiState) {
    if (searchUiState is SearchUiState.Success) {
      delay(2000)
      showModSelectionAfterSuccess = true
    } else {
      showModSelectionAfterSuccess = false
    }
  }

  // Mod file & folder pickers
  val pickModLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.analyzeModUri(uri)
    }
  }

  val pickFolderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.analyzeModUri(uri)
    }
  }

  if (modAnalysisState is ModAnalysisUiState.Analyzed) {
    ModAnalysisResultView(
      result = (modAnalysisState as ModAnalysisUiState.Analyzed).result,
      matchState = matchUiState,
      rebuildState = rebuildUiState,
      onRebuild = { plan -> viewModel.executeRebuild(plan) },
      onClose = { viewModel.resetModAnalysis() }
    )
    return
  }

  // Starts OFF (false) on app launch until user explicitly taps "Encontrar"
  var isSearching by rememberSaveable { mutableStateOf(false) }
  var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
  var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
  var waitingForPermissionResult by rememberSaveable { mutableStateOf(false) }
  var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }

  val executeAction: (String) -> Unit = { action ->
    if (action == "UPDATE") {
      viewModel.startUpdateFlow { changed, message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      }
    } else {
      isSearching = true
      viewModel.startRealContainerSearch { success ->
        if (success) {
          Toast.makeText(
            context,
            context.getString(R.string.status_found_success),
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
  }

  val proceedAfterStorage: (String) -> Unit = { action ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !NotificationHelper.hasNotificationPermission(context)) {
      pendingAction = action
      showNotificationPermissionDialog = true
    } else {
      executeAction(action)
    }
  }

  // Launcher for Android 13+ (API 33+) Notification Permission
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    val action = pendingAction ?: "FIND"
    pendingAction = null
    executeAction(action)
  }

  // Launcher for Android 11+ (API 30+) Broad Storage Access
  val manageAllFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) {
    if (permissionManager.hasStorageAccess()) {
      val action = pendingAction ?: "FIND"
      proceedAfterStorage(action)
      Toast.makeText(
        context,
        context.getString(R.string.permission_granted_success),
        Toast.LENGTH_SHORT
      ).show()
    } else {
      Toast.makeText(
        context,
        context.getString(R.string.permission_denied_warning),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  // Launcher for Android 10 (API 29) and lower legacy permissions
  val legacyPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      val action = pendingAction ?: "FIND"
      proceedAfterStorage(action)
      Toast.makeText(
        context,
        context.getString(R.string.permission_granted_success),
        Toast.LENGTH_SHORT
      ).show()
    } else {
      Toast.makeText(
        context,
        context.getString(R.string.permission_denied_warning),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  // Auto-resume check when returning from system Settings screen on Android 11+
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        if (waitingForPermissionResult) {
          waitingForPermissionResult = false
          if (permissionManager.hasStorageAccess()) {
            val action = pendingAction ?: "FIND"
            proceedAfterStorage(action)
            Toast.makeText(
              context,
              context.getString(R.string.permission_granted_success),
              Toast.LENGTH_SHORT
            ).show()
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Step 1: Storage permission explanation dialog
  if (showPermissionDialog) {
    PermissionExplanationDialog(
      onDismiss = {
        showPermissionDialog = false
      },
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

  // Step 2: Notification permission explanation dialog (shown after storage)
  if (showNotificationPermissionDialog) {
    NotificationPermissionDialog(
      onDismiss = {
        showNotificationPermissionDialog = false
        val action = pendingAction ?: "FIND"
        pendingAction = null
        executeAction(action)
      },
      onAccept = {
        showNotificationPermissionDialog = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          val action = pendingAction ?: "FIND"
          pendingAction = null
          executeAction(action)
        }
      }
    )
  }

  val screenBg = MaterialTheme.colorScheme.background
  val onBg = MaterialTheme.colorScheme.onBackground
  val buttonBg = if (isDarkMode) GoldButtonColor else LightButtonColor
  val buttonContent = if (isDarkMode) GoldButtonContentColor else LightButtonContentColor

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(screenBg)
  ) {
    // Top-right corner: Small minimalist 3-line icon with guaranteed 48dp touch target
    IconButton(
      onClick = onOpenHistory,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .statusBarsPadding()
        .padding(top = 8.dp, end = 8.dp)
        .size(48.dp)
        .testTag("menu_button")
    ) {
      Canvas(
        modifier = Modifier.size(20.dp)
      ) {
        val lineWidth = size.width
        val strokeWidth = 2.2.dp.toPx()
        val spacing = 5.5.dp.toPx()
        val centerY = size.height / 2f

        // Top line
        drawLine(
          color = onBg,
          start = Offset(0f, centerY - spacing),
          end = Offset(lineWidth, centerY - spacing),
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round
        )
        // Middle line
        drawLine(
          color = onBg,
          start = Offset(0f, centerY),
          end = Offset(lineWidth, centerY),
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round
        )
        // Bottom line
        drawLine(
          color = onBg,
          start = Offset(0f, centerY + spacing),
          end = Offset(lineWidth, centerY + spacing),
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round
        )
      }
    }

    // Center feedback area based on searchUiState & modAnalysisState
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      when (val modState = modAnalysisState) {
        is ModAnalysisUiState.Analyzing -> {
          RotatingBallIndicator(statusText = modState.statusMessage)
        }
        is ModAnalysisUiState.Error -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.testTag("mod_analysis_error")
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEF2F2)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(32.dp)
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "Error al analizar el mod",
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF1E1F22)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = modState.message,
              fontSize = 12.5.sp,
              color = Color(0xFF6B7280),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { viewModel.resetModAnalysis() }) {
              Text("Reintentar", color = Color(0xFF2563EB))
            }
          }
        }
        is ModAnalysisUiState.Idle -> {
          when (val state = searchUiState) {
            is SearchUiState.Idle -> {
              if (isSearching || isScanning) {
                RotatingBallIndicator(statusText = stringResource(R.string.status_searching))
              }
            }
            is SearchUiState.Searching -> {
              RotatingBallIndicator(statusText = state.currentStepMessage)
            }
            is SearchUiState.Success -> {
              if (!showModSelectionAfterSuccess) {
                // Initial 2 seconds: green checkmark ("chulito verde")
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .testTag("found_success_indicator")
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenHistory() }
                    .padding(12.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(72.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF16A34A))
                      .testTag("success_checkmark_circle"),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = stringResource(R.string.action_found),
                      tint = Color.White,
                      modifier = Modifier.size(42.dp)
                    )
                  }

                  Spacer(modifier = Modifier.height(16.dp))

                  Text(
                    text = stringResource(R.string.found_success_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg,
                    modifier = Modifier.testTag("found_success_title")
                  )

                  Spacer(modifier = Modifier.height(6.dp))

                  Text(
                    text = stringResource(R.string.found_success_subtitle),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                  )
                }
              } else {
                // After 2 seconds: Green checkmark disappears and Mod Selection UI appears
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .testTag("select_mod_component")
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                      pickModLauncher.launch(arrayOf("*/*"))
                    }
                    .padding(16.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(76.dp)
                      .clip(CircleShape)
                      .background(if (isDarkMode) Color(0xFF262830) else Color(0xFFF3F4F6))
                      .testTag("select_mod_icon_button"),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.FolderZip,
                      contentDescription = stringResource(R.string.mod_selection_title),
                      tint = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22),
                      modifier = Modifier.size(38.dp)
                    )
                  }

                  Spacer(modifier = Modifier.height(16.dp))

                  Text(
                    text = stringResource(R.string.mod_selection_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg,
                    modifier = Modifier.testTag("select_mod_title")
                  )

                  Spacer(modifier = Modifier.height(6.dp))

                  Text(
                    text = stringResource(R.string.mod_selection_subtitle),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                  )

                  Spacer(modifier = Modifier.height(8.dp))

                  TextButton(
                    onClick = { pickFolderLauncher.launch(null) },
                    modifier = Modifier.testTag("select_mod_folder_button")
                  ) {
                    Text(
                      text = stringResource(R.string.mod_selection_choose_folder),
                      fontSize = 12.5.sp,
                      color = if (isDarkMode) GoldButtonColor else Color(0xFF4B5563)
                    )
                  }
                }
              }
            }
            is SearchUiState.NotFound -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.testTag("not_found_indicator")
              ) {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color(0xFF262830) else Color(0xFFF3F4F6)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = "No encontrado",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(34.dp)
                  )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = stringResource(R.string.not_found_title),
                  fontSize = 15.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = onBg,
                  modifier = Modifier.testTag("not_found_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = stringResource(R.string.not_found_subtitle),
                  fontSize = 12.5.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 24.dp)
                )
              }
            }
          }
        }
        is ModAnalysisUiState.Analyzed -> {
          // Handled full screen at top of ModstudioHomeScreen
        }
      }
    }

    // Dynamic button adapting text and behavior to search & mod state
    if (modAnalysisState !is ModAnalysisUiState.Analyzing) {
      val buttonText = when {
        searchUiState is SearchUiState.Success && showModSelectionAfterSuccess ->
          stringResource(R.string.action_select_mod)
        searchUiState is SearchUiState.Searching ->
          stringResource(R.string.status_searching)
        searchUiState is SearchUiState.Success ->
          stringResource(R.string.action_update)
        searchUiState is SearchUiState.NotFound ->
          stringResource(R.string.action_try_again)
        else ->
          stringResource(R.string.action_find)
      }

      Button(
        onClick = {
          if (searchUiState is SearchUiState.Success && showModSelectionAfterSuccess) {
            pickModLauncher.launch(arrayOf("*/*"))
          } else {
            when (searchUiState) {
              is SearchUiState.Searching -> {
                isSearching = false
                viewModel.stopSearch()
              }
              is SearchUiState.Success -> {
                if (permissionManager.hasStorageAccess()) {
                  proceedAfterStorage("UPDATE")
                } else {
                  pendingAction = "UPDATE"
                  showPermissionDialog = true
                }
              }
              is SearchUiState.NotFound, is SearchUiState.Idle -> {
                if (permissionManager.hasStorageAccess()) {
                  proceedAfterStorage("FIND")
                } else {
                  pendingAction = "FIND"
                  showPermissionDialog = true
                }
              }
            }
          }
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
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 32.dp)
          .testTag("find_button")
      ) {
        Text(
          text = buttonText,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.3.sp,
          color = buttonContent,
          modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
        )
      }
    }
  }
}

// Kept for backward compatibility with greeting tests if referenced
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun ModstudioHomeScreenPreview() {
  ModstudioTheme {
    ModstudioHomeScreen()
  }
}
