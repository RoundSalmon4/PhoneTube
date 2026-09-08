package com.roundsalmon4.phonetube.ui.iptv

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import com.roundsalmon4.phonetube.core.engine.model.IptvCategory
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.roundsalmon4.phonetube.ui.components.AddToPlaylistDialog
import com.roundsalmon4.phonetube.ui.components.VideoCard
import kotlinx.coroutines.launch

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
                    IconButton(onClick = { viewModel.requestRemoveProvider(selectedProvider.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove provider")
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
            error != null -> ErrorState(error)
            selectedCategoryId == null -> CategoriesList(
                categories = categories,
                loading = loading,
                onCategoryClick = { category -> viewModel.selectCategory(category.categoryId, category.name) }
            )
            else -> ChannelGrid(
                provider = selectedProvider,
                categoryName = channelCategoryName,
                channels = channels,
                loading = loading,
                onBack = { viewModel.backToCategories() },
                onChannelClick = onVideoClick,
                onChannelLongClick = { viewModel.showAddToPlaylistDialog(it) }
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
private fun ChannelGrid(
    provider: IptvProvider?,
    categoryName: String?,
    channels: List<Video>,
    loading: Boolean,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onChannelLongClick: (Video) -> Unit
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(channels, key = { it.videoId }) { video ->
                VideoCard(
                    video = video,
                    onClick = { onChannelClick(video.videoId) },
                    onLongClick = { onChannelLongClick(video) }
                )
            }
        }
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
                    label = { Text("Server (e.g. iptv.example.com:8080)") },
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