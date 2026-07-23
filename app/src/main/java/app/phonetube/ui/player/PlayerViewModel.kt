package app.phonetube.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.SponsorSegment
import app.phonetube.core.engine.model.StreamInfo
import app.phonetube.player.PlayerEngineController
import app.phonetube.player.PlayerPlaybackSnapshot
import app.phonetube.player.SponsorBlockService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val sponsorBlockService: SponsorBlockService
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerVM"
    }

    private val videoId: String = savedStateHandle["videoId"]!!

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()

    val playerController = PlayerEngineController(application)

    val playbackState: StateFlow<PlayerPlaybackSnapshot> = playerController.playbackState

    init {
        loadStreamInfo()
        loadSponsorSegments()
        startAutoSkip()
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

    private fun loadSponsorSegments() {
        viewModelScope.launch {
            sponsorBlockService.getSegments(videoId)
                .catch { e ->
                    Log.w(TAG, "Failed to load sponsor segments", e)
                }
                .collect { segments ->
                    Log.d(TAG, "Loaded ${segments.size} sponsor segments")
                    _sponsorSegments.value = segments
                }
        }
    }

    private fun startAutoSkip() {
        viewModelScope.launch {
            while (isActive) {
                val segments = _sponsorSegments.value
                if (segments.isNotEmpty()) {
                    val positionMs = playerController.exoPlayer.currentPosition
                    val skipAction = sponsorBlockService.checkForSkip(positionMs, segments)
                    if (skipAction != null) {
                        Log.d(TAG, "Auto-skipping ${skipAction.segment.category} " +
                            "at ${skipAction.segment.startMs}ms -> ${skipAction.seekToMs}ms")
                        playerController.seekTo(skipAction.seekToMs)
                    }
                }
                delay(SKIP_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun startPlayback(info: StreamInfo) {
        Log.d(TAG, "startPlayback: isUnplayable=${info.isUnplayable}, playabilityReason=${info.playabilityReason}, " +
            "dash=${info.dashManifestUrl != null}, hls=${info.hlsManifestUrl != null}, " +
            "urlFormats=${info.urlFormats.size}")
        when {
            info.isUnplayable -> {
                Log.w(TAG, "Video is unplayable: ${info.playabilityReason}")
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

private const val SKIP_CHECK_INTERVAL_MS = 500L
