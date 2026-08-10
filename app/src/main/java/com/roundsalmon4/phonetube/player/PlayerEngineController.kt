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

class PlayerEngineController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val dataSourceFactory = DefaultDataSource.Factory(context)

    private val trackSelector = DefaultTrackSelector(context)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            PlaybackRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
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

        // Match by height only, preferring the best (highest) frame rate at that height.
        var bestGroup: Tracks.Group? = null
        var bestIndex = -1
        var bestFps = -1
        for (group in videoGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.height == height) {
                    val trackFps = format.frameRate.toInt()
                    if (trackFps > bestFps) {
                        bestIndex = i
                        bestFps = trackFps
                        bestGroup = group
                    }
                }
            }
        }
        if (bestGroup != null && bestIndex >= 0) {
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
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        // Current quality label from selected video track
        val qualityLabel = videoGroups.firstOrNull { it.isSelected }?.let { group ->
            val selectedIndex = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
            val format = group.getTrackFormat(selectedIndex)
            val height = format.height
            val fps = format.frameRate.toInt()
            if (height > 0) {
                if (fps > 0) "${height}p${fps}" else "${height}p"
            } else null
        } ?: ""

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
