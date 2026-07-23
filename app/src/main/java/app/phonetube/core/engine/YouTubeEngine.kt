package app.phonetube.core.engine

import android.util.Log
import app.phonetube.core.engine.model.ChannelInfo
import app.phonetube.core.engine.model.ChannelSection
import app.phonetube.core.engine.model.HomeFeed
import app.phonetube.core.engine.model.HomeSection
import app.phonetube.core.engine.model.SearchChannel
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
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.rx2.awaitAll
import kotlinx.coroutines.rx2.awaitFirstOrDefault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeEngine @Inject constructor(
    private val initializer: YouTubeInitializer
) {
    companion object {
        private const val TAG = "YouTubeEngine"
    }

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
        try {
            val groups = contentService.homeObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Home"))
        } catch (e: Exception) {
            Log.e(TAG, "getHome failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getMusic(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.musicObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Music"))
        } catch (e: Exception) {
            Log.e(TAG, "getMusic failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getSports(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.sportsObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Sports"))
        } catch (e: Exception) {
            Log.e(TAG, "getSports failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getLive(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.liveObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Live"))
        } catch (e: Exception) {
            Log.e(TAG, "getLive failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getNews(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.newsObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("News"))
        } catch (e: Exception) {
            Log.e(TAG, "getNews failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getGaming(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.gamingObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Gaming"))
        } catch (e: Exception) {
            Log.e(TAG, "getGaming failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getKidsHome(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.kidsHomeObserve.awaitAll()
            emit(groups.flatten().toHomeFeed("Kids"))
        } catch (e: Exception) {
            Log.e(TAG, "getKidsHome failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String, channelOnly: Boolean = false): Flow<SearchResult> = flow {
        try {
            val groups = contentService.getSearchObserve(query).awaitFirstOrDefault(emptyList())

            val videos = if (!channelOnly) groups.toSearchVideos() else emptyList()
            val channelsFromSearch = groups.toSearchChannels()

            val channelGroups = try {
                contentService.getSearchObserve(query, SearchOptions.TYPE_CHANNEL).awaitFirstOrDefault(emptyList())
            } catch (_: Exception) { emptyList() }
            val channelsFromFilter = channelGroups.toSearchChannels()

            val channels = (channelsFromSearch + channelsFromFilter).distinctBy { it.channelId }

            val sections = if (videos.isNotEmpty()) {
                listOf(SearchSection(title = "", videos = videos.distinctBy { it.videoId }))
            } else emptyList()

            emit(SearchResult(
                sections = sections,
                channels = channels
            ))
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed", e)
            emit(SearchResult(emptyList(), emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getSearchSuggestions(query: String): Flow<List<String>> = flow {
        val suggestions = contentService.getSearchTagsObserve(query).awaitFirstOrDefault(emptyList())
        emit(suggestions)
    }.flowOn(Dispatchers.IO)

    fun getChannel(channelId: String): Flow<ChannelResult> = flow {
        try {
            val groups = contentService.getChannelObserve(channelId).awaitFirstOrDefault(emptyList())
            val firstGroup = groups.firstOrNull()
            val sections = groups.mapNotNull { group ->
                val videos = (group.mediaItems ?: emptyList()).filterNotNull().mapNotNull { it.toVideo() }
                if (videos.isNotEmpty()) {
                    ChannelSection(title = group.title.orEmpty(), videos = videos)
                } else null
            }
            val allVideoIds = sections.flatMap { it.videos }.map { it.videoId }.filter { it.isNotBlank() }
            var avatarUrl: String? = null
            for (videoId in allVideoIds.take(3)) {
                try {
                    val metadata = mediaItemService.getMetadataObserve(videoId).awaitFirstOrDefault(null)
                    if (!metadata?.authorImageUrl.isNullOrBlank()) {
                        avatarUrl = metadata.authorImageUrl
                        break
                    }
                } catch (_: Exception) { }
            }
            val channelName = sections.firstOrNull()?.videos?.firstOrNull()?.author?.takeIf { it.isNotBlank() }
            val channelInfo = firstGroup?.let {
                ChannelInfo(
                    channelId = it.channelId.orEmpty(),
                    name = channelName ?: it.title.orEmpty(),
                    avatarUrl = avatarUrl,
                    subscriberCount = null,
                    description = null,
                    isSubscribed = false
                )
            }
            emit(ChannelResult(channelInfo, sections))
        } catch (e: Exception) {
            Log.e(TAG, "getChannel($channelId) failed", e)
            emit(ChannelResult(null, emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getChannelVideos(channelId: String): Flow<List<Video>> = flow {
        val groups = contentService.getChannelObserve(channelId).awaitFirstOrDefault(emptyList())
        val videos = groups.flatMap { (it.mediaItems ?: emptyList()).filterNotNull() }
            .mapNotNull { it.toVideo() }
        emit(videos)
    }.flowOn(Dispatchers.IO)

    fun getStreamInfo(videoId: String): Flow<StreamInfo> = flow {
        try {
            val info = mediaItemService.getFormatInfoObserve(videoId).awaitFirstOrDefault(null)
            if (info == null) {
                throw IllegalStateException("No stream info available for $videoId")
            }
            emit(info.toStreamInfo())
        } catch (e: Exception) {
            Log.e(TAG, "getStreamInfo($videoId) failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun getMetadata(videoId: String): Flow<VideoMetadataResult> = flow {
        val metadata = mediaItemService.getMetadataObserve(videoId).awaitFirstOrDefault(null)
        emit((metadata ?: throw IllegalStateException("No metadata available for $videoId")).toVideoMetadataResult())
    }.flowOn(Dispatchers.IO)

    fun getSponsorSegments(videoId: String): Flow<List<SponsorSegment>> = flow {
        try {
            val segments = mediaItemService.getSponsorSegmentsObserve(videoId).awaitFirstOrDefault(emptyList())
            emit(segments.map { it.toSponsorSegment() })
        } catch (e: Exception) {
            Log.d(TAG, "No sponsor segments for $videoId: ${e.message?.take(80)}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun continueGroup(group: MediaGroup): Flow<List<Video>> = flow {
        val continued = contentService.continueGroupObserve(group).awaitFirstOrDefault(null)
        val videos = (continued?.mediaItems ?: emptyList()).filterNotNull().mapNotNull { it.toVideo() }
        emit(videos)
    }.flowOn(Dispatchers.IO)

    fun isSignedIn(): Boolean {
        initializer.init()
        return serviceManager.signInService.isSigned
    }

    // --- Mapping functions ---

    private fun MediaItem.toVideo(): Video? {
        val videoId = getVideoId()
        if (videoId.isNullOrBlank()) return null
        return Video(
            videoId = videoId,
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
    }

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
        storyboardUrl = try { createStoryboard()?.let { it.getGroupUrl(0) } } catch (_: Exception) { null },
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
            (group.mediaItems ?: emptyList()).filterNotNull().mapNotNull { it.toVideo() }
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

    private fun List<MediaGroup>.toHomeFeed(source: String = ""): HomeFeed = HomeFeed(
        sections = mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull()
                .mapNotNull { it.toVideo() }
                .distinctBy { it.videoId }
            if (videos.isNotEmpty()) {
                HomeSection(title = group.title.orEmpty(), videos = videos, source = source)
            } else null
        }
    )

    private fun List<MediaGroup>.toSearchVideos(): List<Video> {
        val videos = mutableListOf<Video>()
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                if (item.type != MediaItem.TYPE_CHANNEL && !item.videoId.isNullOrBlank()) {
                    val video = item.toVideo()
                    if (video != null) {
                        videos.add(video)
                    }
                }
            }
        }
        return videos
    }

    private fun List<MediaGroup>.toSearchChannels(): List<SearchChannel> {
        val channels = mutableListOf<SearchChannel>()
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                val isChannel = item.type == MediaItem.TYPE_CHANNEL ||
                    (item.videoId.isNullOrBlank() && !item.channelId.isNullOrBlank())
                if (isChannel) {
                    val channelId = item.channelId
                    if (!channelId.isNullOrBlank()) {
                        channels.add(
                            SearchChannel(
                                channelId = channelId,
                                name = item.title.orEmpty(),
                                thumbnailUrl = item.cardImageUrl
                            )
                        )
                    }
                }
            }
        }
        return channels
    }
}
