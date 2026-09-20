package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class LanguageItem(
  val code: String,
  val displayName: String
)

/**
 * Manages the application language state with persistent local storage.
 * Defaults to Spanish ("es") as requested.
 * Supports:
 * - Español ("es")
 * - English ("en")
 * - Português ("pt")
 * - Tiếng Việt ("vi")
 * - 日本語 ("ja")
 */
class LanguageManager private constructor(private val context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _currentLanguage = MutableStateFlow(getSavedLanguageCode())
  val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

  fun getSavedLanguageCode(): String {
    return prefs.getString(KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
  }

  fun setLanguage(languageCode: String) {
    val validCode = if (SUPPORTED_LANGUAGES.any { it.code == languageCode }) languageCode else DEFAULT_LANGUAGE
    prefs.edit().putString(KEY_LANGUAGE_CODE, validCode).apply()
    _currentLanguage.value = validCode

    val locale = Locale(validCode)
    Locale.setDefault(locale)
  }

  companion object {
    const val PREFS_NAME = "modstudio_language_prefs"
    const val KEY_LANGUAGE_CODE = "selected_language_code"
    const val DEFAULT_LANGUAGE = "es"

    val SUPPORTED_LANGUAGES = listOf(
      LanguageItem(code = "es", displayName = "Español"),
      LanguageItem(code = "en", displayName = "English"),
      LanguageItem(code = "pt", displayName = "Português"),
      LanguageItem(code = "vi", displayName = "Tiếng Việt"),
      LanguageItem(code = "ja", displayName = "日本語")
    )

    @Volatile
    private var INSTANCE: LanguageManager? = null

    fun getInstance(context: Context): LanguageManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: LanguageManager(context.applicationContext).also { INSTANCE = it }
      }
    }

    /**
     * Creates a localized Context and Configuration for the given language code.
     */
    fun createLocalizedContext(baseContext: Context, languageCode: String): Context {
      val locale = Locale(languageCode)
      Locale.setDefault(locale)

      val config = Configuration(baseContext.resources.configuration)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
      } else {
        @Suppress("DEPRECATION")
        config.locale = locale
      }
      return baseContext.createConfigurationContext(config)
    }
  }
}
