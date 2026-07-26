package com.roundsalmon4.phonetube.core.engine.model

data class SearchResult(
    val sections: List<SearchSection>,
    val channels: List<SearchChannel>
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

enum class SearchFilter {
    ALL,
    VIDEOS,
    CHANNELS
}
