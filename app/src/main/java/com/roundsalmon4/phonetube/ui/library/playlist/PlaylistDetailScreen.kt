package com.roundsalmon4.phonetube.ui.library.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    onVideoClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlistName by viewModel.playlistName.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(density) { 72.dp.toPx() } // approximate item height
    val itemHeightDp = 72.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (videos.isNotEmpty()) {
                FloatingActionButton(onClick = { onVideoClick(videos.first().videoId) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play all")
                }
            }
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No videos in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(videos, key = { _, video -> video.videoId }) { index, video ->
                    val isDragging = index == draggedIndex
                    val offsetY = if (isDragging) draggedOffset else 0f

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(if (isDragging) Modifier.shadow(4.dp) else Modifier)
                            .offset { IntOffset(0, offsetY.roundToInt()) }
                    ) {
                        PlaylistItem(
                            video = video,
                            index = index,
                            isDragging = isDragging,
                            onDragStart = {
                                draggedIndex = index
                                draggedOffset = 0f
                            },
                            onDrag = { delta ->
                                draggedOffset += delta
                                val currentIndex = draggedIndex
                                if (currentIndex >= 0) {
                                    val targetIndex = (currentIndex + (draggedOffset / itemHeightPx).roundToInt())
                                        .coerceIn(0, videos.size - 1)
                                    if (targetIndex != currentIndex) {
                                        viewModel.moveVideo(video.videoId, currentIndex, targetIndex)
                                        draggedIndex = targetIndex
                                        draggedOffset = 0f
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedIndex = -1
                                draggedOffset = 0f
                            },
                            onRemove = { viewModel.removeVideo(video.videoId) },
                            onClick = { onVideoClick(video.videoId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItem(
    video: PlaylistVideo,
    index: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    val bgColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent

    ListItem(
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = bgColor),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    video.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        supportingContent = {
            Text(
                video.channelName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp, 36.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
