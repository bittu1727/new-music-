package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LyricLine
import com.example.data.model.Lyrics
import com.example.data.model.Song
import com.example.data.model.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aura Music", appName)
    }

    @Test
    fun songDurationFormatting() {
        val song = Song(
            id = "test_1",
            title = "Midnight City",
            artist = "M83",
            durationMs = 243000L,
            source = SongSource.YOUTUBE_MUSIC,
            sourceId = "dQw4w9WgXcQ"
        )
        assertEquals("4:03", song.durationFormatted)
    }

    @Test
    fun lyricsTimestampLookup() {
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(timestampMs = 10000L, text = "First line"),
                LyricLine(timestampMs = 20000L, text = "Second line"),
                LyricLine(timestampMs = 30000L, text = "Third line")
            ),
            isSynced = true
        )

        assertEquals(0, lyrics.getCurrentLineIndex(15000L))
        assertEquals(1, lyrics.getCurrentLineIndex(25000L))
        assertEquals(2, lyrics.getCurrentLineIndex(35000L))
        assertEquals(-1, lyrics.getCurrentLineIndex(5000L))
    }

    @Test
    fun googleMusicSongSource() {
        val googleSong = Song(
            id = "google_dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            durationMs = 212000L,
            source = SongSource.GOOGLE_SEARCH,
            sourceId = "dQw4w9WgXcQ"
        )
        assertEquals(SongSource.GOOGLE_SEARCH, googleSong.source)
        assertEquals("3:32", googleSong.durationFormatted)
    }
}
