package com.roundsalmon4.phonetube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.ExportData
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.LocalPlaylistExport
import com.roundsalmon4.phonetube.core.database.LocalSubscriptionExport
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistVideoExport
import com.roundsalmon4.phonetube.core.database.PreferencesExport
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playerPreferences: PlayerPreferences,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val _showClearHistoryDialog = MutableStateFlow(false)
    val showClearHistoryDialog: StateFlow<Boolean> = _showClearHistoryDialog.asStateFlow()

    private val _showClearPlaylistsDialog = MutableStateFlow(false)
    val showClearPlaylistsDialog: StateFlow<Boolean> = _showClearPlaylistsDialog.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    init {
        viewModelScope.launch {
            playerPreferences.uiState.collect { _uiState.value = it }
        }
    }

    fun clearExportResult() { _exportResult.value = null }
    fun clearImportResult() { _importResult.value = null }

    suspend fun buildExportJson(): String {
        val prefs = playerPreferences.uiState.first()
        val playlists = playlistDao.getAllPlaylists().first()
        val subscriptions = subscriptionDao.getAll().first()

        val exportData = ExportData(
            preferences = PreferencesExport(
                playbackSpeed = prefs.playbackSpeed,
                defaultQuality = prefs.defaultQuality,
                resumePlayback = prefs.resumePlayback,
                landscapeLock = prefs.landscapeLock,
                showMiniPlayer = prefs.showMiniPlayer,
                sponsorBlockEnabled = prefs.sponsorBlockEnabled,
                sponsorBlockCategories = prefs.sponsorBlockCategories,
                feedHome = prefs.feedHome,
                feedTrending = prefs.feedTrending,
                feedWhatToWatch = prefs.feedWhatToWatch,
                feedMusic = prefs.feedMusic,
                feedSports = prefs.feedSports,
                feedLive = prefs.feedLive,
                feedNews = prefs.feedNews,
                feedGaming = prefs.feedGaming,
                feedKids = prefs.feedKids,
                feedSubscriptions = prefs.feedSubscriptions,
                themeMode = prefs.themeMode,
                useAmoledTheme = prefs.useAmoledTheme,
                primaryColor = prefs.primaryColor,
                secondaryColor = prefs.secondaryColor,
                colorSchemeMode = prefs.colorSchemeMode,
                videoSearchLimit = prefs.videoSearchLimit,
                channelSearchLimit = prefs.channelSearchLimit,
                pipEnabled = prefs.pipEnabled
            ),
            playlists = playlists.map { playlist ->
                val videos = playlistDao.getPlaylistVideosSync(playlist.id)
                LocalPlaylistExport(
                    name = playlist.name,
                    createdAt = playlist.createdAt,
                    videos = videos.map { v ->
                        PlaylistVideoExport(
                            videoId = v.videoId,
                            title = v.title,
                            channelName = v.channelName,
                            thumbnailUrl = v.thumbnailUrl,
                            durationMs = v.durationMs,
                            position = v.position
                        )
                    }
                )
            },
            subscriptions = subscriptions.map { sub ->
                LocalSubscriptionExport(
                    channelId = sub.channelId,
                    channelName = sub.channelName,
                    thumbnailUrl = sub.thumbnailUrl,
                    subscribedAt = sub.subscribedAt
                )
            }
        )

        return withContext(Dispatchers.IO) {
            Json { prettyPrint = true }.encodeToString(ExportData.serializer(), exportData)
        }
    }

    fun importFromJson(json: String) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    Json { ignoreUnknownKeys = true }.decodeFromString(ExportData.serializer(), json)
                }

                if (data.preferences != null) {
                    val p = data.preferences
                    playerPreferences.setPlaybackSpeed(p.playbackSpeed)
                    playerPreferences.setDefaultQuality(p.defaultQuality)
                    playerPreferences.setResumePlayback(p.resumePlayback)
                    playerPreferences.setLandscapeLock(p.landscapeLock)
                    playerPreferences.setShowMiniPlayer(p.showMiniPlayer)
                    playerPreferences.setSponsorBlockEnabled(p.sponsorBlockEnabled)
                    p.sponsorBlockCategories.forEach { (cat, action) ->
                        playerPreferences.setSponsorBlockCategory(cat, action)
                    }
                    playerPreferences.setFeedEnabled("home", p.feedHome)
                    playerPreferences.setFeedEnabled("trending", p.feedTrending)
                    playerPreferences.setFeedEnabled("what_to_watch", p.feedWhatToWatch)
                    playerPreferences.setFeedEnabled("music", p.feedMusic)
                    playerPreferences.setFeedEnabled("sports", p.feedSports)
                    playerPreferences.setFeedEnabled("live", p.feedLive)
                    playerPreferences.setFeedEnabled("news", p.feedNews)
                    playerPreferences.setFeedEnabled("gaming", p.feedGaming)
                    playerPreferences.setFeedEnabled("kids", p.feedKids)
                    playerPreferences.setFeedEnabled("subscriptions", p.feedSubscriptions)
                    playerPreferences.setThemeMode(p.themeMode)
                    playerPreferences.setUseAmoledTheme(p.useAmoledTheme)
                    playerPreferences.setPrimaryColor(p.primaryColor)
                    playerPreferences.setSecondaryColor(p.secondaryColor)
                    playerPreferences.setColorSchemeMode(p.colorSchemeMode)
                    playerPreferences.setVideoSearchLimit(p.videoSearchLimit)
                    playerPreferences.setChannelSearchLimit(p.channelSearchLimit)
                    playerPreferences.setPiPEnabled(p.pipEnabled)
                }

                if (data.subscriptions != null) {
                    for (sub in data.subscriptions) {
                        subscriptionDao.subscribe(
                            com.roundsalmon4.phonetube.core.database.entity.LocalSubscription(
                                channelId = sub.channelId,
                                channelName = sub.channelName,
                                thumbnailUrl = sub.thumbnailUrl,
                                subscribedAt = sub.subscribedAt
                            )
                        )
                    }
                }

                if (data.playlists != null) {
                    for (playlistData in data.playlists) {
                        val id = playlistDao.insertPlaylist(
                            LocalPlaylist(name = playlistData.name, createdAt = playlistData.createdAt)
                        )
                        for ((index, video) in playlistData.videos.withIndex()) {
                            playlistDao.insertVideo(
                                com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo(
                                    playlistId = id,
                                    videoId = video.videoId,
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    durationMs = video.durationMs,
                                    position = video.position
                                )
                            )
                        }
                        playlistDao.updatePlaylist(
                            LocalPlaylist(id = id, name = playlistData.name, createdAt = playlistData.createdAt, videoCount = playlistData.videos.size)
                        )
                    }
                }

                _importResult.value = "Import complete"
            } catch (e: Exception) {
                _importResult.value = "Import failed: ${e.message?.take(100)}"
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch {
        playerPreferences.setPlaybackSpeed(speed)
    }

    fun setDefaultQuality(quality: String) = viewModelScope.launch {
        playerPreferences.setDefaultQuality(quality)
    }

    fun setResumePlayback(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setResumePlayback(enabled)
    }

    fun setLandscapeLock(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setLandscapeLock(enabled)
    }

    fun setShowMiniPlayer(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setShowMiniPlayer(enabled)
    }

    fun setSponsorBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setSponsorBlockEnabled(enabled)
    }

    fun setSponsorBlockCategory(category: String, action: String) = viewModelScope.launch {
        playerPreferences.setSponsorBlockCategory(category, action)
    }

    fun setFeedEnabled(feed: String, enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setFeedEnabled(feed, enabled)
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        playerPreferences.setThemeMode(mode)
    }

    fun setUseAmoledTheme(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setUseAmoledTheme(enabled)
    }

    fun setPrimaryColor(color: Int) = viewModelScope.launch {
        playerPreferences.setPrimaryColor(color)
    }

    fun setSecondaryColor(color: Int) = viewModelScope.launch {
        playerPreferences.setSecondaryColor(color)
    }

    fun setColorSchemeMode(mode: String) = viewModelScope.launch {
        playerPreferences.setColorSchemeMode(mode)
    }

    fun setVideoSearchLimit(limit: Int) = viewModelScope.launch {
        playerPreferences.setVideoSearchLimit(limit)
    }

    fun setChannelSearchLimit(limit: Int) = viewModelScope.launch {
        playerPreferences.setChannelSearchLimit(limit)
    }

    fun setPiPEnabled(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setPiPEnabled(enabled)
    }

    fun setOpenLinksIn(mode: String) = viewModelScope.launch {
        playerPreferences.setOpenLinksIn(mode)
    }

    fun showClearHistoryDialog() { _showClearHistoryDialog.value = true }
    fun dismissClearHistoryDialog() { _showClearHistoryDialog.value = false }

    fun showClearPlaylistsDialog() { _showClearPlaylistsDialog.value = true }
    fun dismissClearPlaylistsDialog() { _showClearPlaylistsDialog.value = false }

    fun clearHistory() = viewModelScope.launch {
        historyDao.clearAll()
        _showClearHistoryDialog.value = false
    }

    fun clearPlaylists() = viewModelScope.launch {
        val playlists = playlistDao.getAllPlaylists().first()
        for (playlist in playlists) {
            playlistDao.clearPlaylist(playlist.id)
            playlistDao.deletePlaylist(playlist)
        }
        _showClearPlaylistsDialog.value = false
    }
}
