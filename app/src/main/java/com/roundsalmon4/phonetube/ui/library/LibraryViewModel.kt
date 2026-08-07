package com.roundsalmon4.phonetube.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.PlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { HISTORY, PLAYLISTS, SUBSCRIPTIONS }

data class LibraryUiState(
    val activeTab: LibraryTab = LibraryTab.HISTORY,
    val history: List<WatchHistoryEntry> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val subscriptions: List<LocalSubscription> = emptyList(),
    val showCreatePlaylistDialog: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao
) : ViewModel() {

    private val _activeTab = MutableStateFlow(LibraryTab.HISTORY)
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    private val _addToPlaylistEntry = MutableStateFlow<WatchHistoryEntry?>(null)
    val addToPlaylistEntry: StateFlow<WatchHistoryEntry?> = _addToPlaylistEntry.asStateFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        _activeTab,
        historyDao.getAll(),
        playlistDao.getAllPlaylists(),
        subscriptionDao.getAll(),
        _showCreatePlaylistDialog
    ) { tab, history, playlists, subscriptions, showDialog ->
        Log.d(TAG, "library state: tab=$tab history=${history.size} firstDuration=${history.firstOrNull()?.durationMs} firstPos=${history.firstOrNull()?.positionMs}")
        LibraryUiState(
            activeTab = tab,
            history = history,
            playlists = playlists,
            subscriptions = subscriptions,
            showCreatePlaylistDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun switchTab(tab: LibraryTab) {
        _activeTab.value = tab
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.insertPlaylist(
                LocalPlaylist(
                    name = name,
                    createdAt = System.currentTimeMillis()
                )
            )
            _showCreatePlaylistDialog.value = false
        }
    }

    fun showCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = true
    }

    fun dismissCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = false
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist)
        }
    }

    fun deleteHistoryEntry(videoId: String) {
        viewModelScope.launch {
            historyDao.delete(videoId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun showAddToPlaylistDialog(entry: WatchHistoryEntry) {
        _addToPlaylistEntry.value = entry
    }

    fun dismissAddToPlaylistDialog() {
        _addToPlaylistEntry.value = null
    }

    fun addToPlaylist(playlist: LocalPlaylist) {
        val entry = _addToPlaylistEntry.value ?: return
        viewModelScope.launch {
            if (PlaylistSaver.addToPlaylist(playlistDao, entry.toPlaylistVideoInfo(), playlist)) {
                _addToPlaylistEntry.value = null
            } else {
                Log.e(TAG, "addToPlaylist failed")
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val entry = _addToPlaylistEntry.value ?: return
        viewModelScope.launch {
            if (PlaylistSaver.createAndAdd(playlistDao, entry.toPlaylistVideoInfo(), name)) {
                _addToPlaylistEntry.value = null
            } else {
                Log.e(TAG, "createPlaylistAndAdd failed")
            }
        }
    }

    companion object {
        private const val TAG = "LibraryVM"
    }
}

private fun WatchHistoryEntry.toPlaylistVideoInfo() = PlaylistVideoInfo(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs
)
