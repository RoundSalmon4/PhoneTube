package com.roundsalmon4.phonetube.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.roundsalmon4.phonetube.core.engine.model.Video

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubePlaylistScreen(
    playlistId: String,
    playlistTitle: String,
    onVideoClick: (String) -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onBackClick: () -> Unit,
    viewModel: YouTubePlaylistViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var longPressedVideo by remember { mutableStateOf<Video?>(null) }

    longPressedVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { longPressedVideo = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                if (onChannelClick != null && video.channelId.isNotBlank()) {
                    Text("Go to channel", modifier = Modifier.fillMaxWidth().clickable {
                        longPressedVideo = null; onChannelClick(video.channelId)
                    }.padding(vertical = 12.dp))
                }
            }
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (videos != null && videos!!.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { viewModel.savePlaylist() }) {
                            Text("Save")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (videos != null && videos!!.isNotEmpty()) {
                FloatingActionButton(onClick = { onVideoClick(videos!!.first().videoId) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play all")
                }
            }
        }
    ) { padding ->
        when {
            videos == null && error == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            videos?.isEmpty() == true -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(videos ?: emptyList(), key = { _, v -> v.videoId }) { index, video ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            supportingContent = { Text(video.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop) },
                            modifier = Modifier.combinedClickable(
                                onClick = { onVideoClick(video.videoId) },
                                onLongClick = { longPressedVideo = video }
                            )
                        )
                    }
                }
            }
        }
    }
}
