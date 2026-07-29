package com.roundsalmon4.phonetube.core.engine

import android.util.Log
import com.roundsalmon4.phonetube.core.engine.model.ChannelInfo
import com.roundsalmon4.phonetube.core.engine.model.ChannelSection
import com.roundsalmon4.phonetube.core.engine.model.HomeFeed
import com.roundsalmon4.phonetube.core.engine.model.HomeSection
import com.roundsalmon4.phonetube.core.engine.model.SearchChannel
import com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist
import com.roundsalmon4.phonetube.core.engine.model.SearchResult
import com.roundsalmon4.phonetube.core.engine.model.SearchSection
import com.roundsalmon4.phonetube.core.engine.model.StreamFormat
import com.roundsalmon4.phonetube.core.engine.model.StreamInfo
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.core.engine.model.SubtitleTrack
import com.roundsalmon4.phonetube.core.engine.model.Video
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
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirstOrDefault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeEngine @Inject constructor(
    private val initializer: YouTubeInitializer
) {
    companion object {
        private const val TAG = "YouTubeEngine"
        private const val MAX_SECTION_VIDEOS = 20
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
            initializer.warmup()

            // Use Java HomeFeedLoader that subscribes like SmartTube's BrowsePresenter
            val result = com.roundsalmon4.phonetube.core.engine.java.HomeFeedLoader.loadHomeSync(
                contentService
            )

            if (!result.success) {
                Log.e(TAG, "getHome failed: ${result.error}")
                emit(HomeFeed(emptyList()))
                return@flow
            }

            val flat = result.groups
            Log.d(TAG, "getHome: ${flat.size} groups collected via Java subscriber")
            val totalItems = flat.sumOf { (it.mediaItems?.size ?: 0) }
            Log.d(TAG, "getHome: ${flat.size} groups, $totalItems total items")

            val sections = mutableListOf<HomeSection>()
            for (group in flat) {
                val videos = expandGroupToVideos(group)
                if (videos.isNotEmpty()) {
                    sections.add(HomeSection(title = group.title.orEmpty(), videos = videos, source = "Home"))
                }
            }

            val feed = HomeFeed(sections = sections)
            Log.d(TAG, "getHome: ${feed.sections.size} sections mapped")
            emit(feed)
        } catch (e: Exception) {
            Log.e(TAG, "getHome failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getMusic(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.musicObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Music"))
        } catch (e: Exception) {
            Log.e(TAG, "getMusic failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getTrending(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.trendingObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Trending"))
        } catch (e: Exception) {
            Log.e(TAG, "getTrending failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getSports(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.sportsObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Sports"))
        } catch (e: Exception) {
            Log.e(TAG, "getSports failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getLive(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.liveObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Live"))
        } catch (e: Exception) {
            Log.e(TAG, "getLive failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getWhatToWatch(): Flow<HomeFeed> = flow {
        try {
            val result = com.roundsalmon4.phonetube.core.engine.java.HomeFeedLoader.loadHomeSync(
                contentService, "WhatToWatch"
            )

            if (!result.success) {
                Log.e(TAG, "getWhatToWatch failed: ${result.error}")
                emit(HomeFeed(emptyList()))
                return@flow
            }

            val flat = result.groups
            val sections = mutableListOf<HomeSection>()
            for (group in flat) {
                val videos = expandGroupToVideos(group)
                if (videos.isNotEmpty()) {
                    sections.add(HomeSection(title = group.title.orEmpty(), videos = videos, source = "What to Watch"))
                }
            }

            emit(HomeFeed(sections = sections))
        } catch (e: Exception) {
            Log.e(TAG, "getWhatToWatch failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getNews(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.newsObserve.toList().await()
            emit(groups.flatten().toHomeFeed("News"))
        } catch (e: Exception) {
            Log.e(TAG, "getNews failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getGaming(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.gamingObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Gaming"))
        } catch (e: Exception) {
            Log.e(TAG, "getGaming failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun getKidsHome(): Flow<HomeFeed> = flow {
        try {
            val groups = contentService.kidsHomeObserve.toList().await()
            emit(groups.flatten().toHomeFeed("Kids"))
        } catch (e: Exception) {
            Log.e(TAG, "getKidsHome failed", e)
            emit(HomeFeed(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<SearchResult> = flow {
        try {
            val groups = contentService.getSearchObserve(query).awaitFirstOrDefault(emptyList())

            val totalItems = groups.sumOf { (it.mediaItems?.size ?: 0) }
            Log.d(TAG, "search('$query'): ${groups.size} groups, $totalItems total items")

            if (groups.isNotEmpty()) {
                val sampleItems = groups.flatMap { (it.mediaItems ?: emptyList()).filterNotNull() }.take(5)
                for ((i, item) in sampleItems.withIndex()) {
                    Log.d(TAG, "search item[$i]: type=${item.type} videoId=${item.videoId?.take(12)} channelId=${item.channelId?.take(12)} title='${item.title?.take(30)}'")
                }
            }

            val videos = groups.toSearchVideos()
            val playlists = groups.toSearchPlaylists()
            val channelsFromSearch = groups.toSearchChannels()

            Log.d(TAG, "search('$query'): ${videos.size} videos, ${playlists.size} playlists, ${channelsFromSearch.size} channels from search")

            val channelGroups = try {
                contentService.getSearchObserve(query, SearchOptions.TYPE_CHANNEL).awaitFirstOrDefault(emptyList())
            } catch (_: Exception) { emptyList() }
            val channelsFromFilter = channelGroups.toSearchChannels()

            val channels = (channelsFromSearch + channelsFromFilter).distinctBy { it.channelId }

            val sections = if (videos.isNotEmpty()) {
                listOf(SearchSection(title = "", videos = videos.distinctBy { it.videoId }))
            } else emptyList()

            Log.d(TAG, "search('$query'): final ${videos.size} videos, ${channels.size} channels")

            emit(SearchResult(
                sections = sections,
                channels = channels,
                playlists = playlists
            ))
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed", e)
            emit(SearchResult(emptyList(), emptyList(), emptyList()))
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
                val videos = (group.mediaItems ?: emptyList()).filterNotNull()
                    .flatMap { it.resolveVideos() }
                    .distinctBy { it.videoId }
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

    fun getStreamInfo(videoId: String): Flow<StreamInfo> = flow {
        try {
            val info = mediaItemService.getFormatInfoObserve(videoId).awaitFirstOrDefault(null)
            if (info == null) {
                throw IllegalStateException("No stream info available for $videoId")
            }
            var streamInfo = info.toStreamInfo()

            emit(streamInfo)
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

    fun reportWatchProgress(videoId: String, positionSec: Float) {
        try {
            mediaItemService.updateHistoryPosition(videoId, positionSec)
        } catch (e: Exception) {
            Log.e(TAG, "reportWatchProgress failed for $videoId: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    suspend fun getPlaylistFirstVideoId(playlistId: String): String? {
        return try {
            val group = contentService.getGroup(playlistId) ?: return null
            val items = (group.mediaItems ?: emptyList()).filterNotNull()
            items.firstOrNull()?.videoId
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistFirstVideoId failed for $playlistId", e)
            null
        }
    }

    suspend fun getPlaylistVideos(playlistId: String): List<Video> {
        return try {
            val group = contentService.getGroup(playlistId) ?: return emptyList()
            val items = (group.mediaItems ?: emptyList()).filterNotNull()
            items.mapNotNull { it.toVideo() }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistVideos failed for $playlistId", e)
            emptyList()
        }
    }

    // --- Mapping functions ---

    private fun MediaItem.toVideo(): Video? {
        val videoId = getVideoId()
        if (videoId.isNullOrBlank()) {
            Log.d(TAG, "toVideo: null videoId for type=${getType()} title='${getTitle()?.take(40)}' channelId=${getChannelId()?.take(8)}")
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

    private suspend fun expandGroupToVideos(group: MediaGroup): List<Video> {
        val items = (group.mediaItems ?: emptyList()).filterNotNull()

        if (items.isNotEmpty()) {
            return items.flatMap { it.resolveVideos() }.distinctBy { it.videoId }
        }

        if (group.isEmpty) {
            try {
                val expanded = contentService.continueGroup(group) ?: return emptyList()
                val expandedItems = (expanded.mediaItems ?: emptyList()).filterNotNull()
                return expandedItems.mapNotNull { it.toVideo() }.distinctBy { it.videoId }
            } catch (e: Exception) {
                Log.d(TAG, "expandGroupToVideos: continueGroup failed for '${group.title?.take(30)}': ${e.message?.take(80)}")
            }
        }

        return emptyList()
    }

    private fun MediaItem.resolveVideos(): List<Video> {
        val directVideo = toVideo()
        if (directVideo != null) return listOf(directVideo)

        val type = type
        if (type != MediaItem.TYPE_PLAYLIST && type != MediaItem.TYPE_CHANNEL) return emptyList()

        return try {
            val childGroup = contentService.getGroup(this) ?: return emptyList()
            val childItems = (childGroup.mediaItems ?: emptyList()).filterNotNull()
            childItems.mapNotNull { it.toVideo() }
                .distinctBy { it.videoId }
                .take(MAX_SECTION_VIDEOS)
        } catch (e: Exception) {
            Log.d(TAG, "resolveVideos: failed for type=$type title='${title?.take(30)}': ${e.message?.take(80)}")
            emptyList()
        }
    }

    private suspend fun List<MediaGroup>.toHomeFeed(source: String = ""): HomeFeed {
        val sections = mutableListOf<HomeSection>()

        for (group in this) {
            val videos = expandGroupToVideos(group)

            if (videos.isNotEmpty()) {
                sections.add(HomeSection(title = group.title.orEmpty(), videos = videos, source = source))
            } else {
                val allItems = (group.mediaItems ?: emptyList()).filterNotNull()
                if (allItems.isNotEmpty()) {
                    val typeBreakdown = allItems.groupBy { it.type }
                        .mapValues { it.value.size }
                        .entries.joinToString { "${it.key}:${it.value}" }
                    Log.d(TAG, "toHomeFeed($source): dropped section '${group.title?.take(30)}' (${allItems.size} items, types=$typeBreakdown, 0 videos)")
                }
            }
        }

        return HomeFeed(sections = sections)
    }

    private fun List<MediaGroup>.toSearchVideos(): List<Video> {
        val videos = mutableListOf<Video>()
        var debugCount = 0
        var debugSkipped = 0
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                if (item.type != MediaItem.TYPE_CHANNEL && !item.videoId.isNullOrBlank()) {
                    val video = item.toVideo()
                    if (video != null) {
                        videos.add(video)
                        debugCount++
                    }
                } else {
                    debugSkipped++
                }
            }
        }
        Log.d(TAG, "toSearchVideos: $debugCount videos found, $debugSkipped skipped (type_channel=${MediaItem.TYPE_CHANNEL})")
        return videos
    }

    private fun List<MediaGroup>.toSearchChannels(): List<SearchChannel> {
        val channels = mutableListOf<SearchChannel>()
        var debugCount = 0
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                val isChannel = item.type == MediaItem.TYPE_CHANNEL ||
                    (item.videoId.isNullOrBlank() && !item.channelId.isNullOrBlank())
                if (isChannel) {
                    val channelId = item.channelId
                    if (!channelId.isNullOrBlank()) {
                        val thumbUrl = item.cardImageUrl
                            ?: item.backgroundImageUrl
                        channels.add(
                            SearchChannel(
                                channelId = channelId,
                                name = item.title.orEmpty(),
                                thumbnailUrl = thumbUrl
                            )
                        )
                        debugCount++
                    }
                }
            }
        }
        Log.d(TAG, "toSearchChannels: $debugCount channels found")
        return channels
    }
    private fun List<MediaGroup>.toSearchPlaylists(): List<SearchPlaylist> {
        val playlists = mutableListOf<SearchPlaylist>()
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                if (item.type == MediaItem.TYPE_PLAYLIST && !item.playlistId.isNullOrBlank()) {
                    playlists.add(
                        SearchPlaylist(
                            playlistId = item.playlistId,
                            title = item.title.orEmpty(),
                            channelName = item.author.orEmpty(),
                            thumbnailUrl = item.cardImageUrl?.ifBlank { null }
                                ?: item.backgroundImageUrl?.ifBlank { null },
                            videoCount = 0
                        )
                    )
                }
            }
        }
        Log.d(TAG, "toSearchPlaylists:  playlists found")
        return playlists
    }
}
