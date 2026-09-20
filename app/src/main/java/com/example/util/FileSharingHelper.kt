package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.ModFileEntry
import com.example.data.repository.ModstudioRepository

object FileSharingHelper {

  fun shareContainer(context: Context, entry: ModFileEntry) {
    try {
      val repository = ModstudioRepository.getInstance(context)
      val file = repository.getContainerFile(entry.fileName)

      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, entry.fileName)
        putExtra(Intent.EXTRA_TEXT, "Contenedor ${entry.fileName} - Modstudio")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "Compartir ${entry.fileName}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(
        context,
        "No se pudo compartir el archivo: ${e.localizedMessage ?: "Error desconocido"}",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  fun shareFile(context: Context, file: java.io.File, displayName: String = file.name) {
    try {
      if (!file.exists()) {
        Toast.makeText(context, "El archivo no existe", Toast.LENGTH_SHORT).show()
        return
      }

      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val mimeType = when {
        file.name.endsWith(".apk", ignoreCase = true) -> "application/vnd.android.package-archive"
        file.name.endsWith(".obb", ignoreCase = true) -> "application/octet-stream"
        file.name.endsWith(".zip", ignoreCase = true) -> "application/zip"
        else -> "application/octet-stream"
      }

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, displayName)
        putExtra(Intent.EXTRA_TEXT, "Archivo $displayName - Modstudio")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "Compartir $displayName").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(
        context,
        "No se pudo compartir el archivo: ${e.localizedMessage ?: "Error desconocido"}",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  fun shareMultipleFiles(
    context: Context,
    files: List<java.io.File>,
    title: String = "Compartir archivos de copia de seguridad"
  ) {
    try {
      val existingFiles = files.filter { it.exists() }
      if (existingFiles.isEmpty()) {
        Toast.makeText(context, "No hay archivos disponibles para compartir", Toast.LENGTH_SHORT).show()
        return
      }

      val uris = ArrayList<android.net.Uri>()
      for (file in existingFiles) {
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          file
        )
        uris.add(uri)
      }

      val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "Archivos de copia de seguridad - Modstudio")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(
        context,
        "No se pudieron compartir los archivos: ${e.localizedMessage ?: "Error desconocido"}",
        Toast.LENGTH_LONG
      ).show()
    }
  }
}
