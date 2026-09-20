package com.example.data.service

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object CleoInstallationManager {
  private const val PREFS_NAME = "cleo_installation_prefs"
  private const val KEY_PENDING_APK_PATH = "key_pending_apk_path"
  private const val KEY_SCRIPTS_COUNT = "key_scripts_count"
  private const val KEY_INSTALL_REQUESTED_TIMESTAMP = "key_install_requested_timestamp"
  private const val KEY_APK_INSTALLED = "key_apk_installed"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun savePendingInstallation(context: Context, apkFile: File?, scriptsCount: Int) {
    val prefs = getPrefs(context)
    prefs.edit().apply {
      if (apkFile != null && apkFile.exists()) {
        putString(KEY_PENDING_APK_PATH, apkFile.absolutePath)
        putBoolean(KEY_APK_INSTALLED, false)
      } else {
        remove(KEY_PENDING_APK_PATH)
        putBoolean(KEY_APK_INSTALLED, true)
      }
      putInt(KEY_SCRIPTS_COUNT, scriptsCount)
      apply()
    }
  }

  fun markApkInstallRequested(context: Context) {
    getPrefs(context).edit()
      .putLong(KEY_INSTALL_REQUESTED_TIMESTAMP, System.currentTimeMillis())
      .apply()
  }

  fun isApkPendingInstallation(context: Context): Boolean {
    val prefs = getPrefs(context)
    val isInstalled = prefs.getBoolean(KEY_APK_INSTALLED, false)
    if (isInstalled) return false

    val apkPath = prefs.getString(KEY_PENDING_APK_PATH, null)
    if (apkPath != null) {
      val f = File(apkPath)
      if (f.exists()) {
        // If it was already confirmed installed via lastUpdateTime, mark complete
        if (checkIfApkWasInstalledByTimestamp(context, f)) {
          markInstallationComplete(context)
          return false
        }
        return true
      }
    }

    // Check cleo_apk directory
    val apkDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cleo_apk")
    if (apkDir.exists()) {
      val apks = apkDir.listFiles { f -> f.extension.equals("apk", ignoreCase = true) }
      if (!apks.isNullOrEmpty()) {
        val firstApk = apks.first()
        if (checkIfApkWasInstalledByTimestamp(context, firstApk)) {
          markInstallationComplete(context)
          return false
        }
        return true
      }
    }

    return false
  }

  fun isInstallationComplete(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_APK_INSTALLED, false)
  }

  fun getPendingApk(context: Context): File? {
    val prefs = getPrefs(context)
    val apkPath = prefs.getString(KEY_PENDING_APK_PATH, null)
    if (apkPath != null) {
      val f = File(apkPath)
      if (f.exists()) return f
    }
    val apkDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cleo_apk")
    if (apkDir.exists()) {
      val apks = apkDir.listFiles { f -> f.extension.equals("apk", ignoreCase = true) }
      if (!apks.isNullOrEmpty()) return apks.first()
    }
    return null
  }

  fun getPendingScriptsCount(context: Context): Int {
    return getPrefs(context).getInt(KEY_SCRIPTS_COUNT, 0)
  }

  fun markInstallationComplete(context: Context) {
    getPrefs(context).edit()
      .putBoolean(KEY_APK_INSTALLED, true)
      .apply()
  }

  fun checkIfApkWasInstalledByTimestamp(context: Context, apkFile: File?): Boolean {
    val prefs = getPrefs(context)
    if (prefs.getBoolean(KEY_APK_INSTALLED, false)) {
      return true
    }

    val requestedTimestamp = prefs.getLong(KEY_INSTALL_REQUESTED_TIMESTAMP, 0L)
    if (requestedTimestamp <= 0L) {
      return false
    }

    val pm = context.packageManager
    val apkPkg = try {
      if (apkFile != null && apkFile.exists()) {
        pm.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName ?: "com.rockstargames.gtasa"
      } else {
        "com.rockstargames.gtasa"
      }
    } catch (_: Throwable) {
      "com.rockstargames.gtasa"
    }

    val targetPackages = listOf(apkPkg, "com.rockstargames.gtasa", "com.rockstargames.gtasager", "com.rockstargames.gtasa.de").distinct()

    for (pkg in targetPackages) {
      try {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(pkg, 0)
        }
        if (pInfo.lastUpdateTime >= requestedTimestamp - 5000L) {
          return true
        }
      } catch (_: PackageManager.NameNotFoundException) {
        // Not found
      } catch (_: Throwable) {}
    }
    return false
  }

  fun checkAnyGamePackageInstalled(context: Context, apkFile: File?): Boolean {
    val pm = context.packageManager
    val apkPkg = try {
      if (apkFile != null && apkFile.exists()) {
        pm.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName ?: "com.rockstargames.gtasa"
      } else {
        "com.rockstargames.gtasa"
      }
    } catch (_: Throwable) {
      "com.rockstargames.gtasa"
    }

    val targetPackages = listOf(apkPkg, "com.rockstargames.gtasa", "com.rockstargames.gtasager", "com.rockstargames.gtasa.de").distinct()

    for (pkg in targetPackages) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(pkg, 0)
        }
        return true
      } catch (_: PackageManager.NameNotFoundException) {
        // Not found
      } catch (_: Throwable) {}
    }
    return false
  }

  fun clear(context: Context) {
    getPrefs(context).edit().clear().apply()
  }
}
