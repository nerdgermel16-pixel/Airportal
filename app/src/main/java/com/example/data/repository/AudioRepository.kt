package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.AudioTrackItem
import java.io.File

class AudioRepository(private val context: Context) {

  fun getAllAudioTracks(limit: Int = 300): List<AudioTrackItem> {
    val list = mutableListOf<AudioTrackItem>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.SIZE,
      MediaStore.Audio.Media.DATA
    )

    // MediaStore Audio query
    try {
      val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 5000"
      val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

      val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
      )

      cursor?.use {
        val idIdx = it.getColumnIndex(MediaStore.Audio.Media._ID)
        val titleIdx = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artistIdx = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val albumIdx = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val durIdx = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val sizeIdx = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dataIdx = it.getColumnIndex(MediaStore.Audio.Media.DATA)

        while (it.moveToNext() && list.size < limit) {
          val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
          val title = (if (titleIdx >= 0) it.getString(titleIdx) else null)?.ifBlank { null } ?: "Audio Track $id"
          val artist = (if (artistIdx >= 0) it.getString(artistIdx) else null)?.ifBlank { null } ?: "Unknown Artist"
          val album = (if (albumIdx >= 0) it.getString(albumIdx) else null)?.ifBlank { null } ?: "Unknown Album"
          val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L
          val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
          val path = (if (dataIdx >= 0) it.getString(dataIdx) else null) ?: ""

          list.add(
            AudioTrackItem(
              id = id,
              title = title,
              artist = artist,
              album = album,
              duration = duration,
              size = size,
              path = path
            )
          )
        }
      }
    } catch (_: Exception) {
    }

    // Fallback: search common music folders on disk if media store returned empty
    if (list.isEmpty()) {
      try {
        val audioExts = setOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus")
        val dirsToScan = listOf(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
          Environment.getExternalStorageDirectory()
        )

        for (dir in dirsToScan) {
          if (dir != null && dir.exists() && dir.isDirectory) {
            dir.walkTopDown().maxDepth(3).filter { f ->
              f.isFile && audioExts.contains(f.extension.lowercase()) && f.length() > 50000
            }.take(limit).forEach { file ->
              val nameWithoutExt = file.nameWithoutExtension
              list.add(
                AudioTrackItem(
                  id = file.hashCode().toLong(),
                  title = nameWithoutExt,
                  artist = "Device Audio",
                  album = file.parentFile?.name ?: "Music",
                  duration = 0L,
                  size = file.length(),
                  path = file.absolutePath
                )
              )
            }
          }
          if (list.size >= limit) break
        }
      } catch (_: Exception) {
      }
    }

    return list
  }
}
