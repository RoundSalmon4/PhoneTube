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

    /** Set of Invidious instance hosts that should be parsed like YouTube. */
    private val invidiousHosts = mutableSetOf<String>()

    fun configureInvidiousHosts(hosts: Set<String>) {
        invidiousHosts.clear()
        invidiousHosts.addAll(hosts)
    }

    fun isInvidiousHost(host: String): Boolean = host in invidiousHosts

    fun parse(uri: Uri): YouTubeLink {
        val scheme = uri.scheme?.lowercase()

        // vnd.youtube:VIDEO_ID or vnd.youtube.launch:VIDEO_ID (opaque URIs with no host)
        if (scheme == "vnd.youtube" || scheme == "vnd.youtube.launch") {
            val videoId = uri.schemeSpecificPart?.substringBefore('?')?.trim()
            if (!videoId.isNullOrEmpty()) {
                return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
            }
        }

        val host = uri.host?.lowercase() ?: return YouTubeLink(YouTubeLink.Type.UNKNOWN, "")
        val path = uri.path ?: ""
        val isYouTubeLike = host.contains("youtube.com") || host == "youtu.be" || isInvidiousHost(host)

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

            // /watch?v=VIDEO_ID
            if (pathLower.startsWith("/watch")) {
                val videoId = uri.getQueryParameter("v")
                if (!videoId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
                // Could be a playlist: /watch?list=PLAYLIST_ID
                val listId = uri.getQueryParameter("list")
                if (!listId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.PLAYLIST, listId)
                }
            }

            // /playlist?list=PLAYLIST_ID
            if (pathLower.startsWith("/playlist")) {
                val listId = uri.getQueryParameter("list")
                if (!listId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.PLAYLIST, listId)
                }
            }

            // /attribution_link?a=...&v=VIDEO_ID (YouTube share redirects)
            if (pathLower.startsWith("/attribution_link")) {
                val videoId = uri.getQueryParameter("v")
                if (!videoId.isNullOrEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
                }
                val uParam = uri.getQueryParameter("u")
                if (!uParam.isNullOrEmpty()) {
                    val decoded = Uri.decode(uParam)
                    Uri.parse(decoded).getQueryParameter("v")?.takeIf { it.isNotEmpty() }?.let {
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
                val channelId = path.removePrefix("/channel/").trimEnd('/')
                if (channelId.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, channelId)
                }
            }

            // /user/CHANNEL_HANDLE
            if (pathLower.startsWith("/user/")) {
                val handle = path.removePrefix("/user/").trimEnd('/')
                if (handle.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, handle)
                }
            }

            // /@CHANNEL_HANDLE or /c/CHANNEL_NAME
            if (pathLower.startsWith("/@") || pathLower.startsWith("/c/")) {
                val handle = path.removePrefix("/@").removePrefix("/c/").trimEnd('/')
                if (handle.isNotEmpty()) {
                    return YouTubeLink(YouTubeLink.Type.CHANNEL, handle)
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

}
