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

  fun detectGpuFormat(texdbDir: File? = null): GpuFormat {
    // 0. If texdb directory exists and only contains textures of a specific GPU format, prioritize that
    if (texdbDir != null && texdbDir.exists()) {
      try {
        val gta3Dir = File(texdbDir, "gta3")
        val candidateNames = mutableListOf<String>()
        if (gta3Dir.exists()) {
          gta3Dir.listFiles()?.forEach { candidateNames.add(it.name.lowercase(Locale.ROOT)) }
        }
        texdbDir.listFiles()?.forEach { candidateNames.add(it.name.lowercase(Locale.ROOT)) }

        val hasDxt = candidateNames.any { it.contains(".dxt.") || it.endsWith(".dxt") }
        val hasEtc = candidateNames.any { it.contains(".etc.") || it.endsWith(".etc") }
        val hasPvr = candidateNames.any { it.contains(".pvr.") || it.endsWith(".pvr") }

        if (hasDxt && !hasEtc && !hasPvr) return GpuFormat.DXT
        if (hasEtc && !hasDxt && !hasPvr) return GpuFormat.ETC
        if (hasPvr && !hasDxt && !hasEtc) return GpuFormat.PVR
      } catch (_: Exception) {}
    }

    val hardware = (Build.HARDWARE ?: "").lowercase(Locale.ROOT)
    val board = (Build.BOARD ?: "").lowercase(Locale.ROOT)
    val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
    val model = (Build.MODEL ?: "").lowercase(Locale.ROOT)
    val product = (Build.PRODUCT ?: "").lowercase(Locale.ROOT)
    val socModel = if (Build.VERSION.SDK_INT >= 31) {
      (Build.SOC_MODEL ?: "").lowercase(Locale.ROOT)
    } else {
      ""
    }

    // 1. Qualcomm Snapdragon / Adreno
    if (hardware.contains("qcom") || hardware.contains("qualcomm") || hardware.contains("adreno") ||
      board.contains("qcom") || board.contains("msm") || board.contains("sdm") || board.contains("sm") ||
      hardware.contains("snapdragon") || product.contains("qcom") || socModel.contains("sm") ||
      socModel.contains("snapdragon")
    ) {
      return GpuFormat.DXT
    }

    // 2. PowerVR / Imagination
    if (hardware.contains("powervr") || hardware.contains("pvr") || hardware.contains("rogue") ||
      hardware.contains("sgx") || board.contains("pvr")
    ) {
      return GpuFormat.PVR
    }

    // 3. Mali / MediaTek / Samsung Exynos / Google Tensor / HiSilicon Kirin
    if (hardware.contains("mali") || hardware.contains("exynos") || hardware.contains("mt") ||
      hardware.contains("mediatek") || hardware.contains("kirin") || hardware.contains("hi") ||
      board.contains("exynos") || board.contains("universal") || board.contains("mt") ||
      board.contains("gs") || hardware.contains("tensor") || socModel.contains("mt") ||
      socModel.contains("tensor") || socModel.contains("exynos")
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
