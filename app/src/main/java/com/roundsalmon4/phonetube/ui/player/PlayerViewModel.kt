package com.roundsalmon4.phonetube.ui.player

import android.app.Application
import android.util.Log
import androidx.media3.common.C
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.core.engine.model.StreamInfo
import com.roundsalmon4.phonetube.player.AudioTrackInfo
import com.roundsalmon4.phonetube.player.PlayerEngineController
import com.roundsalmon4.phonetube.player.PlayerPlaybackSnapshot
import com.roundsalmon4.phonetube.player.PlayerStateManager
import com.roundsalmon4.phonetube.player.SponsorBlockService
import com.roundsalmon4.phonetube.player.SubtitleTrackInfo
import com.roundsalmon4.phonetube.player.service.PlaybackService
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val playlistDao: PlaylistDao,
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

    private val _showAudioPicker = MutableStateFlow(false)
    val showAudioPicker: StateFlow<Boolean> = _showAudioPicker.asStateFlow()

    private val _landscapeLock = MutableStateFlow(false)
    val landscapeLock: StateFlow<Boolean> = _landscapeLock.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _description = MutableStateFlow<String?>(null)
    val description: StateFlow<String?> = _description.asStateFlow()

    private val _showAddToPlaylist = MutableStateFlow(false)
    val showAddToPlaylist: StateFlow<Boolean> = _showAddToPlaylist.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    val playbackState: StateFlow<PlayerPlaybackSnapshot> = playerController.playbackState

    private val historyMutex = Mutex()

    init {
        loadStreamInfo()
        loadSponsorSegments()
        loadDescription()
        startAutoSkip()
        restoreSpeedPreference()
        loadLandscapeLockPreference()
        loadPlaylists()
        startPeriodicHistorySave()
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
                        resumeFromHistory(info)
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

    private fun loadDescription() {
        viewModelScope.launch {
            try {
                engine.getMetadata(videoId)
                    .catch { /* ignore */ }
                    .collect { metadata ->
                        _description.value = metadata.description
                    }
            } catch (_: Exception) { }
        }
    }

    private fun startAutoSkip() {
        viewModelScope.launch {
            while (isActive) {
                val prefs = playerPreferences.uiState.first()
                if (!prefs.sponsorBlockEnabled) {
                    delay(SKIP_CHECK_INTERVAL_MS)
                    continue
                }
                val streamInfo = (uiState.value as? PlayerUiState.Ready)?.streamInfo
                if (streamInfo != null && !streamInfo.isLive && !streamInfo.isLiveContent) {
                    val segments = _sponsorSegments.value
                    if (segments.isNotEmpty()) {
                        val positionMs = playerController.exoPlayer.currentPosition
                        val skipAction = sponsorBlockService.checkForSkip(
                            positionMs, segments, prefs.sponsorBlockCategories
                        )
                        if (skipAction != null) {
                            Log.d(TAG, "Auto-skipping ${skipAction.segment.category} " +
                                "at ${skipAction.segment.startMs}ms -> ${skipAction.seekToMs}ms")
                            playerController.seekTo(skipAction.seekToMs)
                            if (skipAction.showToast) {
                                _toastMessage.value = "Skipped ${skipAction.segment.category}"
                            }
                        }
                    }
                }
                delay(SKIP_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun applyDefaultQuality(info: StreamInfo) {
        viewModelScope.launch {
            try {
                val prefs = playerPreferences.uiState.first()
                if (prefs.defaultQuality == "AUTO") return@launch

                val targetHeight = prefs.defaultQuality.removeSuffix("p").toIntOrNull() ?: return@launch

                // Wait a bit for tracks to be available
                delay(500)

                val tracks = playerController.exoPlayer.currentTracks
                val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                if (videoGroups.isEmpty()) return@launch

                // Find the best matching format
                val formats = info.urlFormats.filter { it.height != null }
                val bestMatch = formats.minByOrNull {
                    kotlin.math.abs((it.height ?: 0) - targetHeight)
                }

                if (bestMatch != null && bestMatch.height != null) {
                    selectVideoTrack(bestMatch.height, bestMatch.fps?.toIntOrNull() ?: 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply default quality", e)
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

        if (_uiState.value !is PlayerUiState.Error) {
            PlaybackService.start(playerController, getApplication())
            if (!isLive) {
                applyDefaultQuality(info)
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

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    private fun recordToHistory(info: StreamInfo) {
        viewModelScope.launch {
            historyMutex.withLock {
                try {
                    val existing = historyDao.getById(videoId)
                    if (existing == null) {
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
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record history", e)
                }
            }
        }
    }

    private fun resumeFromHistory(info: StreamInfo) {
        viewModelScope.launch {
            try {
                val prefs = playerPreferences.uiState.first()
                if (!prefs.resumePlayback) return@launch

                val entry = historyDao.getById(videoId) ?: return@launch
                val durationMs = info.lengthSeconds * 1000

                // Don't resume if video was finished (within 5 seconds of end)
                if (entry.positionMs > 0 && entry.positionMs < durationMs - 5000) {
                    // Wait for player to be ready before seeking
                    delay(1000)
                    Log.d(TAG, "Resuming from ${entry.positionMs}ms")
                    playerController.seekTo(entry.positionMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resume from history", e)
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

    fun showAudioPicker() { _showAudioPicker.value = true }
    fun hideAudioPicker() { _showAudioPicker.value = false }

    fun showAddToPlaylist() { _showAddToPlaylist.value = true }
    fun hideAddToPlaylist() { _showAddToPlaylist.value = false }
    fun addToPlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            try {
                val count = playlistDao.getVideoCount(playlist.id)
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = playlist.id,
                        videoId = videoId,
                        title = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.title ?: "",
                        channelName = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.author ?: "",
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        durationMs = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.lengthSeconds?.times(1000) ?: 0L,
                        position = count
                    )
                )
                playlistDao.updatePlaylist(playlist.copy(videoCount = count + 1))
                _toastMessage.value = "Added to playlist"
                _showAddToPlaylist.value = false
            } catch (e: Exception) {
                Log.w(TAG, "addToPlaylist failed", e)
            }
        }
    }
    fun createPlaylistAndAdd(name: String) {
        viewModelScope.launch {
            try {
                val id = playlistDao.insertPlaylist(
                    LocalPlaylist(name = name.trim(), createdAt = System.currentTimeMillis())
                )
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = id,
                        videoId = videoId,
                        title = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.title ?: "",
                        channelName = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.author ?: "",
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        durationMs = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.lengthSeconds?.times(1000) ?: 0L,
                        position = 0
                    )
                )
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = name.trim(), createdAt = System.currentTimeMillis(), videoCount = 1))
                _toastMessage.value = "Created and added to playlist"
                _showAddToPlaylist.value = false
            } catch (e: Exception) {
                Log.w(TAG, "createPlaylistAndAdd failed", e)
            }
        }
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        playerController.selectAudioTrack(track)
    }

    fun clearToast() { _toastMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        try {
            val positionMs = playerController.exoPlayer.currentPosition
            if (positionMs > 0) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val current = historyDao.getById(videoId)
                    if (current != null) {
                        historyDao.upsert(current.copy(
                            positionMs = positionMs,
                            timestamp = System.currentTimeMillis()
                        ))
                        Log.d(TAG, "Saved history position on clear for $videoId: ${positionMs}ms")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save history position on clear", e)
        }
    }

    private fun saveCurrentPosition() {
        viewModelScope.launch {
            historyMutex.withLock {
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
    }

    private fun startPeriodicHistorySave() {
        viewModelScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val positionMs = playerController.exoPlayer.currentPosition
                if (positionMs > 0) {
                    val isLive = playerController.exoPlayer.isCurrentMediaItemLive
                    if (!isLive) {
                        saveCurrentPosition()
                    }
                    playerStateManager.updatePlaybackState(
                        isPlaying = playerController.exoPlayer.isPlaying,
                        currentPosition = positionMs,
                        duration = playerController.exoPlayer.duration,
                        bufferedPosition = playerController.exoPlayer.bufferedPosition
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
