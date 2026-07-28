package com.roundsalmon4.phonetube.ui.library.playlist

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
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
        .map { playlists ->
            val name = playlists.find { it.id == playlistId }?.name ?: "Playlist"
            Log.d(TAG, "playlistName: id=$playlistId, name=$name, total=${playlists.size}")
            name
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Playlist")

    val videos: StateFlow<List<PlaylistVideo>> = playlistDao.getPlaylistVideos(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            try {
                playlistDao.removeVideo(playlistId, videoId)
                val playlist = playlistDao.getPlaylistById(playlistId)
                if (playlist != null) {
                    val count = playlistDao.getVideoCount(playlistId)
                    playlistDao.updatePlaylist(playlist.copy(videoCount = count))
                }
                Log.d(TAG, "Removed $videoId from playlist $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove video", e)
            }
        }
    }

    fun moveVideo(videoId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                val current = playlistDao.getPlaylistVideosSync(playlistId)
                if (fromIndex < 0 || fromIndex >= current.size || toIndex < 0 || toIndex >= current.size) return@launch
                val mutable = current.toMutableList()
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                for ((newPos, video) in mutable.withIndex()) {
                    playlistDao.updateVideoPosition(playlistId, video.videoId, newPos)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move video", e)
            }
        }
    }
}
