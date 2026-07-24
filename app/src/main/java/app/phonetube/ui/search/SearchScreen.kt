package app.phonetube.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.phonetube.core.engine.model.SearchFilter
import app.phonetube.ui.components.ChannelCard
import app.phonetube.ui.components.VideoCard

@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                                }
                            )
                        }
                        if (state.channels.isNotEmpty()) {
                            items(state.channels, key = { "ch-${it.channelId}" }) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.channelId) }
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
            }
            FilterChip(
                selected = filter == entry,
                onClick = { onFilterChange(entry) },
                label = { Text(label) }
            )
        }
    }
}

