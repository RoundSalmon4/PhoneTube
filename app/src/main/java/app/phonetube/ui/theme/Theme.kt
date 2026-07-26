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

private fun Color.lighten(factor: Float = 0.4f): Color {
    val red = red + (1f - red) * factor
    val green = green + (1f - green) * factor
    val blue = blue + (1f - blue) * factor
    return Color(red, green, blue, alpha)
}

@Composable
fun PhoneTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAmoled: Boolean = false,
    primaryColor: Int = 0xFFFF0000.toInt(),
    secondaryColor: Int = 0xFF282828.toInt(),
    content: @Composable () -> Unit
) {
    val primary = Color(primaryColor)
    val secondary = Color(secondaryColor)

    val colorScheme = when {
        darkTheme && useAmoled -> darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1A1A1A),
            primary = primary.lighten(),
            secondary = secondary,
            tertiary = secondary.lighten(),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White
        )
        darkTheme -> darkColorScheme(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            primary = primary.lighten(),
            secondary = secondary,
            tertiary = secondary.lighten(),
            onPrimary = Color.White,
            onBackground = LightBackground,
            onSurface = LightBackground,
            onSurfaceVariant = LightBackground
        )
        else -> lightColorScheme(
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            primary = primary,
            secondary = secondary,
            tertiary = secondary.lighten(0.3f),
            onPrimary = Color.White,
            onBackground = DarkBackground,
            onSurface = DarkBackground,
            onSurfaceVariant = DarkBackground
        )
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
