package com.roundsalmon4.phonetube.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerEngineController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setMaxVideoSizeSd())
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
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

    fun playDash(manifestUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(manifestUrl)
            .setMimeType("application/dash+xml")
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun playHls(manifestUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(manifestUrl)
            .setMimeType("application/x-mpegURL")
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun playUrl(url: String, mimeType: String?) {
        val builder = MediaItem.Builder().setUri(url)
        mimeType?.let { builder.setMimeType(it) }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
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

        val group = textGroups.first()
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(track.index))
        trackSelector.setParameters(
            trackSelector.buildUponParameters().addOverride(override)
        )
        updateSnapshot()
    }

    fun selectVideoTrack(height: Int, fps: Int) {
        val tracks = exoPlayer.currentTracks
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty()) return

        for (group in videoGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val matchesHeight = format.height == height
                val matchesFps = if (fps > 0) format.frameRate.toInt() == fps else true
                if (matchesHeight && matchesFps) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().addOverride(override)
                    )
                    updateSnapshot()
                    return
                }
            }
        }
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
        scope.cancel()
        exoPlayer.release()
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
            val format = group.getTrackFormat(0)
            val height = format.height
            val fps = format.frameRate.toInt()
            if (height > 0) {
                if (fps > 0) "${height}p${fps}" else "${height}p"
            } else null
        } ?: ""

        // Subtitle tracks
        val subtitleTracks = textGroups.flatMap { group ->
            (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                SubtitleTrackInfo(
                    index = i,
                    languageCode = format.language ?: "unknown",
                    name = format.label ?: format.language ?: "Unknown",
                    mimeType = format.sampleMimeType ?: ""
                )
            }
        }

        val isSubEnabled = textGroups.any { it.isSelected }

        _playbackState.update {
            PlayerPlaybackSnapshot(
                isPlaying = exoPlayer.isPlaying,
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0),
                duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L,
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0),
                playbackSpeed = exoPlayer.playbackParameters.speed,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                isLive = exoPlayer.isCurrentMediaItemLive,
                videoWidth = exoPlayer.videoSize.width,
                videoHeight = exoPlayer.videoSize.height,
                currentQualityLabel = qualityLabel,
                isSubtitlesEnabled = isSubEnabled,
                availableSubtitleTracks = subtitleTracks,
                audioTrackCount = audioGroups.size
            )
        }
    }
}
