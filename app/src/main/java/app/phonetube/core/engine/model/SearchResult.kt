package app.phonetube.core.engine.model

data class SearchResult(
    val sections: List<SearchSection>
)

data class SearchSection(
    val title: String,
    val videos: List<Video>
)
