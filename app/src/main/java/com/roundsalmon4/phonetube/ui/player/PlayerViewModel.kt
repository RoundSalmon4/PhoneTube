package com.roundsalmon4.phonetube.ui.player

import android.app.Application
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.core.engine.model.StreamInfo
import com.roundsalmon4.phonetube.player.PlayerEngineController
import com.roundsalmon4.phonetube.player.PlayerPlaybackSnapshot
import com.roundsalmon4.phonetube.player.PlayerStateManager
import com.roundsalmon4.phonetube.player.SponsorBlockService
import com.roundsalmon4.phonetube.player.SubtitleTrackInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val sponsorBlockService: SponsorBlockService,
    private val playerPreferences: PlayerPreferences,
    private val historyDao: HistoryDao,
    val playerController: PlayerEngineController,
    private val playerStateManager: PlayerStateManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerVM"
    }

    private val videoId: String = savedStateHandle["videoId"]!!

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()

    private val _showSpeedPicker = MutableStateFlow(false)
    val showSpeedPicker: StateFlow<Boolean> = _showSpeedPicker.asStateFlow()

    private val _showQualityPicker = MutableStateFlow(false)
    val showQualityPicker: StateFlow<Boolean> = _showQualityPicker.asStateFlow()

    private val _showSubtitlePicker = MutableStateFlow(false)
    val showSubtitlePicker: StateFlow<Boolean> = _showSubtitlePicker.asStateFlow()

    private val _landscapeLock = MutableStateFlow(false)
    val landscapeLock: StateFlow<Boolean> = _landscapeLock.asStateFlow()

    val playbackState: StateFlow<PlayerPlaybackSnapshot> = playerController.playbackState

    init {
        loadStreamInfo()
        loadSponsorSegments()
        startAutoSkip()
        restoreSpeedPreference()
        loadLandscapeLockPreference()
        startPeriodicHistorySave()
        setupPhoneStateListener()
    }

    @Suppress("DEPRECATION")
    private fun setupPhoneStateListener() {
        val telephonyManager = getApplication<Application>()
            .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        @Suppress("MissingPermission")
        telephonyManager.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING,
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (playerController.exoPlayer.isPlaying) {
                            playerController.togglePlayPause()
                        }
                    }
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
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
                    Log.d(TAG, "Stream info loaded: dash=${info.dashManifestUrl != null}, hls=${info.hlsManifestUrl != null}, " +
                        "urlFormats=${info.urlFormats.size}, isLive=${info.isLive}, isLiveContent=${info.isLiveContent}")
                    _uiState.value = PlayerUiState.Ready(info)
                    playerStateManager.updateVideoInfo(
                        videoId = videoId,
                        title = info.title,
                        channelName = info.author,
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    )
                    startPlayback(info)
                    if (!info.isLive && !info.isLiveContent) {
                        recordToHistory(info)
                    }
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
                val streamInfo = (uiState.value as? PlayerUiState.Ready)?.streamInfo
                if (streamInfo != null && !streamInfo.isLive && !streamInfo.isLiveContent) {
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
                }
                delay(SKIP_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun startPlayback(info: StreamInfo) {
        val isLive = info.isLive || info.isLiveContent
        Log.d(TAG, "startPlayback: isUnplayable=${info.isUnplayable}, playabilityReason=${info.playabilityReason}, " +
            "dash=${info.dashManifestUrl != null}, hls=${info.hlsManifestUrl != null}, " +
            "urlFormats=${info.urlFormats.size}, isLive=$isLive")
        when {
            info.isUnplayable -> {
                Log.w(TAG, "Video is unplayable: ${info.playabilityReason}")
                _uiState.value = PlayerUiState.Error(info.playabilityReason ?: "Video is unavailable")
            }
            isLive && info.hlsManifestUrl != null -> {
                playerController.playHls(info.hlsManifestUrl)
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

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                engine.reportWatchProgress(videoId, 0f)
            }
        }
    }

    private fun restoreSpeedPreference() {
        viewModelScope.launch {
            val savedSpeed = playerPreferences.uiState.first().playbackSpeed
            playerController.setPlaybackSpeed(savedSpeed)
        }
    }

    private fun loadLandscapeLockPreference() {
        viewModelScope.launch {
            val enabled = playerPreferences.uiState.first().landscapeLock
            _landscapeLock.value = enabled
        }
    }

    private fun recordToHistory(info: StreamInfo) {
        viewModelScope.launch {
            try {
                val entry = WatchHistoryEntry(
                    videoId = videoId,
                    title = info.title,
                    channelName = info.author,
                    channelId = info.channelId,
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    durationMs = info.lengthSeconds * 1000,
                    positionMs = 0L,
                    speed = playerPreferences.uiState.first().playbackSpeed,
                    timestamp = System.currentTimeMillis()
                )
                historyDao.upsert(entry)
                Log.d(TAG, "Recorded history for $videoId: ${info.title}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record history", e)
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

    fun selectSubtitle(subtitle: SubtitleTrackInfo?) {
        if (subtitle == null) {
            playerController.setSubtitleEnabled(false)
        } else {
            playerController.selectSubtitleTrack(subtitle)
        }
    }

    fun selectVideoTrack(height: Int, fps: Int) {
        playerController.selectVideoTrack(height, fps)
    }

    fun showSpeedPicker() { _showSpeedPicker.value = true }
    fun hideSpeedPicker() { _showSpeedPicker.value = false }

    fun showQualityPicker() { _showQualityPicker.value = true }
    fun hideQualityPicker() { _showQualityPicker.value = false }

    fun showSubtitlePicker() { _showSubtitlePicker.value = true }
    fun hideSubtitlePicker() { _showSubtitlePicker.value = false }

    override fun onCleared() {
        super.onCleared()
    }

    private fun saveCurrentPosition() {
        viewModelScope.launch {
            try {
                val positionMs = playerController.exoPlayer.currentPosition
                val current = historyDao.getById(videoId)
                if (current != null) {
                    historyDao.upsert(current.copy(
                        positionMs = positionMs,
                        timestamp = System.currentTimeMillis()
                    ))
                    Log.d(TAG, "Saved history position for $videoId: ${positionMs}ms")
                }
                withContext(Dispatchers.IO) {
                    engine.reportWatchProgress(videoId, positionMs / 1000f)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save history position", e)
            }
        }
    }

    private fun startPeriodicHistorySave() {
        viewModelScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val positionMs = playerController.exoPlayer.currentPosition
                if (positionMs > 0) {
                    saveCurrentPosition()
                    playerStateManager.updatePlaybackState(
                        isPlaying = playerController.exoPlayer.isPlaying,
                        currentPosition = positionMs,
                        duration = playerController.exoPlayer.duration
                    )
                }
            }
        }
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val streamInfo: StreamInfo) : PlayerUiState
}

private const val SKIP_CHECK_INTERVAL_MS = 500L
private const val POSITION_SAVE_INTERVAL_MS = 10_000L
