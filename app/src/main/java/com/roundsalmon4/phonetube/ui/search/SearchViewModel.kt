package com.roundsalmon4.phonetube.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.InvidiousDao
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.toPlaylistVideoInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YouTubeEngine,
    private val playerPreferences: PlayerPreferences,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val invidiousDao: InvidiousDao
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

    private val _savedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPlaylistIds: StateFlow<Set<String>> = _savedPlaylistIds.asStateFlow()

    private val _pendingSavePlaylist = MutableStateFlow<SearchPlaylist?>(null)
    val pendingSavePlaylist: StateFlow<SearchPlaylist?> = _pendingSavePlaylist.asStateFlow()

    private var allVideos: List<Video> = emptyList()
    private var allChannels: List<SearchChannel> = emptyList()
    private var allPlaylists: List<SearchPlaylist> = emptyList()
    private var allInvidiousVideos: List<Video> = emptyList()
    private var suggestionJob: Job? = null

    init {
        loadSubscriptions()
        loadPlaylists()
        loadSavedPlaylistIds()
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

    private fun loadSavedPlaylistIds() {
        viewModelScope.launch {
            playlistDao.getSavedPlaylistIds().collect { ids ->
                _savedPlaylistIds.value = ids.map { it.removePrefix("VL") }.toSet()
            }
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
                SearchFilter.INVIDIOUS -> emptyList()
                SearchFilter.CHANNELS, SearchFilter.PLAYLISTS -> emptyList()
            }
            val filteredChannels = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.CHANNELS -> allChannels.take(prefs.channelSearchLimit)
                SearchFilter.VIDEOS, SearchFilter.PLAYLISTS, SearchFilter.INVIDIOUS -> emptyList()
            }
            val filteredPlaylists = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.PLAYLISTS -> allPlaylists.take(prefs.playlistSearchLimit)
                else -> emptyList()
            }
            val filteredInvidious = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.VIDEOS, SearchFilter.INVIDIOUS -> allInvidiousVideos
                SearchFilter.CHANNELS, SearchFilter.PLAYLISTS -> emptyList()
            }

            if (filteredVideos.isEmpty() && filteredChannels.isEmpty() && filteredPlaylists.isEmpty() && filteredInvidious.isEmpty()) {
                _uiState.value = SearchUiState.Empty
            } else {
                _uiState.value = SearchUiState.Results(
                    videos = filteredVideos,
                    channels = filteredChannels,
                    playlists = filteredPlaylists,
                    invidiousVideos = filteredInvidious
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

    fun savePlaylistAsLocal(playlist: SearchPlaylist) {
        Log.d(TAG, "savePlaylistAsLocal: saving playlist '${playlist.title}' (${playlist.playlistId})")
        if (_saveMessage.value != null) return
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
                    LocalPlaylist(name = playlist.title, createdAt = System.currentTimeMillis(), sourcePlaylistId = playlist.playlistId.removePrefix("VL"))
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
                    LocalPlaylist(id = id, name = playlist.title, createdAt = System.currentTimeMillis(), videoCount = videos.size, sourcePlaylistId = playlist.playlistId.removePrefix("VL"))
                )
                Log.d(TAG, "savePlaylistAsLocal: saved ${videos.size} videos")
                _saveMessage.value = "Playlist saved"
            } catch (e: Exception) {
                Log.e(TAG, "savePlaylistAsLocal failed", e)
                _saveMessage.value = "Save failed"
            }
        }
    }

    fun onSavePlaylist(playlist: SearchPlaylist) {
        viewModelScope.launch {
            val prefs = playerPreferences.uiState.first()
            val isSaved = playlist.playlistId.removePrefix("VL") in _savedPlaylistIds.value
            if (isSaved && prefs.duplicatePlaylistWarning) {
                _pendingSavePlaylist.value = playlist
            } else {
                savePlaylistAsLocal(playlist)
            }
        }
    }

    fun confirmSaveDuplicate() {
        val playlist = _pendingSavePlaylist.value ?: return
        _pendingSavePlaylist.value = null
        savePlaylistAsLocal(playlist)
    }

    fun dismissSaveDuplicate() {
        _pendingSavePlaylist.value = null
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    private fun search(query: String) {
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            allVideos = emptyList()
            allChannels = emptyList()
            allPlaylists = emptyList()
            allInvidiousVideos = emptyList()

            var youtubeError: String? = null

            engine.search(query)
                .catch { e ->
                    youtubeError = e.message ?: "Search failed"
                }
                .firstOrNull()
                ?.let { result ->
                    allVideos = result.sections.flatMap { it.videos }.distinctBy { it.videoId }
                    allChannels = result.channels
                    allPlaylists = result.playlists
                }

            val invidiousResults = try {
                val instances = withContext(Dispatchers.IO) {
                    invidiousDao.getEnabledSync()
                }
                if (instances.isNotEmpty()) {
                    instances.map { instance ->
                        async {
                            engine.getInvidiousSearchResults(query, instance.host)
                        }
                    }.awaitAll().flatten().distinctBy { it.videoId }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invidious search failed", e)
                emptyList()
            }
            allInvidiousVideos = invidiousResults

            if (youtubeError != null && allVideos.isEmpty() && allInvidiousVideos.isEmpty()) {
                _uiState.value = SearchUiState.Error(youtubeError!!)
            } else {
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
        val playlists: List<SearchPlaylist> = emptyList(),
        val invidiousVideos: List<Video> = emptyList()
    ) : SearchUiState
}

private const val SUGGESTION_DEBOUNCE_MS = 500L
