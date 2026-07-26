package app.phonetube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.phonetube.core.datastore.PlayerPreferences
import app.phonetube.core.engine.YouTubeInitializer
import app.phonetube.ui.navigation.AppNavigation
import app.phonetube.ui.theme.PhoneTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var youtubeInitializer: YouTubeInitializer

    @Inject
    lateinit var playerPreferences: PlayerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            youtubeInitializer.warmup()
        }
        setContent {
            val prefs = playerPreferences.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (prefs.value.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            PhoneTubeTheme(
                darkTheme = darkTheme,
                useAmoled = prefs.value.useAmoledTheme
            ) {
                AppNavigation()
            }
        }
    }
}
