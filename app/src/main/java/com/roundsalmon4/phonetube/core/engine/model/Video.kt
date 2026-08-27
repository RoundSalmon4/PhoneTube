package com.roundsalmon4.phonetube.core.engine.model

data class Video(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewCount: String?,
    val publishedDate: Long,
    val percentWatched: Int,
    val source: String? = null,
    val channelHost: String? = null
) {
    /**
     * The id to pass into the player route. When this video comes from a
     * PeerTube instance (source = host), the id is prefixed so the player
     * routes it to the PeerTube loader instead of the YouTube engine.
     */
    fun playableId(): String = if (source != null && source.isNotBlank()) {
        "peertube:$source:${videoId.removePrefix("peertube:")}"
    } else {
        videoId
    }

    /**
     * The id to use when navigating to this video's channel. For a PeerTube
     * video this is prefixed so the channel screen routes to the PeerTube
     * channel loader. Uses the channel's own host (its federated origin) when
     * known, falling back to the serving instance host.
     */
    fun channelPlayableId(): String = if (channelId.isNotBlank()) {
        val chanHost = channelHost ?: source
        "peertube:$chanHost:${channelId.removePrefix("peertube:")}"
    } else {
        ""
    }
}
