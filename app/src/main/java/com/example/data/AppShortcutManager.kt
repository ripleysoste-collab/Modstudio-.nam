package com.example.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.ScreenState

object AppShortcutManager {
  const val ACTION_OPEN_CACHE = "com.example.modstudio.ACTION_OPEN_CACHE"
  const val ACTION_OPEN_BACKUP = "com.example.modstudio.ACTION_OPEN_BACKUP"
  const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
  const val SHORTCUT_ACTION_CACHE = "open_cache"
  const val SHORTCUT_ACTION_BACKUP = "open_backup"

  const val SHORTCUT_ID_CACHE = "open_cache"
  const val SHORTCUT_ID_BACKUP = "open_backup"

  fun initShortcuts(context: Context) {
    try {
      val appContext = context.applicationContext

      // 1. Shortcut: "Abrir cachés"
      val cacheIntent = Intent(appContext, MainActivity::class.java).apply {
        action = ACTION_OPEN_CACHE
        putExtra(EXTRA_SHORTCUT_ACTION, SHORTCUT_ACTION_CACHE)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

      val cacheShortcut = ShortcutInfoCompat.Builder(appContext, SHORTCUT_ID_CACHE)
        .setShortLabel(appContext.getString(R.string.shortcut_open_cache))
        .setLongLabel(appContext.getString(R.string.shortcut_open_cache_long))
        .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_shortcut_cache))
        .setIntent(cacheIntent)
        .setRank(1)
        .build()

      // 2. Shortcut: "Abrir backup"
      val backupIntent = Intent(appContext, MainActivity::class.java).apply {
        action = ACTION_OPEN_BACKUP
        putExtra(EXTRA_SHORTCUT_ACTION, SHORTCUT_ACTION_BACKUP)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

      val backupShortcut = ShortcutInfoCompat.Builder(appContext, SHORTCUT_ID_BACKUP)
        .setShortLabel(appContext.getString(R.string.shortcut_open_backup))
        .setLongLabel(appContext.getString(R.string.shortcut_open_backup_long))
        .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_shortcut_backup))
        .setIntent(backupIntent)
        .setRank(2)
        .build()

      ShortcutManagerCompat.setDynamicShortcuts(appContext, listOf(cacheShortcut, backupShortcut))
    } catch (_: Throwable) {
      // Ignored safely if launcher restrictions apply
    }
  }

  fun extractTargetScreen(intent: Intent?): ScreenState? {
    if (intent == null) return null
    val action = intent.action
    val extra = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
    if (action == ACTION_OPEN_CACHE || extra == SHORTCUT_ACTION_CACHE) {
      return ScreenState.TXD_EXPLORER
    }
    if (action == ACTION_OPEN_BACKUP || extra == SHORTCUT_ACTION_BACKUP) {
      return ScreenState.GAME_BACKUP
    }
    return null
  }
}
