package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.GameIntegrityManager
import com.example.data.IntegrityReport
import com.example.data.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SolutionServiceState {
  object Idle : SolutionServiceState()
  data class Checking(val message: String) : SolutionServiceState()
  data class ErrorFound(val report: IntegrityReport) : SolutionServiceState()
  object Ok : SolutionServiceState()
  data class Repairing(val message: String) : SolutionServiceState()
  object Solved : SolutionServiceState()
}

class SolutionForegroundService : Service() {
  companion object {
    private const val ACTION_CHECK = "com.example.action.SOLUTION_CHECK"
    private const val ACTION_REPAIR = "com.example.action.SOLUTION_REPAIR"

    private val _serviceState = MutableStateFlow<SolutionServiceState>(SolutionServiceState.Idle)
    val serviceState: StateFlow<SolutionServiceState> = _serviceState.asStateFlow()

    fun startCheck(context: Context) {
      val intent = Intent(context, SolutionForegroundService::class.java).apply {
        action = ACTION_CHECK
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun startRepair(context: Context) {
      val intent = Intent(context, SolutionForegroundService::class.java).apply {
        action = ACTION_REPAIR
      }
      ContextCompat.startForegroundService(context, intent)
    }
    
    fun resetState() {
      _serviceState.value = SolutionServiceState.Idle
    }
  }

  private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
  private lateinit var integrityManager: GameIntegrityManager

  override fun onCreate() {
    super.onCreate()
    integrityManager = GameIntegrityManager.getInstance(applicationContext)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    val initialMessage = if (action == ACTION_CHECK) "Buscando errores..." else "Aplicando solución..."
    val notification = NotificationHelper.buildForegroundNotification(this, initialMessage)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NotificationHelper.NOTIFICATION_ID_FOREGROUND,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
    }

    serviceScope.launch {
      try {
        if (action == ACTION_CHECK) {
          _serviceState.value = SolutionServiceState.Checking("Buscando errores...")
          NotificationHelper.updateForegroundNotification(this@SolutionForegroundService, "Buscando errores...")
          delay(1500)
          val report = integrityManager.checkIntegrity()
          if (report.isOk) {
            _serviceState.value = SolutionServiceState.Ok
            NotificationHelper.showCompletedNotification(this@SolutionForegroundService, "Soluciones", "No se encontraron errores. El juego está íntegro y limpio.")
          } else {
            _serviceState.value = SolutionServiceState.ErrorFound(report)
            NotificationHelper.showCompletedNotification(
              this@SolutionForegroundService, 
              "Errores encontrados", 
              report.errorMessage ?: "Se detectaron problemas en los archivos del juego."
            )
          }
        } else if (action == ACTION_REPAIR) {
          val currentState = _serviceState.value
          if (currentState is SolutionServiceState.ErrorFound) {
            _serviceState.value = SolutionServiceState.Repairing("Iniciando solución...")
            NotificationHelper.updateForegroundNotification(this@SolutionForegroundService, "Iniciando solución...")
            val success = integrityManager.repairFiles(currentState.report) { progress ->
              _serviceState.value = SolutionServiceState.Repairing(progress)
              NotificationHelper.updateForegroundNotification(this@SolutionForegroundService, progress)
            }
            if (success) {
              _serviceState.value = SolutionServiceState.Solved
              NotificationHelper.showCompletedNotification(this@SolutionForegroundService, "Solución completada", "Los archivos han sido restaurados y los cambios deshechos con éxito.")
            } else {
              _serviceState.value = SolutionServiceState.ErrorFound(currentState.report)
              NotificationHelper.showCompletedNotification(this@SolutionForegroundService, "Error de reparación", "Hubo un problema al reparar los archivos.")
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
