package app.phonetube.core.engine

import app.phonetube.core.engine.model.ChannelInfo
import app.phonetube.core.engine.model.ChannelSection
import app.phonetube.core.engine.model.HomeFeed
import app.phonetube.core.engine.model.HomeSection
import app.phonetube.core.engine.model.SearchResult
import app.phonetube.core.engine.model.SearchSection
import app.phonetube.core.engine.model.StreamFormat
import app.phonetube.core.engine.model.StreamInfo
import app.phonetube.core.engine.model.SponsorSegment
import app.phonetube.core.engine.model.SubtitleTrack
import app.phonetube.core.engine.model.Video
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.MediaItemService
import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.rx2.awaitSingle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeEngine @Inject constructor(
    private val initializer: YouTubeInitializer
) {
    private val serviceManager: ServiceManager
        get() {
            initializer.init()
            return YouTubeServiceManager.instance()
        }

    private val contentService: ContentService
        get() = serviceManager.contentService

    private val mediaItemService: MediaItemService
        get() = serviceManager.mediaItemService

    fun getHome(): Flow<HomeFeed> = flow {
        val groups = contentService.homeObserve.awaitSingle()
        emit(groups.toHomeFeed())
    }.flowOn(Dispatchers.IO)

    fun getTrending(): Flow<HomeFeed> = flow {
        val groups = contentService.trendingObserve.awaitSingle()
        emit(groups.toHomeFeed())
    }.flowOn(Dispatchers.IO)

    fun getMusic(): Flow<HomeFeed> = flow {
        val groups = contentService.musicObserve.awaitSingle()
        emit(groups.toHomeFeed())
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<SearchResult> = flow {
        val groups = contentService.getSearchObserve(query).awaitSingle()
        emit(groups.toSearchResult())
    }.flowOn(Dispatchers.IO)

    fun getSearchSuggestions(query: String): Flow<List<String>> = flow {
        val suggestions = contentService.getSearchTagsObserve(query).awaitSingle()
        emit(suggestions)
    }.flowOn(Dispatchers.IO)

    fun getChannel(channelId: String): Flow<ChannelResult> = flow {
        val groups = contentService.getChannelObserve(channelId).awaitSingle()
        val firstGroup = groups.firstOrNull()
        val channelInfo = firstGroup?.let {
            ChannelInfo(
                channelId = it.channelId.orEmpty(),
                name = it.title.orEmpty(),
                avatarUrl = null,
                subscriberCount = null,
                description = null,
                isSubscribed = false
            )
        }
        val sections = groups.mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull().map { it.toVideo() }
            if (videos.isNotEmpty()) {
                ChannelSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
        emit(ChannelResult(channelInfo, sections))
    }.flowOn(Dispatchers.IO)

    fun getChannelVideos(channelId: String): Flow<List<Video>> = flow {
        val groups = contentService.getChannelObserve(channelId).awaitSingle()
        val videos = groups.flatMap { (it.mediaItems ?: emptyList()).filterNotNull() }
            .map { it.toVideo() }
        emit(videos)
    }.flowOn(Dispatchers.IO)

    fun getStreamInfo(videoId: String): Flow<StreamInfo> = flow {
        val info = mediaItemService.getFormatInfoObserve(videoId).awaitSingle()
        emit(info.toStreamInfo())
    }.flowOn(Dispatchers.IO)

    fun getMetadata(videoId: String): Flow<VideoMetadataResult> = flow {
        val metadata = mediaItemService.getMetadataObserve(videoId).awaitSingle()
        emit(metadata.toVideoMetadataResult())
    }.flowOn(Dispatchers.IO)

    fun getSponsorSegments(videoId: String): Flow<List<SponsorSegment>> = flow {
        val segments = mediaItemService.getSponsorSegmentsObserve(videoId).awaitSingle()
        emit(segments.map { it.toSponsorSegment() })
    }.flowOn(Dispatchers.IO)

    fun continueGroup(group: MediaGroup): Flow<List<Video>> = flow {
        val continued = contentService.continueGroupObserve(group).awaitSingle()
        val videos = (continued.mediaItems ?: emptyList()).filterNotNull().map { it.toVideo() }
        emit(videos)
    }.flowOn(Dispatchers.IO)

    fun isSignedIn(): Boolean {
        initializer.init()
        return serviceManager.signInService.isSigned
    }

    // --- Mapping functions ---

    private fun MediaItem.toVideo(): Video = Video(
        videoId = getVideoId().orEmpty(),
        title = getTitle().orEmpty(),
        author = getAuthor().orEmpty(),
        channelId = getChannelId().orEmpty(),
        thumbnailUrl = getCardImageUrl().orEmpty(),
        durationMs = getDurationMs(),
        viewCount = null,
        publishedDate = getPublishedDate(),
        isLive = isLive,
        isShort = isShorts,
        percentWatched = getPercentWatched()
    )

    private fun MediaItemFormatInfo.toStreamInfo(): StreamInfo = StreamInfo(
        videoId = getVideoId().orEmpty(),
        title = getTitle().orEmpty(),
        author = getAuthor().orEmpty(),
        channelId = getChannelId().orEmpty(),
        lengthSeconds = getLengthSeconds()?.toLongOrNull() ?: 0L,
        isLive = isLive,
        isLiveContent = isLiveContent,
        adaptiveFormats = (adaptiveFormats ?: emptyList()).map { it.toStreamFormat() },
        urlFormats = (urlFormats ?: emptyList()).map { it.toStreamFormat() },
        subtitles = (subtitles ?: emptyList()).map { it.toSubtitleTrack() },
        dashManifestUrl = getDashManifestUrl(),
        hlsManifestUrl = getHlsManifestUrl(),
        storyboardUrl = createStoryboard()?.let { it.getGroupUrl(0) },
        isUnplayable = isUnplayable,
        playabilityReason = getPlayabilityReason()
    )

    private fun com.liskovsoft.mediaserviceinterfaces.data.MediaFormat.toStreamFormat(): StreamFormat =
        StreamFormat(
            formatType = getFormatType(),
            url = getUrl(),
            mimeType = getMimeType(),
            itag = getITag(),
            width = getWidth(),
            height = getHeight(),
            bitrate = getBitrate(),
            fps = getFps(),
            qualityLabel = getQualityLabel(),
            language = getLanguage(),
            isDrc = isDrc
        )

    private fun com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle.toSubtitleTrack(): SubtitleTrack =
        SubtitleTrack(
            baseUrl = getBaseUrl().orEmpty(),
            languageCode = getLanguageCode().orEmpty(),
            name = getName().orEmpty(),
            mimeType = getMimeType().orEmpty(),
            isTranslatable = isTranslatable
        )

    private fun MediaItemMetadata.toVideoMetadataResult(): VideoMetadataResult = VideoMetadataResult(
        video = Video(
            videoId = getVideoId().orEmpty(),
            title = getTitle().orEmpty(),
            author = getAuthor().orEmpty(),
            channelId = getChannelId().orEmpty(),
            thumbnailUrl = getAuthorImageUrl().orEmpty(),
            durationMs = getDurationMs(),
            viewCount = getViewCount(),
            publishedDate = 0L,
            isLive = isLive,
            isShort = false,
            percentWatched = getPercentWatched()
        ),
        description = getDescription().orEmpty(),
        viewCount = getViewCount().orEmpty(),
        likeCount = getLikeCount().orEmpty(),
        subscriberCount = getSubscriberCount().orEmpty(),
        suggestions = (suggestions ?: emptyList()).flatMap { group ->
            (group.mediaItems ?: emptyList()).filterNotNull().map { it.toVideo() }
        }
    )

    private fun com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment.toSponsorSegment(): SponsorSegment =
        SponsorSegment(
            startMs = getStartMs(),
            endMs = getEndMs(),
            category = getCategory().orEmpty(),
            action = getAction().orEmpty()
        )

    // --- Result wrapper types ---

    data class ChannelResult(
        val channel: ChannelInfo?,
        val sections: List<ChannelSection>
    )

    data class VideoMetadataResult(
        val video: Video,
        val description: String,
        val viewCount: String,
        val likeCount: String,
        val subscriberCount: String,
        val suggestions: List<Video>
    )

    // --- Group-to-feed mappers ---

    private fun List<MediaGroup>.toHomeFeed(): HomeFeed = HomeFeed(
        sections = mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull().map { it.toVideo() }
            if (videos.isNotEmpty()) {
                HomeSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
    )

    private fun List<MediaGroup>.toSearchResult(): SearchResult = SearchResult(
        sections = mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull().map { it.toVideo() }
            if (videos.isNotEmpty()) {
                SearchSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
    )
}
