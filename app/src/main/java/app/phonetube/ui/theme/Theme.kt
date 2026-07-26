package app.phonetube.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    primary = YouTubeRed,
    onPrimary = LightBackground,
    onBackground = LightBackground,
    onSurface = LightBackground,
    onSurfaceVariant = LightBackground
)

private val AmoledDarkColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
    primary = YouTubeRed,
    onPrimary = LightBackground,
    onBackground = LightBackground,
    onSurface = LightBackground,
    onSurfaceVariant = LightBackground
)

private val LightColorScheme = lightColorScheme(
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    primary = YouTubeRed,
    onPrimary = LightBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground,
    onSurfaceVariant = DarkBackground
)

@Composable
fun PhoneTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && useAmoled -> AmoledDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
