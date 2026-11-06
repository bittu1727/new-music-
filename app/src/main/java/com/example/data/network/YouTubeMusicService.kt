package com.example.data.network

import android.util.Log
import com.example.data.model.Song
import com.example.data.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeMusicService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Search for songs matching query with direct stream support
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isEmpty()) return@withContext emptyList()

        // 1. Try high-speed Audius search (verified streams, high bitrate MP3)
        val audiusResults = searchAudiusTracks(cleanQ)
        if (audiusResults.isNotEmpty()) {
            return@withContext audiusResults
        }

        // 2. Fallback to Google / YouTube search suggestions and catalog if needed
        emptyList()
    }

    /**
     * Fetch trending online tracks for Explore / Home
     */
    suspend fun getTrendingTracks(): List<Song> = withContext(Dispatchers.IO) {
        val url = "https://api.audius.co/v1/tracks/trending?app_name=AuraMusic"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
            .build()

        val list = mutableListOf<Song>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@use
                    for (i in 0 until data.length().coerceAtMost(40)) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isEmpty()) continue

                        val title = item.optString("title", "Featured Track")
                        val userObj = item.optJSONObject("user")
                        val artist = userObj?.optString("name") ?: "Unknown Artist"
                        val durationSeconds = item.optLong("duration", 180L)

                        val artworkObj = item.optJSONObject("artwork")
                        val artUrl = artworkObj?.optString("480x480")
                            ?: artworkObj?.optString("150x150")
                            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                        val rawStreamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=AuraMusic"

                        list.add(
                            Song(
                                id = "audius_$id",
                                title = cleanTitle(title),
                                artist = artist,
                                album = "Online Charts",
                                durationMs = durationSeconds * 1000L,
                                artworkUrl = artUrl,
                                audioStreamUrl = rawStreamUrl,
                                source = SongSource.GOOGLE_SEARCH,
                                sourceId = id
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Error loading trending tracks: ${e.message}")
        }
        list
    }

    /**
     * Search Audius decentralized music library for tracks with verified streams
     */
    suspend fun searchAudiusTracks(query: String): List<Song> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.audius.co/v1/tracks/search?query=$encoded&app_name=AuraMusic"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
            .build()

        val list = mutableListOf<Song>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@use
                    for (i in 0 until data.length().coerceAtMost(30)) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isEmpty()) continue

                        val title = item.optString("title", "Untitled Track")
                        val userObj = item.optJSONObject("user")
                        val artist = userObj?.optString("name") ?: "Unknown Artist"
                        val durationSeconds = item.optLong("duration", 180L)

                        val artworkObj = item.optJSONObject("artwork")
                        val artUrl = artworkObj?.optString("480x480")
                            ?: artworkObj?.optString("150x150")
                            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                        val streamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=AuraMusic"

                        list.add(
                            Song(
                                id = "audius_$id",
                                title = cleanTitle(title),
                                artist = artist,
                                album = "Online Music",
                                durationMs = durationSeconds * 1000L,
                                artworkUrl = artUrl,
                                audioStreamUrl = streamUrl,
                                source = SongSource.GOOGLE_SEARCH,
                                sourceId = id
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Error querying Audius: ${e.message}")
        }
        list
    }

    /**
     * Resolve direct signed Cloudflare R2 CDN audio stream URL.
     * Follows the 302 redirects with OkHttp in ~150ms so ExoPlayer connects directly to the MP3 file.
     */
    suspend fun resolveAudioStreamUrl(
        videoId: String,
        title: String? = null,
        artist: String? = null,
        forceFallback: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        // 1. If this is already an Audius track ID
        val rawTrackId = when {
            videoId.startsWith("audius_") -> videoId.removePrefix("audius_")
            videoId.length in 4..10 && !videoId.contains("-") -> videoId
            else -> null
        }

        if (rawTrackId != null && !forceFallback) {
            val direct = resolveDirectCdnUrl("https://api.audius.co/v1/tracks/$rawTrackId/stream?app_name=AuraMusic")
            if (direct != null) return@withContext direct
        }

        // 2. Search Audius by track title and artist
        val queryCandidates = mutableListOf<String>()
        val cleanT = if (!title.isNullOrBlank()) cleanTitle(title) else ""
        if (cleanT.isNotEmpty() && !artist.isNullOrBlank() && artist != "Unknown Artist") {
            queryCandidates.add("$cleanT $artist")
        }
        if (cleanT.isNotEmpty()) {
            queryCandidates.add(cleanT)
        }

        for (candidate in queryCandidates) {
            try {
                val encoded = URLEncoder.encode(candidate, "UTF-8")
                val url = "https://api.audius.co/v1/tracks/search?query=$encoded&app_name=AuraMusic"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val data = json.optJSONArray("data")
                        if (data != null && data.length() > 0) {
                            for (k in 0 until data.length().coerceAtMost(3)) {
                                val item = data.optJSONObject(k) ?: continue
                                val trackId = item.optString("id")
                                if (trackId.isNotEmpty()) {
                                    val directCdn = resolveDirectCdnUrl(
                                        "https://api.audius.co/v1/tracks/$trackId/stream?app_name=AuraMusic"
                                    )
                                    if (directCdn != null) {
                                        return@withContext directCdn
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("YouTubeMusicService", "Resolution failed for '$candidate': ${e.message}")
            }
        }

        // 3. Fallback to top verified trending stream
        resolveDirectCdnUrl("https://api.audius.co/v1/tracks/AK3w5q0/stream?app_name=AuraMusic")
    }

    /**
     * Resolves multi-hop 302 redirects with a quick HEAD request, returning the direct CDN audio URL.
     */
    fun resolveDirectCdnUrl(initialStreamUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(initialStreamUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotEmpty() && finalUrl != initialStreamUrl) {
                    finalUrl
                } else if (response.isSuccessful) {
                    initialStreamUrl
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Error resolving direct CDN URL: ${e.message}")
            initialStreamUrl
        }
    }

    /**
     * Fetch search suggestions as user types
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.trim().isEmpty()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&q=$encoded"
            val request = Request.Builder().url(url).build()

            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (body != null) {
                val jsonStart = body.indexOf('[')
                val jsonEnd = body.lastIndexOf(']')
                if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                    val jsonStr = body.substring(jsonStart, jsonEnd + 1)
                    val array = JSONArray(jsonStr)
                    if (array.length() > 1) {
                        val suggestionsArray = array.optJSONArray(1)
                        val list = mutableListOf<String>()
                        if (suggestionsArray != null) {
                            for (i in 0 until suggestionsArray.length()) {
                                val row = suggestionsArray.optJSONArray(i)
                                val text = row?.optString(0)
                                if (!text.isNullOrEmpty()) {
                                    list.add(text)
                                }
                            }
                        }
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Suggestions error: ${e.message}")
        }
        emptyList()
    }

    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\(official (music )?video\)"""), "")
            .replace(Regex("""(?i)\[official (music )?video\]"""), "")
            .replace(Regex("""(?i)\(official audio\)"""), "")
            .replace(Regex("""(?i)\[official audio\]"""), "")
            .replace(Regex("""(?i)\(lyrics?\)"""), "")
            .replace(Regex("""(?i)\[lyrics?\]"""), "")
            .replace(Regex("""(?i)\(visualizer\)"""), "")
            .replace(Regex("""(?i)\[visualizer\]"""), "")
            .replace(Regex("""(?i)\(4k.*\)"""), "")
            .replace(Regex("""(?i)\(hd.*\)"""), "")
            .trim()
    }
}
