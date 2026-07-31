package com.roundsalmon4.phonetube.ui.home

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import com.roundsalmon4.phonetube.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val addToPlaylistVideo by viewModel.addToPlaylistVideo.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    var longPressVideo by remember { mutableStateOf<Video?>(null) }
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadHome()
                viewModel.refreshHomeOnly()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    longPressVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { longPressVideo = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    "Add to playlist",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            longPressVideo = null
                            viewModel.showAddToPlaylistDialog(video)
                        }
                        .padding(vertical = 12.dp)
                )
                Text(
                    "Open channel",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            longPressVideo = null
                            onChannelClick(video.channelId)
                        }
                        .padding(vertical = 12.dp)
                )
                Text(
                    "Share",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            longPressVideo = null
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.videoId}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share video"))
                        }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }

    when (val s = state) {
        is HomeUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading...")
                }
            }
        }
        is HomeUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap to retry",
                        modifier = Modifier.clickable { viewModel.refreshAll() },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        is HomeUiState.Empty -> {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshAll() },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No videos found")
                        }
                    }
                }
            }
        }
        is HomeUiState.Success -> {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshAll() },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                val displayItems = buildList {
                    var lastSource = ""
                    for ((index, section) in s.sections.withIndex()) {
                        if (section.source.isNotEmpty() && section.source != lastSource) {
                            lastSource = section.source
                            add(HomeDisplayItem.SourceHeader(section.source))
                        }
                        add(HomeDisplayItem.Section(section, index))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 48.dp, bottom = 16.dp)
                ) {
                    items(displayItems, key = {
                        when (it) {
                            is HomeDisplayItem.SourceHeader -> "header-${it.source}"
                            is HomeDisplayItem.Section -> "section-${it.index}"
                        }
                    }) { item ->
                        when (item) {
                            is HomeDisplayItem.SourceHeader -> {
                                Text(
                                    text = item.source,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            is HomeDisplayItem.Section -> {
                                VideoRow(
                                    title = item.section.title,
                                    videos = item.section.videos,
                                    onVideoClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                    onVideoLongClick = { longPressVideo = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    addToPlaylistVideo?.let { video ->
        AddToPlaylistDialog(
            videoTitle = video.title,
            playlists = playlists,
            onDismiss = { viewModel.dismissAddToPlaylistDialog() },
            onAddToPlaylist = { viewModel.addToPlaylist(it) },
            onCreatePlaylist = { viewModel.createPlaylistAndAdd(it) }
        )
    }
}

private sealed interface HomeDisplayItem {
    data class SourceHeader(val source: String) : HomeDisplayItem
    data class Section(val section: com.roundsalmon4.phonetube.core.engine.model.HomeSection, val index: Int) : HomeDisplayItem
}

@Composable
private fun VideoRow(
    title: String,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onVideoLongClick: ((Video) -> Unit)? = null
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.videoId }) { video ->
                VideoCard(
                    video = video,
                    onClick = { onVideoClick(video.videoId) },
                    onChannelClick = onChannelClick,
                    onLongClick = { onVideoLongClick?.invoke(video) },
                    modifier = Modifier.width(320.dp)
                )
            }
        }
    }
}
