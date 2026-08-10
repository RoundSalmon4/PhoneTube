package com.roundsalmon4.phonetube.core.engine.model

data class ChannelInfo(
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: String?
)

data class ChannelSection(
    val title: String,
    val videos: List<Video>,
    val playlists: List<SearchPlaylist> = emptyList()
)
