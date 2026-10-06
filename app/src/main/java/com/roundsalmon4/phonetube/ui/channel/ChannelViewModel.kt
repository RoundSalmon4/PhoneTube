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
import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine
import com.roundsalmon4.phonetube.core.engine.YouTubeEngine.ChannelPlaylistsResult
import com.roundsalmon4.phonetube.core.engine.model.ChannelSection
import com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist
import com.roundsalmon4.phonetube.ui.common.PlaylistDialogController
import com.roundsalmon4.phonetube.core.engine.model.Video
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
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

    val playlistDialog = PlaylistDialogController(playlistDao, viewModelScope)
    val addToPlaylistVideo get() = playlistDialog.video
    val playlists get() = playlistDialog.playlists

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _savedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPlaylistIds: StateFlow<Set<String>> = _savedPlaylistIds.asStateFlow()

    private val _pendingSavePlaylist = MutableStateFlow<com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist?>(null)
    val pendingSavePlaylist: StateFlow<com.roundsalmon4.phonetube.core.engine.model.SearchPlaylist?> = _pendingSavePlaylist.asStateFlow()

    private val _playlistsState = MutableStateFlow(ChannelPlaylistsUiState())
    val playlistsState: StateFlow<ChannelPlaylistsUiState> = _playlistsState.asStateFlow()

    /** Handle for the next playlists page; null once the channel is exhausted. */
    private var playlistsGroup: MediaGroup? = null

    init {
        loadChannel()
        observeSubscription()
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
                        loadChannelPlaylists()
                    }
                } ?: run {
                    _uiState.value = ChannelUiState.Error("Channel not found")
                }
        }
    }

    /**
     * Playlists are loaded separately from the channel home browse because that browse
     * only returns the small preview row, which is empty for channels with a lot of them.
     */
    private fun loadChannelPlaylists() {
        if (isPeerTubeChannel) {
            Log.d(TAG, "loadChannelPlaylists: skipping, $channelId is a PeerTube channel")
            return
        }
        playlistsGroup = null
        _playlistsState.value = ChannelPlaylistsUiState(isLoading = true)
        Log.d(TAG, "loadChannelPlaylists: $channelId requesting first page")
        viewModelScope.launch {
            val result = engine.getChannelPlaylists(channelId).firstOrNull()
                ?: ChannelPlaylistsResult(emptyList(), null)
            playlistsGroup = result.nextGroup
            _playlistsState.value = ChannelPlaylistsUiState(
                items = result.playlists,
                hasMore = result.hasMore,
                isLoading = false,
                isEmpty = result.playlists.isEmpty()
            )
            Log.d(
                TAG,
                "loadChannelPlaylists: $channelId loaded ${result.playlists.size} playlists " +
                    "hasMore=${result.hasMore} empty=${result.playlists.isEmpty()} " +
                    "first=${result.playlists.firstOrNull()?.playlistId ?: "none"}"
            )
        }
    }

    fun loadMoreChannelPlaylists() {
        val state = _playlistsState.value
        val group = playlistsGroup
        if (group == null || state.isLoadingMore || state.isLoading) {
            Log.d(
                TAG,
                "loadMoreChannelPlaylists: skipped, hasGroup=${group != null} " +
                    "loadingMore=${state.isLoadingMore} loading=${state.isLoading}"
            )
            return
        }
        _playlistsState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            val before = state.items.size
            val result = engine.getMoreChannelPlaylists(channelId, group)
            playlistsGroup = result.nextGroup
            _playlistsState.value = _playlistsState.value.copy(
                items = (_playlistsState.value.items + result.playlists).distinctBy { it.playlistId },
                hasMore = result.hasMore,
                isLoadingMore = false
            )
            Log.d(
                TAG,
                "loadMoreChannelPlaylists: $channelId added ${result.playlists.size} " +
                    "total=${before} -> ${_playlistsState.value.items.size} hasMore=${result.hasMore}"
            )
        }
    }

    /**
     * Applies the playlist filter. Deliberately not called per keystroke: filtering on
     * every character re-laid out the list while the keyboard was on screen. It also
     * asks the channel search for matches, because the listings tab only has the first
     * page loaded and the playlist being looked for can be well past that.
     */
    fun applyPlaylistFilter(query: String) {
        val term = query.trim()
        _playlistsState.value = _playlistsState.value.copy(
            filter = term,
            searched = emptyList(),
            isSearching = term.isNotEmpty()
        )
        Log.d(TAG, "applyPlaylistFilter: '$term'")
        if (term.isEmpty()) return
        viewModelScope.launch {
            val found = engine.searchChannelPlaylists(channelId, term)
            _playlistsState.value = _playlistsState.value
                .copy(searched = found, isSearching = false)
            Log.d(TAG, "applyPlaylistFilter: '$term' -> ${found.size} from the channel search")
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

    private fun loadSavedPlaylistIds() {
        viewModelScope.launch {
            playlistDao.getSavedPlaylistIds().collect { ids ->
                _savedPlaylistIds.value = ids.map { it.removePrefix("VL") }.toSet()
            }
        }
    }

    fun showAddToPlaylistDialog(video: Video) = playlistDialog.show(video)

    fun dismissAddToPlaylistDialog() = playlistDialog.dismiss()

    fun addToPlaylist(playlist: LocalPlaylist) = playlistDialog.addToPlaylist(playlist)

    fun createPlaylistAndAdd(name: String) = playlistDialog.createAndAdd(name)

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

/**
 * Channel playlists are paged, so the screen shows what is loaded so far and pulls the
 * next batch on demand instead of rendering thousands of rows at once.
 */
data class ChannelPlaylistsUiState(
    val items: List<SearchPlaylist> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEmpty: Boolean = false,
    val filter: String = "",
    /** Channel search hits that are not among [items] yet, because the tab is paged. */
    val searched: List<SearchPlaylist> = emptyList(),
    val isSearching: Boolean = false
) {
    val visibleItems: List<SearchPlaylist>
        get() {
            val loaded = if (filter.isBlank()) {
                items
            } else {
                items.filter { it.title.contains(filter, ignoreCase = true) }
            }
            return (loaded + searched).distinctBy { it.playlistId }
        }
}
