package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local offline manager for custom background images.
 * Operates 100% locally on the device with zero network calls.
 * Copies chosen images into the app's internal files directory (filesDir)
 * ensuring full persistence across app restarts without permission expiry.
 */
class BackgroundManager(private val context: Context) {
  private val prefs = context.getSharedPreferences("modstudio_prefs", Context.MODE_PRIVATE)
  private val bgFile = File(context.filesDir, "history_custom_bg.jpg")

  private val _backgroundImageFile = MutableStateFlow<File?>(
    if (bgFile.exists() && prefs.getBoolean(KEY_HAS_CUSTOM_BG, false)) bgFile else null
  )
  val backgroundImageFile: StateFlow<File?> = _backgroundImageFile.asStateFlow()

  suspend fun saveBackgroundFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
      // Decode bounds first
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
      }

      val maxDimension = 1280
      var sampleSize = 1
      while ((options.outWidth / sampleSize) > maxDimension || (options.outHeight / sampleSize) > maxDimension) {
        sampleSize *= 2
      }

      // Decode with sample size to prevent any memory or UI stutters
      val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
      }

      val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
      } ?: return@withContext false

      FileOutputStream(bgFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
      }
      bitmap.recycle()

      prefs.edit().putBoolean(KEY_HAS_CUSTOM_BG, true).apply()
      _backgroundImageFile.value = bgFile
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  suspend fun removeBackground(): Boolean = withContext(Dispatchers.IO) {
    try {
      if (bgFile.exists()) {
        bgFile.delete()
      }
      prefs.edit().putBoolean(KEY_HAS_CUSTOM_BG, false).apply()
      _backgroundImageFile.value = null
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  companion object {
    private const val KEY_HAS_CUSTOM_BG = "has_custom_bg"

    @Volatile
    private var INSTANCE: BackgroundManager? = null

    fun getInstance(context: Context): BackgroundManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: BackgroundManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
