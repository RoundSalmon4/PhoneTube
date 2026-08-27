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
    val source: String? = null
)
