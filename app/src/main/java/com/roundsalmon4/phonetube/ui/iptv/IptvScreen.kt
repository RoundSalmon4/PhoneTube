package com.roundsalmon4.phonetube.ui.iptv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import com.roundsalmon4.phonetube.core.engine.model.IptvCategory
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IptvScreen(onVideoClick: (String) -> Unit) {
    val viewModel: IptvViewModel = hiltViewModel()

    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedProviderId by viewModel.selectedProviderId.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelCategoryName by viewModel.channelCategoryName.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val clearingProviderId by viewModel.clearingProviderId.collectAsStateWithLifecycle()
    val addToPlaylistVideo by viewModel.addToPlaylistVideo.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val epgLoading by viewModel.epgLoading.collectAsStateWithLifecycle()
    val showFavorites by viewModel.showFavorites.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val selectedProvider = providers.find { it.id == selectedProviderId }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("IPTV", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (providers.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { providerMenuExpanded = true }) {
                        Text(selectedProvider?.name ?: "Select provider")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    viewModel.selectProvider(provider.id)
                                    providerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (selectedProvider != null) {
                    IconButton(onClick = { viewModel.setShowFavorites(!showFavorites) }) {
                        Icon(
                            if (showFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorites"
                        )
                    }
                    if (!showFavorites) {
                        IconButton(onClick = { viewModel.requestRemoveProvider(selectedProvider.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove provider")
                        }
                    }
                }
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add provider")
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            providers.isEmpty() -> EmptyIptvState(onAddClick = { showAddDialog = true })
            error != null -> ErrorState(error.orEmpty())
            showFavorites -> FavoritesList(
                favorites = favorites,
                onBack = { viewModel.setShowFavorites(false) },
                onChannelClick = onVideoClick,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                isFavorite = { it.videoId in favoriteIds }
            )
            selectedCategoryId == null -> CategoriesList(
                categories = categories,
                loading = loading,
                onCategoryClick = { category -> viewModel.selectCategory(category.categoryId, category.name) }
            )
            else -> ChannelList(
                provider = selectedProvider,
                categoryName = channelCategoryName,
                channels = channels,
                nowPlaying = nowPlaying,
                epgLoading = epgLoading,
                loading = loading,
                onBack = { viewModel.backToCategories() },
                onChannelClick = onVideoClick,
                onChannelLongClick = { viewModel.showAddToPlaylistDialog(it) },
                onNowPlaying = { videoId, streamId -> viewModel.loadNowPlaying(videoId, streamId) },
                isFavorite = { it.videoId in favoriteIds },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            save = viewModel::addProvider,
            onSaved = { showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    clearingProviderId?.let { providerId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRemoveProvider() },
            title = { Text("Remove provider?") },
            text = { Text("This removes the IPTV provider and its saved credentials from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeProvider(providerId)
                    viewModel.dismissRemoveProvider()
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemoveProvider() }) {
                    Text("Cancel")
                }
            }
        )
    }

    addToPlaylistVideo?.let { video ->
        AddToPlaylistDialog(
            videoTitle = video.title,
            playlists = playlists,
            onDismiss = { viewModel.dismissAddToPlaylistDialog() },
            onAddToPlaylist = { playlist -> viewModel.addToPlaylist(playlist) },
            onCreatePlaylist = { name -> viewModel.createPlaylistAndAdd(name) }
        )
    }
}

@Composable
private fun EmptyIptvState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No IPTV providers yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add an Xtream Codes provider (server, username, password) to start watching live TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        OutlinedButton(onClick = onAddClick) {
            Text("Add provider")
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CategoriesList(
    categories: List<IptvCategory>,
    loading: Boolean,
    onCategoryClick: (IptvCategory) -> Unit
) {
    if (loading && categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (categories.isEmpty()) {
        Text(
            "No categories found on this provider",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(categories, key = { it.categoryId }) { category ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category) }
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ChannelList(
    provider: IptvProvider?,
    categoryName: String?,
    channels: List<Video>,
    nowPlaying: Map<String, String>,
    epgLoading: Set<String>,
    loading: Boolean,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onChannelLongClick: (Video) -> Unit,
    onNowPlaying: (String, String) -> Unit,
    isFavorite: (Video) -> Boolean,
    onToggleFavorite: (Video) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
            }
            Text(
                categoryName ?: provider?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
        if (loading && channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        if (channels.isEmpty()) {
            Text(
                "No channels in this category",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.videoId }) { video ->
                val streamId = video.videoId.substringAfterLast(':')
                LaunchedEffect(video.videoId) {
                    onNowPlaying(video.videoId, streamId)
                }
                IptvChannelRow(
                    video = video,
                    nowPlayingText = nowPlaying[video.videoId].orEmpty(),
                    epgLoading = video.videoId in epgLoading,
                    isFavorite = isFavorite(video),
                    onClick = { onChannelClick(video.videoId) },
                    onLongClick = { onChannelLongClick(video) },
                    onToggleFavorite = { onToggleFavorite(video) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FavoritesList(
    favorites: List<Video>,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    isFavorite: (Video) -> Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Favorites", style = MaterialTheme.typography.titleMedium)
        }
        if (favorites.isEmpty()) {
            Text(
                "No favorite channels yet. Tap the star on a channel to add it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(favorites, key = { it.videoId }) { video ->
                IptvChannelRow(
                    video = video,
                    nowPlayingText = "",
                    epgLoading = false,
                    isFavorite = isFavorite(video),
                    onClick = { onChannelClick(video.videoId) },
                    onLongClick = { onToggleFavorite(video) },
                    onToggleFavorite = { onToggleFavorite(video) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IptvChannelRow(
    video: Video,
    nowPlayingText: String,
    epgLoading: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (epgLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Loading program...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            } else if (nowPlayingText.isNotBlank()) {
                Text(
                    text = nowPlayingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = video.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .background(color = Color(0xFFE53935), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AddProviderDialog(
    save: suspend (String, String, String, String) -> String?,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (validating) return
        validating = true
        error = null
        scope.launch {
            val result = save(host, username, password, name)
            validating = false
            if (result == null) {
                onSaved()
            } else {
                error = result
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!validating) onDismiss() },
        title = { Text("Add IPTV Provider") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server (host, add :port only if required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (validating) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !validating) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text("Cancel")
            }
        }
    )
}