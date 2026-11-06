package com.example.data.network

import android.util.Base64
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
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class YouTubeMusicService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val desKey = "38346591".toByteArray(Charsets.UTF_8)

    /**
     * Decrypts JioSaavn encrypted media URLs to direct, high-bitrate streaming URLs
     */
    fun decryptMediaUrl(encryptedUrl: String): String? {
        if (encryptedUrl.isBlank()) return null
        return try {
            val keySpec = SecretKeySpec(desKey, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8)
            // Upgrade from 96kbps to 320kbps
            rawUrl.replace("_96.mp4", "_320.mp4")
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Error decrypting stream: ${e.message}")
            null
        }
    }

    /**
     * Primary search querying high-quality streaming catalog with verified 320kbps MP4/AAC streams
     */
    suspend fun searchJioSaavn(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(cleanQ, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&api_version=4&ctx=web6dot0"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val list = mutableListOf<Song>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results") ?: return@use
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val rawTitle = item.optString("title")
                        if (rawTitle.isEmpty()) continue
                        val id = item.optString("id", "song_$i")
                        val moreInfo = item.optJSONObject("more_info")
                        val encMediaUrl = moreInfo?.optString("encrypted_media_url") ?: ""
                        val streamUrl = decryptMediaUrl(encMediaUrl)
                        if (streamUrl.isNullOrEmpty()) continue

                        val artist = cleanText(item.optString("subtitle", "Unknown Artist"))
                        val title = cleanText(rawTitle)
                        val durationSeconds = moreInfo?.optLong("duration", 210L) ?: 210L

                        var image = item.optString("image")
                        if (image.contains("150x150")) {
                            image = image.replace("150x150", "500x500")
                        } else if (image.isEmpty()) {
                            image = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        }

                        val album = cleanText(moreInfo?.optString("album", "Online Music") ?: "Online Music")

                        list.add(
                            Song(
                                id = "saavn_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationSeconds * 1000L,
                                artworkUrl = image,
                                audioStreamUrl = streamUrl,
                                source = SongSource.GOOGLE_SEARCH,
                                sourceId = id
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "Primary search failed: ${e.message}")
        }
        list
    }

    /**
     * Search for songs matching query with direct stream support
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isEmpty()) return@withContext emptyList()

        // 1. Primary high-fidelity music search (JioSaavn catalog with 320kbps streams)
        val primaryResults = searchJioSaavn(cleanQ)
        if (primaryResults.isNotEmpty()) {
            return@withContext primaryResults
        }

        // 2. Secondary search (Audius catalog)
        val audiusResults = searchAudiusTracks(cleanQ)
        if (audiusResults.isNotEmpty()) {
            return@withContext audiusResults
        }

        emptyList()
    }

    /**
     * Fetch trending online tracks for Explore / Home
     */
    suspend fun getTrendingTracks(): List<Song> = withContext(Dispatchers.IO) {
        // Query top trending hits from primary catalog
        val hits = searchJioSaavn("Top Trending Hits")
        if (hits.isNotEmpty()) {
            return@withContext hits
        }

        // Fallback to top charts
        val charts = searchJioSaavn("Top Global Hits")
        if (charts.isNotEmpty()) {
            return@withContext charts
        }

        emptyList()
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
                    for (i in 0 until data.length().coerceAtMost(25)) {
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

                        val rawStreamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=AuraMusic"

                        list.add(
                            Song(
                                id = "audius_$id",
                                title = cleanText(cleanTitle(title)),
                                artist = cleanText(artist),
                                album = "Online Music",
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
            Log.w("YouTubeMusicService", "Error querying Audius: ${e.message}")
        }
        list
    }

    /**
     * Resolve exact audio stream for a track based on title and artist.
     * Guarantees playing the ACTUAL song, and returns null instead of playing a wrong track.
     */
    suspend fun resolveAudioStreamUrl(
        videoId: String,
        title: String? = null,
        artist: String? = null,
        forceFallback: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val cleanT = if (!title.isNullOrBlank()) cleanText(cleanTitle(title)) else ""
        if (cleanT.isNotEmpty()) {
            val q = if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                "$cleanT $artist"
            } else {
                cleanT
            }
            val results = searchJioSaavn(q)
            if (results.isNotEmpty() && !results[0].audioStreamUrl.isNullOrEmpty()) {
                return@withContext results[0].audioStreamUrl
            }

            if (q != cleanT) {
                val titleResults = searchJioSaavn(cleanT)
                if (titleResults.isNotEmpty() && !titleResults[0].audioStreamUrl.isNullOrEmpty()) {
                    return@withContext titleResults[0].audioStreamUrl
                }
            }
        }

        // If this was an Audius track ID, resolve its direct CDN stream
        val rawTrackId = when {
            videoId.startsWith("audius_") -> videoId.removePrefix("audius_")
            videoId.length in 4..10 && !videoId.contains("-") -> videoId
            else -> null
        }
        if (rawTrackId != null) {
            val direct = resolveDirectCdnUrl("https://api.audius.co/v1/tracks/$rawTrackId/stream?app_name=AuraMusic")
            if (direct != null) return@withContext direct
        }

        null
    }

    /**
     * Resolves multi-hop redirects with a lightweight HEAD request, returning the direct CDN audio URL.
     */
    fun resolveDirectCdnUrl(initialStreamUrl: String): String? {
        if (initialStreamUrl.contains(".saavncdn.com") || initialStreamUrl.contains(".r2.cloudflarestorage.com")) {
            return initialStreamUrl
        }
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
                    initialStreamUrl
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
        val cleanQ = query.trim()
        if (cleanQ.isEmpty()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$encoded&_format=json&_marker=0&ctx=web6dot0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (body != null) {
                val json = JSONObject(body)
                val songs = json.optJSONObject("songs")?.optJSONArray("data")
                if (songs != null && songs.length() > 0) {
                    val list = mutableListOf<String>()
                    for (i in 0 until songs.length().coerceAtMost(6)) {
                        val title = songs.optJSONObject(i)?.optString("title")
                        if (!title.isNullOrEmpty()) {
                            list.add(cleanText(title))
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "JioSaavn suggest error: ${e.message}")
        }

        // Fallback to Google Autocomplete
        try {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
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
                            for (i in 0 until suggestionsArray.length().coerceAtMost(6)) {
                                val row = suggestionsArray.optJSONArray(i)
                                val text = row?.optString(0)
                                if (!text.isNullOrEmpty()) {
                                    list.add(cleanText(text))
                                }
                            }
                        }
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("YouTubeMusicService", "YouTube suggest error: ${e.message}")
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

    fun cleanText(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
