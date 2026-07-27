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

    fun parse(uri: Uri): YouTubeLink {
        val host = uri.host?.lowercase() ?: return YouTubeLink(YouTubeLink.Type.UNKNOWN, "")
        val path = uri.path ?: ""
        val query = uri.query

        // youtu.be/VIDEO_ID (short links)
        if (host == "youtu.be") {
            val videoId = path.trimStart('/')
            if (videoId.isNotEmpty()) {
                return YouTubeLink(YouTubeLink.Type.VIDEO, videoId)
            }
        }

        // youtube.com paths
        if (host.contains("youtube.com")) {
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
