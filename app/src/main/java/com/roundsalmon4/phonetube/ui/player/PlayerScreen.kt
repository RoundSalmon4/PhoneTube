package com.roundsalmon4.phonetube.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import com.roundsalmon4.phonetube.ui.components.openLink

@Composable
fun PlayerScreen(
    videoId: String,
    onBackClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val sponsorSegments by viewModel.sponsorSegments.collectAsStateWithLifecycle()
    val showSpeedPicker by viewModel.showSpeedPicker.collectAsStateWithLifecycle()
    val showQualityPicker by viewModel.showQualityPicker.collectAsStateWithLifecycle()
    val showSubtitlePicker by viewModel.showSubtitlePicker.collectAsStateWithLifecycle()
    val showAudioPicker by viewModel.showAudioPicker.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val openLinksIn by viewModel.openLinksIn.collectAsStateWithLifecycle()
    val showAddToPlaylist by viewModel.showAddToPlaylist.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val landscapeLock by viewModel.landscapeLock.collectAsStateWithLifecycle()

    // Lock landscape orientation if enabled
    LaunchedEffect(landscapeLock) {
        activity?.requestedOrientation = if (landscapeLock) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

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

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

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
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
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
                            description?.let { desc ->
                                DescriptionSection(
                                    description = desc,
                                    expanded = expanded,
                                    onToggleExpand = { expanded = !expanded },
                                    onTimestampClick = { seconds ->
                                        viewModel.seekTo(seconds * 1000)
                                    },
                                    onUrlClick = { url ->
                                        openLink(url, openLinksIn, context) { _, _ ->
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                            Row {
                                TextButton(onClick = { viewModel.showAddToPlaylist() }) {
                                    Text("Add to playlist")
                                }
                                val channelId = state.streamInfo.channelId
                                if (channelId.isNotBlank() && onChannelClick != null) {
                                    TextButton(onClick = { onChannelClick(channelId) }) {
                                        Text("Go to channel")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (playbackState.isBuffering && uiState is PlayerUiState.Ready) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
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
            onAudioClick = { viewModel.showAudioPicker() },
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

    if (showAudioPicker) {
        AudioTrackPickerSheet(
            audioTracks = playbackState.availableAudioTracks,
            selectedAudioTrackIndex = playbackState.selectedAudioTrackIndex,
            onAudioTrackSelected = { viewModel.selectAudioTrack(it) },
            onDismiss = { viewModel.hideAudioPicker() }
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            videoTitle = (viewModel.uiState.value as? PlayerUiState.Ready)?.streamInfo?.title ?: "",
            playlists = playlists,
            onDismiss = { viewModel.hideAddToPlaylist() },
            onAddToPlaylist = { viewModel.addToPlaylist(it) },
            onCreatePlaylist = { viewModel.createPlaylistAndAdd(it) }
        )
    }
}

@Composable
private fun DescriptionSection(
    description: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onTimestampClick: (Long) -> Unit,
    onUrlClick: (String) -> Unit
) {
    val timestampRegex = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{2})""")
    val urlRegex = Regex("""https?://[^\s]+""")
    val annotatedString = buildAnnotatedString {
        val text = if (expanded) description else description.lines().take(2).joinToString("\n")
        var lastIndex = 0
        val allMatches = (timestampRegex.findAll(text).map { Triple(it.range, "timestamp", it.value) } +
                urlRegex.findAll(text).map { Triple(it.range, "url", it.value) })
            .sortedBy { it.first.first }
        for ((range, type, value) in allMatches) {
            if (range.first > lastIndex) {
                append(text.substring(lastIndex, range.first))
            }
            when (type) {
                "timestamp" -> {
                    val groups = timestampRegex.find(value)?.groupValues ?: continue
                    val hours = groups[1].trimEnd(':').toIntOrNull() ?: 0
                    val minutes = groups[2].toInt()
                    val seconds = groups[3].toInt()
                    val totalSeconds = hours * 3600L + minutes * 60L + seconds
                    pushStringAnnotation("ts_$totalSeconds", value)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append(value)
                    }
                    pop()
                }
                "url" -> {
                    pushStringAnnotation("url", value)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(value)
                    }
                    pop()
                }
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            onClick = { offset ->
                annotatedString.getStringAnnotations(offset, offset).firstOrNull()?.let { annotation ->
                    when (annotation.tag) {
                        "url" -> onUrlClick(annotation.item)
                        else -> {
                            val seconds = annotation.tag.removePrefix("ts_").toLongOrNull()
                            if (seconds != null) onTimestampClick(seconds)
                        }
                    }
                }
            }
        )
        if (description.lines().size > 2) {
            TextButton(onClick = onToggleExpand) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}
