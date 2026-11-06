package com.example.data.network

import android.util.Log
import com.example.data.model.LyricLine
import com.example.data.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LyricsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lrcPattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

    suspend fun fetchLyrics(trackName: String, artistName: String, durationMs: Long): Lyrics? = withContext(Dispatchers.IO) {
        try {
            // First attempt: direct exact match
            val durationSeconds = if (durationMs > 0) durationMs / 1000 else 0
            val encodedTrack = URLEncoder.encode(sanitizeName(trackName), "UTF-8")
            val encodedArtist = URLEncoder.encode(sanitizeName(artistName), "UTF-8")

            var url = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            if (durationSeconds > 0) {
                url += "&duration=$durationSeconds"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AuraMusic/1.0 (Android; OpenSource)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    val synced = json.optString("syncedLyrics")
                    val plain = json.optString("plainLyrics")

                    if (synced.isNotEmpty()) {
                        val lines = parseLrc(synced)
                        return@withContext Lyrics(lines = lines, isSynced = true, plainLyrics = plain)
                    } else if (plain.isNotEmpty()) {
                        return@withContext Lyrics(isSynced = false, plainLyrics = plain)
                    }
                }
            }

            // Second attempt: search query fallback
            val searchQuery = URLEncoder.encode("${sanitizeName(trackName)} ${sanitizeName(artistName)}", "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$searchQuery"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "AuraMusic/1.0 (Android; OpenSource)")
                .build()

            client.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        for (i in 0 until minOf(array.length(), 3)) {
                            val item = array.optJSONObject(i) ?: continue
                            val synced = item.optString("syncedLyrics")
                            val plain = item.optString("plainLyrics")
                            if (synced.isNotEmpty()) {
                                val lines = parseLrc(synced)
                                return@withContext Lyrics(lines = lines, isSynced = true, plainLyrics = plain)
                            } else if (plain.isNotEmpty()) {
                                return@withContext Lyrics(isSynced = false, plainLyrics = plain)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LyricsService", "Failed to fetch lyrics: ${e.message}")
        }
        null
    }

    private fun parseLrc(lrcText: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val lines = lrcText.split("\n")
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.matches()) {
                try {
                    val min = matcher.group(1)?.toLong() ?: 0L
                    val sec = matcher.group(2)?.toLong() ?: 0L
                    val msStr = matcher.group(3) ?: "0"
                    val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                    val totalMs = min * 60 * 1000 + sec * 1000 + ms
                    val text = matcher.group(4)?.trim() ?: ""
                    list.add(LyricLine(timestampMs = totalMs, text = text))
                } catch (e: Exception) {
                    // skip malformed line
                }
            }
        }
        return list.sortedBy { it.timestampMs }
    }

    private fun sanitizeName(name: String): String {
        return name
            .replace(Regex("""(?i)\(feat.*?\)|\[feat.*?\]|\(ft.*?\)|\[ft.*?\]"""), "")
            .replace(Regex("""(?i)\(official.*?\)"""), "")
            .replace(Regex("""(?i)remaster(ed)?"""), "")
            .trim()
    }
}
