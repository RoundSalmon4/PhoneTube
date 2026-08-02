package com.roundsalmon4.phonetube.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

@Composable
fun SubtitleOverlay(
    player: Player?,
    fontSizeSp: Float = 18f,
    modifier: Modifier = Modifier
) {
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val visibleCues = currentCues.filter { !it.text.isNullOrBlank() }
    if (visibleCues.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            visibleCues.forEach { cue ->
                Text(
                    text = cue.text.toString(),
                    color = Color.White,
                    fontSize = fontSizeSp.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        background = Color.Black.copy(alpha = 0.5f),
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = androidx.compose.ui.geometry.Offset.Zero,
                            blurRadius = 8f
                        )
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
