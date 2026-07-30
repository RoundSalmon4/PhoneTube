package com.roundsalmon4.phonetube.ui.channel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.ChannelSection
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelVM"
    }

    private val channelId: String = savedStateHandle["channelId"]!!

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _addToPlaylistVideo = MutableStateFlow<Video?>(null)
    val addToPlaylistVideo: StateFlow<Video?> = _addToPlaylistVideo.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    init {
        loadChannel()
        observeSubscription()
        loadPlaylists()
    }

    private fun loadChannel() {
        _uiState.value = ChannelUiState.Loading
        viewModelScope.launch {
            engine.getChannel(channelId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "getChannel failed", e)
                    _uiState.value = ChannelUiState.Error(e.message ?: "Failed to load channel")
                }
                .firstOrNull()
                ?.let { result ->
                    val channel = result.channel
                    val sections = result.sections
                    if (channel == null && sections.isEmpty()) {
                        _uiState.value = ChannelUiState.Error("Channel not found")
                    } else {
                        _uiState.value = ChannelUiState.Success(
                            name = channel?.name ?: channelId,
                            avatarUrl = channel?.avatarUrl,
                            subscriberCount = channel?.subscriberCount,
                            description = channel?.description,
                            sections = sections
                        )
                    }
                } ?: run {
                    _uiState.value = ChannelUiState.Error("Channel not found")
                }
        }
    }

    private fun observeSubscription() {
        viewModelScope.launch {
            subscriptionDao.isSubscribed(channelId).collect { subscribed ->
                _isSubscribed.value = subscribed
            }
        }
    }

    fun toggleSubscription() {
        viewModelScope.launch {
            if (_isSubscribed.value) {
                subscriptionDao.unsubscribe(channelId)
            } else {
                val state = _uiState.value
                val name = if (state is ChannelUiState.Success) state.name else channelId
                var avatar = if (state is ChannelUiState.Success) state.avatarUrl else null
                if (avatar.isNullOrBlank() && state is ChannelUiState.Success) {
                    val firstVideoId = state.sections.firstOrNull()?.videos?.firstOrNull()?.videoId
                    if (!firstVideoId.isNullOrBlank()) {
                        try {
                            val metadata = engine.getMetadata(firstVideoId).firstOrNull()
                            avatar = metadata?.video?.thumbnailUrl
                        } catch (_: Exception) { }
                    }
                }
                subscriptionDao.subscribe(
                    LocalSubscription(
                        channelId = channelId,
                        channelName = name,
                        thumbnailUrl = avatar.orEmpty(),
                        subscribedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { _playlists.value = it }
        }
    }

    fun showAddToPlaylistDialog(video: Video) {
        _addToPlaylistVideo.value = video
    }

    fun dismissAddToPlaylistDialog() {
        _addToPlaylistVideo.value = null
    }

    fun addToPlaylist(playlist: LocalPlaylist) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            try {
                val count = playlistDao.getVideoCount(playlist.id)
                playlistDao.insertVideo(PlaylistVideo(playlistId = playlist.id, videoId = video.videoId, title = video.title, channelName = video.author, thumbnailUrl = video.thumbnailUrl, durationMs = video.durationMs, position = count))
                playlistDao.updatePlaylist(playlist.copy(videoCount = count + 1))
                _addToPlaylistVideo.value = null
            } catch (e: Exception) {
                Log.e(TAG, "addToPlaylist failed", e)
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            try {
                val id = playlistDao.insertPlaylist(LocalPlaylist(name = name, createdAt = System.currentTimeMillis()))
                playlistDao.insertVideo(PlaylistVideo(playlistId = id, videoId = video.videoId, title = video.title, channelName = video.author, thumbnailUrl = video.thumbnailUrl, durationMs = video.durationMs, position = 0))
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = name, createdAt = System.currentTimeMillis(), videoCount = 1))
                _addToPlaylistVideo.value = null
            } catch (e: Exception) {
                Log.e(TAG, "createPlaylistAndAdd failed", e)
            }
        }
    }

    fun saveChannelPlaylist(playlist: com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist) {
        viewModelScope.launch {
            try {
                val videos = engine.getPlaylistVideos(playlist.playlistId)
                Log.d(TAG, "saveChannelPlaylist: got ${videos.size} videos for ${playlist.playlistId}")
                if (videos.isEmpty()) {
                    Log.w(TAG, "saveChannelPlaylist: no videos returned, aborting")
                    return@launch
                }
                val id = playlistDao.insertPlaylist(LocalPlaylist(name = playlist.title, createdAt = System.currentTimeMillis()))
                for ((index, video) in videos.withIndex()) {
                    playlistDao.insertVideo(PlaylistVideo(playlistId = id, videoId = video.videoId, title = video.title, channelName = video.author, thumbnailUrl = video.thumbnailUrl, durationMs = video.durationMs, position = index))
                }
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = playlist.title, createdAt = System.currentTimeMillis(), videoCount = videos.size))
                Log.d(TAG, "saveChannelPlaylist: saved ${videos.size} videos")
            } catch (e: Exception) {
                Log.e(TAG, "saveChannelPlaylist failed", e)
            }
        }
    }

    fun retry() {
        loadChannel()
    }
}

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Error(val message: String) : ChannelUiState
    data class Success(
        val name: String,
        val avatarUrl: String?,
        val subscriberCount: String?,
        val description: String?,
        val sections: List<ChannelSection>
    ) : ChannelUiState
}
