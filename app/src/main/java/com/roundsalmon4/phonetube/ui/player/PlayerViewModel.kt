package com.roundsalmon4.phonetube.ui.player

import android.app.Application
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.cast.CastConnectionState
import com.roundsalmon4.phonetube.core.cast.CastRepository
import com.roundsalmon4.phonetube.core.cast.toCastSubtitles
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.IptvDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.PlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.XtreamClient
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.core.engine.model.StreamFormat
import com.roundsalmon4.phonetube.core.engine.model.SubtitleTrack
import com.roundsalmon4.phonetube.core.engine.model.bestCastUrl
import com.roundsalmon4.phonetube.core.engine.model.StreamInfo
import com.roundsalmon4.phonetube.core.engine.model.VideoChapter
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
    private val iptvDao: IptvDao,
    private val xtreamClient: XtreamClient,
    val playerController: PlayerEngineController,
    private val playerStateManager: PlayerStateManager,
    private val castRepository: CastRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerVM"

        // Reddit serves video from these hosts (video-only renditions plus a
        // sibling audio file). Matches any v.redd.it / packaged-media.redd.it
        // media URL so the audio sibling can be located next to it.
        private val REDDIT_MEDIA_URL =
            Regex("""^https?://(?:v\.redd\.it|packaged-media\.redd\.it)/""")
    }

    private val videoId: String = savedStateHandle["videoId"]!!
    private val queue: List<String> = savedStateHandle["queue"] ?: emptyList()

    private val isCasting: Boolean
        get() = castRepository.connectionState.value is CastConnectionState.Connected

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

    private val _chapters = MutableStateFlow<List<VideoChapter>>(emptyList())
    val chapters: StateFlow<List<VideoChapter>> = _chapters.asStateFlow()

    private val _showChapterPicker = MutableStateFlow(false)
    val showChapterPicker: StateFlow<Boolean> = _showChapterPicker.asStateFlow()

    private val _viewCount = MutableStateFlow<String?>(null)
    val viewCount: StateFlow<String?> = _viewCount.asStateFlow()

    private val _likeCount = MutableStateFlow<String?>(null)
    val likeCount: StateFlow<String?> = _likeCount.asStateFlow()

    private val _subscriberCount = MutableStateFlow<String?>(null)
    val subscriberCount: StateFlow<String?> = _subscriberCount.asStateFlow()

    private val _openLinksIn = MutableStateFlow("browser")
    val openLinksIn: StateFlow<String> = _openLinksIn.asStateFlow()

    private val _screenProtection = MutableStateFlow(false)
    val screenProtection: StateFlow<Boolean> = _screenProtection.asStateFlow()

    private val _pipEnabled = MutableStateFlow(true)
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

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
        get() = videoId.startsWith("streamable:") || videoId.startsWith("media:") || videoId.startsWith("peertube:") || videoId.startsWith("iptv:")

    init {
        playerStateManager.isPlayerScreenVisible = true
        loadStreamInfo()
        restoreSpeedPreference()
        loadLandscapeLockPreference()
        loadOpenLinksInPreference()
        loadScreenProtectionPreference()
        loadPlaylists()
        loadPipEnabledPreference()
        startPeriodicHistorySave()
        setupCastEndedAdvance()
        if (!isExternalVideo) {
            loadSponsorSegments()
            loadDescription()
            startAutoSkip()
            setupContinuePlaying()
        }
    }

    // When a casted video ends on the TV, advance the same way local playback
    // does: explicit queue first, then continue-playing suggestions.
    private fun setupCastEndedAdvance() {
        viewModelScope.launch {
            var wasEnded = false
            castRepository.tvStatus.collect { status ->
                val ended = status.state == "ended" &&
                    castRepository.connectionState.value is CastConnectionState.Connected
                if (ended && !wasEnded) {
                    advanceToNextVideo()
                }
                wasEnded = ended
            }
        }
    }

    suspend fun defaultQualityHeight(): Int? {
        val prefs = playerPreferences.uiState.first()
        if (prefs.defaultQuality == "AUTO") return null
        return prefs.defaultQuality.removeSuffix("p").toIntOrNull()
    }

    private fun loadStreamInfo() {
        // A video was opened directly, so drop any stale continue-playing
        // navigation that could otherwise hijack this new player screen.
        _navigateToVideo.value = null
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
            if (videoId.startsWith("peertube:")) {
                loadPeerTube()
                return@launch
            }
            if (videoId.startsWith("iptv:")) {
                loadIptv()
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
        Log.d(TAG, "loadDirectMedia: url=$url")
        // Reddit ships its DASH/CMAF renditions as separate video and audio
        // files, so the shared video file alone is silent. Its HLS playlist
        // bundles both tracks, so prefer that when it exists next to the file.
        val redditPlaylist = findRedditHlsPlaylist(url)
        val info = StreamInfo(
            title = "Direct media",
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
            hlsManifestUrl = redditPlaylist,
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

    /**
     * Reddit serves its videos as separate video and audio files (DASH/CMAF),
     * so a shared video rendition is always silent. Reddit also publishes an
     * HLS playlist next to the renditions that references both video and audio
     * tracks. When the shared URL is hosted on Reddit's media servers, locate
     * that playlist so it can be played as HLS. Returns null otherwise.
     */
    private suspend fun findRedditHlsPlaylist(url: String): String? {
        val normalized = url.trim()
        if (!REDDIT_MEDIA_URL.containsMatchIn(normalized)) {
            Log.d(TAG, "findRedditHlsPlaylist: not reddit media, skipping")
            return null
        }
        val dir = normalized.substringBeforeLast('/', normalized).plus('/')
        val playlist = dir + "HLSPlaylist.m3u8"
        val exists = redditFileExists(playlist)
        Log.d(TAG, "findRedditHlsPlaylist: $playlist exists=$exists")
        return if (exists) playlist else null
    }

    /**
     * Quick reachability check for a Reddit media file. Prefers HEAD, and
     * falls back to a 1-byte range GET when the server rejects HEAD.
     */
    private suspend fun redditFileExists(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val head = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            head.requestMethod = "HEAD"
            head.connectTimeout = 8_000
            head.readTimeout = 8_000
            head.instanceFollowRedirects = true
            val headCode = head.responseCode
            head.disconnect()
            if (headCode != 405) {
                headCode in 200..399
            } else {
                redditRangeProbe(url)
            }
        } catch (e: Exception) {
            Log.w(TAG, "redditFileExists: probe failed for $url", e)
            false
        }
    }

    private fun redditRangeProbe(url: String): Boolean {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            connection.inputStream.readBytes().take(1)
            connection.disconnect()
            code in 200..299
        } catch (e: Exception) {
            try { connection.disconnect() } catch (_: Exception) {}
            Log.w(TAG, "redditRangeProbe: failed for $url", e)
            false
        }
    }

    private fun loadPeerTube() {
        val parts = videoId.removePrefix("peertube:").split(":", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            Log.e(TAG, "Malformed peertube videoId: '$videoId'")
            _uiState.value = PlayerUiState.Error("Invalid PeerTube video link")
            return
        }
        val host = parts[0]
        val uuid = parts[1]
        Log.d(TAG, "loadPeerTube: host=$host uuid=$uuid")
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                engine.getPeerTubeStreamInfo(host, uuid)
            }
            if (info == null) {
                Log.e(TAG, "loadPeerTube: stream info unavailable for $host/$uuid")
                _uiState.value = PlayerUiState.Error("Could not get playback data for this PeerTube video")
                return@launch
            }
            _uiState.value = PlayerUiState.Ready(info)
            playerStateManager.updateVideoInfo(
                videoId = videoId,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl.orEmpty()
            )
            startPlayback(info)
            if (!info.isLive && !info.isLiveContent) {
                recordToHistory(info)
                resumeFromHistory(info)
            }
        }
    }

    private fun loadIptv() {
        val parts = videoId.removePrefix("iptv:").split(":", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            Log.e(TAG, "loadIptv: malformed iptv videoId: '$videoId'")
            _uiState.value = PlayerUiState.Error("Invalid IPTV stream link")
            return
        }
        val providerId = parts[0]
        val streamId = parts[1]
        Log.d(TAG, "loadIptv: providerKey=$providerId streamId=$streamId")
        viewModelScope.launch {
            try {
                val provider = withContext(Dispatchers.IO) { iptvDao.getById(providerId) }
                if (provider == null) {
                    Log.e(TAG, "loadIptv: provider '$providerId' not found")
                    _uiState.value = PlayerUiState.Error("IPTV provider not found. Re-add it from the IPTV tab.")
                    return@launch
                }
                val hlsUrl = xtreamClient.liveStreamUrl(provider.scheme, provider.host, provider.username, provider.password, streamId)
                Log.d(TAG, "loadIptv: stream $streamId via ${provider.scheme}${provider.host}")
                val info = StreamInfo(
                    title = provider.name,
                    author = provider.name,
                    channelId = "",
                    lengthSeconds = 0L,
                    isLive = true,
                    isLiveContent = false,
                    adaptiveFormats = emptyList(),
                    urlFormats = emptyList(),
                    subtitles = emptyList(),
                    dashManifestUrl = null,
                    hlsManifestUrl = hlsUrl,
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
                // Live TV should always play at 1x regardless of the global
                // playback speed preference.
                Log.d(TAG, "loadIptv: forcing 1x playback speed for live stream")
                playerController.setPlaybackSpeed(1f)
            } catch (e: Exception) {
                Log.e(TAG, "loadIptv failed", e)
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to start IPTV stream")
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
                        _chapters.value = parseChapters(metadata.description).also {
                            Log.d(TAG, "loadDescription: parsed ${it.size} chapters")
                        }
                        _viewCount.value = metadata.viewCount
                        _likeCount.value = metadata.likeCount
                        _subscriberCount.value = metadata.subscriberCount
                    }
            } catch (_: Exception) { }
        }
    }

    fun showChapterPicker() { _showChapterPicker.value = true }
    fun hideChapterPicker() { _showChapterPicker.value = false }

    /**
     * Parses timestamped description lines (00:00 Intro, 1:23:45 Credits) into
     * an ordered list of chapters. Lines are kept only when their start time is
     * strictly after the previous one, which filters out recap/redo formatting.
     */
    private fun parseChapters(description: String): List<VideoChapter> {
        val regex = Regex("""^((\d{1,2}):)?(\d{1,2}):(\d{2})\s+(.+)$""")
        val result = mutableListOf<VideoChapter>()
        var lastStartMs = -1L
        for (line in description.lineSequence()) {
            val match = regex.matchEntire(line.trim()) ?: continue
            val hours = match.groupValues[2].toIntOrNull() ?: 0
            val minutes = match.groupValues[3].toIntOrNull() ?: 0
            val seconds = 60L * (hours * 60 + minutes) + (match.groupValues[4].toIntOrNull() ?: 0)
            val title = match.groupValues[5].trim()
            if (title.isBlank()) continue
            val startMs = seconds * 1000L
            if (startMs > lastStartMs) {
                result.add(VideoChapter(title = title, startMs = startMs))
                lastStartMs = startMs
            }
        }
        return result
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
                        // While casting the phone's player is paused, so watch
                        // the TV's reported position and seek the TV instead.
                        val casting = isCasting
                        val positionMs = if (casting) {
                            castRepository.tvStatus.value.position
                        } else {
                            playerController.exoPlayer.currentPosition
                        }
                        val skipAction = sponsorBlockService.checkForSkip(
                            positionMs, segments, prefs.sponsorBlockCategories
                        )
                        if (skipAction != null) {
                            Log.d(TAG, "Auto-skipping ${skipAction.segment.category} " +
                                "at ${skipAction.segment.startMs}ms -> ${skipAction.seekToMs}ms (cast=$casting)")
                            if (casting) {
                                castRepository.sendSeek(skipAction.seekToMs)
                            } else {
                                playerController.seekTo(skipAction.seekToMs)
                            }
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

                // Wait (with retries) until the video tracks are actually exposed;
                // DASH/HLS track groups appear shortly after prepare and a fixed
                // delay can miss them, leaving the default lowest-first selection.
                var videoReady = false
                for (attempt in 0 until 20) {
                    val groups = playerController.exoPlayer.currentTracks.groups
                    if (groups.any { it.type == C.TRACK_TYPE_VIDEO }) {
                        videoReady = true
                        break
                    }
                    delay(300)
                }
                if (!videoReady) {
                    Log.w(TAG, "applyDefaultQuality: no video tracks became available; using adaptive default")
                    return@launch
                }

                // Pass the user's target straight to the controller, which picks
                // the closest available track (DASH or HLS) instead of exact-height
                // matching that could silently fail and fall back to low quality.
                Log.d(TAG, "applyDefaultQuality: requesting ${targetHeight}p")
                selectVideoTrack(targetHeight, 0)
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

        // While a cast session is active (or connecting), new videos hand off to the
        // TV instead of playing locally, so the phone works purely as a remote.
        val castState = castRepository.connectionState.value
        val castActive = castState is CastConnectionState.Connected ||
            castState is CastConnectionState.Connecting
        if (castActive) {
            val castUrl = info.bestCastUrl()
            if (castUrl != null) {
                Log.d(TAG, "startPlayback: casting '${info.title}' to TV: $castUrl")
                val live = info.isLive || info.isLiveContent
                val resumeMs = if (videoId == lastCastVideoId) {
                    castRepository.tvStatus.value.position.coerceAtLeast(0L)
                } else {
                    0L
                }
                lastCastVideoId = videoId
                viewModelScope.launch {
                    val prefs = playerPreferences.uiState.first()
                    val speed = if (live) 1f else playerController.exoPlayer.playbackParameters.speed
                    val qualityHint = if (prefs.defaultQuality == "AUTO") null
                        else prefs.defaultQuality.removeSuffix("p").toIntOrNull()
                    castRepository.sendPlay(
                        url = castUrl,
                        title = info.title,
                        position = resumeMs,
                        subtitles = info.subtitles.takeIf { it.isNotEmpty() }?.toCastSubtitles(),
                        quality = qualityHint,
                        speed = speed,
                        activeSubtitleIndex = activeCastSubtitleIndex(info.subtitles)
                    )
                }
                // Keep the media on the local player (paused) so the phone can
                // act as a remote: the CC picker still reads real tracks and
                // disconnecting can resume instantly without reloading.
                when {
                    live && info.hlsManifestUrl != null ->
                        playerController.preparePaused(info.hlsManifestUrl, "application/x-mpegURL", info.subtitles, info.title, info.author)
                    info.dashManifestUrl != null ->
                        playerController.preparePaused(info.dashManifestUrl, "application/dash+xml", info.subtitles, info.title, info.author)
                    info.hlsManifestUrl != null ->
                        playerController.preparePaused(info.hlsManifestUrl, "application/x-mpegURL", info.subtitles, info.title, info.author)
                    else -> {
                        val direct = info.urlFormats.firstOrNull { !it.url.isNullOrBlank() }
                        if (direct != null) {
                            playerController.preparePaused(direct.url!!, direct.mimeType, info.subtitles, info.title, info.author)
                        }
                    }
                }
                _uiState.value = PlayerUiState.Ready(info)
                return
            }
            Log.w(TAG, "startPlayback: casting active but no playable URL found, falling back to local")
        }

        when {
            info.isUnplayable -> {
                Log.w(TAG, "Video is unplayable: ${info.playabilityReason}")
                _uiState.value = PlayerUiState.Error(friendlyPlaybackError(info.playabilityReason))
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
            // IPTV is always 1x; the global playback speed preference must not
            // apply to live streams (and must not race the 1x force in loadIptv).
            if (videoId.startsWith("iptv:")) {
                playerController.setPlaybackSpeed(1f)
            } else {
                playerController.setPlaybackSpeed(savedSpeed)
            }
        }
    }

    private fun loadLandscapeLockPreference() {
        viewModelScope.launch {
            playerPreferences.uiState.collect { prefs ->
                _landscapeLock.value = prefs.landscapeLock
            }
        }
    }

    fun toggleMute() {
        val next = !_muted.value
        _muted.value = next
        Log.d(TAG, "toggleMute: ${if (next) "muted" else "unmuted"}")
        playerController.setVolume(if (next) 0f else _volume.value)
    }

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _volume.value = v
        _muted.value = v <= 0f
        Log.d(TAG, "setVolume: $v")
        playerController.setVolume(v)
    }

    private fun loadPipEnabledPreference() {
        viewModelScope.launch {
            _pipEnabled.value = playerPreferences.uiState.first().pipEnabled
        }
    }

    private fun loadOpenLinksInPreference() {
        viewModelScope.launch {
            playerPreferences.uiState.collect { prefs ->
                _openLinksIn.value = prefs.openLinksIn
            }
        }
    }

    private fun loadScreenProtectionPreference() {
        viewModelScope.launch {
            playerPreferences.uiState.collect { prefs ->
                _screenProtection.value = prefs.screenProtection
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
            // Skip history recording when incognito mode is enabled
            val incognito = playerPreferences.uiState.first().incognitoMode
            if (incognito) return@launch

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
                            thumbnailUrl = info.thumbnailUrl
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
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

    fun pausePlayback() {
        if (playerController.exoPlayer.isPlaying) {
            playerController.exoPlayer.pause()
        }
    }

    fun resumePlayback() {
        playerController.exoPlayer.play()
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
            activeSubtitleUrl = null
            subtitleExplicitlyDisabled = true
        } else {
            playerController.selectSubtitleTrack(subtitle)
            activeSubtitleUrl = resolveSubtitleUrl(subtitle.name)
            subtitleExplicitlyDisabled = false
        }
        mirrorSubtitleToTv()
    }

    fun selectVideoTrack(height: Int, fps: Int) {
        playerController.selectVideoTrack(height, fps)
        if (isCasting) {
            castRepository.sendQuality(height)
        }
    }

    // The cast protocol identifies a subtitle by its index in the list sent
    // with the play command. Track the active one by its (normalized) URL so
    // we can mirror selections made before or during a cast session.
    private var activeSubtitleUrl: String? = null
    private var subtitleExplicitlyDisabled = false

    // Tracks which video is currently on the TV so returning to the same video
    // (e.g. via the mini player) resumes at the TV position instead of 0.
    private var lastCastVideoId: String? = null

    fun markCurrentVideoCasted() {
        lastCastVideoId = videoId
    }

    private fun resolveSubtitleUrl(name: String?): String? {
        val subs = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.subtitles ?: return null
        return subs.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.let { listOf(it).toCastSubtitles().first().url }
    }

    /**
     * Returns the label/language of the text track the local player currently
     * has selected, so a handoff can mirror an auto-selected subtitle too.
     */
    private fun currentlySelectedSubtitle(): Pair<String?, String?>? {
        val groups = playerController.exoPlayer.currentTracks.groups
        for (group in groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    val format = group.getTrackFormat(i)
                    return format.label to format.language
                }
            }
        }
        return null
    }

    fun activeCastSubtitleIndex(subtitles: List<SubtitleTrack>): Int? {
        if (subtitleExplicitlyDisabled) return -1
        val castList = subtitles.toCastSubtitles()
        val url = activeSubtitleUrl
        val index = if (url != null) castList.indexOfFirst { it.url == url } else {
            // Fall back to whatever the local player auto-selected.
            currentlySelectedSubtitle()?.let { (label, lang) ->
                castList.indexOfFirst {
                    it.name.equals(label, ignoreCase = true) ||
                        (lang != null && it.languageCode.equals(lang, ignoreCase = true))
                }
            } ?: -1
        }
        return if (index < 0) null else index
    }

    private fun mirrorSubtitleToTv() {
        if (!isCasting) return
        val castList = (uiState.value as? PlayerUiState.Ready)?.streamInfo?.subtitles?.toCastSubtitles()
            ?: return
        val index = when {
            subtitleExplicitlyDisabled -> -1
            activeSubtitleUrl != null -> castList.indexOfFirst { it.url == activeSubtitleUrl }
            else -> currentlySelectedSubtitle()?.let { (label, lang) ->
                castList.indexOfFirst {
                    it.name.equals(label, ignoreCase = true) ||
                        (lang != null && it.languageCode.equals(lang, ignoreCase = true))
                }
            } ?: -1
        }
        castRepository.sendSubtitle(if (index < 0) -1 else index)
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
                    viewModelScope.launch { advanceToNextVideo() }
                }
            }
        }
        continuePlayingListener = listener
        playerController.exoPlayer.addListener(listener)
    }

    /**
     * Shared by local end-of-video and casted-video end: advance through an
     * explicit queue (e.g. Play All) first, otherwise the next suggested video
     * when continue-playing is enabled.
     */
    private suspend fun advanceToNextVideo() {
        if (queue.isNotEmpty()) {
            val nextId = queue.first()
            Log.d(TAG, "Advancing queue: next video $nextId")
            _navigateToVideo.value = NextVideoToPlay(nextId, queue.drop(1))
            return
        }
        val prefs = playerPreferences.uiState.first()
        if (!prefs.continuePlaying) return
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

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(val streamInfo: StreamInfo) : PlayerUiState
}

data class NextVideoToPlay(
    val videoId: String,
    val queue: List<String>
)

/**
 * Turns YouTube's raw playability reason into something actionable for an app
 * with no sign-in flow. YouTube's "confirm you're not a bot" verification is
 * network/visitor based, so we avoid implying that logging in is an option.
 */
private fun friendlyPlaybackError(raw: String?): String {
    if (raw.isNullOrBlank()) return "Video is unavailable"
    val lower = raw.lowercase()
    return if (lower.contains("sign in") || lower.contains("not a bot") || lower.contains("verify")) {
        "YouTube is blocking playback on this network — try switching your VPN region."
    } else {
        raw
    }
}

private const val SKIP_CHECK_INTERVAL_MS = 500L
private const val POSITION_SAVE_INTERVAL_MS = 10_000L

