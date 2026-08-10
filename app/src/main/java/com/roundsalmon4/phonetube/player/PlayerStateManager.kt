package com.roundsalmon4.phonetube.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MiniPlayerState(
    val videoId: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L
) {
    val hasActivePlayback: Boolean get() = videoId.isNotEmpty()
}

@Singleton
class PlayerStateManager @Inject constructor(
    private val playerController: PlayerEngineController
) {

    @Volatile
    var isPlayerScreenVisible: Boolean = false

    private val _miniPlayerState = MutableStateFlow(MiniPlayerState())
    val miniPlayerState: StateFlow<MiniPlayerState> = _miniPlayerState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Keep the mini player state fresh even after the player screen is closed.
        startPlaybackStatePolling()
    }

    fun updateVideoInfo(
        videoId: String,
        title: String,
        thumbnailUrl: String
    ) {
        _miniPlayerState.value = _miniPlayerState.value.copy(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl
        )
    }

    fun updatePlaybackState(isPlaying: Boolean, currentPosition: Long, duration: Long, bufferedPosition: Long = 0L) {
        _miniPlayerState.value = _miniPlayerState.value.copy(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = bufferedPosition
        )
    }

    fun clear() {
        _miniPlayerState.value = MiniPlayerState()
    }

    private fun startPlaybackStatePolling() {
        scope.launch {
            while (isActive) {
                if (_miniPlayerState.value.hasActivePlayback) {
                    _miniPlayerState.value = _miniPlayerState.value.copy(
                        isPlaying = playerController.exoPlayer.isPlaying,
                        currentPosition = playerController.exoPlayer.currentPosition,
                        duration = playerController.exoPlayer.duration,
                        bufferedPosition = playerController.exoPlayer.bufferedPosition
                    )
                }
                delay(MINI_PLAYER_POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val MINI_PLAYER_POLL_INTERVAL_MS = 1_000L
    }
}
