package com.roundsalmon4.phonetube.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MiniPlayerState(
    val videoId: String = "",
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L
) {
    val hasActivePlayback: Boolean get() = videoId.isNotEmpty()
}

@Singleton
class PlayerStateManager @Inject constructor() {

    private val _miniPlayerState = MutableStateFlow(MiniPlayerState())
    val miniPlayerState: StateFlow<MiniPlayerState> = _miniPlayerState.asStateFlow()

    fun updateVideoInfo(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String
    ) {
        _miniPlayerState.value = _miniPlayerState.value.copy(
            videoId = videoId,
            title = title,
            channelName = channelName,
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
}
