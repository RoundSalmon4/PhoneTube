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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadHome()
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
                    musicSections.await(),
                    gamingSections.await(),
                    newsSections.await(),
                    sportsSections.await(),
                    liveSections.await(),
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
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
}
