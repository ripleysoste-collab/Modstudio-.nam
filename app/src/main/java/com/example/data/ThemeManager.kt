package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the application theme state (Light Mode / Dark Mode) with persistent local storage.
 * Defaults to Light Mode (false) as requested.
 * When Dark Mode is active, the app transitions into an eye-comfortable dark theme with golden buttons.
 */
class ThemeManager private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _isDarkMode = MutableStateFlow(isSavedDarkMode())
  val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

  fun isSavedDarkMode(): Boolean {
    return prefs.getBoolean(KEY_DARK_MODE, false)
  }

  fun setDarkMode(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    _isDarkMode.value = enabled
  }

  companion object {
    private const val PREFS_NAME = "modstudio_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode_enabled"

    @Volatile
    private var INSTANCE: ThemeManager? = null

    fun getInstance(context: Context): ThemeManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
