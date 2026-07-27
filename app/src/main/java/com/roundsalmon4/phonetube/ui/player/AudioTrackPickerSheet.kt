package com.roundsalmon4.phonetube.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onAudioTrackSelected: (AudioTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Text(
            text = "Audio Track",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (audioTracks.isEmpty()) {
            Text(
                text = "No audio tracks available",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            audioTracks.forEach { track ->
                androidx.compose.material3.ListItem(
                    headlineContent = {
                        Text(track.name.ifBlank { track.languageCode })
                    },
                    supportingContent = {
                        Text(track.languageCode)
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
