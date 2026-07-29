package com.roundsalmon4.phonetube.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SearchChannel
import com.roundsalmon4.phonetube.core.engine.model.SearchFilter
import com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YouTubeEngine,
    private val playerPreferences: PlayerPreferences,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    companion object {
        private const val TAG = "SearchVM"
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _addToPlaylistVideo = MutableStateFlow<Video?>(null)
    val addToPlaylistVideo: StateFlow<Video?> = _addToPlaylistVideo.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _subscribedChannels = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannels: StateFlow<Set<String>> = _subscribedChannels.asStateFlow()

    private var allVideos: List<Video> = emptyList()
    private var allChannels: List<SearchChannel> = emptyList()
    private var allPlaylists: List<SearchPlaylist> = emptyList()
    private var suggestionJob: Job? = null

    init {
        loadSubscriptions()
        loadPlaylists()
    }

    private fun loadSubscriptions() {
        viewModelScope.launch {
            subscriptionDao.getAll().collect { subs ->
                _subscribedChannels.value = subs.map { it.channelId }.toSet()
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    fun subscribeToChannel(channelId: String, channelName: String, thumbnailUrl: String?) {
        viewModelScope.launch {
            try {
                subscriptionDao.subscribe(
                    LocalSubscription(
                        channelId = channelId,
                        channelName = channelName,
                        thumbnailUrl = thumbnailUrl ?: "",
                        subscribedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe", e)
            }
        }
    }

    fun unsubscribeFromChannel(channelId: String) {
        viewModelScope.launch {
            try {
                subscriptionDao.unsubscribe(channelId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unsubscribe", e)
            }
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

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            suggestionJob?.cancel()
            return
        }
        if (_uiState.value is SearchUiState.Results || _uiState.value is SearchUiState.Empty) {
            _uiState.value = SearchUiState.Idle
        }
        fetchSuggestions(newQuery)
    }

    fun onSearch() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        suggestionJob?.cancel()
        _suggestions.value = emptyList()
        search(q)
    }

    fun onSuggestionClick(suggestion: String) {
        _query.value = suggestion
        _suggestions.value = emptyList()
        search(suggestion)
    }

    fun onFilterChange(newFilter: SearchFilter) {
        _filter.value = newFilter
        if (allVideos.isNotEmpty() || allChannels.isNotEmpty()) {
            applyFilter()
        } else if (_query.value.isNotBlank()) {
            search(_query.value.trim())
        }
    }

    private fun applyFilter() {
        viewModelScope.launch {
            val prefs = playerPreferences.uiState.first()
            val filteredVideos = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.VIDEOS -> allVideos.take(prefs.videoSearchLimit)
                SearchFilter.CHANNELS, SearchFilter.PLAYLISTS -> emptyList()
            }
            val filteredChannels = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.CHANNELS -> allChannels.take(prefs.channelSearchLimit)
                SearchFilter.VIDEOS, SearchFilter.PLAYLISTS -> emptyList()
            }
            val filteredPlaylists = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.PLAYLISTS -> allPlaylists.take(prefs.playlistSearchLimit)
                else -> emptyList()
            }

            if (filteredVideos.isEmpty() && filteredChannels.isEmpty() && filteredPlaylists.isEmpty()) {
                _uiState.value = SearchUiState.Empty
            } else {
                _uiState.value = SearchUiState.Results(
                    videos = filteredVideos,
                    channels = filteredChannels,
                    playlists = filteredPlaylists
                )
            }
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            engine.getSearchSuggestions(query)
                .catch { /* ignore suggestion errors */ }
                .firstOrNull()
                ?.let { results ->
                    if (_query.value == query) {
                        _suggestions.value = results
                    }
                }
        }
    }

    fun fetchChannelIdForVideo(videoId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            engine.getMetadata(videoId)
                .catch { /* ignore */ }
                .firstOrNull()
                ?.let { onResult(it.video.channelId) }
        }
    }

    fun getPlaylistFirstVideoId(playlistId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val videoId = engine.getPlaylistFirstVideoId(playlistId)
                if (videoId != null) onResult(videoId)
            } catch (_: Exception) { }
        }
    }

    fun savePlaylistAsLocal(playlist: SearchPlaylist) {
        Log.d(TAG, "savePlaylistAsLocal: saving playlist '${playlist.title}' (${playlist.playlistId})")
        if (_saveMessage.value != null) return // prevent double-tap
        _saveMessage.value = "Saving..."
        viewModelScope.launch {
            try {
                val videos = engine.getPlaylistVideos(playlist.playlistId)
                Log.d(TAG, "savePlaylistAsLocal: got ${videos.size} videos from API")
                if (videos.isEmpty()) {
                    Log.w(TAG, "savePlaylistAsLocal: no videos returned, aborting")
                    _saveMessage.value = "Could not save playlist"
                    return@launch
                }
                val id = playlistDao.insertPlaylist(
                    LocalPlaylist(name = playlist.title, createdAt = System.currentTimeMillis())
                )
                Log.d(TAG, "savePlaylistAsLocal: created playlist id=$id")
                for ((index, video) in videos.withIndex()) {
                    playlistDao.insertVideo(
                        PlaylistVideo(
                            playlistId = id,
                            videoId = video.videoId,
                            title = video.title,
                            channelName = video.author,
                            thumbnailUrl = video.thumbnailUrl,
                            durationMs = video.durationMs,
                            position = index
                        )
                    )
                }
                playlistDao.updatePlaylist(
                    LocalPlaylist(id = id, name = playlist.title, createdAt = System.currentTimeMillis(), videoCount = videos.size)
                )
                Log.d(TAG, "savePlaylistAsLocal: saved ${videos.size} videos")
                _saveMessage.value = "Playlist saved"
            } catch (e: Exception) {
                Log.e(TAG, "savePlaylistAsLocal failed", e)
                _saveMessage.value = "Save failed"
            }
        }
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    private fun search(query: String) {
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            engine.search(query)
                .catch { e ->
                    _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
                }
                .firstOrNull()
                ?.let { result ->
                    allVideos = result.sections.flatMap { it.videos }.distinctBy { it.videoId }
                    allChannels = result.channels
                    allPlaylists = result.playlists
                    applyFilter()
                }
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Results(
        val videos: List<Video>,
        val channels: List<SearchChannel>,
        val playlists: List<SearchPlaylist> = emptyList()
    ) : SearchUiState
}

private const val SUGGESTION_DEBOUNCE_MS = 300L
