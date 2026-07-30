package com.roundsalmon4.phonetube.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import com.roundsalmon4.phonetube.ui.components.WebViewDialog
import com.roundsalmon4.phonetube.ui.components.openLink
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

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
    val exportResult by viewModel.exportResult.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewTitle by remember { mutableStateOf("") }
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
            SearchSection(uiState, viewModel)
            AppearanceSection(uiState, viewModel)
            DataSection(viewModel, exportResult, importResult)
            AboutSection(
                onLicenseClick = onLicenseClick,
                onCreditsClick = onCreditsClick,
                openLinksIn = uiState.openLinksIn,
                onWebView = { url, title ->
                    webViewUrl = url
                    webViewTitle = title
                }
            )

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

    webViewUrl?.let { url ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { webViewUrl = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            WebViewDialog(
                url = url,
                title = webViewTitle,
                onDismiss = { webViewUrl = null }
            )
        }
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

        SwitchItem(
            name = "Mini Player",
            description = "Show mini player bar when playback continues in background",
            checked = uiState.showMiniPlayer,
            onCheckedChange = { viewModel.setShowMiniPlayer(it) }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SwitchItem(
                name = "Picture-in-Picture",
                description = "Auto-enter PiP when leaving the player screen",
                checked = uiState.pipEnabled,
                onCheckedChange = { viewModel.setPiPEnabled(it) }
            )
        }

        var linkModeExpanded by remember { mutableStateOf(false) }
        val linkModes = listOf("browser" to "Browser", "webview" to "WebView")
        val currentLinkMode = linkModes.firstOrNull { it.first == uiState.openLinksIn }?.second ?: "Browser"

        ExposedDropdownMenuBox(
            expanded = linkModeExpanded,
            onExpandedChange = { linkModeExpanded = it }
        ) {
            OutlinedTextField(
                value = currentLinkMode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Open Links In") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkModeExpanded) }
            )
            ExposedDropdownMenu(expanded = linkModeExpanded, onDismissRequest = { linkModeExpanded = false }) {
                linkModes.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.setOpenLinksIn(key)
                            linkModeExpanded = false
                        }
                    )
                }
            }
        }

        SwitchItem(
            name = "Continue Playing",
            description = "Automatically play the next suggested video when the current one ends",
            checked = uiState.continuePlaying,
            onCheckedChange = { viewModel.setContinuePlaying(it) }
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
 
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedsSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    val feedLabels = mapOf(
        "home" to "Home",
        "what_to_watch" to "What to Watch",
        "subscriptions" to "Subscriptions",
        "trending" to "Trending",
        "music" to "Music",
        "sports" to "Sports",
        "live" to "Live",
        "news" to "News",
        "gaming" to "Gaming",
        "kids" to "Kids"
    )

    val orderedFeeds = uiState.feedOrder.filter { it in feedLabels }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 56.dp.toPx() }

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Feeds")

        orderedFeeds.forEachIndexed { index, key ->
            val label = feedLabels[key] ?: key
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
                "subscriptions" -> uiState.feedSubscriptions
                else -> true
            }
            val isDragged = index == draggedIndex

            Box(
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .then(if (isDragged) Modifier.shadow(4.dp) else Modifier)
                    .offset { IntOffset(0, if (isDragged) draggedOffset.roundToInt() else 0) }
            ) {
                ListItem(
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceContainer else androidx.compose.ui.graphics.Color.Transparent
                    ),
                    headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggedIndex = index; draggedOffset = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedOffset += dragAmount.y
                                            val currentIndex = draggedIndex
                                            if (currentIndex >= 0) {
                                                val targetIndex = (currentIndex + (draggedOffset / itemHeightPx).roundToInt())
                                                    .coerceIn(0, orderedFeeds.size - 1)
                                                if (targetIndex != currentIndex) {
                                                    val mutable = orderedFeeds.toMutableList()
                                                    val item = mutable.removeAt(currentIndex)
                                                    mutable.add(targetIndex, item)
                                                    viewModel.setFeedOrder(mutable)
                                                    draggedIndex = targetIndex
                                                    draggedOffset = 0f
                                                }
                                            }
                                        },
                                        onDragEnd = { draggedIndex = -1; draggedOffset = 0f },
                                        onDragCancel = { draggedIndex = -1; draggedOffset = 0f }
                                    )
                                }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setFeedEnabled(key, it) }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Search")

        var videoLimitExpanded by remember { mutableStateOf(false) }
        val videoLimits = listOf(10, 20, 30, 50, 100)
        val currentVideoLimit = uiState.videoSearchLimit

        ExposedDropdownMenuBox(
            expanded = videoLimitExpanded,
            onExpandedChange = { videoLimitExpanded = it }
        ) {
            OutlinedTextField(
                value = "$currentVideoLimit results",
                onValueChange = {},
                readOnly = true,
                label = { Text("Video Results Limit") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoLimitExpanded) }
            )
            ExposedDropdownMenu(expanded = videoLimitExpanded, onDismissRequest = { videoLimitExpanded = false }) {
                videoLimits.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text("$limit results") },
                        onClick = {
                            viewModel.setVideoSearchLimit(limit)
                            videoLimitExpanded = false
                        }
                    )
                }
            }
        }

        var channelLimitExpanded by remember { mutableStateOf(false) }
        val channelLimits = listOf(5, 10, 20, 30, 50)
        val currentChannelLimit = uiState.channelSearchLimit

        ExposedDropdownMenuBox(
            expanded = channelLimitExpanded,
            onExpandedChange = { channelLimitExpanded = it }
        ) {
            OutlinedTextField(
                value = "$currentChannelLimit results",
                onValueChange = {},
                readOnly = true,
                label = { Text("Channel Results Limit") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelLimitExpanded) }
            )
            ExposedDropdownMenu(expanded = channelLimitExpanded, onDismissRequest = { channelLimitExpanded = false }) {
                channelLimits.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text("$limit results") },
                        onClick = {
                            viewModel.setChannelSearchLimit(limit)
                            channelLimitExpanded = false
                        }
                    )
                }
            }
        }
        
        var playlistLimitExpanded by remember { mutableStateOf(false) }
        val playlistLimits = listOf(5, 10, 20, 30, 50)
        val currentPlaylistLimit = uiState.playlistSearchLimit

        ExposedDropdownMenuBox(
            expanded = playlistLimitExpanded,
            onExpandedChange = { playlistLimitExpanded = it }
        ) {
            OutlinedTextField(
                value = "$currentPlaylistLimit results",
                onValueChange = {},
                readOnly = true,
                label = { Text("Playlist Results Limit") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playlistLimitExpanded) }
            )
            ExposedDropdownMenu(expanded = playlistLimitExpanded, onDismissRequest = { playlistLimitExpanded = false }) {
                playlistLimits.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text("$limit results") },
                        onClick = {
                            viewModel.setPlaylistSearchLimit(limit)
                            playlistLimitExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(uiState: PreferencesUiState, viewModel: SettingsViewModel) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Appearance")

        val themeModeOptions = listOf("Follow System" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK")
        val selectedThemeIndex = themeModeOptions.indexOfFirst { it.second == uiState.themeMode }.coerceAtLeast(0)

        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            themeModeOptions.forEachIndexed { index, (label, _) ->
                SegmentedButton(
                    selected = selectedThemeIndex == index,
                    onClick = { viewModel.setThemeMode(themeModeOptions[index].second) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModeOptions.size),
                    icon = {
                        Icon(
                            imageVector = when (themeModeOptions[index].second) {
                                "LIGHT" -> Icons.Filled.LightMode
                                "DARK" -> Icons.Filled.DarkMode
                                else -> Icons.Filled.BrightnessAuto
                            },
                            contentDescription = null
                        )
                    }
                ) {
                    Text(label)
                }
            }
        }

        val isDarkModeActive = uiState.themeMode == "DARK" ||
            (uiState.themeMode == "SYSTEM" && isSystemInDarkTheme())

        if (isDarkModeActive) {
            SwitchItem(
                name = "AMOLED Dark",
                description = "Pure black background for OLED screens",
                checked = uiState.useAmoledTheme,
                onCheckedChange = { viewModel.setUseAmoledTheme(it) }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colorSchemeOptions = listOf("Standard" to "STANDARD", "Dynamic Color" to "DYNAMIC_COLOR")
            val selectedColorSchemeIndex = colorSchemeOptions.indexOfFirst { it.second == uiState.colorSchemeMode }.coerceAtLeast(0)

            Text(
                text = "Color Scheme",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                colorSchemeOptions.forEachIndexed { index, (label, _) ->
                    SegmentedButton(
                        selected = selectedColorSchemeIndex == index,
                        onClick = { viewModel.setColorSchemeMode(colorSchemeOptions[index].second) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = colorSchemeOptions.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        if (uiState.colorSchemeMode == "STANDARD") {
            val presetPrimaryColors = listOf(
                0xFFFF0000.toInt() to "Red",
                0xFF1A237E.toInt() to "Blue",
                0xFF1B5E20.toInt() to "Green",
                0xFF4A148C.toInt() to "Purple",
                0xFF006064.toInt() to "Teal",
                0xFFE65100.toInt() to "Orange",
                0xFF880E4F.toInt() to "Pink",
                0xFF0D47A1.toInt() to "Indigo",
                0xFF33691E.toInt() to "Olive",
                0xFFBF360C.toInt() to "Deep Orange",
                0xFF311B92.toInt() to "Deep Purple",
                0xFF004D40.toInt() to "Dark Teal"
            )
            val presetSecondaryColors = listOf(
                0xFF282828.toInt() to "Dark Gray",
                0xFFFFD54F.toInt() to "Gold",
                0xFF00BCD4.toInt() to "Cyan",
                0xFFCDDC39.toInt() to "Lime",
                0xFFFFC107.toInt() to "Amber",
                0xFFFF5722.toInt() to "Deep Orange",
                0xFF9C27B0.toInt() to "Purple",
                0xFF03A9F4.toInt() to "Light Blue",
                0xFF66BB6A.toInt() to "Green",
                0xFFFF7043.toInt() to "Peach",
                0xFFAB47BC.toInt() to "Mauve",
                0xFF26A69A.toInt() to "Teal"
            )

            var showPrimaryColorDialog by remember { mutableStateOf(false) }
            var showSecondaryColorDialog by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text("Primary Color", fontWeight = FontWeight.SemiBold) },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(uiState.primaryColor))
                    )
                },
                modifier = Modifier.clickable { showPrimaryColorDialog = true }
            )

            ListItem(
                headlineContent = { Text("Secondary Color", fontWeight = FontWeight.SemiBold) },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(uiState.secondaryColor))
                    )
                },
                modifier = Modifier.clickable { showSecondaryColorDialog = true }
            )

            if (showPrimaryColorDialog) {
                AlertDialog(
                    onDismissRequest = { showPrimaryColorDialog = false },
                    title = { Text("Select Color") },
                    text = {
                        ColorSwatchGrid(
                            colors = presetPrimaryColors,
                            selectedColor = uiState.primaryColor,
                            onColorSelected = { color ->
                                viewModel.setPrimaryColor(color)
                                showPrimaryColorDialog = false
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showPrimaryColorDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showSecondaryColorDialog) {
                AlertDialog(
                    onDismissRequest = { showSecondaryColorDialog = false },
                    title = { Text("Select Color") },
                    text = {
                        ColorSwatchGrid(
                            colors = presetSecondaryColors,
                            selectedColor = uiState.secondaryColor,
                            onColorSelected = { color ->
                                viewModel.setSecondaryColor(color)
                                showSecondaryColorDialog = false
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSecondaryColorDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchGrid(
    colors: List<Pair<Int, String>>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { (colorInt, name) ->
            val isSelected = colorInt == selectedColor
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .clickable { onColorSelected(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = name,
                        tint = if (Color(colorInt).luminance() > 0.5f) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun DataSection(
    viewModel: SettingsViewModel,
    exportResult: String?,
    importResult: String?
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.buildExportJson()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    Toast.makeText(context, "Export complete", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (json != null) {
                        viewModel.importFromJson(json)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("Data")

        if (importResult != null) {
            LaunchedEffect(importResult) {
                Toast.makeText(context, importResult, Toast.LENGTH_SHORT).show()
                viewModel.clearImportResult()
            }
        }

        ListItem(
            modifier = Modifier.clickable { exportLauncher.launch("PhoneTube_backup.json") },
            headlineContent = { Text("Export Data", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Save settings, playlists, and subscriptions to a JSON file") }
        )

        ListItem(
            modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "*/*")) },
            headlineContent = { Text("Import Data", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Restore settings, playlists, and subscriptions from a JSON file") }
        )

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
private fun AboutSection(
    onLicenseClick: () -> Unit,
    onCreditsClick: () -> Unit,
    openLinksIn: String,
    onWebView: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategory("About")

        ListItem(
            modifier = Modifier.clickable {
                openLink("https://github.com/RoundSalmon4/SmartTube", openLinksIn, context, onWebView)
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
