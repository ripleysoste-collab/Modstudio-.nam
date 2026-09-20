package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clean, modern splash screen on a pure white background.
 * Draws the letter 'M' stroke-by-stroke ("palito por palito") in 4 smooth sequential strokes:
 * 1. Left vertical stem (bottom to top)
 * 2. Left diagonal inner stroke (top to center-bottom)
 * 3. Right diagonal inner stroke (center-bottom to right-top)
 * 4. Right vertical stem (top to bottom)
 *
 * Finished with a subtle fade-out transition into the main app (~2.8s total).
 */
@Composable
fun SplashScreen(
  onAnimationFinished: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Stroke progress 0f..1f for each of the 4 sticks ("palitos") of the M
  val stroke1 = remember { Animatable(0f) }
  val stroke2 = remember { Animatable(0f) }
  val stroke3 = remember { Animatable(0f) }
  val stroke4 = remember { Animatable(0f) }

  // Overall content alpha for a seamless, ultra-smooth exit
  val exitAlpha = remember { Animatable(1f) }

  LaunchedEffect(Unit) {
    // Small initial breath (100ms)
    delay(100)

    // Palito 1: Tallo izquierdo vertical hacia arriba
    stroke1.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
    )

    // Palito 2: Diagonal descendente hacia el vértice central
    stroke2.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
    )

    // Palito 3: Diagonal ascendente hacia la derecha
    stroke3.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
    )

    // Palito 4: Tallo derecho vertical hacia abajo
    stroke4.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
    )

    // Pause briefly to appreciate the completed clean 'M' logo
    delay(400)

    // Smooth fade-out exit transition into the main app
    exitAlpha.animateTo(
      targetValue = 0f,
      animationSpec = tween(durationMillis = 350, easing = LinearEasing)
    )

    onAnimationFinished()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .alpha(exitAlpha.value)
      .testTag("modstudio_splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    // Clean, elegant black color for the letter M sticks ('palitos')
    val strokeColor = Color(0xFF141416)

    Canvas(
      modifier = Modifier
        .size(140.dp)
        .testTag("animated_m_canvas")
    ) {
      val w = size.width
      val h = size.height

      // Dimensions & Key Coordinates of the clean minimalist 'M'
      val strokeWidthPx = 14.dp.toPx()
      val paddingX = w * 0.14f
      val paddingY = h * 0.16f

      val leftX = paddingX
      val rightX = w - paddingX
      val midX = w / 2f

      val topY = paddingY
      val bottomY = h - paddingY
      val centerVeeY = bottomY - (bottomY - topY) * 0.35f

      // 1. First stick: Left vertical stroke (from bottom to top)
      if (stroke1.value > 0f) {
        val currentY = bottomY - (bottomY - topY) * stroke1.value
        drawLine(
          color = strokeColor,
          start = Offset(leftX, bottomY),
          end = Offset(leftX, currentY),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round
        )
      }

      // 2. Second stick: Left diagonal stroke (from top-left to center-vee)
      if (stroke2.value > 0f) {
        val currentX = leftX + (midX - leftX) * stroke2.value
        val currentY = topY + (centerVeeY - topY) * stroke2.value
        drawLine(
          color = strokeColor,
          start = Offset(leftX, topY),
          end = Offset(currentX, currentY),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round
        )
      }

      // 3. Third stick: Right diagonal stroke (from center-vee to top-right)
      if (stroke3.value > 0f) {
        val currentX = midX + (rightX - midX) * stroke3.value
        val currentY = centerVeeY + (topY - centerVeeY) * stroke3.value
        drawLine(
          color = strokeColor,
          start = Offset(midX, centerVeeY),
          end = Offset(currentX, currentY),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round
        )
      }

      // 4. Fourth stick: Right vertical stroke (from top-right to bottom-right)
      if (stroke4.value > 0f) {
        val currentY = topY + (bottomY - topY) * stroke4.value
        drawLine(
          color = strokeColor,
          start = Offset(rightX, topY),
          end = Offset(rightX, currentY),
          strokeWidth = strokeWidthPx,
          cap = StrokeCap.Round
        )
      }
    }
  }
}
