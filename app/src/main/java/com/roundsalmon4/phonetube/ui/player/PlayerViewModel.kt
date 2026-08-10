package com.roundsalmon4.phonetube.ui.player

import android.app.Application
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.PlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.core.engine.model.StreamFormat
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
import kotlinx.coroutines.flow.firstOrNull
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
    private val queue: List<String> = savedStateHandle["queue"] ?: emptyList()

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

    private val _viewCount = MutableStateFlow<String?>(null)
    val viewCount: StateFlow<String?> = _viewCount.asStateFlow()

    private val _likeCount = MutableStateFlow<String?>(null)
    val likeCount: StateFlow<String?> = _likeCount.asStateFlow()

    private val _subscriberCount = MutableStateFlow<String?>(null)
    val subscriberCount: StateFlow<String?> = _subscriberCount.asStateFlow()

    private val _openLinksIn = MutableStateFlow("browser")
    val openLinksIn: StateFlow<String> = _openLinksIn.asStateFlow()

    private val _showAddToPlaylist = MutableStateFlow(false)
    val showAddToPlaylist: StateFlow<Boolean> = _showAddToPlaylist.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _navigateToVideo = MutableStateFlow<NextVideoToPlay?>(null)
    val navigateToVideo: StateFlow<NextVideoToPlay?> = _navigateToVideo.asStateFlow()

    val playbackState: StateFlow<PlayerPlaybackSnapshot> = playerController.playbackState

    private val historyMutex = Mutex()
    private var continuePlayingListener: Player.Listener? = null
    private val isExternalVideo: Boolean
        get() = videoId.startsWith("streamable:") || videoId.startsWith("media:")

    init {
        playerStateManager.isPlayerScreenVisible = true
        loadStreamInfo()
        restoreSpeedPreference()
        loadLandscapeLockPreference()
        loadOpenLinksInPreference()
        loadPlaylists()
        startPeriodicHistorySave()
        if (!isExternalVideo) {
            loadSponsorSegments()
            loadDescription()
            startAutoSkip()
            setupContinuePlaying()
        }
    }

    private fun loadStreamInfo() {
        _uiState.value = PlayerUiState.Loading
        viewModelScope.launch {
            if (videoId.startsWith("streamable:")) {
                loadStreamable()
                return@launch
            }
            if (videoId.startsWith("media:")) {
                val encoded = videoId.removePrefix("media:")
                val url = try {
                    String(
                        android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
                        Charsets.UTF_8
                    )
                } catch (e: Exception) {
                    encoded
                }
                loadDirectMedia(url)
                return@launch
            }
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

    private suspend fun loadStreamable() {
        val shortcode = videoId.removePrefix("streamable:")
        val streamable = engine.getStreamableInfo(shortcode)
        if (streamable == null) {
            _uiState.value = PlayerUiState.Error(
                "This Streamable video could not be loaded. The Streamable API may be blocked or the video is unavailable."
            )
            return
        }
        val info = StreamInfo(
            title = streamable.title,
            author = "Streamable",
            channelId = "",
            lengthSeconds = 0L,
            isLive = false,
            isLiveContent = false,
            adaptiveFormats = emptyList(),
            urlFormats = listOf(
                StreamFormat(
                    url = streamable.mp4Url,
                    mimeType = "video/mp4",
                    height = 0,
                    bitrate = null,
                    fps = null,
                    qualityLabel = null
                )
            ),
            subtitles = emptyList(),
            dashManifestUrl = null,
            hlsManifestUrl = null,
            isUnplayable = false,
            playabilityReason = null
        )
        _uiState.value = PlayerUiState.Ready(info)
        playerStateManager.updateVideoInfo(
            videoId = videoId,
            title = info.title,
            thumbnailUrl = streamable.thumbnailUrl ?: ""
        )
        startPlayback(info)
    }

    private suspend fun loadDirectMedia(url: String) {
        val info = StreamInfo(
            title = "Reddit video",
            author = "",
            channelId = "",
            lengthSeconds = 0L,
            isLive = false,
            isLiveContent = false,
            adaptiveFormats = emptyList(),
            urlFormats = listOf(
                StreamFormat(
                    url = url,
                    mimeType = "video/mp4",
                    height = 0,
                    bitrate = null,
                    fps = null,
                    qualityLabel = null
                )
            ),
            subtitles = emptyList(),
            dashManifestUrl = null,
            hlsManifestUrl = null,
            isUnplayable = false,
            playabilityReason = null
        )
        _uiState.value = PlayerUiState.Ready(info)
        playerStateManager.updateVideoInfo(
            videoId = videoId,
            title = info.title,
            thumbnailUrl = ""
        )
        startPlayback(info)
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
                        _viewCount.value = metadata.viewCount
                        _likeCount.value = metadata.likeCount
                        _subscriberCount.value = metadata.subscriberCount
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

                // Find the best matching format from the DASH adaptive list (what's actually played)
                val formats = info.adaptiveFormats
                val bestMatch = formats.minByOrNull {
                    kotlin.math.abs(it.height - targetHeight)
                }

                if (bestMatch != null) {
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
                playerController.playHls(info.hlsManifestUrl, info.subtitles, info.title, info.author)
            }
            info.dashManifestUrl != null -> {
                playerController.playDash(info.dashManifestUrl, info.subtitles, info.title, info.author)
            }
            info.hlsManifestUrl != null -> {
                playerController.playHls(info.hlsManifestUrl, info.subtitles, info.title, info.author)
            }
            info.urlFormats.isNotEmpty() -> {
                val best = info.urlFormats.firstOrNull { it.url != null }
                if (best != null) {
                    playerController.playUrl(best.url!!, best.mimeType, info.subtitles, info.title, info.author)
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

        if (!isExternalVideo) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    engine.reportWatchProgress(videoId, 0f)
                }
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

    private fun loadOpenLinksInPreference() {
        viewModelScope.launch {
            playerPreferences.uiState.collect { prefs ->
                _openLinksIn.value = prefs.openLinksIn
            }
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
                        var title = info.title
                        var channelName = info.author
                        Log.d(TAG, "recordToHistory: videoId=$videoId streamTitle='$title' streamAuthor='$channelName'")
                        // The stream info sometimes lacks the title/author (e.g. HLS); fill from metadata.
                        if (title.isBlank() || channelName.isBlank()) {
                            try {
                                val meta = engine.getMetadata(videoId).firstOrNull()
                                Log.d(TAG, "recordToHistory: metadata result=${meta != null}")
                                if (meta != null) {
                                    if (title.isBlank()) title = meta.video.title.orEmpty()
                                    if (channelName.isBlank()) channelName = meta.video.author.orEmpty()
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "recordToHistory: metadata fallback failed: ${e.message?.take(80)}")
                            }
                        }
                        Log.d(TAG, "recordToHistory: final title='$title' channel='$channelName'")
                        val entry = WatchHistoryEntry(
                            videoId = videoId,
                            title = title,
                            channelName = channelName,
                            channelId = info.channelId,
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            durationMs = info.lengthSeconds * 1000,
                            positionMs = 0L,
                            speed = playerController.exoPlayer.playbackParameters.speed,
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
                    // Restore the speed this video was watched at
                    if (entry.speed > 0f && entry.speed != prefs.playbackSpeed) {
                        playerController.setPlaybackSpeed(entry.speed)
                    }
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
        val info = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.let {
            PlaylistVideoInfo(
                videoId = videoId,
                title = it.title,
                channelName = it.author,
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = it.lengthSeconds * 1000
            )
        } ?: return
        viewModelScope.launch {
            if (PlaylistSaver.addToPlaylist(playlistDao, info, playlist)) {
                _toastMessage.value = "Added to playlist"
                _showAddToPlaylist.value = false
            } else {
                Log.w(TAG, "addToPlaylist failed")
            }
        }
    }
    fun createPlaylistAndAdd(name: String) {
        val info = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.let {
            PlaylistVideoInfo(
                videoId = videoId,
                title = it.title,
                channelName = it.author,
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = it.lengthSeconds * 1000
            )
        } ?: return
        viewModelScope.launch {
            if (PlaylistSaver.createAndAdd(playlistDao, info, name)) {
                _toastMessage.value = "Created and added to playlist"
                _showAddToPlaylist.value = false
            } else {
                Log.w(TAG, "createPlaylistAndAdd failed")
            }
        }
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        playerController.selectAudioTrack(track)
    }

    fun clearToast() { _toastMessage.value = null }

    fun clearNavigateToVideo() { _navigateToVideo.value = null }

    override fun onCleared() {
        super.onCleared()
        playerStateManager.isPlayerScreenVisible = false
        continuePlayingListener?.let { playerController.exoPlayer.removeListener(it) }
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
                            speed = playerController.exoPlayer.playbackParameters.speed,
                            timestamp = System.currentTimeMillis()
                        ))
                        Log.d(TAG, "Saved history position for $videoId: ${positionMs}ms")
                    }
                    if (!isExternalVideo) {
                        withContext(Dispatchers.IO) {
                            engine.reportWatchProgress(videoId, positionMs / 1000f)
                        }
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
                }
            }
        }
    }

    private fun setupContinuePlaying() {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    viewModelScope.launch {
                        // Advance through an explicit queue (e.g. Play All) first
                        if (queue.isNotEmpty()) {
                            val nextId = queue.first()
                            _navigateToVideo.value = NextVideoToPlay(nextId, queue.drop(1))
                            return@launch
                        }
                        val prefs = playerPreferences.uiState.first()
                        if (!prefs.continuePlaying) return@launch
                        try {
                            val meta = engine.getMetadata(videoId).firstOrNull()
                            val next = meta?.suggestions?.firstOrNull()
                            if (next != null) {
                                Log.d(TAG, "Continue playing: loading next video ${next.videoId}")
                                _navigateToVideo.value = NextVideoToPlay(next.videoId, emptyList())
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        continuePlayingListener = listener
        playerController.exoPlayer.addListener(listener)
    }
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val streamInfo: StreamInfo) : PlayerUiState
}

private data class NextVideoToPlay(
    val videoId: String,
    val queue: List<String>
)

private const val SKIP_CHECK_INTERVAL_MS = 500L
private const val POSITION_SAVE_INTERVAL_MS = 10_000L

