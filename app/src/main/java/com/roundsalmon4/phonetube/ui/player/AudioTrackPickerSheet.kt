package com.roundsalmon4.phonetube.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roundsalmon4.phonetube.player.AudioTrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackPickerSheet(
    audioTracks: List<AudioTrackInfo>,
    selectedAudioTrackIndex: Int = -1,
    onAudioTrackSelected: (AudioTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Text(
            text = "Audio Track",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (audioTracks.isEmpty()) {
            Text(
                text = "No audio tracks available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            audioTracks.forEach { track ->
                ListItem(
                    headlineContent = {
                        Text(track.name.ifBlank { track.languageCode })
                    },
                    supportingContent = {
                        Text(track.languageCode)
                    },
                    trailingContent = {
                        if (track.index == selectedAudioTrackIndex) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        onAudioTrackSelected(track)
                        onDismiss()
                    }
                )
            }
        }
    }
}
