package com.roundsalmon4.phonetube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.datastore.PreferencesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playerPreferences: PlayerPreferences,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val _showClearHistoryDialog = MutableStateFlow(false)
    val showClearHistoryDialog: StateFlow<Boolean> = _showClearHistoryDialog.asStateFlow()

    private val _showClearPlaylistsDialog = MutableStateFlow(false)
    val showClearPlaylistsDialog: StateFlow<Boolean> = _showClearPlaylistsDialog.asStateFlow()

    init {
        viewModelScope.launch {
            playerPreferences.uiState.collect { _uiState.value = it }
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
