package app.phonetube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.HomeFeed
import app.phonetube.core.engine.model.HomeSection
import app.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: YouTubeEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            engine.getMusic()
                .catch { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
                .collect { feed ->
                    val nonEmpty = feed.sections.filter { it.videos.isNotEmpty() }
                    _uiState.value = if (nonEmpty.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(nonEmpty)
                    }
                }
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
