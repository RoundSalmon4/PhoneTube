package com.roundsalmon4.phonetube.ui.player

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val playlistDao: PlaylistDao,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    companion object { private const val TAG = "YTPlaylistVM" }

    val playlistId: String = savedStateHandle["playlistId"]!!
    val playlistTitle: String = savedStateHandle["playlistTitle"]!!

    private val _videos = MutableStateFlow<List<Video>?>(null)
    val videos: StateFlow<List<Video>?> = _videos.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _showDuplicateDialog = MutableStateFlow(false)
    val showDuplicateDialog: StateFlow<Boolean> = _showDuplicateDialog.asStateFlow()

    init {
        load()
        observeSaved()
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    private fun observeSaved() {
        viewModelScope.launch {
            val normalizedId = playlistId.removePrefix("VL")
            playlistDao.getSavedPlaylistIds().collect { ids ->
                _isSaved.value = normalizedId in ids.map { it.removePrefix("VL") }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val result = engine.getPlaylistVideos(playlistId)
                if (result.isEmpty()) {
                    _error.value = "This playlist could not be loaded. Try viewing from search instead."
                } else {
                    _videos.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _error.value = e.message ?: "Failed to load"
            }
        }
    }

    fun onSavePlaylist() {
        viewModelScope.launch {
            val prefs = playerPreferences.uiState.first()
            if (_isSaved.value && prefs.duplicatePlaylistWarning) {
                _showDuplicateDialog.value = true
            } else {
                savePlaylist()
            }
        }
    }

    fun confirmSaveDuplicate() {
        _showDuplicateDialog.value = false
        savePlaylist()
    }

    fun dismissDuplicateDialog() {
        _showDuplicateDialog.value = false
    }

    fun savePlaylist() {
        val v = _videos.value ?: return
        if (v.isEmpty()) return
        _saveMessage.value = "Saving..."
        viewModelScope.launch {
            try {
                val id = playlistDao.insertPlaylist(
                    LocalPlaylist(name = playlistTitle, createdAt = System.currentTimeMillis(), sourcePlaylistId = playlistId.removePrefix("VL"))
                )
                for ((index, video) in v.withIndex()) {
                    playlistDao.insertVideo(
                        PlaylistVideo(
                            playlistId = id,
                            videoId = video.videoId,
                            title = video.title,
                            channelName = video.author,
                            thumbnailUrl = video.thumbnailUrl,
                            durationMs = video.durationMs,
                            position = index
                        )
                    )
                }
                playlistDao.updatePlaylist(
                    LocalPlaylist(id = id, name = playlistTitle, createdAt = System.currentTimeMillis(), videoCount = v.size, sourcePlaylistId = playlistId.removePrefix("VL"))
                )
                _saveMessage.value = "Playlist saved"
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _saveMessage.value = "Save failed"
            }
        }
    }
}
