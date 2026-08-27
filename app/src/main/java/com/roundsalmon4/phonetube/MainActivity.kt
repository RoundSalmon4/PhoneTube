package com.roundsalmon4.phonetube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import com.roundsalmon4.phonetube.core.engine.YouTubeInitializer
import com.roundsalmon4.phonetube.player.PlayerEngineController
import com.roundsalmon4.phonetube.player.PlayerStateManager
import com.roundsalmon4.phonetube.ui.navigation.AppNavigation
import com.roundsalmon4.phonetube.ui.theme.PhoneTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var youtubeInitializer: YouTubeInitializer

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
        requestNotificationPermission()
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
                    deepLinkUri = deepLinkUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playerStateManager.isPlayerScreenVisible &&
            playerController.exoPlayer.isPlaying
        ) {
            val prefs = kotlinx.coroutines.runBlocking { playerPreferences.uiState.first() }
            if (prefs.pipEnabled) {
                val videoWidth = playerController.exoPlayer.videoSize.width
                val videoHeight = playerController.exoPlayer.videoSize.height
                val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                    Rational(videoWidth, videoHeight)
                } else {
                    Rational(16, 9)
                }
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerStateManager.isPlayerScreenVisible = isInPictureInPictureMode
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (isSupportedUrl(uri)) {
            deepLinkUri.value = uri
        }
    }

    private fun isSupportedUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "vnd.youtube" || scheme == "vnd.youtube.launch") return true
        val host = uri.host?.lowercase() ?: return false
        return host == "youtube.com" ||
            host == "m.youtube.com" ||
            host == "www.youtube.com" ||
            host == "music.youtube.com" ||
            host == "youtu.be" ||
            host == "streamable.com" ||
            host == "www.streamable.com" ||
            host == "v.redd.it" ||
            host == "packaged-media.redd.it"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1001
    }
}
