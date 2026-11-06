package com.example.data.model

enum class SongSource {
    GOOGLE_SEARCH,
    YOUTUBE,
    YOUTUBE_MUSIC,
    LOCAL
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val source: SongSource = SongSource.YOUTUBE_MUSIC,
    val sourceId: String, // Video ID or local URI string
    val audioStreamUrl: String? = null,
    val isFavorite: Boolean = false
) {
    val durationFormatted: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
