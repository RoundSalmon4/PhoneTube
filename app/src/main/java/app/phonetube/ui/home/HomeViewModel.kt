package app.phonetube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.FeedCacheDao
import app.phonetube.core.database.PlaylistDao
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: YouTubeEngine,
    private val playlistDao: PlaylistDao,
    private val feedCacheDao: FeedCacheDao
) : ViewModel() {

    companion object {
        private const val TAG = "HomeVM"
        private const val CACHE_MAX_AGE_MS = 15 * 60 * 1000L
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

    init {
        loadHomeFromCache()
        loadPlaylists()
    }

    fun loadHome() {
        val isRefresh = _uiState.value is HomeUiState.Success
        if (isRefresh) {
            _isRefreshing.value = true
        }
        loadFromNetwork(isRefresh)
    }

    private fun loadHomeFromCache() {
        viewModelScope.launch {
            try {
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
                    }
                    if (sections.isNotEmpty()) {
                        _uiState.value = HomeUiState.Success(sections)
                        Log.d(TAG, "Loaded ${sections.size} sections from cache")
                        val oldestFetchedAt = feedCacheDao.getOldestFetchedAt()
                        if (oldestFetchedAt != null && System.currentTimeMillis() - oldestFetchedAt > CACHE_MAX_AGE_MS) {
                            Log.d(TAG, "Cache is stale, refreshing in background")
                            loadFromNetwork(isRefresh = false)
                        }
                        return@launch
                    }
                }
                Log.d(TAG, "Cache empty, loading from network")
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
                val homeSections = async { engine.getHome().firstOrNull() }
                val trendingSections = async { engine.getTrending().firstOrNull() }
                val musicSections = async { engine.getMusic().firstOrNull() }
                val sportsSections = async { engine.getSports().firstOrNull() }
                val liveSections = async { engine.getLive().firstOrNull() }
                val newsSections = async { engine.getNews().firstOrNull() }
                val gamingSections = async { engine.getGaming().firstOrNull() }
                val kidsSections = async { engine.getKidsHome().firstOrNull() }

                val homeFeed = homeSections.await()
                val trendingFeed = trendingSections.await()
                val musicFeed = musicSections.await()
                val sportsFeed = sportsSections.await()
                val liveFeed = liveSections.await()
                val newsFeed = newsSections.await()
                val gamingFeed = gamingSections.await()
                val kidsFeed = kidsSections.await()

                Log.d(TAG, "Feeds: home=${homeFeed?.sections?.size ?: 0} trending=${trendingFeed?.sections?.size ?: 0} music=${musicFeed?.sections?.size ?: 0} sports=${sportsFeed?.sections?.size ?: 0} live=${liveFeed?.sections?.size ?: 0} news=${newsFeed?.sections?.size ?: 0} gaming=${gamingFeed?.sections?.size ?: 0} kids=${kidsFeed?.sections?.size ?: 0}")

                val orderedFeeds = listOf(
                    homeFeed, trendingFeed, sportsFeed, gamingFeed, liveFeed, newsFeed, musicFeed, kidsFeed
                )

                val allSections = orderedFeeds.flatMap { it?.sections ?: emptyList() }
                val nonEmpty = allSections.filter { it.videos.isNotEmpty() }
                if (nonEmpty.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(nonEmpty)
                    withContext(NonCancellable) { writeToCache(nonEmpty) }
                    Log.d(TAG, "Loaded ${nonEmpty.size} sections from network, cached")
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

    /**
     * Retry the home feed after video playback.
     * SmartTube BrowsePresenter uses Utils.postDelayed(mRefreshSection, 30_000) —
     * the "default" browseId returns empty until YouTube builds enough history,
     * then starts returning personalized shelves (type 0 groups).
     */
    fun retryHomeAfterPlayback() {
        if (homeRetryJob?.isActive == true) return
        homeRetryJob = viewModelScope.launch {
            Log.d(TAG, "Scheduling home retry in 30s after playback")
            kotlinx.coroutines.delay(30_000L)
            Log.d(TAG, "Retrying home feed after playback")
            loadFromNetwork(isRefresh = true)
        }
    }

    private suspend fun writeToCache(sections: List<HomeSection>) {
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
            playlistDao.insertPlaylist(playlist.copy(videoCount = count + 1))
            _addToPlaylistVideo.value = null
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
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
            playlistDao.insertPlaylist(LocalPlaylist(id = id, name = name, createdAt = System.currentTimeMillis(), videoCount = 1))
            _addToPlaylistVideo.value = null
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
