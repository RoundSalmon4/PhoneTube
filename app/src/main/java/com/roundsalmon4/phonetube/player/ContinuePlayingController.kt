package com.roundsalmon4.phonetube.player

import androidx.media3.common.Player
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the app which video to play next. Lives outside any screen so
 * auto-advance keeps working when the player screen is closed (mini player,
 * other tabs), not just while the player is on top.
 */
data class NextVideoToPlay(
    val videoId: String,
    val queue: List<String>
)

@Singleton
class ContinuePlayingController @Inject constructor(
    private val playerController: PlayerEngineController,
    private val playerPreferences: PlayerPreferences,
    private val engine: YouTubeEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _nextVideo = MutableStateFlow<NextVideoToPlay?>(null)
    val nextVideo: StateFlow<NextVideoToPlay?> = _nextVideo.asStateFlow()

    private var currentVideoId = ""
    private var currentQueue: List<String> = emptyList()

    init {
        playerController.exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { advance() }
                }
            }
        })
    }

    /** Records what the player is now showing so end-of-video knows the context. */
    fun onPlaybackStarted(videoId: String, queue: List<String>) {
        currentVideoId = videoId
        currentQueue = queue
        // Any pending navigation from a previous video must not hijack this one.
        _nextVideo.value = null
    }

    /** Advanced from the TV when a casted video ends. */
    fun onCastEnded() {
        scope.launch { advance() }
    }

    fun consume() {
        _nextVideo.value = null
    }

    /**
     * Advance through an explicit queue (e.g. Play All) first, otherwise the
     * next suggested video when continue-playing is enabled.
     */
    private suspend fun advance() {
        if (currentVideoId.isBlank()) return
        if (currentQueue.isNotEmpty()) {
            val nextId = currentQueue.first()
            _nextVideo.value = NextVideoToPlay(nextId, currentQueue.drop(1))
            return
        }
        val prefs = playerPreferences.uiState.first()
        if (!prefs.continuePlaying) return
        try {
            val meta = engine.getMetadata(currentVideoId).firstOrNull()
            val next = meta?.suggestions?.firstOrNull()
            if (next != null) {
                _nextVideo.value = NextVideoToPlay(next.videoId, emptyList())
            }
        } catch (_: Exception) {
        }
    }
}