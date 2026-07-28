package com.roundsalmon4.phonetube.player

data class PlayerPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val isLive: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val currentQualityLabel: String = "",
    val isSubtitlesEnabled: Boolean = false,
    val availableSubtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val availableAudioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioTrackIndex: Int = -1,
    val audioTrackCount: Int = 0
)

data class SubtitleTrackInfo(
    val index: Int,
    val languageCode: String,
    val name: String,
    val mimeType: String
)

data class AudioTrackInfo(
    val index: Int,
    val languageCode: String,
    val name: String,
    val mimeType: String
)
