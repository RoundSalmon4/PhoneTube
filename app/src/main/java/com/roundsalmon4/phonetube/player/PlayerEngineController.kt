package com.roundsalmon4.phonetube.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.roundsalmon4.phonetube.core.engine.model.SubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log

class PlayerEngineController(context: Context) {

    companion object {
        private const val TAG = "PlayerEngine"
        // Seeded adaptive start (~10 Mbps -> roughly 1080p+ target) so AUTO does
        // not begin at the floor on a good connection; still ramps with the real
        // measured throughput.
        private const val INITIAL_BITRATE_ESTIMATE_BPS = 10_000_000L
        // DefaultBandwidthMeter default sliding window is 2000ms; widen it so the
        // estimate averages more history and recovers quickly after a low start.
        private const val SLIDING_WINDOW_WEIGHT_MS = 10_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Share one bandwidth meter so the initial estimate is a realistic start
    // for adaptive (ExoPlayer's default ~1 Mbps makes AUTO begin at a very low
    // resolution and ramp slowly). The same meter measures the actual
    // transfers and is read by the AdaptiveTrackSelection.
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(INITIAL_BITRATE_ESTIMATE_BPS)
        // Longer averaging window so the very first measured chunk does not
        // immediately collapse the estimate to that stream's lowest bitrate.
        .setSlidingWindowMaxWeight(SLIDING_WINDOW_WEIGHT_MS)
        .build()

    private val dataSourceFactory = DefaultDataSource.Factory(context)
        .setTransferListener(bandwidthMeter)

    private val trackSelector = DefaultTrackSelector(context)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            PlaybackRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
        .setBandwidthMeter(bandwidthMeter)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _playbackState = MutableStateFlow(PlayerPlaybackSnapshot())
    val playbackState: StateFlow<PlayerPlaybackSnapshot> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateSnapshot()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateSnapshot()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateSnapshot()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            updateSnapshot()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // Log the actual rendered resolution (includes adaptive switches) so
            // AUTO's behavior can be verified from the app log.
            Log.d(TAG, "videoSize changed to ${videoSize.width}x${videoSize.height}")
            updateSnapshot()
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        startSnapshotPolling()
    }

    fun playDash(
        manifestUrl: String,
        subtitles: List<SubtitleTrack> = emptyList(),
        title: String? = null,
        artist: String? = null
    ) {
        play(buildMediaItem(manifestUrl, "application/dash+xml", title, artist), subtitles)
    }

    fun playHls(
        manifestUrl: String,
        subtitles: List<SubtitleTrack> = emptyList(),
        title: String? = null,
        artist: String? = null
    ) {
        play(buildMediaItem(manifestUrl, "application/x-mpegURL", title, artist), subtitles)
    }

    fun playUrl(
        url: String,
        mimeType: String?,
        subtitles: List<SubtitleTrack> = emptyList(),
        title: String? = null,
        artist: String? = null
    ) {
        play(buildMediaItem(url, mimeType, title, artist), subtitles)
    }

    private fun buildMediaItem(
        uri: String,
        mimeType: String?,
        title: String?,
        artist: String?
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        mimeType?.let { builder.setMimeType(it) }
        if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
            val metadataBuilder = MediaMetadata.Builder()
            title?.let { metadataBuilder.setTitle(it) }
            artist?.let { metadataBuilder.setArtist(it) }
            builder.setMediaMetadata(metadataBuilder.build())
        }
        return builder.build()
    }

    private fun play(mediaItem: MediaItem, subtitles: List<SubtitleTrack>) {
        if (subtitles.isEmpty()) {
            exoPlayer.setMediaItem(mediaItem)
        } else {
            val mainSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
            val textSources = subtitles.map { subtitle ->
                // Use WebVTT when the track is TTML — much more reliably decoded by ExoPlayer
                val useVtt = subtitle.mimeType.contains("ttml")
                val subtitleUrl = if (useVtt) subtitle.baseUrl.replace("fmt=ttml", "fmt=vtt") else subtitle.baseUrl
                val subtitleMime = if (useVtt) "text/vtt" else subtitle.mimeType.ifBlank { "text/vtt" }
                val subtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse(subtitleUrl)
                )
                    .setMimeType(subtitleMime)
                    .setLanguage(subtitle.languageCode.ifBlank { null })
                    .setLabel(subtitle.name.ifBlank { subtitle.languageCode })
                    .build()
                SingleSampleMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(subtitleConfiguration, C.TIME_UNSET)
            }
            exoPlayer.setMediaSource(MergingMediaSource(mainSource, *textSources.toTypedArray()))
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun seekForward(seconds: Long = 30L) {
        seekBy(seconds * 1000)
    }

    fun seekBackward(seconds: Long = 10L) {
        seekBy(-seconds * 1000)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekBy(offsetMs: Long) {
        val newPos = (exoPlayer.currentPosition + offsetMs).coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
        exoPlayer.seekTo(newPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun setSubtitleEnabled(enabled: Boolean) {
        val tracks = exoPlayer.currentTracks
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) return

        val group = textGroups.first()
        if (enabled) {
            // Enable first subtitle track
            val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(0))
            trackSelector.setParameters(
                trackSelector.buildUponParameters().addOverride(override)
            )
        } else {
            // Disable all subtitle tracks by disabling the group
            val override = TrackSelectionOverride(group.mediaTrackGroup, emptyList())
            trackSelector.setParameters(
                trackSelector.buildUponParameters().addOverride(override)
            )
        }
        updateSnapshot()
    }

    fun selectSubtitleTrack(track: SubtitleTrackInfo) {
        val tracks = exoPlayer.currentTracks
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) return

        var globalIndex = 0
        for (group in textGroups) {
            for (i in 0 until group.length) {
                if (globalIndex == track.index) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().addOverride(override)
                    )
                    updateSnapshot()
                    return
                }
                globalIndex++
            }
        }
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        val tracks = exoPlayer.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return

        var globalIndex = 0
        for (group in audioGroups) {
            for (i in 0 until group.length) {
                if (globalIndex == track.index) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().addOverride(override)
                    )
                    updateSnapshot()
                    return
                }
                globalIndex++
            }
        }
    }

    fun selectVideoTrack(height: Int, fps: Int) {
        val tracks = exoPlayer.currentTracks
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty()) return

        // Match the closest available height instead of an exact match. The
        // requested height can come from a different metadata source (e.g. DASH
        // format info) than the tracks the player actually exposes (e.g. HLS
        // variants), so exact matching silently applied no override and ExoPlayer
        // fell back to its lowest-first adaptive default.
        var bestGroup: Tracks.Group? = null
        var bestIndex = -1
        var bestHeight = -1
        var bestFps = -1
        var bestDiff = Int.MAX_VALUE
        for (group in videoGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.height <= 0) continue
                val trackFps = format.frameRate.toInt().let { if (it > 0) it else 0 }
                val diff = kotlin.math.abs(format.height - height)
                val isCloser = diff < bestDiff
                val isTieBetter = diff == bestDiff &&
                    (format.height > bestHeight || (format.height == bestHeight && trackFps > bestFps))
                if (isCloser || isTieBetter) {
                    bestGroup = group
                    bestIndex = i
                    bestHeight = format.height
                    bestFps = trackFps
                    bestDiff = diff
                }
            }
        }
        if (bestGroup != null && bestIndex >= 0) {
            Log.d(TAG, "selectVideoTrack: requested ${height}p -> matched ${bestHeight}p (fps=$bestFps)")
            val override = TrackSelectionOverride(bestGroup.mediaTrackGroup, listOf(bestIndex))
            trackSelector.setParameters(
                trackSelector.buildUponParameters().addOverride(override)
            )
            updateSnapshot()
        }
    }

    private fun startSnapshotPolling() {
        scope.launch {
            while (isActive) {
                updateSnapshot()
                delay(250)
            }
        }
    }

    private fun updateSnapshot() {
        val tracks = exoPlayer.currentTracks
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        // Current quality label from the actually rendered output. currentTracks is
        // not reliable here: for an adaptive group isTrackSelected returns true
        // for every enabled track, so picking the first selected always reported
        // the lowest (e.g. 144p) regardless of playback.
        val renderedHeight = exoPlayer.videoSize.height
        val qualityLabel = if (renderedHeight > 0) "${renderedHeight}p" else ""

        // Subtitle tracks (global index across all groups)
        var subtitleIndex = 0
        val subtitleTracks = textGroups.flatMap { group ->
            (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                SubtitleTrackInfo(
                    index = subtitleIndex++,
                    name = format.label ?: format.language ?: "Unknown"
                )
            }
        }

        val isSubEnabled = textGroups.any { it.isSelected }

        // Audio tracks (global index across all groups)
        var audioIndex = 0
        var selectedAudioIndex = -1
        val audioTracks = audioGroups.flatMap { group ->
            (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                if (group.isTrackSelected(i)) selectedAudioIndex = audioIndex
                AudioTrackInfo(
                    index = audioIndex++,
                    languageCode = format.language ?: "unknown",
                    name = format.label ?: format.language ?: "Unknown"
                )
            }
        }

        _playbackState.update {
            PlayerPlaybackSnapshot(
                isPlaying = exoPlayer.isPlaying,
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0),
                duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L,
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0),
                playbackSpeed = exoPlayer.playbackParameters.speed,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                videoWidth = exoPlayer.videoSize.width,
                videoHeight = exoPlayer.videoSize.height,
                currentQualityLabel = qualityLabel,
                isSubtitlesEnabled = isSubEnabled,
                availableSubtitleTracks = subtitleTracks,
                availableAudioTracks = audioTracks,
                selectedAudioTrackIndex = selectedAudioIndex
            )
        }
    }
}
