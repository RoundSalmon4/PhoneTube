package com.roundsalmon4.phonetube.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState

private val PRIMARY_COLORS = listOf(
    0xFFFF0000.toInt() to "Red",
    0xFFE91E63.toInt() to "Pink",
    0xFF9C27B0.toInt() to "Purple",
    0xFF673AB7.toInt() to "Deep Purple",
    0xFF3F51B5.toInt() to "Indigo",
    0xFF2196F3.toInt() to "Blue",
    0xFF03A9F4.toInt() to "Light Blue",
    0xFF00BCD4.toInt() to "Cyan",
    0xFF009688.toInt() to "Teal",
    0xFF4CAF50.toInt() to "Green",
    0xFFFF9800.toInt() to "Orange",
    0xFF795548.toInt() to "Brown"
)

private val SECONDARY_COLORS = listOf(
    0xFF282828.toInt() to "Dark Gray",
    0xFF424242.toInt() to "Gray",
    0xFF616161.toInt() to "Medium Gray",
    0xFF757575.toInt() to "Gray 500",
    0xFF9E9E9E.toInt() to "Gray 400",
    0xFFBDBDBD.toInt() to "Light Gray",
    0xFFE0E0E0.toInt() to "Gray 300",
    0xFFF5F5F5.toInt() to "Gray 100",
    0xFFFF5722.toInt() to "Deep Orange",
    0xFFFF9800.toInt() to "Orange",
    0xFF4CAF50.toInt() to "Green",
    0xFF2196F3.toInt() to "Blue"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLicenseClick: () -> Unit,
    onCreditsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showClearHistoryDialog by viewModel.showClearHistoryDialog.collectAsState()
    val showClearPlaylistsDialog by viewModel.showClearPlaylistsDialog.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            PlayerSection(uiState, viewModel)
            SponsorBlockSection(uiState, viewModel)
            FeedsSection(uiState, viewModel)
            AppearanceSection(uiState, viewModel)
            DataSection(viewModel)
            AboutSection(onLicenseClick, onCreditsClick)

            val context = LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName ?: ""
                } catch (_: Exception) { "" }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearHistoryDialog() },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear your watch history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearHistoryDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearPlaylistsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearPlaylistsDialog() },
            title = { Text("Clear Playlists") },
            text = { Text("Are you sure you want to delete all playlists? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPlaylists() }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearPlaylistsDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        SettingsCategory("Player")

        var speedExpanded by remember { mutableStateOf(false) }
        val speeds = listOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "2.5x", "3.0x")
        val currentSpeed = "${uiState.playbackSpeed}x"

        ExposedDropdownMenuBox(
            expanded = speedExpanded,
            onExpandedChange = { speedExpanded = it }
        ) {
            OutlinedTextField(
                value = currentSpeed,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Speed") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speedExpanded) }
            )
            ExposedDropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                speeds.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(speed) },
                        onClick = {
                            viewModel.setPlaybackSpeed(speed.removeSuffix("x").toFloat())
                            speedExpanded = false
                        }
                    )
                }
            }
        }

        var qualityExpanded by remember { mutableStateOf(false) }
        val qualities = listOf("AUTO", "2160p", "1080p", "720p", "480p", "360p")

        ExposedDropdownMenuBox(
            expanded = qualityExpanded,
            onExpandedChange = { qualityExpanded = it }
        ) {
            OutlinedTextField(
                value = uiState.defaultQuality,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Quality") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) }
            )
            ExposedDropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                qualities.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality) },
                        onClick = {
                            viewModel.setDefaultQuality(quality)
                            qualityExpanded = false
                        }
                    )
                }
            }
        }

        SwitchItem(
            name = "Resume Playback",
            description = "Remember position and resume where you left off",
            checked = uiState.resumePlayback,
            onCheckedChange = { viewModel.setResumePlayback(it) }
        )

        SwitchItem(
            name = "Landscape Lock",
            description = "Force landscape orientation during playback",
            checked = uiState.landscapeLock,
            onCheckedChange = { viewModel.setLandscapeLock(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsorBlockSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("SponsorBlock")

        SwitchItem(
            name = "SponsorBlock",
            description = "Skip sponsor segments and other interruptions",
            checked = uiState.sponsorBlockEnabled,
            onCheckedChange = { viewModel.setSponsorBlockEnabled(it) }
        )

        if (uiState.sponsorBlockEnabled) {
            val categories = listOf(
                "sponsor" to "Sponsor",
                "intro" to "Intro",
                "outro" to "Outro",
                "interaction" to "Interaction Reminder",
                "selfpromo" to "Self-Promotion",
                "music_offtopic" to "Non-Music Section",
                "preview" to "Preview",
                "poi_highlight" to "Highlight",
                "filler" to "Filler"
            )

            categories.forEach { (key, label) ->
                var expanded by remember { mutableStateOf(false) }
                val currentAction = uiState.sponsorBlockCategories[key] ?: "skip"
                val displayAction = currentAction.replaceFirstChar { it.uppercase() }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = displayAction,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("skip" to "Skip", "toast" to "Toast", "none" to "None").forEach { (action, display) ->
                            DropdownMenuItem(
                                text = { Text(display) },
                                onClick = {
                                    viewModel.setSponsorBlockCategory(key, action)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedsSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Feeds")

        val feeds = listOf(
            "home" to "Home",
            "trending" to "Trending",
            "what_to_watch" to "What to Watch",
            "music" to "Music",
            "sports" to "Sports",
            "live" to "Live",
            "news" to "News",
            "gaming" to "Gaming",
            "kids" to "Kids"
        )

        feeds.forEach { (key, label) ->
            val enabled = when (key) {
                "home" -> uiState.feedHome
                "trending" -> uiState.feedTrending
                "what_to_watch" -> uiState.feedWhatToWatch
                "music" -> uiState.feedMusic
                "sports" -> uiState.feedSports
                "live" -> uiState.feedLive
                "news" -> uiState.feedNews
                "gaming" -> uiState.feedGaming
                "kids" -> uiState.feedKids
                else -> true
            }

            SwitchItem(
                name = label,
                description = if (enabled) "Enabled" else "Disabled — won't load on startup",
                checked = enabled,
                onCheckedChange = { viewModel.setFeedEnabled(key, it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Appearance")

        var themeExpanded by remember { mutableStateOf(false) }
        val themes = listOf("SYSTEM" to "Follow System", "LIGHT" to "Light", "DARK" to "Dark")
        val currentTheme = themes.firstOrNull { it.first == uiState.themeMode }?.second ?: "Follow System"

        ExposedDropdownMenuBox(
            expanded = themeExpanded,
            onExpandedChange = { themeExpanded = it }
        ) {
            OutlinedTextField(
                value = currentTheme,
                onValueChange = {},
                readOnly = true,
                label = { Text("Theme") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) }
            )
            ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                themes.forEach { (value, display) ->
                    DropdownMenuItem(
                        text = { Text(display) },
                        onClick = {
                            viewModel.setThemeMode(value)
                            themeExpanded = false
                        }
                    )
                }
            }
        }

        SwitchItem(
            name = "AMOLED Dark",
            description = "Pure black background for OLED screens",
            checked = uiState.useAmoledTheme,
            onCheckedChange = { viewModel.setUseAmoledTheme(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
        SettingsCategory("Colors")

        Text(
            text = "Primary Color",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ColorSwatchRow(
            colors = PRIMARY_COLORS,
            selectedColor = uiState.primaryColor,
            onColorSelected = { viewModel.setPrimaryColor(it) }
        )

        Text(
            text = "Secondary Color",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ColorSwatchRow(
            colors = SECONDARY_COLORS,
            selectedColor = uiState.secondaryColor,
            onColorSelected = { viewModel.setSecondaryColor(it) }
        )
    }
}

@Composable
private fun ColorSwatchRow(
    colors: List<Pair<Int, String>>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        colors.forEach { (colorInt, name) ->
            val isSelected = colorInt == selectedColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    )
                    .clickable { onColorSelected(colorInt) }
            )
        }
    }
}

@Composable
private fun DataSection(viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Data")

        ListItem(
            modifier = Modifier.clickable { viewModel.showClearHistoryDialog() },
            headlineContent = { Text("Clear History", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Remove all watch history") }
        )

        ListItem(
            modifier = Modifier.clickable { viewModel.showClearPlaylistsDialog() },
            headlineContent = { Text("Clear Playlists", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Delete all playlists and their videos") }
        )
    }
}

@Composable
private fun AboutSection(onLicenseClick: () -> Unit, onCreditsClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("About")

        ListItem(
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri("https://github.com/RoundSalmon4/SmartTube") }
            },
            headlineContent = { Text("Source Code", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("View the project on GitHub") },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            }
        )

        ListItem(
            modifier = Modifier.clickable { onLicenseClick() },
            headlineContent = { Text("License", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("MIT License") },
            trailingContent = {
                Icon(Icons.Filled.Info, contentDescription = null)
            }
        )

        ListItem(
            modifier = Modifier.clickable { onCreditsClick() },
            headlineContent = { Text("Credits", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Third-party libraries and attributions") },
            trailingContent = {
                Icon(Icons.Filled.Info, contentDescription = null)
            }
        )
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SwitchItem(
    name: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        }
    )
}
