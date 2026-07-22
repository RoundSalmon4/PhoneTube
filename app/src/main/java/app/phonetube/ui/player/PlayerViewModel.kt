package app.phonetube.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.StreamInfo
import app.phonetube.player.PlayerEngineController
import app.phonetube.player.PlayerPlaybackSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerVM"
    }

    private val videoId: String = savedStateHandle["videoId"]!!

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playerController = PlayerEngineController(application)

    val playbackState: StateFlow<PlayerPlaybackSnapshot> = playerController.playbackState

    init {
        loadStreamInfo()
    }

    private fun loadStreamInfo() {
        _uiState.value = PlayerUiState.Loading
        viewModelScope.launch {
            engine.getStreamInfo(videoId)
                .catch { e ->
                    Log.e(TAG, "Failed to load stream info", e)
                    _uiState.value = PlayerUiState.Error(e.message ?: "Failed to load video")
                }
                .collect { info ->
                    Log.d(TAG, "Stream info loaded: dash=${info.dashManifestUrl != null}, hls=${info.hlsManifestUrl != null}, urlFormats=${info.urlFormats.size}")
                    _uiState.value = PlayerUiState.Ready(info)
                    startPlayback(info)
                }
        }
    }

    private fun startPlayback(info: StreamInfo) {
        when {
            info.isUnplayable -> {
                _uiState.value = PlayerUiState.Error(info.playabilityReason ?: "Video is unavailable")
            }
            info.dashManifestUrl != null -> {
                playerController.playDash(info.dashManifestUrl)
            }
            info.hlsManifestUrl != null -> {
                playerController.playHls(info.hlsManifestUrl)
            }
            info.urlFormats.isNotEmpty() -> {
                val best = info.urlFormats.firstOrNull { it.url != null }
                if (best != null) {
                    playerController.playUrl(best.url!!, best.mimeType)
                } else {
                    _uiState.value = PlayerUiState.Error("No playable format found")
                }
            }
            else -> {
                _uiState.value = PlayerUiState.Error("No stream URL available")
            }
        }
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekBy(offsetMs: Long) {
        playerController.seekBy(offsetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun retry() {
        loadStreamInfo()
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val streamInfo: StreamInfo) : PlayerUiState
}
