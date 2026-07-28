package com.roundsalmon4.phonetube.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roundsalmon4.phonetube.core.engine.model.SponsorSegment
import com.roundsalmon4.phonetube.player.PlayerPlaybackSnapshot
import com.roundsalmon4.phonetube.player.SponsorBlockService
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun PlayerControls(
    state: PlayerPlaybackSnapshot,
    title: String,
    sponsorSegments: List<SponsorSegment>,
    onBackClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                // Top bar: back, title, speed/quality/subtitle buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    // Speed indicator
                    Text(
                        text = formatSpeed(state.playbackSpeed),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onSpeedClick() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    // Quality button
                    val qualityText = state.currentQualityLabel.ifEmpty { "HQ" }
                    Text(
                        text = qualityText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onQualityClick() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    // Subtitles button
                    Text(
                        text = "CC",
                        color = if (state.isSubtitlesEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onSubtitleClick() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    // Audio button
                    if (state.availableAudioTracks.size > 1) {
                        Text(
                            text = "AD",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onAudioClick() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Spacer pushes bottom section to the bottom
                Spacer(modifier = Modifier.weight(1f))

                // Bottom section: seekbar + time + transport, pinned to bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Seekbar
                    var scrubbing by remember { mutableStateOf(false) }
                    var scrubPosition by remember { mutableFloatStateOf(0f) }
                    val duration = state.duration
                    val displayFraction = if (scrubbing) scrubPosition
                        else if (duration > 0) state.currentPosition.toFloat() / duration else 0f

                    Box(modifier = Modifier.fillMaxWidth()) {
                        SponsorBlockSeekbar(
                            segments = sponsorSegments,
                            durationMs = duration,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Buffered progress bar behind the slider
                        val bufferedFraction = if (duration > 0) {
                            (state.bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
                        } else 0f
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .height(4.dp)
                        ) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.3f),
                                topLeft = Offset.Zero,
                                size = Size(size.width * bufferedFraction, size.height)
                            )
                        }
                        Slider(
                            value = displayFraction,
                            onValueChange = { fraction ->
                                scrubbing = true
                                scrubPosition = fraction
                            },
                            onValueChangeFinished = {
                                onSeekTo((scrubPosition * duration).roundToInt().toLong())
                                scrubbing = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (scrubbing) (scrubPosition * duration).roundToLong() else state.currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Transport controls: rewind, play/pause, forward
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSeekBy(-10_000) }) {
                            Icon(
                                Icons.Rounded.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = { onSeekBy(10_000) }) {
                            Icon(
                                Icons.Rounded.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockSeekbar(
    segments: List<SponsorSegment>,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty() || durationMs <= 0) return

    Box(
        modifier = modifier
            .height(20.dp)
            .clip(MaterialTheme.shapes.extraSmall)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(4.dp)
        ) {
            val trackWidth = size.width
            val trackHeight = size.height

            for (segment in segments) {
                val startFraction = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val endFraction = (segment.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val color = SponsorBlockService.getCategoryColor(segment.category)

                drawRect(
                    color = color.copy(alpha = 0.8f),
                    topLeft = Offset(x = startFraction * trackWidth, y = 0f),
                    size = Size(
                        width = ((endFraction - startFraction) * trackWidth).coerceAtLeast(1f),
                        height = trackHeight
                    )
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatSpeed(speed: Float): String {
    return if (speed == 1.0f) "1x" else "%.2fx".format(speed).trimEnd('0').trimEnd('.')
}
