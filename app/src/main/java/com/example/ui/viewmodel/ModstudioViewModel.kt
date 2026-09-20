package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.analyzer.DffMatchEngine
import com.example.data.analyzer.MatchPlan
import com.example.data.analyzer.ModAnalysisResult
import com.example.data.analyzer.ModDffAnalyzer
import com.example.data.local.entity.HistoryEntry
import com.example.data.local.entity.ModFileEntry
import com.example.data.repository.ContainerSearchResult
import com.example.data.repository.ModstudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed class CleoMenuUiState {
  object Idle : CleoMenuUiState()
  data class Processing(val statusMessage: String) : CleoMenuUiState()
  data class Success(
    val message: String,
    val scriptsCount: Int,
    val hasApk: Boolean,
    val apkFile: File?
  ) : CleoMenuUiState()
  data class Error(val message: String) : CleoMenuUiState()
}

sealed class SearchUiState {
  object Idle : SearchUiState()
  data class Searching(val currentStepMessage: String) : SearchUiState()
  data class Success(val gta3Size: Long, val gtaIntSize: Long) : SearchUiState()
  data class NotFound(val reason: String) : SearchUiState()
}

sealed class ModAnalysisUiState {
  object Idle : ModAnalysisUiState()
  data class Analyzing(val statusMessage: String) : ModAnalysisUiState()
  data class Analyzed(val result: ModAnalysisResult) : ModAnalysisUiState()
  data class Error(val message: String) : ModAnalysisUiState()
}

sealed class MatchUiState {
  object Idle : MatchUiState()
  data class Matching(val stepMessage: String) : MatchUiState()
  data class Matched(val plan: MatchPlan) : MatchUiState()
  data class Error(val message: String) : MatchUiState()
}

sealed class RebuildUiState {
  object Idle : RebuildUiState()
  data class Rebuilding(val statusMessage: String, val progress: Float = 0f) : RebuildUiState()
  data class Success(
    val message: String,
    val summary: com.example.data.analyzer.ModImplementationSummary? = null
  ) : RebuildUiState()
  data class Error(val message: String) : RebuildUiState()
}

/**
 * Main ViewModel handling reactive Room database state for Modstudio screens.
 */
class ModstudioViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = ModstudioRepository.getInstance(application)

  val historyList: StateFlow<List<HistoryEntry>> = repository.allHistory
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val imgFiles: StateFlow<List<ModFileEntry>> = repository.imgFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val gta3DffFiles: StateFlow<List<ModFileEntry>> = repository.gta3DffFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val gtaIntDffFiles: StateFlow<List<ModFileEntry>> = repository.gtaIntDffFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val gta3TxdFiles: StateFlow<List<ModFileEntry>> = repository.gta3TxdFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val gtaIntTxdFiles: StateFlow<List<ModFileEntry>> = repository.gtaIntTxdFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val txdFiles: StateFlow<List<ModFileEntry>> = repository.txdFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val gtaContainers: StateFlow<List<ModFileEntry>> = repository.gtaContainers
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val csaFiles: StateFlow<List<ModFileEntry>> = repository.csaFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val csiFiles: StateFlow<List<ModFileEntry>> = repository.csiFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val fxtFiles: StateFlow<List<ModFileEntry>> = repository.fxtFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  fun refreshScripts() {
    viewModelScope.launch {
      repository.populateScriptEntries()
    }
  }

  fun refreshTextures() {
    viewModelScope.launch {
      repository.populateTextureEntries()
    }
  }

  private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _modAnalysisState = MutableStateFlow<ModAnalysisUiState>(ModAnalysisUiState.Idle)
  val modAnalysisState: StateFlow<ModAnalysisUiState> = _modAnalysisState.asStateFlow()

  private val _matchUiState = MutableStateFlow<MatchUiState>(MatchUiState.Idle)
  val matchUiState: StateFlow<MatchUiState> = _matchUiState.asStateFlow()

  private val _rebuildUiState = MutableStateFlow<RebuildUiState>(RebuildUiState.Idle)
  val rebuildUiState: StateFlow<RebuildUiState> = _rebuildUiState.asStateFlow()

  // Rule 1: Cleo menu is protected by default. isCleoMenuActive = true prevents access.
  private val _isCleoMenuActive = MutableStateFlow(true)
  val isCleoMenuActive: StateFlow<Boolean> = _isCleoMenuActive.asStateFlow()

  // showCleoMenuInMenu: completely hidden at start. Only appears if scan ran and confirmed no Cleo scripts or fastman files.
  private val _showCleoMenuInMenu = MutableStateFlow(false)
  val showCleoMenuInMenu: StateFlow<Boolean> = _showCleoMenuInMenu.asStateFlow()

  private val _isCleoMenuChecked = MutableStateFlow(false)
  val isCleoMenuChecked: StateFlow<Boolean> = _isCleoMenuChecked.asStateFlow()

  private val _cleoMenuState = MutableStateFlow<CleoMenuUiState>(CleoMenuUiState.Idle)
  val cleoMenuState: StateFlow<CleoMenuUiState> = _cleoMenuState.asStateFlow()

  init {
    checkCleoMenuStatus()
    // Check if containers are already backed up and saved in local database
    viewModelScope.launch {
      val existing = repository.getGtaContainersDirect()
      val gta3 = existing.firstOrNull { it.fileName.equals("gta3.img", ignoreCase = true) }
      val gtaInt = existing.firstOrNull { it.fileName.equals("gta_int.img", ignoreCase = true) }
      if (gta3 != null && gtaInt != null && gta3.sizeBytes > 0 && gtaInt.sizeBytes > 0) {
        _searchUiState.value = SearchUiState.Success(
          gta3Size = gta3.sizeBytes,
          gtaIntSize = gtaInt.sizeBytes
        )
        // Ensure all container .dff models are populated in Explorador table
        repository.populateContainerDffEntries()
        // Ensure textures are populated in Explorador TXD
        repository.populateTextureEntries()
      } else {
        repository.populateTextureEntries()
      }
    }

    // Observe background foreground service state
    viewModelScope.launch {
      com.example.service.GtaSyncService.serviceState.collect { syncState ->
        when (syncState) {
          is com.example.service.SyncServiceState.Running -> {
            if (syncState.action == com.example.service.GtaSyncService.ACTION_GAME_BACKUP ||
                syncState.action == com.example.service.GtaSyncService.ACTION_COMPRESS_BACKUP) {
              // Game backup has its own dedicated screen and does not affect the home screen
              return@collect
            }
            _isScanning.value = true
            _searchUiState.value = SearchUiState.Searching(syncState.message)
          }
          is com.example.service.SyncServiceState.Finished -> {
            if (syncState.action == com.example.service.GtaSyncService.ACTION_GAME_BACKUP ||
                syncState.action == com.example.service.GtaSyncService.ACTION_COMPRESS_BACKUP) {
              // Game backup has its own dedicated screen and does not affect the home screen
              return@collect
            }
            _isScanning.value = false
            val existing = repository.getGtaContainersDirect()
            val gta3 = existing.firstOrNull { it.fileName.equals("gta3.img", ignoreCase = true) }
            val gtaInt = existing.firstOrNull { it.fileName.equals("gta_int.img", ignoreCase = true) }
            if (syncState.success && gta3 != null && gtaInt != null) {
              _searchUiState.value = SearchUiState.Success(gta3.sizeBytes, gtaInt.sizeBytes)
              repository.populateContainerDffEntries()
              repository.populateTextureEntries()
            } else if (!syncState.success && (gta3 == null || gtaInt == null)) {
              _searchUiState.value = SearchUiState.NotFound(syncState.message)
              repository.populateTextureEntries()
            }
            checkCleoMenuStatus()
          }
          is com.example.service.SyncServiceState.Idle -> {}
        }
      }
    }
  }

  fun startRealContainerSearch(onCompleted: ((Boolean) -> Unit)? = null) {
    if (_isScanning.value) return
    try {
      // Launch foreground background service for uninterrupted execution
      com.example.service.GtaSyncService.startFindBackup(getApplication())
    } catch (e: Exception) {
      // Fallback to in-app coroutine if service cannot start
      viewModelScope.launch {
        _isScanning.value = true
        _searchUiState.value = SearchUiState.Searching("Verificando almacenamiento...")
        try {
          val result = repository.findAndBackupGtaContainers { stepMsg ->
            _searchUiState.value = SearchUiState.Searching(stepMsg)
          }
          when (result) {
            is ContainerSearchResult.Success -> {
              _searchUiState.value = SearchUiState.Success(result.gta3Size, result.gtaIntSize)
              onCompleted?.invoke(true)
            }
            is ContainerSearchResult.NotFound -> {
              _searchUiState.value = SearchUiState.NotFound(result.reason)
              onCompleted?.invoke(false)
            }
          }
        } catch (err: Exception) {
          _searchUiState.value = SearchUiState.NotFound(err.localizedMessage ?: "Error durante la búsqueda")
          onCompleted?.invoke(false)
        } finally {
          _isScanning.value = false
          checkCleoMenuStatus()
        }
      }
    }
  }

  fun startUpdateFlow(onCompleted: ((Boolean, String) -> Unit)? = null) {
    if (_isScanning.value) return
    try {
      // Launch foreground background service for uninterrupted execution
      com.example.service.GtaSyncService.startUpdate(getApplication())
    } catch (e: Exception) {
      viewModelScope.launch {
        _isScanning.value = true
        _searchUiState.value = SearchUiState.Searching("Buscando actualizaciones...")
        try {
          val result = repository.updateOrRefreshGtaContainers { stepMsg ->
            _searchUiState.value = SearchUiState.Searching(stepMsg)
          }
          when (result) {
            is com.example.data.repository.ContainerUpdateResult.Success -> {
              _searchUiState.value = SearchUiState.Success(result.gta3Size, result.gtaIntSize)
              onCompleted?.invoke(result.changed, result.message)
            }
            is com.example.data.repository.ContainerUpdateResult.Error -> {
              val existing = repository.getGtaContainersDirect()
              val gta3 = existing.firstOrNull { it.fileName.equals("gta3.img", ignoreCase = true) }
              val gtaInt = existing.firstOrNull { it.fileName.equals("gta_int.img", ignoreCase = true) }
              if (gta3 != null && gtaInt != null) {
                _searchUiState.value = SearchUiState.Success(gta3.sizeBytes, gtaInt.sizeBytes)
              } else {
                _searchUiState.value = SearchUiState.NotFound(result.message)
              }
              onCompleted?.invoke(false, result.message)
            }
          }
        } catch (err: Exception) {
          _searchUiState.value = SearchUiState.NotFound(err.localizedMessage ?: "Error al actualizar")
          onCompleted?.invoke(false, err.localizedMessage ?: "Error al actualizar")
        } finally {
          _isScanning.value = false
        }
      }
    }
  }

  fun resetSearchToIdle() {
    _searchUiState.value = SearchUiState.Idle
    _isScanning.value = false
  }

  fun startSearchAndScan(onFinished: ((Int) -> Unit)? = null) {
    startRealContainerSearch { success ->
      onFinished?.invoke(if (success) 2 else 0)
    }
  }

  fun stopSearch() {
    _isScanning.value = false
    _searchUiState.value = SearchUiState.Idle
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      repository.clearHistory()
    }
  }

  fun deleteHistory(id: Long) {
    viewModelScope.launch {
      repository.deleteHistory(id)
    }
  }

  fun addHistoryLog(title: String, description: String, category: String = "INFO") {
    viewModelScope.launch {
      repository.addHistoryEntry(title, description, category)
    }
  }

  fun clearAllFiles() {
    viewModelScope.launch {
      repository.clearModFiles()
    }
  }

  fun deleteModFile(id: Long) {
    viewModelScope.launch {
      repository.deleteModFile(id)
    }
  }

  private val analysisExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
    throwable.printStackTrace()
    _modAnalysisState.value = ModAnalysisUiState.Error("No se pudo procesar el archivo: ${throwable.localizedMessage ?: "error de lectura"}")
  }

  private var activeModUri: Uri? = null
  private var activeModFile: File? = null

  fun analyzeModUri(uri: Uri) {
    activeModUri = uri
    activeModFile = null

    // Populate container .dff models in parallel immediately
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      repository.populateContainerDffEntries()
    }

    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + analysisExceptionHandler) {
      _modAnalysisState.value = ModAnalysisUiState.Analyzing("Analizando mod milimétricamente...")
      try {
        val workingDir = repository.getWorkingContainersDir()
        // Stage DFF files in background for instant injection capability
        com.example.data.analyzer.ModStagingManager.stageDffFiles(getApplication(), uri)

        val result = ModDffAnalyzer.analyzeFromUri(
          context = getApplication(),
          uri = uri,
          gameWorkingDir = workingDir
        )
        _modAnalysisState.value = ModAnalysisUiState.Analyzed(result)
        repository.addHistoryEntry(
          title = "Mod analizado: ${result.modName}",
          description = "${result.totalDffFound} modelos DFF clasificados (${result.gta3Entries.size} gta3.img, ${result.gtaIntEntries.size} gta_int.img). Ignorados: ${result.ignoredNonDffCount}.",
          category = "MOD_ANALYSIS"
        )
        triggerMatchProcess(result)
      } catch (t: Throwable) {
        t.printStackTrace()
        _modAnalysisState.value = ModAnalysisUiState.Error(t.localizedMessage ?: "Error al analizar el mod")
      }
    }
  }

  fun analyzeModFile(file: File) {
    activeModFile = file
    activeModUri = null

    // Populate container .dff models in parallel immediately
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      repository.populateContainerDffEntries()
    }

    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + analysisExceptionHandler) {
      _modAnalysisState.value = ModAnalysisUiState.Analyzing("Analizando mod milimétricamente...")
      try {
        val workingDir = repository.getWorkingContainersDir()
        val result = ModDffAnalyzer.analyzeFromFile(
          file = file,
          gameWorkingDir = workingDir
        )
        _modAnalysisState.value = ModAnalysisUiState.Analyzed(result)
        repository.addHistoryEntry(
          title = "Mod analizado: ${result.modName}",
          description = "${result.totalDffFound} modelos DFF clasificados (${result.gta3Entries.size} gta3.img, ${result.gtaIntEntries.size} gta_int.img). Ignorados: ${result.ignoredNonDffCount}.",
          category = "MOD_ANALYSIS"
        )
        triggerMatchProcess(result)
      } catch (t: Throwable) {
        t.printStackTrace()
        _modAnalysisState.value = ModAnalysisUiState.Error(t.localizedMessage ?: "Error al analizar el mod")
      }
    }
  }

  fun triggerMatchProcess(result: ModAnalysisResult) {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      // Exactly 2 seconds delay after analysis as requested
      delay(2000L)
      _matchUiState.value = MatchUiState.Matching("Identificando modelos DFF...")
      delay(600L)
      _matchUiState.value = MatchUiState.Matching("Comprobando coincidencias en GTA3 y GTA_INT...")

      try {
        val workingDir = repository.getWorkingContainersDir()
        val allEntries = result.gta3Entries + result.gtaIntEntries
        val plan = DffMatchEngine.buildMatchPlan(allEntries, workingDir, result.modName)

        delay(600L)
        _matchUiState.value = MatchUiState.Matching("Determinando reemplazos e inyecciones...")
        delay(400L)
        _matchUiState.value = MatchUiState.Matched(plan)

        repository.addHistoryEntry(
          title = "Match de modelos DFF: ${result.modName}",
          description = "${plan.replaceCount} reemplazos, ${plan.injectCount} inyecciones. Afecta: ${if (plan.affectsGta3) "gta3.img " else ""}${if (plan.affectsGtaInt) "gta_int.img" else ""}",
          category = "DFF_MATCH"
        )

        // AUTOMATION: Automatically start rebuilding and deploying without requiring manual button click
        delay(700L)
        executeRebuild(plan)
      } catch (t: Throwable) {
        t.printStackTrace()
        _matchUiState.value = MatchUiState.Error("Error al realizar match: ${t.localizedMessage ?: "error desconocido"}")
      }
    }
  }

  fun executeRebuild(plan: MatchPlan) {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      _rebuildUiState.value = RebuildUiState.Rebuilding("Iniciando inyección y reconstrucción...", 0.05f)
      try {
        val summary = repository.executeRebuildPlan(
          plan = plan,
          modUri = activeModUri,
          modFile = activeModFile,
          onProgress = { msg, prog ->
            _rebuildUiState.value = RebuildUiState.Rebuilding(msg, prog)
          }
        )

        if (summary.success) {
          _rebuildUiState.value = RebuildUiState.Success(
            message = "Modelos DFF inyectados en texdb/ y scripts procesados para Android/data.",
            summary = summary
          )
        } else {
          _rebuildUiState.value = RebuildUiState.Error(
            summary.errorMessage ?: "No se pudo completar la reconstrucción del contenedor."
          )
        }
      } catch (t: Throwable) {
        t.printStackTrace()
        _rebuildUiState.value = RebuildUiState.Error("Error en reconstrucción: ${t.localizedMessage ?: "error desconocido"}")
      }
    }
  }

  fun resetRebuildState() {
    _rebuildUiState.value = RebuildUiState.Idle
  }

  fun resetMatchState() {
    _matchUiState.value = MatchUiState.Idle
  }

  fun resetModAnalysis() {
    activeModUri = null
    activeModFile = null
    _modAnalysisState.value = ModAnalysisUiState.Idle
    _matchUiState.value = MatchUiState.Idle
    _rebuildUiState.value = RebuildUiState.Idle
  }

  fun checkCleoMenuStatus() {
    viewModelScope.launch(Dispatchers.IO) {
      val context = getApplication<Application>()

      // 0. If an APK is pending installation, keep the Cleo Menu active and visible!
      if (com.example.data.service.CleoInstallationManager.isApkPendingInstallation(context)) {
        _isCleoMenuActive.value = false
        _showCleoMenuInMenu.value = true
        _isCleoMenuChecked.value = true
        val pendingApk = com.example.data.service.CleoInstallationManager.getPendingApk(context)
        val count = com.example.data.service.CleoInstallationManager.getPendingScriptsCount(context)
        _cleoMenuState.value = CleoMenuUiState.Success(
          message = "Menú Cleo procesado. APK disponible para instalar.",
          scriptsCount = count,
          hasApk = pendingApk != null,
          apkFile = pendingApk
        )
        return@launch
      }

      // 1. Check if scripts exist in repository / DB flows
      val hasScripts = csaFiles.value.isNotEmpty() || csiFiles.value.isNotEmpty() || fxtFiles.value.isNotEmpty()
      if (hasScripts) {
        _isCleoMenuActive.value = true
        _showCleoMenuInMenu.value = false
        _isCleoMenuChecked.value = true
        return@launch
      }

      // 2. Check game data folder and files/ subfolder on device
      val active = com.example.data.service.GameDirectoryDeployer.checkCleoMenuActive(getApplication())
      if (active) {
        _isCleoMenuActive.value = true
        _showCleoMenuInMenu.value = false
        _isCleoMenuChecked.value = true
        return@launch
      }

      // 3. Check if container scan or background copy has run
      // At initial launch or before scanning, keep it completely hidden!
      val containers = repository.getGtaContainersDirect()
      val scanFinished = containers.isNotEmpty() || _searchUiState.value !is SearchUiState.Idle
      if (scanFinished && !active && !hasScripts) {
        // ONLY if the container copy/data scan ran and found neither cleos nor fastman files:
        _isCleoMenuActive.value = false
        _showCleoMenuInMenu.value = true
      } else {
        // Otherwise it remains hidden and locked
        _isCleoMenuActive.value = true
        _showCleoMenuInMenu.value = false
      }
      _isCleoMenuChecked.value = true
    }
  }

  fun resetCleoMenuState() {
    _cleoMenuState.value = CleoMenuUiState.Idle
  }

  fun installCleoMenu(uri: Uri? = null, file: File? = null) {
    viewModelScope.launch {
      _cleoMenuState.value = CleoMenuUiState.Processing("Iniciando despliegue de Menú Cleo...")
      val context = getApplication<Application>()
      com.example.data.NotificationHelper.updateForegroundNotification(
        context,
        "Instalando Menú Cleo en segundo plano..."
      )

      val result = withContext(Dispatchers.IO) {
        com.example.data.service.GameDirectoryDeployer.deployCleoMenuPackage(
          context = context,
          uri = uri,
          file = file,
          onProgress = { msg ->
            _cleoMenuState.value = CleoMenuUiState.Processing(msg)
            com.example.data.NotificationHelper.updateForegroundNotification(context, msg)
          }
        )
      }

      com.example.data.NotificationHelper.cancelForegroundNotification(context)

      if (result.success) {
        refreshScripts()
        com.example.data.service.CleoInstallationManager.savePendingInstallation(
          context = context,
          apkFile = result.apkFile,
          scriptsCount = result.scriptsCount
        )
        com.example.data.NotificationHelper.showCompletedNotification(
          context,
          "Menú Cleo procesado",
          "${result.scriptsCount} scripts y archivos desplegados en data."
        )
        _cleoMenuState.value = CleoMenuUiState.Success(
          message = result.message,
          scriptsCount = result.scriptsCount,
          hasApk = result.hasApk,
          apkFile = result.apkFile
        )
      } else {
        _cleoMenuState.value = CleoMenuUiState.Error(result.message)
      }
    }
  }

  fun completeCleoMenuInstallation() {
    viewModelScope.launch(Dispatchers.IO) {
      val context = getApplication<Application>()
      com.example.data.service.CleoInstallationManager.markInstallationComplete(context)
      _isCleoMenuActive.value = true
      _showCleoMenuInMenu.value = false
      _isCleoMenuChecked.value = true
      _cleoMenuState.value = CleoMenuUiState.Idle
      checkCleoMenuStatus()
    }
  }
}
