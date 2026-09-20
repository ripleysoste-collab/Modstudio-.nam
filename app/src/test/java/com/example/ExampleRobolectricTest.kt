package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.StoragePermissionManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConfigEntry
import com.example.data.local.entity.HistoryEntry
import com.example.data.local.entity.ModFileEntry
import com.example.data.repository.ModstudioRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Modstudio", appName)
  }

  @Test
  fun `permission strings are defined correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    val dialogTitle = esContext.getString(R.string.permission_dialog_title)
    val obbText = esContext.getString(R.string.permission_point_obb)
    val dataText = esContext.getString(R.string.permission_point_data)

    assertEquals("Permiso de acceso a archivos", dialogTitle)
    assertNotNull(obbText)
    assertNotNull(dataText)
  }

  @Test
  fun `storage permission manager generates intents properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = StoragePermissionManager.getInstance(context)

    val allFilesIntent = manager.createManageAllFilesIntent()
    assertNotNull(allFilesIntent)

    val safTreeIntent = manager.createOpenDocumentTreeIntent("Android/data")
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, safTreeIntent.action)
  }

  @Test
  fun `room database performs local persistence for history and mod files`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = ModstudioRepository.getInstance(context)

    // Clear previous state for test isolation
    repository.clearHistory()
    repository.clearModFiles()

    // Test History insertion
    val id = repository.addHistoryEntry(
      title = "Escaneo de prueba",
      description = "Verificación de base de datos local Room",
      category = "SCAN"
    )
    assertTrue(id > 0)

    val history = repository.allHistory.first()
    assertEquals(1, history.size)
    assertEquals("Escaneo de prueba", history[0].title)

    // Test Mod files insertion (DFF model file)
    repository.addModFile(
      ModFileEntry(
        fileName = "infernus.dff",
        fileType = "IMG",
        relativePath = "gta3.img",
        sizeBytes = 2048
      )
    )
    repository.addModFile(
      ModFileEntry(
        fileName = "txd.txt",
        fileType = "TXD",
        relativePath = "/data/txd.txt",
        sizeBytes = 1024
      )
    )

    val imgList = repository.imgFiles.first()
    assertEquals(1, imgList.size)
    assertEquals("infernus.dff", imgList[0].fileName)

    val txdList = repository.txdFiles.first()
    assertEquals(1, txdList.size)
    assertEquals("txd.txt", txdList[0].fileName)

    // Test GTA containers query
    repository.addModFile(
      ModFileEntry(
        fileName = "gta3.img",
        fileType = "CONTAINER",
        relativePath = "/data/gta3.img",
        sizeBytes = 2048
      )
    )
    repository.addModFile(
      ModFileEntry(
        fileName = "gta_int.img",
        fileType = "CONTAINER",
        relativePath = "/data/gta_int.img",
        sizeBytes = 4096
      )
    )
    val containers = repository.gtaContainers.first()
    assertEquals(2, containers.size)
    assertEquals("gta3.img", containers[0].fileName)
    assertEquals("gta_int.img", containers[1].fileName)

    // Test direct query
    val directContainers = repository.getGtaContainersDirect()
    assertEquals(2, directContainers.size)

    // Clean up
    repository.clearHistory()
    assertEquals(0, repository.allHistory.first().size)
  }

  @Test
  fun `update string is defined correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    val updateAction = esContext.getString(R.string.action_update)
    assertEquals("Actualizar", updateAction)

    val enContext = com.example.data.LanguageManager.createLocalizedContext(context, "en")
    assertEquals("Update", enContext.getString(R.string.action_update))
  }

  @Test
  fun `notification helper creates notification channel and builds notification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.NotificationHelper.createNotificationChannel(context)
    val notification = com.example.data.NotificationHelper.buildForegroundNotification(context, "Buscando contenedores...")
    assertNotNull(notification)
  }

  @Test
  fun `container strings and repository file retrieval for sharing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    assertEquals("Compartir", esContext.getString(R.string.container_action_share))
    assertEquals("Ver", esContext.getString(R.string.container_action_view))

    val enContext = com.example.data.LanguageManager.createLocalizedContext(context, "en")
    assertEquals("Share", enContext.getString(R.string.container_action_share))
    assertEquals("View", enContext.getString(R.string.container_action_view))

    val repo = ModstudioRepository.getInstance(context)
    val gta3File = repo.getContainerFile("gta3.img")
    assertTrue(gta3File.exists())
    assertTrue(gta3File.length() > 0)

    val gtaIntFile = repo.getContainerFile("gta_int.img")
    assertTrue(gtaIntFile.exists())
    assertTrue(gtaIntFile.length() > 0)
  }

  @Test
  fun `language manager defaults to spanish and persists language selection`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.data.LanguageManager.getInstance(context)

    // Default must be Spanish
    val defaultLang = manager.getSavedLanguageCode()
    assertEquals("es", defaultLang)

    // Change to English
    manager.setLanguage("en")
    assertEquals("en", manager.getSavedLanguageCode())
    assertEquals("en", manager.currentLanguage.value)

    val enContext = com.example.data.LanguageManager.createLocalizedContext(context, "en")
    assertEquals("History", enContext.getString(R.string.history_title))
    assertEquals("Languages", enContext.getString(R.string.languages_title))

    // Change to Portuguese
    manager.setLanguage("pt")
    assertEquals("pt", manager.getSavedLanguageCode())
    val ptContext = com.example.data.LanguageManager.createLocalizedContext(context, "pt")
    assertEquals("Histórico", ptContext.getString(R.string.history_title))
    assertEquals("Idiomas", ptContext.getString(R.string.languages_title))

    // Change to Vietnamese
    manager.setLanguage("vi")
    assertEquals("vi", manager.getSavedLanguageCode())
    val viContext = com.example.data.LanguageManager.createLocalizedContext(context, "vi")
    assertEquals("Lịch sử", viContext.getString(R.string.history_title))
    assertEquals("Ngôn ngữ", viContext.getString(R.string.languages_title))

    // Change to Japanese
    manager.setLanguage("ja")
    assertEquals("ja", manager.getSavedLanguageCode())
    val jaContext = com.example.data.LanguageManager.createLocalizedContext(context, "ja")
    assertEquals("履歴", jaContext.getString(R.string.history_title))
    assertEquals("言語", jaContext.getString(R.string.languages_title))

    // Restore to Spanish
    manager.setLanguage("es")
    assertEquals("es", manager.getSavedLanguageCode())
  }

  @Test
  fun `theme manager defaults to light mode and persists dark mode toggle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val themeManager = com.example.data.ThemeManager.getInstance(context)

    // Default must be Light Mode (false)
    assertFalse(themeManager.isSavedDarkMode())
    assertFalse(themeManager.isDarkMode.value)

    // Toggle Dark Mode on
    themeManager.setDarkMode(true)
    assertTrue(themeManager.isSavedDarkMode())
    assertTrue(themeManager.isDarkMode.value)

    // Restore to Light Mode
    themeManager.setDarkMode(false)
    assertFalse(themeManager.isSavedDarkMode())
  }

  @Test
  fun `backup settings manager defaults to enabled and persists user toggle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val backupManager = com.example.data.BackupSettingsManager.getInstance(context)

    // Default must be true (active) as requested: "por default siempre el sistema guarda los contenedores anteriores"
    assertTrue(backupManager.isSavedBackupEnabled())
    assertTrue(backupManager.isBackupEnabled.value)

    // When user turns it off
    backupManager.setBackupEnabled(false)
    assertFalse(backupManager.isSavedBackupEnabled())
    assertFalse(backupManager.isBackupEnabled.value)

    // When user turns it back on
    backupManager.setBackupEnabled(true)
    assertTrue(backupManager.isSavedBackupEnabled())
    assertTrue(backupManager.isBackupEnabled.value)

    // Strings verification
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    assertEquals("Copias", esContext.getString(R.string.functions_backups_title))
    assertNotNull(esContext.getString(R.string.functions_backups_dialog_desc))
  }

  @Test
  fun `game backup manager scans items and performs backup`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val obbDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/obb/com.rockstargames.gtasa").apply { mkdirs() }
    java.io.File(obbDir, "main.8.com.rockstargames.gtasa.obb").writeText("dummy main obb")
    java.io.File(obbDir, "patch.8.com.rockstargames.gtasa.obb").writeText("dummy patch obb")
    java.io.File(obbDir, "com.rockstargames.gtasa.apk").writeText("dummy apk")

    val gameBackupManager = com.example.data.GameBackupManager.getInstance(context)

    // Scan files
    val items = gameBackupManager.scanGameFiles()
    assertEquals(3, items.size)

    val mainItem = items.find { it.type == com.example.data.GameBackupFileType.MAIN_OBB }
    val patchItem = items.find { it.type == com.example.data.GameBackupFileType.PATCH_OBB }
    val apkItem = items.find { it.type == com.example.data.GameBackupFileType.APK }

    assertNotNull(mainItem)
    assertNotNull(patchItem)
    assertNotNull(apkItem)

    // Perform backup
    val success = gameBackupManager.performGameBackup()
    assertTrue(success)

    val updatedState = gameBackupManager.state.value
    assertTrue(updatedState.backedUpItems.isNotEmpty())
    assertEquals(com.example.data.BackupActionType.CREATED, updatedState.actionType)

    val repository = ModstudioRepository.getInstance(context)
    val history = repository.allHistory.first()
    assertTrue(history.any { it.category == "BACKUP" && (it.title.contains("Copia de seguridad") || it.title.contains("Game Backup")) })

    // String localization check
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    assertEquals("Copia de seguridad", esContext.getString(R.string.game_backup_title))
    assertEquals("Crear copia de seguridad", esContext.getString(R.string.game_backup_action_create))
    assertEquals("Creado", esContext.getString(R.string.game_backup_action_created))
    assertEquals("Completar archivos", esContext.getString(R.string.game_backup_action_complete_missing))
    assertEquals("No encontrados", esContext.getString(R.string.game_backup_status_not_found))

    val enContext = com.example.data.LanguageManager.createLocalizedContext(context, "en")
    assertEquals("Game Backup", enContext.getString(R.string.game_backup_title))
    assertEquals("Create Backup", enContext.getString(R.string.game_backup_action_create))
    assertEquals("Created", enContext.getString(R.string.game_backup_action_created))
    assertEquals("Complete files", enContext.getString(R.string.game_backup_action_complete_missing))
    assertEquals("Not found", enContext.getString(R.string.game_backup_status_not_found))
  }

  @Test
  fun `archive explorer parses directories and extracts files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testDir = java.io.File(context.cacheDir, "test_archive_dir").apply { mkdirs() }
    val sampleObb = java.io.File(testDir, "main.8.com.rockstargames.gtasa.obb")
    com.example.data.parser.ArchiveExplorerHelper.createSampleZipArchive(sampleObb, isPatch = false, isApk = false)

    assertTrue(sampleObb.exists())

    // 1. Root inspection
    val rootContent = com.example.data.parser.ArchiveExplorerHelper.readDirectory(sampleObb, "")
    assertTrue(rootContent.folders.isNotEmpty())
    assertTrue(rootContent.folders.any { it.name == "models" || it.name == "data" })

    // 2. Subdirectory inspection
    val modelsContent = com.example.data.parser.ArchiveExplorerHelper.readDirectory(sampleObb, "models")
    assertTrue(modelsContent.files.isNotEmpty() || modelsContent.folders.isNotEmpty())

    // 3. File extraction ("Mover")
    val destDir = java.io.File(context.cacheDir, "moved_files").apply { mkdirs() }
    val extractResult = com.example.data.parser.ArchiveExplorerHelper.extractSingleFile(
      archiveFile = sampleObb,
      entryPath = "models/gta3.img",
      targetDir = destDir
    )
    assertTrue(extractResult.isSuccess)
    val extractedFile = extractResult.getOrNull()
    assertNotNull(extractedFile)
    assertTrue(extractedFile!!.exists())
    assertEquals("gta3.img", extractedFile.name)

    // 4. Localization check
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    assertEquals("Mover", esContext.getString(R.string.game_backup_action_move))
    assertEquals("Mover aquí", esContext.getString(R.string.game_backup_move_here))
    assertEquals("Memoria del dispositivo", esContext.getString(R.string.game_backup_move_device_memory))
  }

  @Test
  fun `txd explorer strings are localized and defined`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val esContext = com.example.data.LanguageManager.createLocalizedContext(context, "es")
    assertEquals("Explorador TXD", esContext.getString(R.string.txd_explorer_title))
    assertEquals("Exteriores", esContext.getString(R.string.txd_explorer_col_exteriors))
    assertEquals("Interiores", esContext.getString(R.string.txd_explorer_col_interiors))
    assertEquals("Sin texturas", esContext.getString(R.string.txd_explorer_empty))

    val enContext = com.example.data.LanguageManager.createLocalizedContext(context, "en")
    assertEquals("TXD Explorer", enContext.getString(R.string.txd_explorer_title))
    assertEquals("Exteriors", enContext.getString(R.string.txd_explorer_col_exteriors))
    assertEquals("Interiors", enContext.getString(R.string.txd_explorer_col_interiors))
    assertEquals("No textures", enContext.getString(R.string.txd_explorer_empty))
  }

  @Test
  fun `game integrity manager detects extra unrecognized files and undoes them`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.data.GameIntegrityManager.getInstance(context)

    val storageRoot = android.os.Environment.getExternalStorageDirectory()
    val backupDir = java.io.File(storageRoot, "Modstudio/Backups/com.rockstargames.gtasa")
    backupDir.mkdirs()

    val obbBackup = java.io.File(backupDir, "main.8.com.rockstargames.gtasa.obb")
    obbBackup.writeBytes(ByteArray(1024) { 1 })

    manager.generateManifest(backupDir)

    val obbLiveDir = java.io.File(storageRoot, "Android/obb/com.rockstargames.gtasa")
    obbLiveDir.mkdirs()
    val validObbLive = java.io.File(obbLiveDir, "main.8.com.rockstargames.gtasa.obb")
    validObbLive.writeBytes(ByteArray(1024) { 1 })

    val rogueObb = java.io.File(obbLiveDir, "unknown_malicious.obb")
    rogueObb.writeBytes(ByteArray(256) { 0 })

    val dataLiveDir = java.io.File(storageRoot, "Android/data/com.rockstargames.gtasa/files")
    dataLiveDir.mkdirs()
    val rogueScript = java.io.File(dataLiveDir, "bad_script.csi")
    rogueScript.writeBytes(ByteArray(128) { 2 })

    val report = manager.checkIntegrity()
    assertFalse(report.isOk)
    assertTrue(report.extraFiles.any { it.fileName == "unknown_malicious.obb" })
    assertTrue(report.extraFiles.any { it.fileName == "bad_script.csi" })

    val repairSuccess = manager.repairFiles(report) {}
    assertTrue(repairSuccess)
    assertFalse(rogueObb.exists())
    assertFalse(rogueScript.exists())
    assertTrue(validObbLive.exists())

    val reportAfter = manager.checkIntegrity()
    assertTrue(reportAfter.isOk)
  }
}


