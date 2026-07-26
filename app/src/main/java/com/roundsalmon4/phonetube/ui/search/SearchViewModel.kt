package com.roundsalmon4.phonetube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SearchChannel
import com.roundsalmon4.phonetube.core.engine.model.SearchFilter
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
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private var allVideos: List<Video> = emptyList()
    private var allChannels: List<SearchChannel> = emptyList()
    private var suggestionJob: Job? = null

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
                SearchFilter.CHANNELS -> emptyList()
            }
            val filteredChannels = when (_filter.value) {
                SearchFilter.ALL, SearchFilter.CHANNELS -> allChannels.take(prefs.channelSearchLimit)
                SearchFilter.VIDEOS -> emptyList()
            }

            if (filteredVideos.isEmpty() && filteredChannels.isEmpty()) {
                _uiState.value = SearchUiState.Empty
            } else {
                _uiState.value = SearchUiState.Results(
                    videos = filteredVideos,
                    channels = filteredChannels
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
        val channels: List<SearchChannel>
    ) : SearchUiState
}

private const val SUGGESTION_DEBOUNCE_MS = 300L
