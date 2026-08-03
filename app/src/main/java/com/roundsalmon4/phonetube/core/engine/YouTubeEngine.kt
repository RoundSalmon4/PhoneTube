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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeEngine @Inject constructor(
    private val initializer: YouTubeInitializer
) {
    companion object {
        private const val TAG = "YouTubeEngine"
        private const val MAX_SECTION_VIDEOS = 20
        private const val MAX_PLAYLIST_PAGES = 30
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
                val allItems = groups.flatMap { (it.mediaItems ?: emptyList()).filterNotNull() }
                val typeCounts = allItems.groupBy { it.type }.mapValues { it.value.size }
                Log.d(TAG, "search item type breakdown: $typeCounts")
                val withPlaylistId = allItems.count { !it.playlistId.isNullOrBlank() }
                Log.d(TAG, "search items with playlistId: $withPlaylistId")
                val sampleItems = allItems.take(10)
                for ((i, item) in sampleItems.withIndex()) {
                    Log.d(TAG, "search item[$i]: type=${item.type} videoId=${item.videoId?.take(12)} playlistId=${item.playlistId?.take(12)} channelId=${item.channelId?.take(12)} title='${item.title?.take(30)}'")
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
            Log.d(TAG, "getChannel($channelId): ${groups.size} groups from API")
            val sections = groups.mapNotNull { group ->
                val items = (group.mediaItems ?: emptyList()).filterNotNull()
                val videoItems = items.filter { it.videoId?.isNotBlank() == true || it.type != MediaItem.TYPE_PLAYLIST }
                val playlistItems = items.filter { it.type == MediaItem.TYPE_PLAYLIST }
                val videoCount = videoItems.size
                val playlistCount = playlistItems.size
                Log.d(TAG, "getChannel($channelId): group '${group.title?.take(30)}': $videoCount videos, $playlistCount playlists, ${items.size} total")
                val videos = videoItems.flatMap { it.resolveVideos() }
                    .distinctBy { it.videoId }
                val playlists = playlistItems.mapNotNull { item ->
                    val pid = item.playlistId ?: item.channelId?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
                    if (pid.isNullOrBlank()) return@mapNotNull null
                    SearchPlaylist(
                        playlistId = pid,
                        title = item.title.orEmpty(),
                        channelName = item.author.orEmpty(),
                        thumbnailUrl = item.cardImageUrl?.ifBlank { null },
                        firstVideoId = item.videoId?.takeIf { it.isNotBlank() }
                    )
                }
                if (videos.isNotEmpty() || playlists.isNotEmpty()) {
                    ChannelSection(title = group.title.orEmpty(), videos = videos, playlists = playlists)
                } else null
            }
            val allVideoIds = sections.flatMap { it.videos }.map { it.videoId }.filter { it.isNotBlank() }
            var avatarUrl: String? = null
            for (videoId in allVideoIds.take(3)) {
                try {
                    val metadata = mediaItemService.getMetadataObserve(videoId).awaitOrNull()
                    if (!metadata?.authorImageUrl.isNullOrBlank()) {
                        avatarUrl = metadata.authorImageUrl
                        break
                    }
                } catch (_: Exception) { }
            }
            val channelName = sections.firstOrNull()?.videos?.firstOrNull()?.author?.takeIf { it.isNotBlank() }
            val channelInfo = firstGroup?.let {
                ChannelInfo(
                    name = channelName ?: it.title.orEmpty(),
                    avatarUrl = avatarUrl,
                    subscriberCount = null,
                    description = null
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
            val info = mediaItemService.getFormatInfoObserve(videoId).awaitOrNull()
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
        val metadata = mediaItemService.getMetadataObserve(videoId).awaitOrNull()
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

    /**
     * Fetches the latest videos from a set of channels via their public RSS feeds.
     */
    suspend fun getRssFeedVideos(channelIds: List<String>): List<Video> {
        return try {
            withContext(Dispatchers.IO) {
                val group = contentService.getRssFeedObserve(*channelIds.toTypedArray())
                    .awaitOrNull()
                group?.mediaItems?.filterNotNull()?.mapNotNull { it.toVideo() } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRssFeedVideos failed", e)
            emptyList()
        }
    }

    suspend fun getPlaylistFirstVideoId(playlistId: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                val result = contentService.getPlaylist(browseId)
                val firstGroup = result?.filterNotNull()?.firstOrNull()
                val items = (firstGroup?.mediaItems ?: emptyList()).filterNotNull()
                items.firstOrNull()?.videoId
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistFirstVideoId failed for $playlistId", e)
            null
        }
    }

    suspend fun getPlaylistVideos(playlistId: String): List<Video> {
        Log.d(TAG, "getPlaylistVideos: fetching videos for playlist $playlistId")
        return try {
            withContext(Dispatchers.IO) {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                val browseResult = contentService.getPlaylist(browseId)
                if (browseResult != null && browseResult.isNotEmpty()) {
                    val allItems = mutableListOf<MediaItem>()
                    browseResult.filterNotNull().forEach { group ->
                        allItems.addAll((group.mediaItems ?: emptyList()).filterNotNull())
                    }
                    // Page through the playlist via continuation keys
                    var lastGroup: MediaGroup? = browseResult.lastOrNull()?.takeIf { it.nextPageKey != null }
                    var pages = 0
                    while (lastGroup != null && pages < MAX_PLAYLIST_PAGES) {
                        val next = try {
                            contentService.continueGroup(lastGroup)
                        } catch (e: Exception) {
                            Log.w(TAG, "getPlaylistVideos: continue failed: ${e.message?.take(80)}")
                            null
                        }
                        if (next == null) break
                        allItems.addAll((next.mediaItems ?: emptyList()).filterNotNull())
                        lastGroup = next.takeIf { it.nextPageKey != null }
                        pages++
                    }
                    Log.d(TAG, "getPlaylistVideos: got ${allItems.size} items via browse for $playlistId")
                    allItems.mapNotNull { it.toVideo() }.distinctBy { it.videoId }
                } else {
                    Log.w(TAG, "getPlaylistVideos: browse returned empty for $playlistId")
                    emptyList()
                }
            }
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
        val timestamp = getPublishedDate()
        return Video(
            videoId = videoId,
            title = getTitle().orEmpty(),
            author = getAuthor().orEmpty(),
            channelId = getChannelId().orEmpty(),
            thumbnailUrl = getCardImageUrl().orEmpty(),
            durationMs = getDurationMs(),
            viewCount = null,
            publishedDate = if (timestamp > 0) timestamp else parseProductionDate(getProductionDate()),
            percentWatched = getPercentWatched()
        )
    }

    /**
     * Converts YouTube's published-time text ("2 years ago", "Streamed 5 hours ago",
     * "Aug 9, 2024", "2022-09-11T23:39:38+00:00") into epoch millis.
     */
    private fun parseProductionDate(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val trimmed = text.trim()

        // ISO date prefix: "2022-09-11T23:39:38+00:00" or "2022-09-11"
        if (trimmed.length >= 10 && trimmed[4] == '-') {
            try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                fmt.parse(trimmed.take(10))?.let { if (it.time > 0) return it.time }
            } catch (_: Exception) { }
        }

        // Relative: "2 years ago", "Streamed 5 hours ago", "Premiered 3 weeks ago"
        val relative = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)
        relative.find(trimmed)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let
            val unitMs = when (match.groupValues[2].lowercase()) {
                "second" -> 1000L
                "minute" -> 60_000L
                "hour" -> 3_600_000L
                "day" -> 86_400_000L
                "week" -> 604_800_000L
                "month" -> 2_592_000_000L
                "year" -> 31_536_000_000L
                else -> 0L
            }
            if (unitMs > 0) return System.currentTimeMillis() - amount * unitMs
        }

        // Absolute dates: "Aug 9, 2024", "9 Aug 2024", "08/09/2024"
        for (pattern in listOf("MMM d, yyyy", "d MMM yyyy", "MM/dd/yyyy")) {
            try {
                val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                fmt.parse(trimmed)?.let { if (it.time > 0) return it.time }
            } catch (_: Exception) { }
        }

        return 0L
    }

    private fun MediaItemFormatInfo.toStreamInfo(): StreamInfo = StreamInfo(
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
        isUnplayable = isUnplayable,
        playabilityReason = getPlayabilityReason()
    )

    private fun com.liskovsoft.mediaserviceinterfaces.data.MediaFormat.toStreamFormat(): StreamFormat =
        StreamFormat(
            url = getUrl(),
            mimeType = getMimeType(),
            itag = getITag(),
            height = getHeight(),
            bitrate = getBitrate(),
            fps = getFps(),
            qualityLabel = getQualityLabel()
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
            publishedDate = parseProductionDate(getPublishedDate()),
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
        val playlistMap = mutableMapOf<String, SearchPlaylist>()
        for (group in this) {
            for (item in (group.mediaItems ?: emptyList()).filterNotNull()) {
                val pid = item.playlistId
                val channelPid = item.channelId?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
                val effectivePid = pid ?: (if (item.type == MediaItem.TYPE_PLAYLIST) channelPid else null)
                if (!effectivePid.isNullOrBlank() && !effectivePid.startsWith("RD")) {
                    if (effectivePid !in playlistMap) {
                        playlistMap[effectivePid] = SearchPlaylist(
                            playlistId = effectivePid,
                            title = item.title.orEmpty(),
                            channelName = item.author.orEmpty(),
                            thumbnailUrl = item.cardImageUrl?.ifBlank { null }
                                ?: item.backgroundImageUrl?.ifBlank { null },
                            firstVideoId = item.videoId?.takeIf { it.isNotBlank() }
                        )
                    } else {
                        val entry = playlistMap[effectivePid]!!
                        val videoId = item.videoId?.takeIf { it.isNotBlank() }
                        if (videoId != null && entry.firstVideoId.isNullOrBlank()) {
                            playlistMap[effectivePid] = entry.copy(firstVideoId = videoId)
                        }
                    }
                }
            }
        }
        val playlists = playlistMap.values.toList()
        Log.d(TAG, "toSearchPlaylists: ${playlists.size} playlists found (excluding RD mixes)")
        return playlists
    }
}

/**
 * Awaits the first emission, returning null instead of throwing when the source
 * errors (e.g. the API returned nothing and RxHelper emitted onError).
 */
private suspend fun <T> io.reactivex.Observable<T>.awaitOrNull(): T? =
    try { awaitFirstOrDefault(null) } catch (_: Exception) { null }

