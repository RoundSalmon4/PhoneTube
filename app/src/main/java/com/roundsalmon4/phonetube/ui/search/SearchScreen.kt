package com.roundsalmon4.phonetube.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.roundsalmon4.phonetube.core.engine.model.SearchFilter
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import com.roundsalmon4.phonetube.ui.components.ChannelCard
import com.roundsalmon4.phonetube.ui.components.VideoCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: ((playlistId: String, playlistTitle: String) -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val addToPlaylistVideo by viewModel.addToPlaylistVideo.collectAsStateWithLifecycle()
    val subscribedChannels by viewModel.subscribedChannels.collectAsStateWithLifecycle()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsStateWithLifecycle()
    val pendingSavePlaylist by viewModel.pendingSavePlaylist.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var longPressedVideo by remember { mutableStateOf<Video?>(null) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSaveMessage()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    longPressedVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { longPressedVideo = null }) {
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
                            longPressedVideo = null
                            viewModel.showAddToPlaylistDialog(video)
                        }
                        .padding(vertical = 12.dp)
                )
                Text(
                    "Open channel",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            longPressedVideo = null
                            if (video.channelId.isNotBlank()) {
                                onChannelClick(video.channelId)
                            } else if (video.videoId.isNotBlank()) {
                                viewModel.fetchChannelIdForVideo(video.videoId) { id ->
                                    if (id.isNotBlank()) onChannelClick(id)
                                }
                            }
                        }
                        .padding(vertical = 12.dp)
                )
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

    pendingSavePlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveDuplicate() },
            title = { Text("Save duplicate?") },
            text = { Text("\"${playlist.title}\" is already in your library. Save a duplicate anyway?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveDuplicate() }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveDuplicate() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search YouTube") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.onSearch()
                keyboardController?.hide()
            })
        )

        when (val state = uiState) {
            is SearchUiState.Idle -> {
                FilterChips(filter = filter, onFilterChange = viewModel::onFilterChange)
                if (suggestions.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(suggestions, key = { it }) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion) },
                                leadingContent = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                modifier = Modifier.clickable {
                                    viewModel.onSuggestionClick(suggestion)
                                }
                            )
                        }
                    }
                }
            }

            is SearchUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is SearchUiState.Empty -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterChips(filter = filter, onFilterChange = viewModel::onFilterChange)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            is SearchUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is SearchUiState.Results -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterChips(filter = filter, onFilterChange = viewModel::onFilterChange)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.videos, key = { "vid-${it.videoId}" }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video.videoId) },
                                onChannelClick = { channelId ->
                                    if (channelId.isNotBlank()) {
                                        onChannelClick(channelId)
                                    } else if (video.videoId.isNotBlank()) {
                                        viewModel.fetchChannelIdForVideo(video.videoId) { id ->
                                            if (id.isNotBlank()) onChannelClick(id)
                                        }
                                    }
                                },
                                onLongClick = { longPressedVideo = video }
                            )
                        }
                        if (state.playlists.isNotEmpty()) {
                            item {
                                Text(
                                    "Playlists",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(state.playlists, key = { "pl-${it.playlistId}" }) { playlist ->
                                ListItem(
                                    headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(playlist.channelName) },
                                    leadingContent = {
                                        AsyncImage(
                                            model = playlist.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    trailingContent = {
                                        val isSaved = playlist.playlistId.removePrefix("VL") in savedPlaylistIds
                                        androidx.compose.material3.TextButton(onClick = {
                                            viewModel.onSavePlaylist(playlist)
                                        }) {
                                            Text(if (isSaved) "Saved" else "Save")
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        if (onPlaylistClick != null) {
                                            onPlaylistClick(playlist.playlistId, playlist.title)
                                        } else {
                                            viewModel.getPlaylistFirstVideoId(playlist) { videoId ->
                                                onVideoClick(videoId)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        if (state.channels.isNotEmpty()) {
                            items(state.channels, key = { "ch-${it.channelId}" }) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.channelId) },
                                    isSubscribed = channel.channelId in subscribedChannels,
                                    onSubscribe = { channelId ->
                                        if (channelId in subscribedChannels) {
                                            viewModel.unsubscribeFromChannel(channelId)
                                        } else {
                                            viewModel.subscribeToChannel(channelId, channel.name, channel.thumbnailUrl)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(filter: SearchFilter, onFilterChange: (SearchFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilter.entries.forEach { entry ->
            val label = when (entry) {
                SearchFilter.ALL -> "All"
                SearchFilter.VIDEOS -> "Videos"
                SearchFilter.CHANNELS -> "Channels"
                SearchFilter.PLAYLISTS -> "Playlists"
            }
            FilterChip(
                selected = filter == entry,
                onClick = { onFilterChange(entry) },
                label = { Text(label) }
            )
        }
    }
}
