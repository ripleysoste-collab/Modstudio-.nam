package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val GoldButtonColor = Color(0xFFE5A93C)
val GoldButtonContentColor = Color(0xFF161309)
val LightButtonColor = Color(0xFF141416)
val LightButtonContentColor = Color.White

private val DarkColorScheme =
  darkColorScheme(
    primary = GoldButtonColor,
    onPrimary = GoldButtonContentColor,
    secondary = Color(0xFFD4AF37),
    onSecondary = GoldButtonContentColor,
    background = Color(0xFF131417),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1B1C22),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF24262E),
    onSurfaceVariant = Color(0xFFA0A4AD),
    outline = Color(0xFF2E313A),
    outlineVariant = Color(0xFF23252C)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightButtonColor,
    onPrimary = LightButtonContentColor,
    secondary = Color(0xFF44474E),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFF2F4F7)
  )

@Composable
fun ModstudioTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  ModstudioTheme(darkTheme = darkTheme, content = content)
}
