package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Clean, lightweight circular indicator with sleek black outline border
 * and small "buscando" text underneath.
 * Built with native Material 3 CircularProgressIndicator for maximum smoothness,
 * zero frame-drops, and optimal responsiveness.
 */
@Composable
fun RotatingBallIndicator(
  modifier: Modifier = Modifier,
  statusText: String? = null
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val spinnerColor = if (isDark) Color(0xFFE5A93C) else Color(0xFF141416)
  val spinnerTrack = if (isDark) Color(0x33E5A93C) else Color(0x18000000)
  val textColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4A4A4A)

  Column(
    modifier = modifier
      .padding(horizontal = 24.dp)
      .testTag("rotating_ball_indicator"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // Ultra-smooth native circular indicator with golden accent in dark mode
    CircularProgressIndicator(
      modifier = Modifier
        .size(64.dp)
        .testTag("spinning_circle_box"),
      color = spinnerColor,
      strokeWidth = 3.5.dp,
      trackColor = spinnerTrack,
      strokeCap = StrokeCap.Round
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Dynamic real status label
    Text(
      text = statusText ?: stringResource(R.string.status_searching),
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 0.5.sp,
      color = textColor,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      lineHeight = 20.sp,
      modifier = Modifier.testTag("searching_text")
    )
  }
}
