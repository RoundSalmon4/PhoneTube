package com.roundsalmon4.phonetube.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var playlistNameInput by remember { mutableStateOf("") }

    if (uiState.showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreatePlaylistDialog() },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.createPlaylist(playlistNameInput.trim())
                            playlistNameInput = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    playlistNameInput = ""
                    viewModel.dismissCreatePlaylistDialog()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (uiState.activeTab == LibraryTab.PLAYLISTS) {
                FloatingActionButton(onClick = { viewModel.showCreatePlaylistDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = uiState.activeTab == tab,
                        onClick = { viewModel.switchTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    LibraryTab.HISTORY -> "History"
                                    LibraryTab.PLAYLISTS -> "Playlists"
                                    LibraryTab.SUBSCRIPTIONS -> "Subscriptions"
                                }
                            )
                        }
                    )
                }
            }

            when (uiState.activeTab) {
                LibraryTab.HISTORY -> HistoryTab(
                    history = uiState.history,
                    onVideoClick = onVideoClick,
                    onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                    onClearAll = { viewModel.clearHistory() }
                )
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = uiState.playlists,
                    onPlaylistClick = onPlaylistClick,
                    onDeletePlaylist = { viewModel.deletePlaylist(it) }
                )
                LibraryTab.SUBSCRIPTIONS -> SubscriptionsTab(
                    subscriptions = uiState.subscriptions,
                    onChannelClick = onChannelClick
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<WatchHistoryEntry>,
    onVideoClick: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No watch history", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearAll) {
                    Text("Clear all")
                }
            }
        }
        items(history, key = { it.videoId }) { entry ->
            val progress = if (entry.durationMs > 0) {
                (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
            } else 0f

            ListItem(
                headlineContent = {
                    Text(
                        entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Box {
                        AsyncImage(
                            model = entry.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp, 36.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (progress > 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Black.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onDeleteEntry(entry.videoId) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.clickable { onVideoClick(entry.videoId) }
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (Long) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playlists", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = {
                    Text(
                        playlist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        "${playlist.videoCount} videos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDeletePlaylist(playlist) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.clickable { onPlaylistClick(playlist.id) }
            )
        }
    }
}

@Composable
private fun SubscriptionsTab(
    subscriptions: List<LocalSubscription>,
    onChannelClick: (String) -> Unit
) {
    if (subscriptions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No subscriptions", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(subscriptions, key = { it.channelId }) { sub ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onChannelClick(sub.channelId) }
            ) {
                AsyncImage(
                    model = sub.thumbnailUrl,
                    contentDescription = sub.channelName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sub.channelName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
