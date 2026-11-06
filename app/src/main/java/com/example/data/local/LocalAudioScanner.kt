package com.example.data.local

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.Song
import com.example.data.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalAudioScanner(private val context: Context) {

    /**
     * Parse metadata and ID3 tags from a selected Uri
     */
    suspend fun parseAudioUri(uri: Uri): Song = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var artworkUrl: String? = null

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrEmpty()) {
                durationMs = durationStr.toLongOrNull() ?: 0L
            }

            // Extract embedded album art if available
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val cacheFile = File(context.cacheDir, "art_${uri.toString().hashCode()}.jpg")
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(artBytes)
                    fos.flush()
                }
                artworkUrl = cacheFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w("LocalAudioScanner", "Failed to retrieve metadata: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Fallback title from filename if ID3 title is missing
        if (title.isNullOrBlank()) {
            title = getFileName(uri) ?: "Audio Track"
            title = title.substringBeforeLast(".")
        }

        if (artist.isNullOrBlank()) {
            artist = "Local Artist"
        }
        if (album.isNullOrBlank()) {
            album = "Local Storage"
        }

        Song(
            id = "local_${uri.toString().hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            source = SongSource.LOCAL,
            sourceId = uri.toString(),
            audioStreamUrl = uri.toString()
        )
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }
}
