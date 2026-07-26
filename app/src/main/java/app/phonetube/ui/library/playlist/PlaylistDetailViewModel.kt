package app.phonetube.ui.library.playlist

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.database.PlaylistDao
import app.phonetube.core.database.entity.PlaylistVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    companion object {
        private const val TAG = "PlaylistDetailVM"
    }

    private val playlistId: Long = savedStateHandle["playlistId"]!!

    val playlistName: StateFlow<String> = playlistDao.getAllPlaylists()
        .map { playlists -> playlists.find { it.id == playlistId }?.name ?: "Playlist" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Playlist")

    val videos: StateFlow<List<PlaylistVideo>> = playlistDao.getPlaylistVideos(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            try {
                playlistDao.removeVideo(playlistId, videoId)
                Log.d(TAG, "Removed $videoId from playlist $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove video", e)
            }
        }
    }
}
