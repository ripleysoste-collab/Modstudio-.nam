package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.RotatingBallIndicator
import com.example.ui.viewmodel.CleoMenuUiState
import com.example.ui.viewmodel.ModstudioViewModel
import java.io.File

/**
 * Minimalist, clean interface for "Menú Cleo".
 * Allows selecting and installing the CLEO Menu mod package.
 * Performs processing in the background with notifications, displays the rotating ball indicator
 * strictly within this screen, and provides a discreet native Android APK installer button if an APK is detected.
 */
@Composable
fun CleoMenuScreen(
  onBack: () -> Unit,
  viewModel: ModstudioViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val cleoMenuState by viewModel.cleoMenuState.collectAsStateWithLifecycle()

  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val screenBg = MaterialTheme.colorScheme.background
  val onBg = MaterialTheme.colorScheme.onBackground
  val subtleTextColor = if (isDark) Color(0xFFA0A4AD) else Color(0xFF6B7280)

  // Launcher to select CLEO package file (ZIP, RAR, 7Z, APK)
  val pickFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.installCleoMenu(uri = uri, file = null)
    }
  }

  // Launcher to select CLEO mod folder
  val pickFolderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.installCleoMenu(uri = uri, file = null)
    }
  }

  val isCleoMenuActive by viewModel.isCleoMenuActive.collectAsStateWithLifecycle()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(screenBg)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
      // Top Bar: Clean, minimalist header with back button and title
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("cleo_menu_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = onBg
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
          text = "Menú Cleo",
          fontSize = 19.sp,
          fontWeight = FontWeight.SemiBold,
          color = onBg,
          modifier = Modifier.testTag("cleo_menu_title")
        )

        Spacer(modifier = Modifier.weight(1f))

        // Status badge
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isCleoMenuActive) Color(0xFFDCFCE7) else (if (isDark) Color(0xFF27272A) else Color(0xFFF3F4F6)),
          modifier = Modifier.padding(end = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isCleoMenuActive) Color(0xFF16A34A) else Color(0xFF9E9E9E))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isCleoMenuActive) "Activo" else "Inactivo",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = if (isCleoMenuActive) Color(0xFF15803D) else subtleTextColor
            )
          }
        }
      }

      // Main content area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
      ) {
        when (val state = cleoMenuState) {
          is CleoMenuUiState.Idle -> {
            if (isCleoMenuActive) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.testTag("cleo_menu_already_active_container")
              ) {
                Box(
                  modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16A34A)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Activo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                  )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                  text = "Menú Cleo Activo",
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = onBg,
                  textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = "Tu juego GTA San Andreas ya cuenta con el Menú Cleo activo y funcionando correctamente.",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Normal,
                  color = subtleTextColor,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 24.dp)
                )
              }
            } else {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.testTag("cleo_menu_idle_container")
              ) {
                // Interactive circle matching home screen
                Box(
                  modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF262830) else Color(0xFFF3F4F6))
                    .clickable {
                      pickFileLauncher.launch(arrayOf("*/*"))
                    }
                    .testTag("select_cleo_menu_circle_button"),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = "Seleccionar Menú Cleo",
                    tint = if (isDark) Color(0xFFF59E0B) else Color(0xFF1E1F22),
                    modifier = Modifier.size(38.dp)
                  )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                  text = "Seleccionar Menú Cleo",
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = onBg,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.testTag("cleo_menu_select_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = "Toca para elegir el archivo ZIP, RAR o paquete con el Menú Cleo",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Normal,
                  color = subtleTextColor,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
              }
            }
          }

          is CleoMenuUiState.Processing -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.testTag("cleo_menu_processing_container")
            ) {
              RotatingBallIndicator(statusText = state.statusMessage)

              Spacer(modifier = Modifier.height(16.dp))

              Text(
                text = "Trabajando en segundo plano",
                fontSize = 12.sp,
                color = subtleTextColor,
                textAlign = TextAlign.Center
              )
            }
          }

          is CleoMenuUiState.Success -> {
            var isApkInstalledOnPhone by remember {
              mutableStateOf(com.example.data.service.CleoInstallationManager.checkIfApkWasInstalledByTimestamp(context, state.apkFile))
            }
            var installerLaunched by remember {
              mutableStateOf(false)
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
              val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                  val wasInstalled = com.example.data.service.CleoInstallationManager.checkIfApkWasInstalledByTimestamp(context, state.apkFile)
                  if (installerLaunched && wasInstalled) {
                    isApkInstalledOnPhone = true
                    Toast.makeText(context, "¡Menú Cleo y APK instalados con éxito!", Toast.LENGTH_LONG).show()
                    viewModel.completeCleoMenuInstallation()
                    onBack()
                  }
                }
              }
              lifecycleOwner.lifecycle.addObserver(observer)
              onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
              }
            }

            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("cleo_menu_success_container")
            ) {
              Box(
                modifier = Modifier
                  .size(68.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF16A34A)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Éxito",
                  tint = Color.White,
                  modifier = Modifier.size(38.dp)
                )
              }

              Spacer(modifier = Modifier.height(14.dp))

              Text(
                text = "Detalles de la instalación",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onBg,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = "Los scripts y archivos se colocaron en el juego.",
                fontSize = 13.sp,
                color = subtleTextColor,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(16.dp))

              // Card with installation guide and details
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF22242A) else Color(0xFFF9FAFB),
                modifier = Modifier
                  .fillMaxWidth()
                  .border(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF2E323B) else Color(0xFFE5E7EB),
                    shape = RoundedCornerShape(14.dp)
                  )
              ) {
                Column(
                  modifier = Modifier.padding(14.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = Color(0xFF16A34A),
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "${state.scriptsCount} scripts desplegados en data (exterior)",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = onBg
                    )
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  Row(
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = Color(0xFF16A34A),
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Ajustes fastman92 e inis copiados a files/",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = onBg
                    )
                  }

                  if (state.hasApk && state.apkFile != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(if (isApkInstalledOnPhone) Color(0xFF16A34A) else Color(0xFFF59E0B))
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = if (isApkInstalledOnPhone) {
                          "APK instalada en el teléfono"
                        } else {
                          "APK pendiente de instalar"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isApkInstalledOnPhone) Color(0xFF16A34A) else Color(0xFFF59E0B)
                      )
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(20.dp))

              // Blue letter "Instalar APK" button when APK is present
              if (state.hasApk && state.apkFile != null) {
                TextButton(
                  onClick = {
                    installerLaunched = true
                    com.example.data.service.CleoInstallationManager.markApkInstallRequested(context)
                    launchApkInstaller(context, state.apkFile)
                  },
                  modifier = Modifier
                    .testTag("install_cleo_apk_button")
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "Instalar APK",
                    color = Color(0xFF2563EB),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                  onClick = {
                    val wasInstalledByTime = com.example.data.service.CleoInstallationManager.checkIfApkWasInstalledByTimestamp(context, state.apkFile)
                    val gameInstalled = com.example.data.service.CleoInstallationManager.checkAnyGamePackageInstalled(context, state.apkFile)
                    if (wasInstalledByTime || gameInstalled) {
                      isApkInstalledOnPhone = true
                      Toast.makeText(context, "¡Menú Cleo y APK instalados con éxito!", Toast.LENGTH_SHORT).show()
                      viewModel.completeCleoMenuInstallation()
                      onBack()
                    } else {
                      Toast.makeText(
                        context,
                        "El APK aún no está instalado en el teléfono. Toca 'Instalar APK' para proceder.",
                        Toast.LENGTH_LONG
                      ).show()
                    }
                  },
                  modifier = Modifier.testTag("verify_cleo_install_button")
                ) {
                  Text(
                    text = if (isApkInstalledOnPhone) "Finalizar" else "Verificar instalación",
                    fontSize = 14.sp,
                    color = if (isApkInstalledOnPhone) Color(0xFF16A34A) else subtleTextColor
                  )
                }
              } else {
                TextButton(
                  onClick = {
                    viewModel.completeCleoMenuInstallation()
                    onBack()
                  },
                  modifier = Modifier.testTag("cleo_menu_finish_button")
                ) {
                  Text(
                    text = "Finalizar",
                    color = Color(0xFF2563EB),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              TextButton(
                onClick = { onBack() },
                modifier = Modifier.testTag("cleo_menu_back_button")
              ) {
                Text(
                  text = "Volver",
                  fontSize = 14.sp,
                  color = subtleTextColor
                )
              }
            }
          }

          is CleoMenuUiState.Error -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.testTag("cleo_menu_error_container")
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = "Error",
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(36.dp)
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              Text(
                text = "No se pudo completar",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = onBg
              )

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = state.message,
                fontSize = 13.sp,
                color = subtleTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
              )

              Spacer(modifier = Modifier.height(20.dp))

              TextButton(
                onClick = { viewModel.resetCleoMenuState() }
              ) {
                Text(
                  text = "Reintentar",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF2563EB)
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Invokes Android's native package installer to install the modded CLEO APK.
 */
private fun launchApkInstaller(context: Context, apkFile: File) {
  try {
    if (!apkFile.exists() || apkFile.length() <= 0) {
      Toast.makeText(context, "El archivo APK no está disponible.", Toast.LENGTH_SHORT).show()
      return
    }

    val apkUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      apkFile
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(apkUri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
  } catch (e: Exception) {
    Toast.makeText(context, "Error al abrir instalador: ${e.message}", Toast.LENGTH_LONG).show()
  }
}

/**
 * Investigates whether the GTA SA Cleo APK is installed on the phone.
 * Checks the package name from the APK archive, plus known GTA SA variants.
 */
private fun checkApkInstalled(context: Context, apkFile: File?): Boolean {
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

  val targetPackages = listOf(apkPkg, "com.rockstargames.gtasa", "com.rockstargames.gtasager", "com.rockstargames.gtasa.de")
  for (pkg in targetPackages.distinct()) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, 0)
      }
      return true
    } catch (_: PackageManager.NameNotFoundException) {
      // Continue checking next variant
    } catch (_: Throwable) {
      // Ignore
    }
  }
  return false
}

