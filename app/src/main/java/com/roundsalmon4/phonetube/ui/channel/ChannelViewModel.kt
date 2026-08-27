package com.roundsalmon4.phonetube.ui.channel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.PlaylistSaver
import com.roundsalmon4.phonetube.core.database.PlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.SubscriptionDao
import com.roundsalmon4.phonetube.core.database.toPlaylistVideoInfo
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.model.ChannelSection
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: YouTubeEngine,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val playerPreferences: PlayerPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelVM"
    }

    private val channelId: String = savedStateHandle["channelId"]!!

    private val isPeerTubeChannel: Boolean
        get() = channelId.startsWith("peertube:")

    private fun peerTubeHost(): String = channelId.removePrefix("peertube:").substringBefore(":")
    private fun peerTubeName(): String = channelId.removePrefix("peertube:").substringAfter(":", "")

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _addToPlaylistVideo = MutableStateFlow<Video?>(null)
    val addToPlaylistVideo: StateFlow<Video?> = _addToPlaylistVideo.asStateFlow()

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _savedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPlaylistIds: StateFlow<Set<String>> = _savedPlaylistIds.asStateFlow()

    private val _pendingSavePlaylist = MutableStateFlow<com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist?>(null)
    val pendingSavePlaylist: StateFlow<com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist?> = _pendingSavePlaylist.asStateFlow()

    init {
        loadChannel()
        observeSubscription()
        loadPlaylists()
        loadSavedPlaylistIds()
    }

    private fun loadChannel() {
        _uiState.value = ChannelUiState.Loading
        viewModelScope.launch {
            if (isPeerTubeChannel) {
                loadPeerTubeChannel()
                return@launch
            }
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
                            sections = sections
                        )
                    }
                } ?: run {
                    _uiState.value = ChannelUiState.Error("Channel not found")
                }
        }
    }

    private fun loadPeerTubeChannel() {
        val host = peerTubeHost()
        val name = peerTubeName()
        Log.d(TAG, "loadPeerTubeChannel: host=$host name=$name")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                engine.getPeerTubeChannel(host, name)
            }
            val channel = result.channel
            if (channel == null && result.sections.isEmpty()) {
                Log.e(TAG, "loadPeerTubeChannel: channel not found $host/$name")
                _uiState.value = ChannelUiState.Error("Channel not found")
            } else {
                Log.d(TAG, "loadPeerTubeChannel: loaded '${channel?.name}' with ${result.sections.size} sections")
                _uiState.value = ChannelUiState.Success(
                    name = channel?.name ?: name,
                    avatarUrl = channel?.avatarUrl,
                    subscriberCount = channel?.subscriberCount,
                    sections = result.sections
                )
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
                Log.d(TAG, "toggleSubscription: unsubscribed $channelId")
            } else {
                val state = _uiState.value
                val name = if (state is ChannelUiState.Success) state.name else channelId
                var avatar = if (state is ChannelUiState.Success) state.avatarUrl else null
                if (avatar.isNullOrBlank() && state is ChannelUiState.Success && !isPeerTubeChannel) {
                    val firstVideoId = state.sections.firstOrNull()?.videos?.firstOrNull()?.videoId
                    if (!firstVideoId.isNullOrBlank()) {
                        try {
                            val metadata = engine.getMetadata(firstVideoId).firstOrNull()
                            avatar = metadata?.video?.thumbnailUrl
                        } catch (_: Exception) { }
                    }
                }
                Log.d(TAG, "toggleSubscription: subscribe channelId=$channelId name=$name avatar=${!avatar.isNullOrBlank()}")
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

    private fun loadSavedPlaylistIds() {
        viewModelScope.launch {
            playlistDao.getSavedPlaylistIds().collect { ids ->
                _savedPlaylistIds.value = ids.map { it.removePrefix("VL") }.toSet()
            }
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
            if (PlaylistSaver.addToPlaylist(playlistDao, video.toPlaylistVideoInfo(), playlist)) {
                _addToPlaylistVideo.value = null
            } else {
                Log.e(TAG, "addToPlaylist failed")
            }
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _addToPlaylistVideo.value ?: return
        viewModelScope.launch {
            if (PlaylistSaver.createAndAdd(playlistDao, video.toPlaylistVideoInfo(), name)) {
                _addToPlaylistVideo.value = null
            } else {
                Log.e(TAG, "createPlaylistAndAdd failed")
            }
        }
    }

    fun clearSaveMessage() { _saveMessage.value = null }

    fun onSavePlaylist(playlist: com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist) {
        viewModelScope.launch {
            val prefs = playerPreferences.uiState.first()
            val isSaved = playlist.playlistId.removePrefix("VL") in _savedPlaylistIds.value
            if (isSaved && prefs.duplicatePlaylistWarning) {
                _pendingSavePlaylist.value = playlist
            } else {
                saveChannelPlaylist(playlist)
            }
        }
    }

    fun confirmSaveDuplicate() {
        val playlist = _pendingSavePlaylist.value ?: return
        _pendingSavePlaylist.value = null
        saveChannelPlaylist(playlist)
    }

    fun dismissSaveDuplicate() {
        _pendingSavePlaylist.value = null
    }

    fun saveChannelPlaylist(playlist: com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist) {
        viewModelScope.launch {
            try {
                val videos = engine.getPlaylistVideos(playlist.playlistId)
                Log.d(TAG, "saveChannelPlaylist: got ${videos.size} videos for ${playlist.playlistId}")
                if (videos.isEmpty()) {
                    Log.w(TAG, "saveChannelPlaylist: no videos returned")
                    _saveMessage.value = "Could not save this playlist"
                    return@launch
                }
                val id = playlistDao.insertPlaylist(LocalPlaylist(name = playlist.title, createdAt = System.currentTimeMillis(), sourcePlaylistId = playlist.playlistId.removePrefix("VL")))
                for ((index, video) in videos.withIndex()) {
                    playlistDao.insertVideo(PlaylistVideo(playlistId = id, videoId = video.videoId, title = video.title, channelName = video.author, thumbnailUrl = video.thumbnailUrl, durationMs = video.durationMs, position = index))
                }
                playlistDao.updatePlaylist(LocalPlaylist(id = id, name = playlist.title, createdAt = System.currentTimeMillis(), videoCount = videos.size, sourcePlaylistId = playlist.playlistId.removePrefix("VL")))
                Log.d(TAG, "saveChannelPlaylist: saved ${videos.size} videos")
                _saveMessage.value = "Playlist saved"
            } catch (e: Exception) {
                Log.e(TAG, "saveChannelPlaylist failed", e)
                _saveMessage.value = "Save failed"
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
        val sections: List<ChannelSection>
    ) : ChannelUiState
}
