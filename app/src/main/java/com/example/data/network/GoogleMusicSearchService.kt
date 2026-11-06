package com.example.data.network

import android.util.Log
import com.example.data.model.Song
import com.example.data.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GoogleMusicSearchService(private val fallbackService: YouTubeMusicService) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Search songs using high-speed online stream engine
     */
    suspend fun searchMusic(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        // 1. If this is a charts/mood request, load curated trending tracks
        if (cleanQuery.contains("Top Global Hits", ignoreCase = true) ||
            cleanQuery.contains("Global Charts", ignoreCase = true) ||
            cleanQuery.contains("Trending", ignoreCase = true)
        ) {
            val trending = fallbackService.getTrendingTracks()
            if (trending.isNotEmpty()) {
                return@withContext trending
            }
        }

        // 2. High-speed online search with direct stream resolution
        try {
            val streamResults = fallbackService.searchSongs(cleanQuery)
            if (streamResults.isNotEmpty()) {
                return@withContext streamResults.map {
                    it.copy(source = SongSource.GOOGLE_SEARCH, album = "Google Music")
                }
            }
        } catch (e: Exception) {
            Log.w("GoogleMusicSearch", "Stream catalog search failed: ${e.message}")
        }

        // 3. Fallback to Google Music direct search endpoint
        val results = mutableListOf<Song>()
        try {
            val payload = JSONObject().apply {
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20230515.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    }
                    put("client", clientObj)
                }
                put("context", contextObj)
                put("query", cleanQuery)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }

            if (body != null) {
                val json = JSONObject(body)
                val parsed = parseGoogleMusicResponse(json)
                results.addAll(parsed)
            }
        } catch (e: Exception) {
            Log.w("GoogleMusicSearch", "Google direct search failed: ${e.message}")
        }

        results
    }

    /**
     * Get real-time search suggestions directly from Google Autocomplete
     */
    suspend fun getGoogleSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://www.google.com/complete/search?client=chrome&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }

            if (body != null) {
                val array = JSONArray(body)
                if (array.length() > 1) {
                    val suggestionsArray = array.optJSONArray(1)
                    if (suggestionsArray != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until suggestionsArray.length()) {
                            val item = suggestionsArray.optString(i)
                            if (item.isNotEmpty()) {
                                list.add(item)
                            }
                        }
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GoogleMusicSearch", "Google suggestions error: ${e.message}")
        }

        // Fallback to youtube suggest if needed
        fallbackService.getSearchSuggestions(trimmed)
    }

    private fun parseGoogleMusicResponse(root: JSONObject): List<Song> {
        val songs = mutableListOf<Song>()
        val seenVideoIds = mutableSetOf<String>()

        fun extractRenderers(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key == "musicResponsiveListItemRenderer") {
                            val renderer = obj.optJSONObject(key)
                            if (renderer != null) {
                                val song = parseItemRenderer(renderer)
                                if (song != null && seenVideoIds.add(song.sourceId)) {
                                    songs.add(song)
                                }
                            }
                        } else {
                            extractRenderers(obj.opt(key))
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        extractRenderers(obj.opt(i))
                    }
                }
            }
        }

        extractRenderers(root)
        return songs
    }

    private fun parseItemRenderer(renderer: JSONObject): Song? {
        val rendererStr = renderer.toString()

        // Extract videoId
        val videoIdMatcher = Pattern.compile(""""videoId":\s*"([a-zA-Z0-9_-]{11})"""").matcher(rendererStr)
        val videoId = if (videoIdMatcher.find()) videoIdMatcher.group(1) else null
        if (videoId.isNullOrEmpty()) return null

        // Extract text from flexColumns
        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        var title = ""
        var artist = "Unknown Artist"
        var durationMs = 0L

        if (flexColumns.length() > 0) {
            val col0 = flexColumns.optJSONObject(0)
            val runs0 = col0?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            if (runs0 != null && runs0.length() > 0) {
                title = runs0.optJSONObject(0)?.optString("text") ?: ""
            }
        }

        if (flexColumns.length() > 1) {
            val col1 = flexColumns.optJSONObject(1)
            val runs1 = col1?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            if (runs1 != null && runs1.length() > 0) {
                val fullArtistRow = StringBuilder()
                for (i in 0 until runs1.length()) {
                    val runText = runs1.optJSONObject(i)?.optString("text") ?: ""
                    fullArtistRow.append(runText)
                }
                val parts = fullArtistRow.toString().split("•").map { it.trim() }
                if (parts.isNotEmpty()) {
                    artist = parts[0]
                }
                // Try to find duration (e.g. 3:45 or 4:12)
                for (part in parts) {
                    if (part.matches(Regex("""\d{1,2}:\d{2}"""))) {
                        durationMs = parseDurationText(part)
                    }
                }
            }
        }

        if (title.isBlank()) return null

        // Thumbnail
        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Song(
            id = "google_$videoId",
            title = cleanTitle(title),
            artist = artist,
            album = "Google Music",
            durationMs = durationMs,
            artworkUrl = thumb,
            source = SongSource.GOOGLE_SEARCH,
            sourceId = videoId
        )
    }

    private fun parseDurationText(text: String): Long {
        val parts = text.split(":")
        if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            return (m * 60 + s) * 1000L
        } else if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            return (h * 3600 + m * 60 + s) * 1000L
        }
        return 0L
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\(official (music )?video\)"""), "")
            .replace(Regex("""(?i)\[official (music )?video\]"""), "")
            .replace(Regex("""(?i)\(official audio\)"""), "")
            .replace(Regex("""(?i)\[official audio\]"""), "")
            .replace(Regex("""(?i)\(lyrics?\)"""), "")
            .replace(Regex("""(?i)\[lyrics?\]"""), "")
            .replace(Regex("""(?i)\(visualizer\)"""), "")
            .trim()
    }
}
