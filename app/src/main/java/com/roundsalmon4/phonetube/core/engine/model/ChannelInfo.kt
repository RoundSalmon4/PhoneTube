package com.roundsalmon4.phonetube.core.engine.model

data class ChannelInfo(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: String?,
    val description: String?,
    val isSubscribed: Boolean
)

data class ChannelSection(
    val title: String,
    val videos: List<Video>,
    val playlists: List<SearchPlaylist> = emptyList()
)
