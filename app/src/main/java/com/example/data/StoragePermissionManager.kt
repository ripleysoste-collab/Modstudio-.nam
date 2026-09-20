package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Manages storage access permissions across Android 10 through Android 16.
 *
 * Handles:
 * - Legacy permissions (READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE) for Android 10 (API 29)
 * - Broad storage access (MANAGE_EXTERNAL_STORAGE) for Android 11+ (API 30+)
 * - Storage Access Framework (SAF) folder tree selection and permanent URI persistence for Android/data & Android/obb
 * - 100% offline, stored locally in SharedPreferences without internet dependency.
 */
class StoragePermissionManager(private val context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /**
   * Checks whether the app has the primary storage permissions needed to read and write files.
   * - On Android 11+ (API 30+): Checks [Environment.isExternalStorageManager].
   * - On Android 10 (API 29) & lower: Checks [Manifest.permission.READ_EXTERNAL_STORAGE] and WRITE_EXTERNAL_STORAGE.
   */
  fun hasStorageAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      val readGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED

      val writeGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

      readGranted && writeGranted
    }
  }

  /**
   * Checks whether a persisted DocumentTree URI is currently valid for a given key (data or obb).
   */
  fun hasPersistedFolderAccess(key: String): Boolean {
    val uriString = prefs.getString(key, null) ?: return false
    val targetUri = Uri.parse(uriString)
    val persistedPermissions = context.contentResolver.persistedUriPermissions
    return persistedPermissions.any { it.uri == targetUri && it.isReadPermission && it.isWritePermission }
  }

  /**
   * Persists a directory tree URI selected via SAF and saves it to local preferences.
   */
  fun persistFolderUri(key: String, uri: Uri): Boolean {
    return try {
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(uri, takeFlags)
      prefs.edit().putString(key, uri.toString()).apply()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun getPersistedFolderUri(key: String): Uri? {
    val uriString = prefs.getString(key, null) ?: return null
    return Uri.parse(uriString)
  }

  /**
   * Creates the system intent to request MANAGE_EXTERNAL_STORAGE on Android 11+ (API 30+).
   */
  fun createManageAllFilesIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
          data = Uri.parse("package:${context.packageName}")
        }
      } catch (e: Exception) {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
      }
    }
  }

  /**
   * Creates an Intent to open the Storage Access Framework Document Tree.
   * Can suggest Android/data or Android/obb as starting location.
   */
  fun createOpenDocumentTreeIntent(initialSubPath: String? = null): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !initialSubPath.isNullOrEmpty()) {
      try {
        val rootUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$initialSubPath")
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
      } catch (_: Exception) {
        // Fallback to default tree intent if initial URI building is restricted
      }
    }

    return intent
  }

  companion object {
    private const val PREFS_NAME = "modstudio_storage_perms"
    const val KEY_DATA_TREE_URI = "saf_data_tree_uri"
    const val KEY_OBB_TREE_URI = "saf_obb_tree_uri"

    @Volatile
    private var INSTANCE: StoragePermissionManager? = null

    fun getInstance(context: Context): StoragePermissionManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: StoragePermissionManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
