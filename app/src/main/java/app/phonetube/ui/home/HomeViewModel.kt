package app.phonetube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.HomeSection
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
            val feeds = listOf(
                async { engine.getHome().firstOrNull() },
                async { engine.getMusic().firstOrNull() },
                async { engine.getSports().firstOrNull() },
                async { engine.getLive().firstOrNull() },
                async { engine.getNews().firstOrNull() },
                async { engine.getGaming().firstOrNull() },
                async { engine.getKidsHome().firstOrNull() }
            )
            val allSections = feeds.flatMap { it.await()?.sections ?: emptyList() }
            val nonEmpty = allSections.filter { it.videos.isNotEmpty() }
            _uiState.value = if (nonEmpty.isEmpty()) {
                HomeUiState.Empty
            } else {
                HomeUiState.Success(nonEmpty)
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
