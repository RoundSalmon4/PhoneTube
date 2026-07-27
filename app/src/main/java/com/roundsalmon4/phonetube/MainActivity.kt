package com.roundsalmon4.phonetube

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.YouTubeInitializer
import com.roundsalmon4.phonetube.player.PlayerEngineController
import com.roundsalmon4.phonetube.player.PlayerStateManager
import com.roundsalmon4.phonetube.ui.navigation.AppNavigation
import com.roundsalmon4.phonetube.ui.theme.PhoneTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var youtubeInitializer: YouTubeInitializer

    @Inject
    lateinit var engine: YouTubeEngine

    @Inject
    lateinit var playerPreferences: PlayerPreferences

    @Inject
    lateinit var playerStateManager: PlayerStateManager

    @Inject
    lateinit var playerController: PlayerEngineController

    val deepLinkUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            youtubeInitializer.warmup()
        }
        handleIntent(intent)
        setContent {
            val prefs by playerPreferences.uiState.collectAsState(initial = PreferencesUiState())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (prefs.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            PhoneTubeTheme(
                darkTheme = darkTheme,
                useAmoled = prefs.useAmoledTheme,
                primaryColor = prefs.primaryColor,
                secondaryColor = prefs.secondaryColor,
                colorSchemeMode = prefs.colorSchemeMode
            ) {
                AppNavigation(
                    playerStateManager = playerStateManager,
                    playerController = playerController,
                    playerPreferences = playerPreferences,
                    deepLinkUri = deepLinkUri,
                    engine = engine
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (isYouTubeUrl(uri)) {
            deepLinkUri.value = uri
        }
    }

    private fun isYouTubeUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "youtube.com" ||
            host == "m.youtube.com" ||
            host == "www.youtube.com" ||
            host == "music.youtube.com" ||
            host == "youtu.be"
    }
}
