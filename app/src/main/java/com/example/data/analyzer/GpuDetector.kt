package com.example.data.analyzer

import android.os.Build
import java.io.File
import java.util.Locale

enum class GpuFormat(val extension: String, val displayName: String) {
  DXT("dxt", "Adreno (Qualcomm DXT)"),
  ETC("etc", "Mali / Genérico (ETC)"),
  PVR("pvr", "PowerVR (PVR)"),
  UNC("unc", "Sin comprimir (UNC)")
}

/**
 * Detects device GPU / CPU architecture to identify the appropriate GTA San Andreas texture format:
 * - Adreno (Qualcomm): dxt
 * - Mali (ARM, MediaTek, Exynos): etc
 * - PowerVR: pvr
 */
object GpuDetector {

  fun detectGpuFormat(): GpuFormat {
    val hardware = (Build.HARDWARE ?: "").lowercase(Locale.ROOT)
    val board = (Build.BOARD ?: "").lowercase(Locale.ROOT)
    val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
    val model = (Build.MODEL ?: "").lowercase(Locale.ROOT)

    // 1. Qualcomm Snapdragon / Adreno
    if (hardware.contains("qcom") || hardware.contains("qualcomm") || hardware.contains("adreno") ||
      board.contains("qcom") || board.contains("msm") || board.contains("sdm") || board.contains("sm") ||
      hardware.contains("snapdragon")
    ) {
      return GpuFormat.DXT
    }

    // 2. PowerVR / Imagination
    if (hardware.contains("powervr") || hardware.contains("pvr") || hardware.contains("rogue") ||
      hardware.contains("sgx")
    ) {
      return GpuFormat.PVR
    }

    // 3. Mali / MediaTek / Samsung Exynos / HiSilicon Kirin
    if (hardware.contains("mali") || hardware.contains("exynos") || hardware.contains("mt") ||
      hardware.contains("mediatek") || hardware.contains("kirin") || hardware.contains("hi") ||
      board.contains("exynos") || board.contains("universal") || board.contains("mt")
    ) {
      return GpuFormat.ETC
    }

    // 4. Secondary check: /proc/cpuinfo
    try {
      val cpuInfo = File("/proc/cpuinfo")
      if (cpuInfo.exists() && cpuInfo.canRead()) {
        val content = cpuInfo.readText().lowercase(Locale.ROOT)
        if (content.contains("qualcomm") || content.contains("adreno") || content.contains("snapdragon")) {
          return GpuFormat.DXT
        }
        if (content.contains("powervr") || content.contains("pvr")) {
          return GpuFormat.PVR
        }
        if (content.contains("mali") || content.contains("mediatek") || content.contains("exynos") || content.contains("kirin")) {
          return GpuFormat.ETC
        }
      }
    } catch (_: Exception) {}

    // Default to ETC (most widespread compatibility on Android)
    return GpuFormat.ETC
  }
}
