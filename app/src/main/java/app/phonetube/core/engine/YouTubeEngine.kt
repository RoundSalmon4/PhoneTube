package app.phonetube.core.engine

import android.util.Log
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
            Log.d(TAG, "getHome: calling homeObserve...")
            val groups = contentService.homeObserve.awaitFirstOrDefault(emptyList())
            Log.d(TAG, "getHome: got ${groups.size} groups")
            for (g in groups) {
                val items = g.mediaItems
                Log.d(TAG, "  group: type=${g.type}, title='${g.title}', items=${items?.size ?: "null"}")
            }
            emit(groups.toHomeFeed())
        } catch (e: Exception) {
            Log.e(TAG, "getHome failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun getTrending(): Flow<HomeFeed> = flow {
        try {
            Log.d(TAG, "getTrending: calling trendingObserve...")
            val groups = contentService.trendingObserve.awaitFirstOrDefault(emptyList())
            Log.d(TAG, "getTrending: got ${groups.size} groups")
            for (g in groups) {
                val items = g.mediaItems
                Log.d(TAG, "  group: type=${g.type}, title='${g.title}', items=${items?.size ?: "null"}")
            }
            emit(groups.toHomeFeed())
        } catch (e: Exception) {
            Log.e(TAG, "getTrending failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun getMusic(): Flow<HomeFeed> = flow {
        try {
            Log.d(TAG, "getMusic: calling musicObserve...")
            val groups = contentService.musicObserve.awaitFirstOrDefault(emptyList())
            Log.d(TAG, "getMusic: got ${groups.size} groups")
            for (g in groups) {
                val items = g.mediaItems
                Log.d(TAG, "  group: type=${g.type}, title='${g.title}', items=${items?.size ?: "null"}")
            }
            emit(groups.toHomeFeed())
        } catch (e: Exception) {
            Log.e(TAG, "getMusic failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<SearchResult> = flow {
        val groups = contentService.getSearchObserve(query).awaitFirstOrDefault(emptyList())
        emit(groups.toSearchResult())
    }.flowOn(Dispatchers.IO)

    fun getSearchSuggestions(query: String): Flow<List<String>> = flow {
        val suggestions = contentService.getSearchTagsObserve(query).awaitFirstOrDefault(emptyList())
        emit(suggestions)
    }.flowOn(Dispatchers.IO)

    fun getChannel(channelId: String): Flow<ChannelResult> = flow {
        val groups = contentService.getChannelObserve(channelId).awaitFirstOrDefault(emptyList())
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
            val videos = (group.mediaItems ?: emptyList()).filterNotNull().mapNotNull { it.toVideo() }
            if (videos.isNotEmpty()) {
                ChannelSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
        emit(ChannelResult(channelInfo, sections))
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
                Log.e(TAG, "getStreamInfo($videoId): null response")
                throw IllegalStateException("No stream info available for $videoId")
            }
            val mapped = info.toStreamInfo()
            Log.d(TAG, "getStreamInfo($videoId): isUnplayable=${mapped.isUnplayable}, " +
                "playabilityReason=${mapped.playabilityReason}, " +
                "hasDash=${mapped.dashManifestUrl != null}, hasHls=${mapped.hlsManifestUrl != null}, " +
                "urlFormats=${mapped.urlFormats.size}, adaptiveFormats=${mapped.adaptiveFormats.size}, " +
                "containsMedia=${info.containsMedia()}, containsUrlFormats=${info.containsUrlFormats()}, " +
                "containsDashFormats=${info.containsDashFormats()}, containsHlsUrl=${info.containsHlsUrl()}")
            emit(mapped)
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
        val segments = mediaItemService.getSponsorSegmentsObserve(videoId).awaitFirstOrDefault(emptyList())
        emit(segments.map { it.toSponsorSegment() })
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
        if (videoId.isNullOrBlank()) {
            Log.w(TAG, "toVideo: skipping item '${getTitle()}' — getVideoId() returned null/blank (type=${getType()})")
            return null
        }
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

    private fun List<MediaGroup>.toHomeFeed(): HomeFeed = HomeFeed(
        sections = mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull()
                .mapNotNull { it.toVideo() }
                .distinctBy { it.videoId }
            Log.d(TAG, "toHomeFeed: section '${group.title}' → ${videos.size} playable videos (from ${(group.mediaItems?.size ?: 0)} items)")
            if (videos.isNotEmpty()) {
                HomeSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
    )

    private fun List<MediaGroup>.toSearchResult(): SearchResult = SearchResult(
        sections = mapNotNull { group ->
            val videos = (group.mediaItems ?: emptyList()).filterNotNull()
                .mapNotNull { it.toVideo() }
                .distinctBy { it.videoId }
            if (videos.isNotEmpty()) {
                SearchSection(title = group.title.orEmpty(), videos = videos)
            } else null
        }
    )
}
