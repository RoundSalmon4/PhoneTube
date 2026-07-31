package com.roundsalmon4.phonetube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.FeedCacheDao
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
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "HomeVM"
        private const val CACHE_MAX_AGE_MS = 15 * 60 * 1000L

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
            "Subscriptions" to "subscriptions"
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
                val newFeeds = mutableListOf<kotlinx.coroutines.Deferred<com.roundsalmon4.phonetube.core.engine.model.HomeFeed?>>()
                if (prefs.feedTrending && "Trending" !in currentSources) newFeeds.add(async { engine.getTrending().firstOrNull() })
                if (prefs.feedWhatToWatch && "What to Watch" !in currentSources) newFeeds.add(async { engine.getWhatToWatch().firstOrNull() })
                if (prefs.feedSports && "Sports" !in currentSources) newFeeds.add(async { engine.getSports().firstOrNull() })
                if (prefs.feedGaming && "Gaming" !in currentSources) newFeeds.add(async { engine.getGaming().firstOrNull() })
                if (prefs.feedLive && "Live" !in currentSources) newFeeds.add(async { engine.getLive().firstOrNull() })
                if (prefs.feedNews && "News" !in currentSources) newFeeds.add(async { engine.getNews().firstOrNull() })
                if (prefs.feedMusic && "Music" !in currentSources) newFeeds.add(async { engine.getMusic().firstOrNull() })
                if (prefs.feedKids && "Kids" !in currentSources) newFeeds.add(async { engine.getKidsHome().firstOrNull() })
                if (prefs.feedSubscriptions && "Subscriptions" !in currentSources) newFeeds.add(async { fetchSubscriptionsFeed() })

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
            val allVideos = mutableListOf<Video>()
            for ((index, sub) in subscriptions.take(10).withIndex()) {
                try {
                    if (index > 0) delay(500)
                    val result = engine.getChannel(sub.channelId).firstOrNull()
                    val videos = result?.sections?.flatMap { it.videos }?.take(5) ?: emptyList()
                    allVideos.addAll(videos)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch channel ${sub.channelId}: ${e.message?.take(60)}")
                }
            }
            if (allVideos.isEmpty()) null
            else com.roundsalmon4.phonetube.core.engine.model.HomeFeed(
                sections = listOf(com.roundsalmon4.phonetube.core.engine.model.HomeSection(
                    title = "Subscriptions",
                    videos = allVideos.distinctBy { it.videoId }.take(20),
                    source = "Subscriptions"
                ))
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubscriptionsFeed failed", e)
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
                    val sourceToKey = mapOf(
                        "Home" to "home", "What to Watch" to "what_to_watch", "Subscriptions" to "subscriptions",
                        "Trending" to "trending", "Music" to "music", "Sports" to "sports",
                        "Live" to "live", "News" to "news", "Gaming" to "gaming", "Kids" to "kids"
                    )
                    val sectionMap = sections.associateBy { it.source }
                    val reordered = prefs.feedOrder.mapNotNull { key ->
                        sourceToKey.entries.firstOrNull { it.value == key }?.key?.let { source ->
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
        viewModelScope.launch {
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

                val feedSourceMap = mapOf(
                    "home" to homeFeed,
                    "what_to_watch" to whatToWatchFeed,
                    "subscriptions" to subscriptionsFeed,
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
                } else if (_uiState.value is HomeUiState.Loading) {
                    _uiState.value = HomeUiState.Empty
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
                            viewCount = video.viewCount ?: "",
                            position = videoIndex,
                            percentWatched = video.percentWatched
                        )
                    }
                }
                feedCacheDao.insertVideos(dbVideos)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write feed cache", e)
            }
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
    viewCount = viewCount.ifBlank { null },
    publishedDate = 0L,
    percentWatched = percentWatched
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
