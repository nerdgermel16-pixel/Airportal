package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.data.model.FileItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

class FileRepository(private val context: Context) {

  // Primary base storage directory
  val defaultRootDir: File
    get() {
      val ext = Environment.getExternalStorageDirectory()
      if (ext != null && ext.exists() && ext.canRead()) {
        return ext
      }
      return context.filesDir
    }

  fun getStorageVolumes(): List<FileItem> {
    val volumes = mutableListOf<File>()

    // Internal shared storage (/storage/emulated/0)
    val ext = Environment.getExternalStorageDirectory()
    if (ext != null && ext.exists() && ext.canRead()) {
      volumes.add(ext)
    }

    // Secondary external storage (e.g. SD cards)
    try {
      val dirs = context.getExternalFilesDirs(null)
      for (dir in dirs) {
        if (dir != null) {
          var root: File? = dir
          while (root != null && root.parentFile != null && root.parentFile?.absolutePath != "/storage" && root.parentFile?.absolutePath != "/") {
            root = root.parentFile
          }
          if (root != null && root.exists() && root.canRead() && !volumes.contains(root) && root.absolutePath != "/storage/emulated") {
            volumes.add(root)
          }
        }
      }
    } catch (_: Exception) {
    }

    // Additional common SD card paths
    listOf("/storage/sdcard1", "/sdcard1", "/mnt/sdcard", "/storage/extSdCard").forEach { p ->
      val f = File(p)
      if (f.exists() && f.isDirectory && f.canRead() && !volumes.contains(f)) {
        volumes.add(f)
      }
    }

    // Scan /storage for physical SD card mounts (e.g., /storage/12A4-5678)
    try {
      val storageDir = File("/storage")
      if (storageDir.exists() && storageDir.isDirectory) {
        storageDir.listFiles()?.forEach { f ->
          if (f.isDirectory && f.canRead() && f.name != "self" && f.name != "emulated" && !volumes.contains(f)) {
            volumes.add(f)
          }
        }
      }
    } catch (_: Exception) {
    }

    if (volumes.isEmpty()) {
      volumes.add(defaultRootDir)
    }

    return volumes.map { toFileItem(it) }
  }

  fun listFiles(requestedPath: String?): Pair<String, List<FileItem>> {
    val targetDir = if (requestedPath.isNullOrBlank()) {
      defaultRootDir
    } else {
      val f = File(requestedPath)
      if (f.exists() && f.isDirectory) f else defaultRootDir
    }

    val currentPath = targetDir.absolutePath
    val filesList = mutableListOf<FileItem>()

    try {
      val files = targetDir.listFiles()
      if (files != null) {
        for (file in files) {
          // Skip hidden files starting with '.' unless needed
          if (file.name.startsWith(".")) continue
          filesList.add(toFileItem(file))
        }
      }
    } catch (_: Exception) {
    }

    // Sort: directories first, then alphabetical
    val sorted = filesList.sortedWith(
      compareBy<FileItem> { !it.isDirectory }
        .thenBy { it.name.lowercase(Locale.ROOT) }
    )

    return Pair(currentPath, sorted)
  }

  fun getFile(path: String): File? {
    if (path.isBlank()) return null
    val f = File(path)
    return if (f.exists()) f else null
  }

  fun saveUploadedFile(directoryPath: String, fileName: String, inputStream: InputStream): Result<File> {
    return try {
      val targetDir = if (directoryPath.isNotBlank()) File(directoryPath) else defaultRootDir
      if (!targetDir.exists()) {
        targetDir.mkdirs()
      }
      // Sanitize fileName to prevent directory traversal
      val safeName = File(fileName).name
      val destFile = File(targetDir, safeName)

      FileOutputStream(destFile).use { output ->
        inputStream.copyTo(output)
      }
      Result.success(destFile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun deleteFile(path: String): Boolean {
    val f = getFile(path) ?: return false
    return try {
      if (f.isDirectory) {
        f.deleteRecursively()
      } else {
        f.delete()
      }
    } catch (_: Exception) {
      false
    }
  }

  fun getMimeType(file: File): String {
    val extension = file.extension.lowercase(Locale.ROOT)
    return when (extension) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "webp" -> "image/webp"
      "svg" -> "image/svg+xml"
      "mp4" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "mp3" -> "audio/mpeg"
      "wav" -> "audio/wav"
      "pdf" -> "application/pdf"
      "txt" -> "text/plain"
      "json" -> "application/json"
      "html", "htm" -> "text/html"
      "apk" -> "application/vnd.android.package-archive"
      "zip" -> "application/zip"
      else -> {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        mime ?: "application/octet-stream"
      }
    }
  }

  private fun toFileItem(file: File): FileItem {
    val isDir = file.isDirectory
    val mime = if (isDir) "inode/directory" else getMimeType(file)
    val isImg = !isDir && mime.startsWith("image/")
    return FileItem(
      name = file.name.ifEmpty { file.absolutePath },
      path = file.absolutePath,
      isDirectory = isDir,
      size = if (isDir) 0L else file.length(),
      lastModified = file.lastModified(),
      mimeType = mime,
      isImage = isImg
    )
  }
}
