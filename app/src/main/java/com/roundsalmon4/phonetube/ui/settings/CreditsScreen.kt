package com.roundsalmon4.phonetube.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight

data class CreditEntry(
    val name: String,
    val description: String,
    val url: String,
    val license: String
)

private val credits = listOf(
    CreditEntry(
        name = "SmartTube",
        description = "YouTube client engine, MediaServiceCore, and code patterns",
        url = "https://github.com/yuliskov/SmartTube",
        license = "MIT"
    ),
    CreditEntry(
        name = "Nuvio Mobile",
        description = "Player architecture design reference",
        url = "https://github.com/NuvioMedia/NuvioMobile",
        license = "GPL-3.0"
    ),
    CreditEntry(
        name = "SponsorBlock",
        description = "Sponsor segment detection API",
        url = "https://github.com/ajay-ay/sponsorblock",
        license = "AGPL-3.0"
    ),
    CreditEntry(
        name = "MediaServiceCore",
        description = "YouTube API engine (git submodule)",
        url = "https://github.com/yuliskov/MediaServiceCore",
        license = "MIT"
    ),
    CreditEntry(
        name = "SharedModules",
        description = "Shared utilities and GlobalPreferences (git submodule)",
        url = "https://github.com/yuliskov/SharedModules",
        license = "MIT"
    ),
    CreditEntry(
        name = "Coil",
        description = "Image loading for Compose",
        url = "https://github.com/coil-kt/coil",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Hilt",
        description = "Dependency injection",
        url = "https://github.com/google/dagger",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Media3 ExoPlayer",
        description = "Video playback engine",
        url = "https://github.com/androidx/media",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "OkHttp",
        description = "HTTP client",
        url = "https://github.com/square/okhttp",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Retrofit",
        description = "REST API client",
        url = "https://github.com/square/retrofit",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Kotlinx Serialization",
        description = "JSON serialization for export, navigation routes",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Room",
        description = "Local database for history, playlists, subscriptions, feed cache",
        url = "https://developer.android.com/jetpack/androidx/releases/room",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "DataStore",
        description = "Preferences storage",
        url = "https://developer.android.com/topic/libraries/architecture/datastore",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Navigation Compose",
        description = "Type-safe screen navigation",
        url = "https://developer.android.com/develop/ui/compose/navigation",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Kotlin Coroutines",
        description = "Async runtime for network calls and database operations",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "RxJava 2",
        description = "Reactive streams bridge for MediaServiceCore",
        url = "https://github.com/ReactiveX/RxJava",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "Cronet",
        description = "Network stack via OkHttp Cronet interceptor",
        url = "https://developer.android.com/guide/topics/connectivity/cronet",
        license = "Apache 2.0"
    ),
    CreditEntry(
        name = "J2V8",
        description = "JavaScript runtime for PoToken generation and nsig solver",
        url = "https://github.com/eclipsesource/J2V8",
        license = "EPL-1.0"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(onBackClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            credits.forEachIndexed { index, credit ->
                ListItem(
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri(credit.url) }
                    },
                    headlineContent = { Text(credit.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${credit.description} • ${credit.license}") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    }
                )
                if (index < credits.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}
