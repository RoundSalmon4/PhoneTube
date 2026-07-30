package com.roundsalmon4.phonetube.core.engine.model

data class SearchResult(
    val sections: List<SearchSection>,
    val channels: List<SearchChannel>,
    val playlists: List<SearchPlaylist> = emptyList()
)

data class SearchSection(
    val title: String,
    val videos: List<Video>
)

data class SearchChannel(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?
)

data class SearchPlaylist(
    val playlistId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val videoCount: Int,
    val firstVideoId: String? = null,
    val sampleVideoIds: List<String> = emptyList()
)

enum class SearchFilter {
    ALL,
    VIDEOS,
    CHANNELS,
    PLAYLISTS
}
