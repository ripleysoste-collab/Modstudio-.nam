package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GameIntegrityManager
import com.example.data.IntegrityReport
import com.example.service.SolutionForegroundService
import com.example.service.SolutionServiceState
import com.example.ui.components.RotatingBallIndicator
import com.example.ui.theme.GoldButtonColor
import com.example.ui.theme.GoldButtonContentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolutionScreen(
  onBack: () -> Unit,
  isDarkMode: Boolean
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val serviceState by SolutionForegroundService.serviceState.collectAsState()

  var showErrorDetails by remember { mutableStateOf(false) }
  var scale by remember { mutableStateOf(1f) }

  val bgColor = if (isDarkMode) Color(0xFF131418) else Color(0xFFF9FAFB)
  val surfaceColor = if (isDarkMode) Color(0xFF1E1F22) else Color.White
  val onSurfaceColor = if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF111827)
  val iconTint = if (isDarkMode) GoldButtonColor else Color(0xFF1F2937)

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      containerColor = bgColor,
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.solution_screen_title),
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
              color = onSurfaceColor
            )
          },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = onSurfaceColor
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = bgColor,
            titleContentColor = onSurfaceColor,
            navigationIconContentColor = onSurfaceColor
          )
        )
      }
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Spacer(modifier = Modifier.weight(1f))

        Crossfade(targetState = serviceState, label = "state_transition") { state ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
          ) {
            when (state) {
              is SolutionServiceState.Idle -> {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = null,
                  tint = iconTint,
                  modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                  text = stringResource(R.string.solution_status_idle_title),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = stringResource(R.string.solution_status_idle_desc),
                  fontSize = 14.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 16.dp)
                )
              }
              is SolutionServiceState.Checking -> {
                RotatingBallIndicator(statusText = stringResource(R.string.solution_status_checking_title))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = state.message.ifEmpty { stringResource(R.string.solution_status_checking_desc) },
                  fontSize = 14.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center
                )
              }
              is SolutionServiceState.Ok -> {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF10B981), // Emerald
                  modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                  text = stringResource(R.string.solution_status_ok_title),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = stringResource(R.string.solution_status_ok_desc),
                  fontSize = 14.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center
                )
              }
              is SolutionServiceState.ErrorFound -> {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                      detectTransformGestures { _, _, zoom, _ ->
                        scale *= zoom
                        if (scale > 1.2f && !showErrorDetails) {
                          showErrorDetails = true
                          scale = 1f
                        } else if (scale < 0.8f && scale > 0f) {
                          scale = 1f
                        }
                      }
                    }
                ) {
                  Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = Color(0xFFEF4444), // Red
                    modifier = Modifier.size(72.dp)
                  )
                  Spacer(modifier = Modifier.height(24.dp))
                  Text(
                    text = stringResource(R.string.solution_status_error_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  
                  val rep = state.report
                  val summaryText = buildString {
                    append(rep.errorMessage ?: stringResource(R.string.solution_status_error_desc))
                    val parts = mutableListOf<String>()
                    if (rep.missingFiles.isNotEmpty()) parts.add("${rep.missingFiles.size} faltante(s)")
                    if (rep.corruptedFiles.isNotEmpty()) parts.add("${rep.corruptedFiles.size} alterado(s)")
                    if (rep.extraFiles.isNotEmpty()) parts.add("${rep.extraFiles.size} extra/no reconocido(s)")
                    if (parts.isNotEmpty()) {
                      append("\n\n(${parts.joinToString(" • ")})")
                    }
                  }
                  
                  Text(
                    text = summaryText,
                    fontSize = 14.sp,
                    color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                  )

                  Spacer(modifier = Modifier.height(16.dp))

                  OutlinedButton(
                    onClick = { showErrorDetails = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                      contentColor = if (isDarkMode) GoldButtonColor else Color(0xFF1E1F22)
                    ),
                    modifier = Modifier.testTag("solution_view_details_button")
                  ) {
                    Text(
                      text = "Ver detalles de archivos",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }
              is SolutionServiceState.Repairing -> {
                RotatingBallIndicator(statusText = stringResource(R.string.solution_status_repairing_title))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = state.message.ifEmpty { stringResource(R.string.solution_status_repairing_desc) },
                  fontSize = 14.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center
                )
              }
              is SolutionServiceState.Solved -> {
                Icon(
                  imageVector = Icons.Default.Build,
                  contentDescription = null,
                  tint = Color(0xFF3B82F6), // Blue
                  modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                  text = stringResource(R.string.solution_status_solved_title),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = stringResource(R.string.solution_status_solved_desc),
                  fontSize = 14.sp,
                  color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Button Area
        val buttonText = when (serviceState) {
          is SolutionServiceState.Idle, is SolutionServiceState.Ok -> stringResource(R.string.solution_btn_review)
          is SolutionServiceState.ErrorFound -> stringResource(R.string.solution_btn_repair)
          is SolutionServiceState.Solved -> stringResource(R.string.solution_btn_solved)
          else -> ""
        }

        val showButton = serviceState !is SolutionServiceState.Checking && serviceState !is SolutionServiceState.Repairing

        if (showButton) {
          Button(
            onClick = {
              when (serviceState) {
                is SolutionServiceState.Idle, is SolutionServiceState.Ok, is SolutionServiceState.Solved -> {
                  SolutionForegroundService.startCheck(context)
                }
                is SolutionServiceState.ErrorFound -> {
                  SolutionForegroundService.startRepair(context)
                }
                else -> {}
              }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (serviceState is SolutionServiceState.ErrorFound) GoldButtonColor else if (serviceState is SolutionServiceState.Solved) Color(0xFF3B82F6) else Color(0xFF1E1F22),
              contentColor = if (serviceState is SolutionServiceState.ErrorFound) GoldButtonContentColor else Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("solution_action_button")
          ) {
            Text(
              text = buttonText,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.5.sp
            )
          }
        }
      }
    }

    // Error Details Overlay
    AnimatedVisibility(
      visible = showErrorDetails,
      enter = scaleIn(initialScale = 0.8f, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
      exit = scaleOut(targetScale = 0.8f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(bgColor)
          .pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
              scale *= zoom
              if (scale < 0.8f && showErrorDetails) {
                showErrorDetails = false
                scale = 1f
              } else if (scale > 1.2f && scale > 1f) {
                scale = 1f
              }
            }
          }
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Custom Top Bar with Back Arrow
          TopAppBar(
            title = {
              Text(
                text = "Detalles del Error",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceColor
              )
            },
            navigationIcon = {
              IconButton(onClick = { showErrorDetails = false }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Cerrar Detalles",
                  tint = onSurfaceColor
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = bgColor,
              titleContentColor = onSurfaceColor,
              navigationIconContentColor = onSurfaceColor
            )
          )

          // Content
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp)
              .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Error,
              contentDescription = null,
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            val currentReport = (serviceState as? SolutionServiceState.ErrorFound)?.report

            if (currentReport != null) {
              // 1. Missing Files Card
              if (currentReport.missingFiles.isNotEmpty()) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = surfaceColor),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                      text = "Archivos Faltantes (${currentReport.missingFiles.size})",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "Estos archivos requeridos por el juego fueron eliminados:",
                      fontSize = 13.sp,
                      color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    currentReport.missingFiles.forEach { file ->
                      Text(
                        text = "• ${file.fileName}\n  Ruta: ${file.relativePath}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(vertical = 2.dp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(16.dp))
              }

              // 2. Corrupted Files Card
              if (currentReport.corruptedFiles.isNotEmpty()) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = surfaceColor),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                      text = "Archivos Dañados o Alterados (${currentReport.corruptedFiles.size})",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "El tamaño o contenido de estos archivos difiere de la copia original:",
                      fontSize = 13.sp,
                      color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    currentReport.corruptedFiles.forEach { file ->
                      Text(
                        text = "• ${file.fileName}\n  Ruta: ${file.relativePath}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(vertical = 2.dp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(16.dp))
              }

              // 3. Extra / Malicious / Unrecognized Files Card
              if (currentReport.extraFiles.isNotEmpty()) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = surfaceColor),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                      text = "Archivos Extra o No Reconocidos (${currentReport.extraFiles.size})",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFE11D48)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "Se encontraron archivos o modificaciones añadidas en las carpetas del juego u OBB que no forman parte del mapa original:",
                      fontSize = 13.sp,
                      color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    currentReport.extraFiles.forEach { file ->
                      Text(
                        text = "• ${file.fileName}\n  Ubicación: ${file.relativePath}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceColor,
                        modifier = Modifier.padding(vertical = 3.dp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(16.dp))
              }

              // 4. Solution Strategy Card
              Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(20.dp)) {
                  Text(
                    text = "Solución a Implementar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceColor
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = buildString {
                      append("Al ejecutar la solución:\n")
                      if (currentReport.missingFiles.isNotEmpty() || currentReport.corruptedFiles.isNotEmpty()) {
                        append("• Se restaurarán milimétricamente los archivos originales desde tu copia de seguridad segura.\n")
                      }
                      if (currentReport.extraFiles.isNotEmpty()) {
                        append("• Se desharán los cambios eliminando los archivos y carpetas no reconocidos que puedan ocasionar fallos o inestabilidad en el juego.\n")
                      }
                      append("El juego quedará completamente íntegro y listo para funcionar con normalidad.")
                    },
                    fontSize = 14.sp,
                    color = if (isDarkMode) Color(0xFFA0A4AD) else Color(0xFF6B7280),
                    lineHeight = 20.sp
                  )
                }
              }

              Spacer(modifier = Modifier.height(20.dp))

              Button(
                onClick = {
                  showErrorDetails = false
                  SolutionForegroundService.startRepair(context)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                  containerColor = GoldButtonColor,
                  contentColor = GoldButtonContentColor
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("solution_details_repair_button")
              ) {
                Text(
                  text = "Solucionar y Deshacer Cambios",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = "(Toca la flecha atrás o pellizca hacia adentro para volver)",
              fontSize = 13.sp,
              color = if (isDarkMode) Color(0xFF6B7280) else Color(0xFFA0A4AD),
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}
