package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Song
import com.example.data.model.SongSource

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val source: String,
    val sourceId: String,
    val orderIndex: Int = 0
) {
    fun toSong(): Song {
        val songSource = try {
            SongSource.valueOf(source)
        } catch (e: Exception) {
            SongSource.YOUTUBE_MUSIC
        }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            source = songSource,
            sourceId = sourceId
        )
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val source: String,
    val sourceId: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        val songSource = try {
            SongSource.valueOf(source)
        } catch (e: Exception) {
            SongSource.YOUTUBE_MUSIC
        }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            source = songSource,
            sourceId = sourceId,
            isFavorite = true
        )
    }
}

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val source: String,
    val sourceId: String,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        val songSource = try {
            SongSource.valueOf(source)
        } catch (e: Exception) {
            SongSource.YOUTUBE_MUSIC
        }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            source = songSource,
            sourceId = sourceId
        )
    }
}
