package app.phonetube.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.HistoryDao
import app.phonetube.core.database.PlaylistDao
import app.phonetube.core.database.SubscriptionDao
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.LocalSubscription
import app.phonetube.core.database.entity.WatchHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
}
