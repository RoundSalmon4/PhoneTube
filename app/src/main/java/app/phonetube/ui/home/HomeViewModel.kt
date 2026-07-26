package app.phonetube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.FeedCacheDao
import app.phonetube.core.database.PlaylistDao
import app.phonetube.core.datastore.PlayerPreferences
import app.phonetube.core.datastore.PreferencesUiState
import app.phonetube.core.database.entity.CachedFeedSection
import app.phonetube.core.database.entity.CachedFeedVideo
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.PlaylistVideo
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.HomeSection
import app.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
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
            "Kids" to "kids"
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
        if (_uiState.value is HomeUiState.Success || _uiState.value is HomeUiState.Empty) {
            return
        }
        loadFromNetwork(isRefresh = false)
    }

    fun refreshAll() {
        _isRefreshing.value = true
        loadFromNetwork(isRefresh = true)
    }

    fun refreshHomeOnly() {
        if (homeRetryJob?.isActive == true) return
        homeRetryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3_000L)
            try {
                val prefs = playerPreferences.uiState.first()
                val homeSections = engine.getHome().firstOrNull()
                val homeVideos = homeSections?.sections?.filter { it.videos.isNotEmpty() }
                if (!homeVideos.isNullOrEmpty()) {
                    val currentSections = when (val s = _uiState.value) {
                        is HomeUiState.Success -> s.sections
                        else -> emptyList()
                    }
                    val merged = homeVideos + currentSections.filter {
                        it.source != "Home" && isFeedEnabled(it.source, prefs)
                    }
                    _uiState.value = HomeUiState.Success(merged)
                    withContext(NonCancellable) { writeToCache(merged) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Home refresh failed", e)
            }
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
                    if (sections.isNotEmpty()) {
                        _uiState.value = HomeUiState.Success(sections)
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

                val homeFeed = homeSections?.await()
                val trendingFeed = trendingSections?.await()
                val whatToWatchFeed = whatToWatchSections?.await()
                val musicFeed = musicSections?.await()
                val sportsFeed = sportsSections?.await()
                val liveFeed = liveSections?.await()
                val newsFeed = newsSections?.await()
                val gamingFeed = gamingSections?.await()
                val kidsFeed = kidsSections?.await()

                val orderedFeeds = listOfNotNull(
                    homeFeed, whatToWatchFeed, trendingFeed, sportsFeed, gamingFeed, liveFeed, newsFeed, musicFeed, kidsFeed
                )

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
                            position = videoIndex
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
            try {
                val count = playlistDao.getVideoCount(playlist.id)
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = playlist.id,
                        videoId = video.videoId,
                        title = video.title,
                        channelName = video.author,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs,
                        position = count
                    )
                )
                playlistDao.updatePlaylist(playlist.copy(videoCount = count + 1))
                _addToPlaylistVideo.value = null
            } catch (e: Exception) {
                Log.e(TAG, "addToPlaylist failed", e)
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            try {
                val id = playlistDao.insertPlaylist(
                    LocalPlaylist(name = name, createdAt = System.currentTimeMillis())
                )
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = id,
                        videoId = video.videoId,
                        title = video.title,
                        channelName = video.author,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs,
                        position = 0
                    )
                )
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = name, createdAt = System.currentTimeMillis(), videoCount = 1))
                _addToPlaylistVideo.value = null
            } catch (e: Exception) {
                Log.e(TAG, "createPlaylistAndAdd failed", e)
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
    isLive = durationMs == Long.MAX_VALUE,
    isShort = false,
    percentWatched = 0
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
