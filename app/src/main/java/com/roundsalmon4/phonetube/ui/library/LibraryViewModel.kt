package com.roundsalmon4.phonetube.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.HistoryDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
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
            try {
                val count = playlistDao.getVideoCount(playlist.id)
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = playlist.id,
                        videoId = entry.videoId,
                        title = entry.title,
                        channelName = entry.channelName,
                        thumbnailUrl = entry.thumbnailUrl,
                        durationMs = entry.durationMs,
                        position = count
                    )
                )
                playlistDao.updatePlaylist(playlist.copy(videoCount = count + 1))
                _addToPlaylistEntry.value = null
            } catch (e: Exception) {
                Log.e(TAG, "addToPlaylist failed", e)
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val entry = _addToPlaylistEntry.value ?: return
        viewModelScope.launch {
            try {
                val id = playlistDao.insertPlaylist(
                    LocalPlaylist(name = name, createdAt = System.currentTimeMillis())
                )
                playlistDao.insertVideo(
                    PlaylistVideo(
                        playlistId = id,
                        videoId = entry.videoId,
                        title = entry.title,
                        channelName = entry.channelName,
                        thumbnailUrl = entry.thumbnailUrl,
                        durationMs = entry.durationMs,
                        position = 0
                    )
                )
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = name, createdAt = System.currentTimeMillis(), videoCount = 1))
                _addToPlaylistEntry.value = null
            } catch (e: Exception) {
                Log.e(TAG, "createPlaylistAndAdd failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "LibraryVM"
    }
}
