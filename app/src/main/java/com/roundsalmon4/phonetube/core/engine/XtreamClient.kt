package com.roundsalmon4.phonetube.core.engine

import android.net.Uri
import android.util.Log
import com.roundsalmon4.phonetube.core.engine.model.IptvCategory
import com.roundsalmon4.phonetube.core.engine.model.IptvLiveStream
import com.roundsalmon4.phonetube.core.engine.model.IptvProgram
import com.roundsalmon4.phonetube.core.engine.model.XtreamAuthInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Xtream Codes client used by PhoneTube. Only the parts needed for
 * live TV are implemented: authentication, live categories and live streams.
 */
@Singleton
class XtreamClient @Inject constructor() {

    companion object {
        private const val TAG = "XtreamClient"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 15_000
    }

    /**
     * Verifies credentials against player_api.php and returns the account info.
     * Returns null when the server could not be reached or the response was not
     * a player API response.
     */
    suspend fun authenticate(host: String, username: String, password: String): XtreamAuthInfo? =
        withContext(Dispatchers.IO) {
            try {
                val (body, scheme) = fetch(host, username, password, action = null, extra = null)
                    ?: return@withContext null
                val json = org.json.JSONObject(body)
                val userInfo = json.optJSONObject("user_info")
                val serverInfo = json.optJSONObject("server_info")
                val serverUrl = serverInfo?.optString("url", "").orEmpty()
                XtreamAuthInfo(
                    auth = userInfo?.optInt("auth", 0) == 1,
                    status = userInfo?.optString("status", "").orEmpty(),
                    expDate = userInfo?.optLong("exp_date", 0L) ?: 0L,
                    serverName = serverUrl.ifBlank { null } ?: host,
                    scheme = scheme,
                    timezone = serverInfo?.optString("timezone", "").orEmpty().ifBlank { null }
                )
            } catch (e: Exception) {
                Log.w(TAG, "authenticate($host) failed", e)
                null
            }
        }

    suspend fun liveCategories(host: String, username: String, password: String): List<IptvCategory> =
        withContext(Dispatchers.IO) {
            val body = fetch(host, username, password, action = "get_live_categories", extra = null)
                ?.body ?: return@withContext emptyList()
            val array = try {
                org.json.JSONArray(body)
            } catch (e: Exception) {
                Log.w(TAG, "liveCategories($host): response was not an array", e)
                return@withContext emptyList()
            }
            val seen = HashSet<String>()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("category_id", "")
                val name = obj.optString("category_name", "")
                if (id.isBlank() || name.isBlank() || !seen.add(id)) return@mapNotNull null
                IptvCategory(categoryId = id, name = name)
            }.sortedBy { it.name }
        }

    suspend fun liveStreams(
        host: String,
        username: String,
        password: String,
        categoryId: String? = null
    ): List<IptvLiveStream> = withContext(Dispatchers.IO) {
        val extra = if (categoryId.isNullOrBlank()) null else ("category_id=" + Uri.encode(categoryId))
        val body = fetch(host, username, password, action = "get_live_streams", extra = extra)
            ?.body ?: return@withContext emptyList()
        val array = try {
            org.json.JSONArray(body)
        } catch (e: Exception) {
            Log.w(TAG, "liveStreams($host): response was not an array", e)
            return@withContext emptyList()
        }
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val streamId = obj.optString("stream_id", "")
            val name = obj.optString("name", "")
            if (streamId.isBlank() || name.isBlank()) return@mapNotNull null
            IptvLiveStream(
                streamId = streamId,
                name = name,
                iconUrl = obj.optString("stream_icon", "").ifBlank { null },
                categoryId = obj.optString("category_id", "")
            )
        }.sortedBy { it.name }
    }

    /**
     * Builds the HLS playback URL for a live stream. Segments are encoded so
     * passwords containing reserved characters still work in the path. The port
     * is embedded in [host] when the provider requires one.
     */
    fun liveStreamUrl(scheme: String, host: String, username: String, password: String, streamId: String): String {
        val path = "live/${Uri.encode(username)}/${Uri.encode(password)}/$streamId.m3u8"
        return "$scheme://$host/$path"
    }

    /**
     * Fetches the short EPG for a stream and returns programs with epoch
     * millis (UTC) so the app can pick the one airing right now. Titles are
     * base64-encoded by some providers and are decoded when applicable.
     */
    suspend fun shortEpg(
        host: String,
        username: String,
        password: String,
        streamId: String,
        timezone: String
    ): List<IptvProgram> = withContext(Dispatchers.IO) {
        val body = fetch(host, username, password, action = "get_short_epg", extra = "stream_id=" + Uri.encode(streamId))
            ?.body ?: return@withContext emptyList()
        val array = try {
            org.json.JSONArray(body)
        } catch (e: Exception) {
            // Some panels wrap the listing: {"epg_listings": [...]}
            try {
                org.json.JSONObject(body).optJSONArray("epg_listings") ?: return@withContext emptyList()
            } catch (e2: Exception) {
                Log.w(TAG, "shortEpg($host): no epg_listings array", e2)
                return@withContext emptyList()
            }
        }
        val zone = try {
            java.time.ZoneId.of(timezone)
        } catch (e: Exception) {
            Log.w(TAG, "shortEpg($host): bad timezone '$timezone', using device zone")
            java.time.ZoneId.systemDefault()
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val programs = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val title = decodeTitle(obj.optString("title", ""))
            val start = parseEpoch(obj.optString("start", ""), fmt, zone)
            val end = parseEpoch(obj.optString("end", ""), fmt, zone)
            if (title.isBlank() || start <= 0L || end <= 0L) return@mapNotNull null
            IptvProgram(title = title, startEpoch = start, endEpoch = end)
        }
        Log.d(TAG, "shortEpg($host, stream=$streamId): ${programs.size} programs")
        programs
    }

    private fun decodeTitle(raw: String): String {
        if (raw.isBlank()) return raw
        // Short EPG titles from some panels are base64-encoded. Attempt to
        // decode, falling back to the raw text if that fails.
        return try {
            val decoded = String(
                android.util.Base64.decode(raw.trim(), android.util.Base64.NO_WRAP),
                Charsets.UTF_8
            )
            if (decoded.isBlank() || decoded.any { it.code == 0 } || decoded.length > raw.length * 2) raw else decoded
        } catch (e: Exception) {
            raw
        }
    }

    private fun parseEpoch(local: String, fmt: java.time.format.DateTimeFormatter, zone: java.time.ZoneId): Long {
        if (local.isBlank()) return 0L
        return try {
            java.time.LocalDateTime.parse(local, fmt).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private data class FetchResult(val body: String, val scheme: String)

    private suspend fun fetch(
        host: String,
        username: String,
        password: String,
        action: String?,
        extra: String?
    ): FetchResult? = withContext(Dispatchers.IO) {
        val base = buildApiUrl(host, username, password, action, extra)
        // Some providers are HTTP-only; try HTTPS first and fall back to HTTP.
        val urls = listOf(base, base.replaceFirst("https://", "http://")).distinct()
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.setRequestProperty("Accept", "application/json")
                    val status = connection.responseCode
                    Log.d(TAG, "fetch($host, action=$action): HTTP $status")
                    if (status !in 200..399) {
                        Log.w(TAG, "fetch($host, action=$action): HTTP error $status")
                        continue
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext FetchResult(body, url.substringBefore("://"))
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "fetch($host) request failed on $url", e)
            }
        }
        Log.e(TAG, "fetch($host, action=$action) failed", lastError)
        null
    }

    private fun buildApiUrl(
        host: String,
        username: String,
        password: String,
        action: String?,
        extra: String?
    ): String {
        val base = "https://$host/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}"
        return when {
            action != null && extra != null && extra.isNotBlank() -> "$base&action=$action&$extra"
            extra != null && extra.isNotBlank() -> "$base&$extra"
            action != null -> "$base&action=$action"
            else -> base
        }
    }
}