package app.phonetube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YouTubeEngine
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            suggestionJob?.cancel()
            return
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

    fun clearResults() {
        _uiState.value = SearchUiState.Idle
        _suggestions.value = emptyList()
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

    private fun search(query: String) {
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            engine.search(query)
                .catch { e ->
                    _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
                }
                .firstOrNull()
                ?.let { result ->
                    val videos = result.sections.flatMap { it.videos }.distinctBy { it.videoId }
                    if (videos.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                    } else {
                        _uiState.value = SearchUiState.Results(videos)
                    }
                }
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Results(val videos: List<Video>) : SearchUiState
}

private const val SUGGESTION_DEBOUNCE_MS = 300L
