package com.roundsalmon4.phonetube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.FeedCacheDao
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.InvidiousDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.PlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.toPlaylistVideoInfo
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import com.roundsalmon4.phonetube.core.database.entity.CachedFeedSection
import com.roundsalmon4.phonetube.core.database.entity.CachedFeedVideo
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.HomeFeed
import com.roundsalmon4.phonetube.core.engine.model.HomeSection
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: YouTubeEngine,
    private val playlistDao: PlaylistDao,
    private val feedCacheDao: FeedCacheDao,
    private val subscriptionDao: SubscriptionDao,
    private val playerPreferences: PlayerPreferences,
    private val historyDao: HistoryDao,
    private val invidiousDao: InvidiousDao
) : ViewModel() {

    companion object {
        private const val TAG = "HomeVM"
        private const val CACHE_MAX_AGE_MS = 15 * 60 * 1000L
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

        private val SOURCE_TO_FEED_KEY = mapOf(
            "Home" to "home",
            "What to Watch" to "what_to_watch",
            "Trending" to "trending",
            "Music" to "music",
            "Sports" to "sports",
            "Live" to "live",
            "News" to "news",
            "Gaming" to "gaming",
            "Kids" to "kids",
            "Subscriptions" to "subscriptions",
            "Invidious" to "invidious"
        )

        private fun isFeedEnabled(source: String, prefs: PreferencesUiState): Boolean {
            return when (SOURCE_TO_FEED_KEY[source]) {
                "home" -> prefs.feedHome
                "what_to_watch" -> prefs.feedWhatToWatch
                "trending" -> prefs.feedTrending
                "music" -> prefs.feedMusic
                "sports" -> prefs.feedSports
                "live" -> prefs.feedLive
                "news" -> prefs.feedNews
                "gaming" -> prefs.feedGaming
                "kids" -> prefs.feedKids
                "subscriptions" -> prefs.feedSubscriptions
                "invidious" -> prefs.feedInvidious
                else -> true
            }
        }
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _addToPlaylistVideo = MutableStateFlow<Video?>(null)
    val addToPlaylistVideo: StateFlow<Video?> = _addToPlaylistVideo.asStateFlow()

    private var homeRetryJob: Job? = null
    private var loadNetworkJob: Job? = null
    private var lastRefreshAt = 0L
    private val cacheMutex = Mutex()

    init {
        loadHomeFromCache()
        loadPlaylists()
    }

    fun loadHome() {
        // Always re-check cache with current feed preferences
        loadHomeFromCache()
    }

    fun refreshAll() {
        _isRefreshing.value = true
        loadFromNetwork(isRefresh = true)
    }

    fun refreshHomeOnly() {
        if (homeRetryJob?.isActive == true) return
        homeRetryJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                delay(3_000L)
                val prefs = playerPreferences.uiState.first()
                val currentSections = when (val s = _uiState.value) {
                    is HomeUiState.Success -> s.sections
                    else -> emptyList()
                }
                val enabledSections = currentSections.filter { isFeedEnabled(it.source, prefs) }
                val homeVideos = if (prefs.feedHome) {
                    engine.getHome().firstOrNull()?.sections?.filter { it.videos.isNotEmpty() }
                } else null

                val currentSources = enabledSections.map { it.source }.toSet()

                // Refresh a feed when it's missing, carries no publish dates yet, or the last
                // refresh happened a while ago.
                val forceRefresh = System.currentTimeMillis() - lastRefreshAt >= REFRESH_INTERVAL_MS
                fun shouldRefresh(source: String): Boolean {
                    if (source !in currentSources) return true
                    if (forceRefresh) return true
                    val section = enabledSections.firstOrNull { it.source == source }
                    val hasAnyDate = section?.videos?.any { it.publishedDate > 0 } ?: false
                    return !hasAnyDate
                }

                val newFeeds = mutableListOf<kotlinx.coroutines.Deferred<com.roundsalmon4.phonetube.core.engine.model.HomeFeed?>>()
                if (prefs.feedTrending && shouldRefresh("Trending")) newFeeds.add(async { engine.getTrending().firstOrNull() })
                if (prefs.feedWhatToWatch && shouldRefresh("What to Watch")) newFeeds.add(async { engine.getWhatToWatch().firstOrNull() })
                if (prefs.feedSports && shouldRefresh("Sports")) newFeeds.add(async { engine.getSports().firstOrNull() })
                if (prefs.feedGaming && shouldRefresh("Gaming")) newFeeds.add(async { engine.getGaming().firstOrNull() })
                if (prefs.feedLive && shouldRefresh("Live")) newFeeds.add(async { engine.getLive().firstOrNull() })
                if (prefs.feedNews && shouldRefresh("News")) newFeeds.add(async { engine.getNews().firstOrNull() })
                if (prefs.feedMusic && shouldRefresh("Music")) newFeeds.add(async { engine.getMusic().firstOrNull() })
                if (prefs.feedKids && shouldRefresh("Kids")) newFeeds.add(async { engine.getKidsHome().firstOrNull() })
                if (prefs.feedSubscriptions) newFeeds.add(async { fetchSubscriptionsFeed() })
                if (prefs.feedInvidious) newFeeds.add(async { fetchInvidiousFeed() })

                val newSections = newFeeds.awaitAll().flatMap { feed ->
                    feed?.sections?.filter { it.videos.isNotEmpty() } ?: emptyList()
                }

                val allSections = (homeVideos ?: emptyList()) + enabledSections + newSections
                val sectionMap = allSections.associateBy { it.source }
                val ordered = prefs.feedOrder.mapNotNull { key ->
                    SOURCE_TO_FEED_KEY.entries.firstOrNull { it.value == key }?.key?.let { source ->
                        sectionMap[source]
                    }
                }
                val leftover = allSections.filter { it.source !in ordered.map { o -> o.source } }
                val merged = ordered + leftover

                if (merged.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(merged)
                    withContext(NonCancellable) { writeToCache(merged) }
                } else {
                    _uiState.value = HomeUiState.Empty
                }
                lastRefreshAt = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "Home refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchSubscriptionsFeed(): com.roundsalmon4.phonetube.core.engine.model.HomeFeed? {
        return try {
            val subscriptions = subscriptionDao.getAll().first()
            if (subscriptions.isEmpty()) return null
            val channelIds = subscriptions.take(10).map { it.channelId }
            val videos = engine.getRssFeedVideos(channelIds)
            Log.d(TAG, "fetchSubscriptionsFeed: got ${videos.size} videos from ${channelIds.size} channels")
            if (videos.isEmpty()) null
            else com.roundsalmon4.phonetube.core.engine.model.HomeFeed(
                sections = listOf(com.roundsalmon4.phonetube.core.engine.model.HomeSection(
                    title = "Subscriptions",
                    videos = videos.distinctBy { it.videoId }.take(20),
                    source = "Subscriptions"
                ))
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubscriptionsFeed failed", e)
            null
        }
    }

    private suspend fun fetchInvidiousFeed(): com.roundsalmon4.phonetube.core.engine.model.HomeFeed? {
        return try {
            val instances = kotlinx.coroutines.withContext(Dispatchers.IO) {
                invidiousDao.getEnabledSync()
            }
            if (instances.isEmpty()) return null
            val instance = instances.first()
            val json = kotlinx.coroutines.withContext(Dispatchers.IO) {
                java.net.URL("https://${instance.host}/api/v1/trending").readText()
            }
            val array = org.json.JSONArray(json)
            val videos = (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val vidId = obj.optString("videoId", "")
                if (vidId.isBlank()) null
                else com.roundsalmon4.phonetube.core.engine.model.Video(
                    videoId = vidId,
                    title = obj.optString("title", ""),
                    author = obj.optString("author", ""),
                    channelId = obj.optString("authorId", ""),
                    thumbnailUrl = "https://${instance.host}${obj.optString("thumbnailPath", "")}",
                    durationMs = (obj.optLong("lengthSeconds", 0L)) * 1000,
                    publishedDate = 0L,
                    viewCount = null,
                    percentWatched = 0
                )
            }.take(20)
            if (videos.isNullOrEmpty()) null
            else com.roundsalmon4.phonetube.core.engine.model.HomeFeed(
                sections = listOf(com.roundsalmon4.phonetube.core.engine.model.HomeSection(
                    title = "Invidious",
                    videos = videos,
                    source = "Invidious"
                ))
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchInvidiousFeed failed", e)
            null
        }
    }

    private fun loadHomeFromCache() {
        viewModelScope.launch {
            try {
                val prefs = playerPreferences.uiState.first()
                val cachedSections = feedCacheDao.getAllSections().firstOrNull()
                if (!cachedSections.isNullOrEmpty()) {
                    val sections = cachedSections.mapNotNull { cached ->
                        if (cached.videos.isNotEmpty()) {
                            HomeSection(
                                title = cached.section.title,
                                videos = cached.videos.sortedBy { it.position }.map { it.toVideo() },
                                source = cached.section.source
                            )
                        } else null
                    }.filter { isFeedEnabled(it.source, prefs) }
                    // Reorder sections to match user's feed order
                    val sectionMap = sections.associateBy { it.source }
                    val reordered = prefs.feedOrder.mapNotNull { key ->
                        SOURCE_TO_FEED_KEY.entries.firstOrNull { it.value == key }?.key?.let { source ->
                            sectionMap[source]
                        }
                    }
                    if (reordered.isNotEmpty()) {
                        _uiState.value = HomeUiState.Success(reordered)
                        val oldestFetchedAt = feedCacheDao.getOldestFetchedAt()
                        if (oldestFetchedAt != null && System.currentTimeMillis() - oldestFetchedAt > CACHE_MAX_AGE_MS) {
                            loadFromNetwork(isRefresh = false)
                        }
                        return@launch
                    }
                }
                loadFromNetwork(isRefresh = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cache", e)
                loadFromNetwork(isRefresh = false)
            }
        }
    }

    private fun loadFromNetwork(isRefresh: Boolean) {
        if (loadNetworkJob?.isActive == true && !isRefresh) return
        loadNetworkJob = viewModelScope.launch {
            try {
                val prefs = playerPreferences.uiState.first()

                val homeSections = if (prefs.feedHome) async { engine.getHome().firstOrNull() } else null
                val trendingSections = if (prefs.feedTrending) async { engine.getTrending().firstOrNull() } else null
                val whatToWatchSections = if (prefs.feedWhatToWatch) async { engine.getWhatToWatch().firstOrNull() } else null
                val musicSections = if (prefs.feedMusic) async { engine.getMusic().firstOrNull() } else null
                val sportsSections = if (prefs.feedSports) async { engine.getSports().firstOrNull() } else null
                val liveSections = if (prefs.feedLive) async { engine.getLive().firstOrNull() } else null
                val newsSections = if (prefs.feedNews) async { engine.getNews().firstOrNull() } else null
                val gamingSections = if (prefs.feedGaming) async { engine.getGaming().firstOrNull() } else null
                val kidsSections = if (prefs.feedKids) async { engine.getKidsHome().firstOrNull() } else null
                val subscriptionsSection = if (prefs.feedSubscriptions) async { fetchSubscriptionsFeed() } else null
                val invidiousSection = if (prefs.feedInvidious) async { fetchInvidiousFeed() } else null

                val homeFeed = homeSections?.await()
                val trendingFeed = trendingSections?.await()
                val whatToWatchFeed = whatToWatchSections?.await()
                val musicFeed = musicSections?.await()
                val sportsFeed = sportsSections?.await()
                val liveFeed = liveSections?.await()
                val newsFeed = newsSections?.await()
                val gamingFeed = gamingSections?.await()
                val kidsFeed = kidsSections?.await()
                val subscriptionsFeed = subscriptionsSection?.await()
                val invidiousFeed = invidiousSection?.await()

                val feedSourceMap = mapOf(
                    "home" to homeFeed,
                    "what_to_watch" to whatToWatchFeed,
                    "subscriptions" to subscriptionsFeed,
                    "invidious" to invidiousFeed,
                    "trending" to trendingFeed,
                    "music" to musicFeed,
                    "sports" to sportsFeed,
                    "live" to liveFeed,
                    "news" to newsFeed,
                    "gaming" to gamingFeed,
                    "kids" to kidsFeed
                )

                val orderedFeeds = prefs.feedOrder.mapNotNull { key ->
                    feedSourceMap[key]
                }

                val allSections = orderedFeeds.flatMap { it.sections }
                val nonEmpty = allSections.filter { it.videos.isNotEmpty() }
                if (nonEmpty.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(nonEmpty)
                    withContext(NonCancellable) { writeToCache(nonEmpty) }
                } else {
                    // When the API returns nothing (e.g. no watch history to seed
                    // recommendations yet), populate from local watch history so the
                    // user gets immediate content.
                    val historyFeed = buildHistoryFallback()
                    if (historyFeed != null && historyFeed.sections.isNotEmpty()) {
                        _uiState.value = HomeUiState.Success(historyFeed.sections)
                        withContext(NonCancellable) { writeToCache(historyFeed.sections) }
                    } else if (_uiState.value is HomeUiState.Loading) {
                        _uiState.value = HomeUiState.Empty
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadHome failed", e)
                if (_uiState.value is HomeUiState.Loading) {
                    _uiState.value = HomeUiState.Error(e.message ?: "Failed to load")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun writeToCache(sections: List<HomeSection>) {
        cacheMutex.withLock {
            try {
                feedCacheDao.clearAllVideos()
                feedCacheDao.clearAllSections()
                val now = System.currentTimeMillis()
                val dbSections = sections.map { section ->
                    CachedFeedSection(
                        source = section.source,
                        title = section.title,
                        fetchedAt = now
                    )
                }
                val sectionIds = feedCacheDao.insertSections(dbSections)
                val dbVideos = sections.flatMapIndexed { sectionIndex, section ->
                    val sectionId = sectionIds[sectionIndex]
                    section.videos.mapIndexed { videoIndex, video ->
                        CachedFeedVideo(
                            sectionId = sectionId,
                            videoId = video.videoId,
                            title = video.title,
                            author = video.author,
                            channelId = video.channelId,
                            thumbnailUrl = video.thumbnailUrl,
                            durationMs = video.durationMs,
                            viewCount = "",
                            position = videoIndex,
                            percentWatched = video.percentWatched,
                            publishedDate = video.publishedDate
                        )
                    }
                }
                feedCacheDao.insertVideos(dbVideos)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write feed cache", e)
            }
        }
    }

    /**
     * When the YouTube API returns an empty home feed (no watch history to
     * seed recommendations), fall back to a section built from the local
     * watch-history table so the user has immediate content.
     */
    private suspend fun buildHistoryFallback(): com.roundsalmon4.phonetube.core.engine.model.HomeFeed? {
        return try {
            val entries: List<WatchHistoryEntry> = historyDao.getAll().first()
                .filter { it.title.isNotBlank() }
                .take(20)
            if (entries.isEmpty()) return null
            HomeFeed(sections = listOf(
                HomeSection(
                    title = "Watch History",
                    videos = entries.map { entry: WatchHistoryEntry ->
                        Video(
                            videoId = entry.videoId,
                            title = entry.title,
                            author = entry.channelName,
                            channelId = entry.channelId,
                            thumbnailUrl = entry.thumbnailUrl,
                            durationMs = entry.durationMs,
                            viewCount = null,
                            publishedDate = entry.timestamp,
                            percentWatched = if (entry.durationMs > 0) ((entry.positionMs * 100f) / entry.durationMs).toInt().coerceIn(0, 100) else 0
                        )
                    },
                    source = "Watch History"
                )
            ))
        } catch (_: Exception) {
            null
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    fun showAddToPlaylistDialog(video: Video) {
        _addToPlaylistVideo.value = video
    }

    fun dismissAddToPlaylistDialog() {
        _addToPlaylistVideo.value = null
    }

    fun fetchChannelIdForVideo(videoId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            engine.getMetadata(videoId)
                .catch { /* ignore */ }
                .firstOrNull()
                ?.let { onResult(it.video.channelId) }
        }
    }

    fun addToPlaylist(playlist: LocalPlaylist) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            if (PlaylistSaver.addToPlaylist(playlistDao, video.toPlaylistVideoInfo(), playlist)) {
                _addToPlaylistVideo.value = null
            } else {
                Log.e(TAG, "addToPlaylist failed")
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            if (PlaylistSaver.createAndAdd(playlistDao, video.toPlaylistVideoInfo(), name)) {
                _addToPlaylistVideo.value = null
            } else {
                Log.e(TAG, "createPlaylistAndAdd failed")
            }
        }
    }
}

private fun CachedFeedVideo.toVideo() = Video(
    videoId = videoId,
    title = title,
    author = author,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    viewCount = null,
    publishedDate = publishedDate,
    percentWatched = percentWatched
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
