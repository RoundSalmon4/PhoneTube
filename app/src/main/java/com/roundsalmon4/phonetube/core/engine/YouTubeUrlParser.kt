package com.roundsalmon4.phonetube.core.engine

import android.net.Uri

data class YouTubeLink(
    val type: Type,
    val id: String
) {
    enum class Type { VIDEO, PLAYLIST, CHANNEL, SHORT, UNKNOWN }

    val isValid: Boolean get() = type != Type.UNKNOWN && id.isNotEmpty()
}

object YouTubeUrlParser {

    fun parse(uri: Uri): YouTubeLink = parseRaw(uri.toString())

    /**
     * Parses the link text directly so the logic can run in plain JVM unit
     * tests without Android's Uri implementation. Kept in sync with [parse],
     * which just delegates here.
     */
    internal fun parseRaw(raw: String): YouTubeLink {
        val noFragment = raw.substringBefore('#')
        val queryPart = noFragment.substringAfter('?', "")
        val withoutQuery = noFragment.substringBefore('?')

        // Scheme is everything up to the first ':' and may be followed by "://"
        // (normal URLs) or by an opaque scheme-specific part (vnd.youtube:ID).
        val colonIdx = withoutQuery.indexOf(':')
        val scheme = if (colonIdx > 0) withoutQuery.substring(0, colonIdx).lowercase() else ""
        val afterColon = if (colonIdx >= 0) withoutQuery.substring(colonIdx + 1) else withoutQuery
        val afterScheme = if (scheme.isNotEmpty() && afterColon.startsWith("//")) {
            afterColon.substring(2)
        } else {
            afterColon
        }

        val pathIdx = afterScheme.indexOf('/')
        val authority = if (pathIdx >= 0) afterScheme.substring(0, pathIdx) else afterScheme
        val path = if (pathIdx >= 0) afterScheme.substring(pathIdx) else ""

        var host = authority
        val atIdx = host.lastIndexOf('@')
        if (atIdx >= 0) host = host.substring(atIdx + 1)
        host = host.substringBefore(':').lowercase()

        // vnd.youtube:VIDEO_ID or vnd.youtube.launch:VIDEO_ID (opaque URIs with no host)
        if (scheme == "vnd.youtube" || scheme == "vnd.youtube.launch") {
            val videoId = afterScheme.substringBefore('?').trim()
            if (videoId.isNotEmpty()) {
                return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
            }
        }

        if (host.isEmpty()) return YouTubeLink(YouTubeLink.Type.UNKNOWN, "")
        val isYouTubeLike = host.contains("youtube.com") || host == "youtu.be"

        // youtu.be/VIDEO_ID (short links)
        if (host == "youtu.be") {
            val videoId = path.trimStart('/')
            if (videoId.isNotEmpty()) {
                return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
            }
        }

        // youtube.com or Invidious-compatible hosts
        if (isYouTubeLike) {
            val pathLower = path.lowercase()

            // Bare-host form (e.g. m.youtube.com/?v=...) carries the id in the
            // query regardless of path, so resolve it first.
            query("v", queryPart)?.takeIf { it.isNotEmpty() }?.let { videoId ->
                return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
            }

            // /watch?v=VIDEO_ID
            if (pathLower.startsWith("/watch")) {
                val videoId = query("v", queryPart)
                if (!videoId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
                // Could be a playlist: /watch?list=PLAYLIST_ID
                val listId = query("list", queryPart)
                if (!listId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.PLAYLIST, listId)
                }
            }

            // /playlist?list=PLAYLIST_ID
            if (pathLower.startsWith("/playlist")) {
                val listId = query("list", queryPart)
                if (!listId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.PLAYLIST, listId)
                }
            }

            // /attribution_link?a=...&v=VIDEO_ID (YouTube share redirects)
            if (pathLower.startsWith("/attribution_link")) {
                val videoId = query("v", queryPart)
                if (!videoId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
                val uParam = query("u", queryPart)
                if (!uParam.isNullOrEmpty()) {
                    val decoded = urlDecode(uParam)
                    queryV(decoded)?.takeIf { it.isNotEmpty() }?.let {
                        return YouTubeLink(YouTubeLink.Type.VIDEO, it)
                    }
                }
            }

            // /shorts/VIDEO_ID
            if (pathLower.startsWith("/shorts/")) {
                val videoId = path.removePrefix("/shorts/").trimEnd('/')
                if (videoId.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.SHORT, videoId)
                }
            }

            // /live/VIDEO_ID
            if (pathLower.startsWith("/live/")) {
                val videoId = path.removePrefix("/live/").trimEnd('/')
                if (videoId.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
            }

            // /channel/CHANNEL_ID
            if (pathLower.startsWith("/channel/")) {
                val channelId = path.removePrefix("/channel/").trim('/').substringBefore('/')
                if (channelId.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, channelId)
                }
            }

            // /user/CHANNEL_HANDLE
            if (pathLower.startsWith("/user/")) {
                val handle = path.removePrefix("/user/").trim('/').substringBefore('/')
                if (handle.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, handle)
                }
            }

            // /@CHANNEL_HANDLE (may carry a tab segment, e.g. /@handle/live); keep
            // only the first segment and preserve the @ prefix, which the
            // innertube channel browse expects (a bare name returns no channel).
            if (pathLower.startsWith("/@")) {
                val handle = path.removePrefix("/@").trim('/').substringBefore('/')
                if (handle.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, "@$handle")
                }
            }

            // /c/CHANNEL_NAME (legacy channel path)
            if (pathLower.startsWith("/c/")) {
                val name = path.removePrefix("/c/").trim('/').substringBefore('/')
                if (name.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, name)
                }
            }

            // /v/VIDEO_ID or /embed/VIDEO_ID
            if (pathLower.startsWith("/v/") || pathLower.startsWith("/embed/")) {
                val videoId = path.removePrefix("/v/").removePrefix("/embed/").trimEnd('/')
                if (videoId.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
            }
        }

        return YouTubeLink(YouTubeLink.Type.UNKNOWN, "")
    }

    private fun query(name: String, queryPart: String): String? {
        for (pair in queryPart.split('&')) {
            if (pair.startsWith("$name=")) return urlDecode(pair.substring(name.length + 1))
            if (pair == name) return ""
        }
        return null
    }

    /** Extracts just the v= param from an arbitrary query string (used after
     *  decoding an attribution 'u' redirect). */
    private fun queryV(raw: String): String? {
        val queryPart = raw.substringAfter('?', "")
        for (pair in queryPart.split('&')) {
            if (pair.startsWith("v=")) return urlDecode(pair.substring(2))
            if (pair == "v") return ""
        }
        return null
    }

    private fun urlDecode(value: String): String =
        try {
            java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
}