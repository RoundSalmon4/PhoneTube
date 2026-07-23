package app.phonetube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.PlaylistDao
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.PlaylistVideo
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.HomeSection
import app.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: YouTubeEngine,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _addToPlaylistVideo = MutableStateFlow<Video?>(null)
    val addToPlaylistVideo: StateFlow<Video?> = _addToPlaylistVideo.asStateFlow()

    init {
        loadHome()
        loadPlaylists()
    }

    fun loadHome() {
        val isInitialLoad = _uiState.value is HomeUiState.Loading
        if (!isInitialLoad) _isRefreshing.value = true

        viewModelScope.launch {
            try {
                val homeSections = async { engine.getHome().firstOrNull() }
                val musicSections = async { engine.getMusic().firstOrNull() }
                val sportsSections = async { engine.getSports().firstOrNull() }
                val liveSections = async { engine.getLive().firstOrNull() }
                val newsSections = async { engine.getNews().firstOrNull() }
                val gamingSections = async { engine.getGaming().firstOrNull() }
                val kidsSections = async { engine.getKidsHome().firstOrNull() }

                // Home feed first, then categories in a fixed order
                val orderedFeeds = listOf(
                    homeSections.await(),
                    sportsSections.await(),
                    gamingSections.await(),
                    liveSections.await(),
                    newsSections.await(),
                    musicSections.await(),
                    kidsSections.await()
                )

                val allSections = orderedFeeds.flatMap { it?.sections ?: emptyList() }
                val nonEmpty = allSections.filter { it.videos.isNotEmpty() }
                _uiState.value = if (nonEmpty.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(nonEmpty)
                }
            } catch (e: Exception) {
                if (_uiState.value is HomeUiState.Loading) {
                    _uiState.value = HomeUiState.Error(e.message ?: "Failed to load")
                }
            } finally {
                _isRefreshing.value = false
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

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
