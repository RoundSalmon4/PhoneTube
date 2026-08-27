package com.roundsalmon4.phonetube.ui.settings

import android.util.Log
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
import com.roundsalmon4.phonetube.core.database.InvidiousDao
import com.roundsalmon4.phonetube.core.database.entity.InvidiousInstance
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playerPreferences: PlayerPreferences,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao,
    private val engine: YouTubeEngine,
    private val invidiousDao: InvidiousDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val invidiousInstances = MutableStateFlow<List<InvidiousInstance>>(emptyList())
    val invidiousInstancesState: StateFlow<List<InvidiousInstance>> = invidiousInstances.asStateFlow()

    companion object {
        private const val TAG = "SettingsVM"
    }

    private val _showClearHistoryDialog = MutableStateFlow(false)
    val showClearHistoryDialog: StateFlow<Boolean> = _showClearHistoryDialog.asStateFlow()

    private val _showClearPlaylistsDialog = MutableStateFlow(false)
    val showClearPlaylistsDialog: StateFlow<Boolean> = _showClearPlaylistsDialog.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    init {
        viewModelScope.launch {
            playerPreferences.uiState.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            invidiousDao.getAll().collect { invidiousInstances.value = it }
        }
    }

    fun clearImportResult() { _importResult.value = null }

    suspend fun buildExportJson(): String {
        val prefs = playerPreferences.uiState.first()
        val playlists = playlistDao.getAllPlaylists().first()
        val subscriptions = subscriptionDao.getAll().first()
        val invidiousInstances = invidiousDao.getAll().first()

        val visitorPrefs = context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
        val clearVisitorOnExit = visitorPrefs.getBoolean("clear_visitor_on_exit", false)

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
                peerTubeVideoSearchLimit = prefs.peerTubeVideoSearchLimit,
                peerTubeChannelSearchLimit = prefs.peerTubeChannelSearchLimit,
                pipEnabled = prefs.pipEnabled,
                openLinksIn = prefs.openLinksIn,
                playlistSearchLimit = prefs.playlistSearchLimit,
                feedOrder = prefs.feedOrder,
                continuePlaying = prefs.continuePlaying,
                duplicatePlaylistWarning = prefs.duplicatePlaylistWarning,
                screenProtection = prefs.screenProtection,
                incognitoMode = prefs.incognitoMode,
                feedInvidious = prefs.feedInvidious,
                clearVisitorOnExit = clearVisitorOnExit
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
            },
            invidiousInstances = invidiousInstances.map { inst ->
                com.roundsalmon4.phonetube.core.database.InvidiousInstanceExport(
                    host = inst.host,
                    name = inst.name,
                    enabled = inst.enabled
                )
            }
        )
        Log.d(TAG, "buildExportJson: exporting ${invidiousInstances.size} peertube instances")

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
                    playerPreferences.setPeerTubeVideoSearchLimit(p.peerTubeVideoSearchLimit)
                    playerPreferences.setPeerTubeChannelSearchLimit(p.peerTubeChannelSearchLimit)
                    playerPreferences.setPiPEnabled(p.pipEnabled)
                    playerPreferences.setOpenLinksIn(p.openLinksIn)
                    playerPreferences.setPlaylistSearchLimit(p.playlistSearchLimit)
                    playerPreferences.setFeedOrder(p.feedOrder)
                    playerPreferences.setContinuePlaying(p.continuePlaying)
                    playerPreferences.setDuplicatePlaylistWarning(p.duplicatePlaylistWarning)
                    playerPreferences.setScreenProtection(p.screenProtection)
                    playerPreferences.setIncognitoMode(p.incognitoMode)
                    playerPreferences.setFeedInvidious(p.feedInvidious)

                    val visitorPrefs = context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
                    visitorPrefs.edit().putBoolean("clear_visitor_on_exit", p.clearVisitorOnExit).apply()
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

                if (data.invidiousInstances != null) {
                    Log.d(TAG, "importFromJson: importing ${data.invidiousInstances.size} peertube instances")
                    for (inst in data.invidiousInstances) {
                        invidiousDao.insert(
                            InvidiousInstance(
                                host = inst.host,
                                name = inst.name,
                                enabled = inst.enabled
                            )
                        )
                    }
                    val allHosts = invidiousDao.getAll().first().joinToString(",") { it.host }
                    Log.d(TAG, "importFromJson: syncing peertube hosts pref: '$allHosts'")
                    context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("invidious_hosts", allHosts).apply()
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

    fun setPlaylistSearchLimit(limit: Int) = viewModelScope.launch {
        playerPreferences.setPlaylistSearchLimit(limit)
    }

    fun setPeerTubeVideoSearchLimit(limit: Int) = viewModelScope.launch {
        playerPreferences.setPeerTubeVideoSearchLimit(limit)
    }

    fun setPeerTubeChannelSearchLimit(limit: Int) = viewModelScope.launch {
        playerPreferences.setPeerTubeChannelSearchLimit(limit)
    }

    fun setPiPEnabled(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setPiPEnabled(enabled)
    }

    fun setOpenLinksIn(mode: String) = viewModelScope.launch {
        playerPreferences.setOpenLinksIn(mode)
    }

    fun setFeedOrder(order: List<String>) = viewModelScope.launch {
        playerPreferences.setFeedOrder(order)
    }

    fun setContinuePlaying(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setContinuePlaying(enabled)
    }

    fun setDuplicatePlaylistWarning(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setDuplicatePlaylistWarning(enabled)
    }

    fun setScreenProtection(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setScreenProtection(enabled)
    }

    fun setIncognitoMode(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setIncognitoMode(enabled)
    }

    private val _clearVisitorOnExit = kotlinx.coroutines.flow.MutableStateFlow(
        context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("clear_visitor_on_exit", false)
    )
    val clearVisitorOnExit: kotlinx.coroutines.flow.StateFlow<Boolean> = _clearVisitorOnExit.asStateFlow()

    fun setClearVisitorOnExit(enabled: Boolean) {
        context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("clear_visitor_on_exit", enabled).apply()
        _clearVisitorOnExit.value = enabled
    }

    fun setFeedInvidious(enabled: Boolean) = viewModelScope.launch {
        playerPreferences.setFeedInvidious(enabled)
    }

    fun addPeerTubeInstance(host: String, name: String) = viewModelScope.launch {
        val normalized = host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (normalized.isNotBlank()) {
            Log.d(TAG, "addPeerTubeInstance: adding '$normalized' (name='$name')")
            invidiousDao.insert(InvidiousInstance(host = normalized, name = name.ifBlank { normalized }))
            syncPeerTubeHostsPref()
        } else {
            Log.w(TAG, "addPeerTubeInstance: empty host after normalization (input='$host')")
        }
    }

    /**
     * Verifies the host actually exposes a working PeerTube API before the instance is saved.
     * Returns null when valid, otherwise a human-readable error message.
     */
    suspend fun validatePeerTubeInstance(host: String): String? = withContext(Dispatchers.IO) {
        val normalized = host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (normalized.isBlank()) return@withContext "Please enter a URL"
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("https://$normalized/api/v1/videos?count=1")
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            Log.d(TAG, "validatePeerTubeInstance($normalized): HTTP $status")
            if (status !in 200..399) {
                return@withContext "Not a valid PeerTube server (HTTP $status)"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(body)
            if (root.has("data")) {
                null
            } else {
                Log.w(TAG, "validatePeerTubeInstance($normalized): response has no data array")
                "Not a valid PeerTube server (no API data)"
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "validatePeerTubeInstance($normalized): connect failed", e)
            "Could not connect to server"
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "validatePeerTubeInstance($normalized): timed out", e)
            "Connection timed out"
        } catch (e: Exception) {
            Log.w(TAG, "validatePeerTubeInstance($normalized): unexpected error", e)
            "Could not reach server"
        } finally {
            connection?.disconnect()
        }
    }

    fun removePeerTubeInstance(host: String) = viewModelScope.launch {
        Log.d(TAG, "removePeerTubeInstance: removing '$host'")
        invidiousDao.delete(host)
        syncPeerTubeHostsPref()
    }

    fun setPeerTubeEnabled(host: String, enabled: Boolean) = viewModelScope.launch {
        Log.d(TAG, "setPeerTubeEnabled: '$host' -> $enabled")
        invidiousDao.setEnabled(host, enabled)
        syncPeerTubeHostsPref()
    }

    private fun syncPeerTubeHostsPref() {
        val enabledHosts = invidiousInstances.value.filter { it.enabled }.map { it.host }
        val hosts = enabledHosts.joinToString(",")
        Log.d(TAG, "syncPeerTubeHostsPref: ${enabledHosts.size} enabled hosts: $enabledHosts")
        context.getSharedPreferences("phonetube_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("invidious_hosts", hosts).apply()
    }

    fun showClearHistoryDialog() { _showClearHistoryDialog.value = true }
    fun dismissClearHistoryDialog() { _showClearHistoryDialog.value = false }

    fun showClearPlaylistsDialog() { _showClearPlaylistsDialog.value = true }
    fun dismissClearPlaylistsDialog() { _showClearPlaylistsDialog.value = false }

    fun clearHistory() = viewModelScope.launch {
        historyDao.clearAll()
        withContext(Dispatchers.IO) {
            engine.clearWatchHistory()
        }
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
