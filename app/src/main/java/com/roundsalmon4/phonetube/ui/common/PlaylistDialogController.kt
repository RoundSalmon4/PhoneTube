package com.roundsalmon4.phonetube.ui.common

import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.toPlaylistVideoInfo
import com.roundsalmon4.phonetube.core.engine.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared logic for the "Add to playlist" dialog used across multiple
 * ViewModels. Holds the target video, the current playlist list, and
 * the add / create actions.
 */
class PlaylistDialogController(
    private val playlistDao: PlaylistDao,
    private val scope: CoroutineScope
) {
    private val _video = MutableStateFlow<Video?>(null)
    val video: StateFlow<Video?> get() = _video.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> get() = _playlists.asStateFlow()

    init {
        scope.launch {
            playlistDao.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    fun show(video: Video) { _video.value = video }
    fun dismiss() { _video.value = null }

    fun addToPlaylist(playlist: LocalPlaylist) {
        val v = _video.value ?: return
        scope.launch {
            PlaylistSaver.addToPlaylist(playlistDao, v.toPlaylistVideoInfo(), playlist)
            _video.value = null
        }
    }

    fun createAndAdd(name: String) {
        val v = _video.value ?: return
        scope.launch {
            PlaylistSaver.createAndAdd(playlistDao, v.toPlaylistVideoInfo(), name)
            _video.value = null
        }
    }
}
