package app.phonetube.core.engine.model

data class HomeFeed(
    val sections: List<HomeSection>
)

data class HomeSection(
    val title: String,
    val videos: List<Video>,
    val source: String = ""
)
