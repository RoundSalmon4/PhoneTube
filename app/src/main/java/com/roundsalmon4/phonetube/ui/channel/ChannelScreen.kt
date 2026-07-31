package com.roundsalmon4.phonetube.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import com.roundsalmon4.phonetube.ui.components.VideoCard
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onPlaylistClick: ((playlistId: String, playlistTitle: String) -> Unit)? = null,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val addToPlaylistVideo by viewModel.addToPlaylistVideo.collectAsStateWithLifecycle()
    val channelPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var longPressedVideo by remember { mutableStateOf<Video?>(null) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSaveMessage()
        }
    }

    longPressedVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { longPressedVideo = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                Text("Add to playlist", modifier = Modifier.fillMaxWidth().clickable {
                    longPressedVideo = null
                    viewModel.showAddToPlaylistDialog(video)
                }.padding(vertical = 12.dp))
                Text("Go to channel", modifier = Modifier.fillMaxWidth().clickable {
                    longPressedVideo = null
                    if (video.channelId.isNotBlank()) onChannelClick(video.channelId)
                }.padding(vertical = 12.dp))
            }
        }
    }

    addToPlaylistVideo?.let { video ->
        AddToPlaylistDialog(
            videoTitle = video.title,
            playlists = channelPlaylists,
            onDismiss = { viewModel.dismissAddToPlaylistDialog() },
            onAddToPlaylist = { viewModel.addToPlaylist(it) },
            onCreatePlaylist = { viewModel.createPlaylistAndAdd(it) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                when (val state = uiState) {
                    is ChannelUiState.Success -> Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else -> Text("Channel")
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        when (val state = uiState) {
            is ChannelUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ChannelUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to retry", modifier = Modifier.clickable { viewModel.retry() }, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            is ChannelUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        ChannelHeader(name = state.name, avatarUrl = state.avatarUrl, subscriberCount = state.subscriberCount, isSubscribed = isSubscribed, onSubscribeClick = { viewModel.toggleSubscription() })
                    }
                    // Aggregate all playlists from all sections
                    val allPlaylists = state.sections.flatMap { it.playlists }
                    items(state.sections.filter { it.videos.isNotEmpty() }, key = { it.title }) { section ->
                        ChannelVideoRow(title = section.title, videos = section.videos, onVideoClick = onVideoClick, onChannelClick = onChannelClick, onVideoLongClick = { longPressedVideo = it })
                    }
                    if (allPlaylists.isNotEmpty()) {
                        item {
                            Text("Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(allPlaylists, key = { it.playlistId }) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(playlist.channelName) },
                                leadingContent = { AsyncImage(model = playlist.thumbnailUrl, contentDescription = null, modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop) },
                                trailingContent = {
                                    Row {
                                        if (onPlaylistClick != null) {
                                            TextButton(onClick = { onPlaylistClick(playlist.playlistId, playlist.title) }) { Text("View") }
                                        }
                                        TextButton(onClick = {
                                            viewModel.saveChannelPlaylist(playlist)
                                        }) { Text("Save") }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (onPlaylistClick != null) onPlaylistClick(playlist.playlistId, playlist.title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(name: String, avatarUrl: String?, subscriberCount: String?, isSubscribed: Boolean, onSubscribeClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(model = avatarUrl, contentDescription = name, modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subscriberCount.isNullOrBlank()) Text(text = subscriberCount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSubscribed) OutlinedButton(onClick = onSubscribeClick) { Text("Subscribed") }
            else Button(onClick = onSubscribeClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Subscribe") }
        }
    }
}

@Composable
private fun ChannelVideoRow(title: String, videos: List<Video>, onVideoClick: (String) -> Unit, onChannelClick: ((String) -> Unit)? = null, onVideoLongClick: ((Video) -> Unit)? = null) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        if (title.isNotBlank()) Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.videoId }) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video.videoId) }, onChannelClick = onChannelClick, onLongClick = if (onVideoLongClick != null) {{ onVideoLongClick(video) }} else null, modifier = Modifier.width(320.dp))
            }
        }
    }
}
