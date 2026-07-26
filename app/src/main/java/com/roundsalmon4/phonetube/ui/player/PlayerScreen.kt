package com.roundsalmon4.phonetube.ui.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoId: String,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val sponsorSegments by viewModel.sponsorSegments.collectAsStateWithLifecycle()
    val showSpeedPicker by viewModel.showSpeedPicker.collectAsStateWithLifecycle()
    val showQualityPicker by viewModel.showQualityPicker.collectAsStateWithLifecycle()
    val showSubtitlePicker by viewModel.showSubtitlePicker.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Hide system bars while player is visible, restore on exit
    DisposableEffect(Unit) {
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, playbackState.isPlaying) {
        if (controlsVisible && playbackState.isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    BackHandler { onBackClick() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            is PlayerUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is PlayerUiState.Ready -> {
                val player = viewModel.playerController.exoPlayer

                if (isLandscape) {
                    // Landscape: full screen player
                    PlayerSurface(
                        player = player,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Portrait: player at top with dynamic aspect ratio, info below
                    Column(modifier = Modifier.fillMaxSize()) {
                        val videoWidth = playbackState.videoWidth
                        val videoHeight = playbackState.videoHeight
                        val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                            videoWidth.toFloat() / videoHeight.toFloat()
                        } else {
                            16f / 9f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerSurface(
                                player = player,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Video info below player in portrait
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = state.streamInfo.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = state.streamInfo.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        PlayerControls(
            state = playbackState,
            title = when (val state = uiState) {
                is PlayerUiState.Ready -> state.streamInfo.title
                else -> ""
            },
            sponsorSegments = sponsorSegments,
            onBackClick = onBackClick,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onSeekBy = { viewModel.seekBy(it) },
            onSpeedClick = { viewModel.showSpeedPicker() },
            onQualityClick = { viewModel.showQualityPicker() },
            onSubtitleClick = { viewModel.showSubtitlePicker() },
            visible = controlsVisible
        )
    }

    // Bottom sheets
    if (showSpeedPicker) {
        SpeedPickerSheet(
            currentSpeed = playbackState.playbackSpeed,
            onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { viewModel.hideSpeedPicker() }
        )
    }

    if (showQualityPicker) {
        val streamInfo = (uiState as? PlayerUiState.Ready)?.streamInfo
        if (streamInfo != null) {
            QualityPickerSheet(
                formats = streamInfo.adaptiveFormats,
                currentQualityLabel = playbackState.currentQualityLabel,
                onQualitySelected = { height, fps -> viewModel.selectVideoTrack(height, fps) },
                onDismiss = { viewModel.hideQualityPicker() }
            )
        }
    }

    if (showSubtitlePicker) {
        SubtitlePickerSheet(
            subtitles = playbackState.availableSubtitleTracks,
            isSubtitlesEnabled = playbackState.isSubtitlesEnabled,
            onSubtitleSelected = { viewModel.selectSubtitle(it) },
            onDismiss = { viewModel.hideSubtitlePicker() }
        )
    }
}
