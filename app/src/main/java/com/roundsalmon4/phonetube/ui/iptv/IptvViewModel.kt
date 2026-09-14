package com.roundsalmon4.phonetube.ui.iptv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundsalmon4.phonetube.core.database.IptvChannelDao
import com.roundsalmon4.phonetube.core.database.IptvDao
import com.roundsalmon4.phonetube.core.database.IptvFavoriteDao
import com.roundsalmon4.phonetube.core.database.PlaylistDao
import com.roundsalmon4.phonetube.core.database.entity.IptvFavorite
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.engine.XtreamClient
import com.roundsalmon4.phonetube.ui.common.PlaylistDialogController
import com.roundsalmon4.phonetube.core.engine.model.IptvCategory
import com.roundsalmon4.phonetube.core.engine.model.IptvLiveStream
import com.roundsalmon4.phonetube.core.engine.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val iptvDao: IptvDao,
    private val iptvFavoriteDao: IptvFavoriteDao,
    private val iptvChannelDao: IptvChannelDao,
    private val playlistDao: PlaylistDao,
    private val xtreamClient: XtreamClient
) : ViewModel() {

    companion object {
        private const val TAG = "IptvVM"
        // How long a cached channel list is considered fresh before the app
        // checks the provider again in the background.
        private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }

    private val _providers = MutableStateFlow<List<IptvProvider>>(emptyList())
    val providers: StateFlow<List<IptvProvider>> = _providers.asStateFlow()

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val _categories = MutableStateFlow<List<IptvCategory>>(emptyList())
    val categories: StateFlow<List<IptvCategory>> = _categories.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _channels = MutableStateFlow<List<Video>>(emptyList())
    val channels: StateFlow<List<Video>> = _channels.asStateFlow()

    private val _channelCategoryName = MutableStateFlow<String?>(null)
    val channelCategoryName: StateFlow<String?> = _channelCategoryName.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _clearingProviderId = MutableStateFlow<String?>(null)
    val clearingProviderId: StateFlow<String?> = _clearingProviderId.asStateFlow()

    // Provider-wide channel search (channels recur across categories, so a
    // whole-provider list makes it easy to find a channel anywhere).
    private val _allChannels = MutableStateFlow<List<Video>>(emptyList())
    private var allChannelsLoadedFor: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Video>>(emptyList())
    val searchResults: StateFlow<List<Video>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    val playlistDialog = PlaylistDialogController(playlistDao, viewModelScope)
    val addToPlaylistVideo get() = playlistDialog.video
    val playlists get() = playlistDialog.playlists

    // videoId -> "Title" (or "" when the provider has no EPG for that stream).
    // An entry also acts as a cache so scrolling does not refetch.
    private val _nowPlaying = MutableStateFlow<Map<String, String>>(emptyMap())
    val nowPlaying: StateFlow<Map<String, String>> = _nowPlaying.asStateFlow()

    // videoIds whose short EPG is still being fetched, so rows can show a
    // loading indicator until now playing is known.
    private val _epgLoading = MutableStateFlow<Set<String>>(emptySet())
    val epgLoading: StateFlow<Set<String>> = _epgLoading.asStateFlow()

    private val _showFavorites = MutableStateFlow(false)
    val showFavorites: StateFlow<Boolean> = _showFavorites.asStateFlow()

    private val _favorites = MutableStateFlow<List<Video>>(emptyList())
    val favorites: StateFlow<List<Video>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        viewModelScope.launch {
            iptvDao.getAll().collect { providers ->
                _providers.value = providers
                val selected = _selectedProviderId.value
                if (selected == null && providers.isNotEmpty()) {
                    val first = providers.first()
                    _selectedProviderId.value = first.id
                    loadCategories(first.id)
                } else if (selected != null && providers.none { it.id == selected }) {
                    _selectedProviderId.value = null
                    _categories.value = emptyList()
                    _channels.value = emptyList()
                    _selectedCategoryId.value = null
                }
            }
        }
        viewModelScope.launch {
            iptvFavoriteDao.getAll().collect { favorites ->
                _favorites.value = favorites.map { it.toVideo() }
                _favoriteIds.value = favorites.map { it.videoId }.toSet()
            }
        }
    }

    fun toggleFavorite(video: Video) {
        viewModelScope.launch {
            if (video.videoId in _favoriteIds.value) {
                Log.d(TAG, "toggleFavorite: removing ${video.videoId}")
                iptvFavoriteDao.delete(video.videoId)
            } else {
                Log.d(TAG, "toggleFavorite: adding ${video.videoId} ('${video.title}')")
                iptvFavoriteDao.insert(
                    IptvFavorite(
                        videoId = video.videoId,
                        title = video.title,
                        providerName = video.author,
                        iconUrl = video.thumbnailUrl
                    )
                )
            }
        }
    }

    fun setShowFavorites(show: Boolean) {
        Log.d(TAG, "setShowFavorites: $show")
        _showFavorites.value = show
        _selectedCategoryId.value = null
        _channelCategoryName.value = null
        _channels.value = emptyList()
        _error.value = null
    }

    private fun IptvFavorite.toVideo(): Video = Video(
        videoId = videoId,
        title = title,
        author = providerName,
        channelId = "",
        thumbnailUrl = iconUrl,
        durationMs = 0L,
        publishedDate = 0L,
        percentWatched = 0,
        source = null,
        channelHost = null
    )

    fun showAddToPlaylistDialog(video: Video) = playlistDialog.show(video)
    fun dismissAddToPlaylistDialog() = playlistDialog.dismiss()
    fun addToPlaylist(playlist: LocalPlaylist) = playlistDialog.addToPlaylist(playlist)
    fun createPlaylistAndAdd(name: String) = playlistDialog.createAndAdd(name)

    fun selectProvider(id: String) {
        Log.d(TAG, "selectProvider: $id")
        _selectedProviderId.value = id
        _selectedCategoryId.value = null
        _channelCategoryName.value = null
        _channels.value = emptyList()
        _error.value = null
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _allChannels.value = emptyList()
        allChannelsLoadedFor = null
        loadCategories(id)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val q = query.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        val provider = _providers.value.find { it.id == _selectedProviderId.value } ?: return
        val all = _allChannels.value
        if (all.isEmpty()) {
            // One in-flight full-provider fetch will compute the results from
            // the latest query; avoid stacking requests per keystroke.
            if (!_searchLoading.value) loadAllChannels()
        } else {
            _searchResults.value = all.filter { it.title.contains(q, ignoreCase = true) }
        }
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    /**
     * Loads the provider-wide channel list for search. Prefers the cached Room
     * copy so search is instant on subsequent app opens; the provider is only
     * re-queried in the background when the cache is stale.
     */
    private fun loadAllChannels() {
        val provider = _providers.value.find { it.id == _selectedProviderId.value } ?: return
        if (_searchLoading.value) return
        if (allChannelsLoadedFor == provider.id && _allChannels.value.isNotEmpty()) {
            _searchResults.value = _allChannels.value.filter { it.title.contains(_searchQuery.value.trim(), ignoreCase = true) }
            return
        }
        viewModelScope.launch {
            val cached = iptvChannelDao.getAllForProvider(provider.id)
            if (cached.isNotEmpty()) {
                Log.d(TAG, "loadAllChannels(${provider.host}): using ${cached.size} cached channels")
                val videos = cached.map { it.toVideo(provider) }
                _allChannels.value = videos
                allChannelsLoadedFor = provider.id
                _searchResults.value = videos.filter { it.title.contains(_searchQuery.value.trim(), ignoreCase = true) }
                val stale = cached.any { it.cachedAt < System.currentTimeMillis() - CACHE_TTL_MS }
                if (stale) refreshAllChannels(provider)
            } else {
                refreshAllChannels(provider)
            }
        }
    }

    /**
     * Fetches every live stream of the provider and merges it into the cached
     * channel list. Idempotent: the cache is only rewritten when the list of
     * channel ids actually changes, so an unchanged provider is not re-cached
     * on every visit.
     */
    private fun refreshAllChannels(provider: IptvProvider) {
        if (_searchLoading.value) return
        _searchLoading.value = true
        viewModelScope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    xtreamClient.liveStreams(provider.host, provider.username, provider.password, categoryId = null)
                }
                val videos = streams.map { it.toVideo(provider) }
                Log.d(TAG, "refreshAllChannels(${provider.host}): ${videos.size} total channels")

                val cached = iptvChannelDao.getAllForProvider(provider.id)
                val cachedIds = cached.map { it.streamId }.toSet()
                val newIds = streams.map { it.streamId }.toSet()
                if (cached.isEmpty() || newIds != cachedIds) {
                    val now = System.currentTimeMillis()
                    iptvChannelDao.deleteForProvider(provider.id)
                    iptvChannelDao.insertAll(
                        streams.map {
                            IptvChannel(
                                id = "${provider.id}|${it.streamId}",
                                providerId = provider.id,
                                streamId = it.streamId,
                                title = it.name,
                                iconUrl = it.iconUrl.orEmpty(),
                                cachedAt = now
                            )
                        }
                    )
                    Log.d(TAG, "refreshAllChannels(${provider.host}): cache updated (changed or first fill)")
                } else {
                    Log.d(TAG, "refreshAllChannels(${provider.host}): list unchanged, cache left intact")
                }

                _allChannels.value = videos
                allChannelsLoadedFor = provider.id
                _searchResults.value = videos.filter { it.title.contains(_searchQuery.value.trim(), ignoreCase = true) }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAllChannels failed", e)
                if (_allChannels.value.isEmpty()) {
                    val fallback = iptvChannelDao.getAllForProvider(provider.id)
                    if (fallback.isNotEmpty()) {
                        Log.w(TAG, "refreshAllChannels(${provider.host}): falling back to cached channels")
                        _allChannels.value = fallback.map { it.toVideo(provider) }
                        _searchResults.value = _allChannels.value.filter { it.title.contains(_searchQuery.value.trim(), ignoreCase = true) }
                    }
                }
            } finally {
                _searchLoading.value = false
            }
        }
    }

    private fun IptvChannel.toVideo(provider: IptvProvider): Video = Video(
        videoId = "iptv:${provider.id}:$streamId",
        title = title,
        author = provider.name,
        channelId = "",
        thumbnailUrl = iconUrl,
        durationMs = 0L,
        publishedDate = 0L,
        percentWatched = 0,
        source = null,
        channelHost = null
    )

    fun selectCategory(categoryId: String, categoryName: String) {
        Log.d(TAG, "selectCategory: $categoryId ($categoryName)")
        _selectedCategoryId.value = categoryId
        _channelCategoryName.value = categoryName
        _error.value = null
        loadChannels(categoryId)
    }

    fun backToCategories() {
        Log.d(TAG, "backToCategories")
        _selectedCategoryId.value = null
        _channelCategoryName.value = null
        _channels.value = emptyList()
        _error.value = null
    }

    /**
     * Fetches the short EPG for one stream and stores the currently airing
     * program. Called lazily from each visible channel row; the map entry
     * doubles as a cache so a stream is only fetched once per visit.
     */
    fun loadNowPlaying(videoId: String, streamId: String) {
        if (_nowPlaying.value.containsKey(videoId) || videoId in _epgLoading.value) return
        val provider = _providers.value.find { it.id == _selectedProviderId.value } ?: return
        _epgLoading.value = _epgLoading.value + videoId
        viewModelScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    xtreamClient.shortEpg(provider.host, provider.username, provider.password, streamId, provider.timezone)
                }
                val now = System.currentTimeMillis()
                val current = programs.firstOrNull { it.startEpoch <= now && now < it.endEpoch }
                val label = current?.let { program ->
                    val end = java.time.Instant.ofEpochMilli(program.endEpoch)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    "${program.title} - until $end"
                }.orEmpty()
                Log.d(TAG, "loadNowPlaying(stream=$streamId): ${if (label.isBlank()) "no current program" else label}")
                _nowPlaying.value = _nowPlaying.value + (videoId to label)
            } catch (e: Exception) {
                Log.e(TAG, "loadNowPlaying(stream=$streamId) failed", e)
                _nowPlaying.value = _nowPlaying.value + (videoId to "")
            } finally {
                _epgLoading.value = _epgLoading.value - videoId
            }
        }
    }

    /**
     * Validates credentials via the player API, then saves the provider.
     * Returns null on success, otherwise a message explaining the failure.
     */
    suspend fun addProvider(host: String, username: String, password: String, name: String): String? {
        val normalized = host.trim()
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/').trim()
        if (normalized.isBlank()) return "Please enter a server URL"
        if (username.isBlank() || password.isBlank()) return "Username and password are required"

        val auth = xtreamClient.authenticate(normalized, username, password)
        if (auth == null) {
            Log.w(TAG, "addProvider($normalized): authentication request failed")
            return "Could not connect to the provider"
        }
        val valid = auth.auth || auth.status.equals("Active", ignoreCase = true)
        if (!valid) {
            Log.w(TAG, "addProvider($normalized): rejected, auth=${auth.auth} status=${auth.status}")
            return if (auth.status.isNotBlank()) {
                "Provider rejected the credentials (status: ${auth.status})"
            } else {
                "Provider rejected the credentials"
            }
        }
        val id = IptvProvider.makeId(normalized, username)
        val displayName = name.ifBlank { auth.serverName?.takeIf { it.isNotBlank() } ?: normalized }
        val scheme = auth.scheme?.takeIf { it == "http" || it == "https" } ?: "https"
        val timezone = auth.timezone.orEmpty()
        Log.d(TAG, "addProvider: '$normalized' validated OK (auth=${auth.auth}, status=${auth.status}, exp=${auth.expDate}, scheme=$scheme, tz=$timezone)")
        Log.d(TAG, "addProvider: saving '$displayName' ($normalized) as $id")
        iptvDao.insert(
            IptvProvider(
                id = id,
                host = normalized,
                username = username.trim(),
                password = password,
                name = displayName,
                scheme = scheme,
                timezone = timezone
            )
        )
        return null
    }

    fun removeProvider(id: String) {
        Log.d(TAG, "removeProvider: $id")
        viewModelScope.launch {
            iptvDao.delete(id)
            iptvChannelDao.deleteForProvider(id)
            if (_selectedProviderId.value == id) {
                _selectedProviderId.value = null
                _categories.value = emptyList()
                _channels.value = emptyList()
                _selectedCategoryId.value = null
            }
        }
    }

    fun requestRemoveProvider(id: String) {
        _clearingProviderId.value = id
    }

    fun dismissRemoveProvider() {
        _clearingProviderId.value = null
    }

    private fun loadCategories(providerId: String) {
        val provider = _providers.value.find { it.id == providerId } ?: return
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val cats = withContext(Dispatchers.IO) {
                    xtreamClient.liveCategories(provider.host, provider.username, provider.password)
                }
                Log.d(TAG, "loadCategories(${provider.host}): ${cats.size} categories")
                _categories.value = cats
            } catch (e: Exception) {
                Log.e(TAG, "loadCategories failed", e)
                _error.value = "Could not load categories from ${provider.name}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadChannels(categoryId: String) {
        val provider = _providers.value.find { it.id == _selectedProviderId.value } ?: return
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    xtreamClient.liveStreams(provider.host, provider.username, provider.password, categoryId)
                }
                Log.d(TAG, "loadChannels(${provider.host}, cat=$categoryId): ${streams.size} streams")
                _channels.value = streams.map { it.toVideo(provider) }
            } catch (e: Exception) {
                Log.e(TAG, "loadChannels failed", e)
                _error.value = "Could not load channels from ${provider.name}"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * IPTV channels reuse the Video model so playback, history, playlists and
     * the queue all work as they do for regular videos. The stored videoId is
     * already the fully qualified playable id (iptv:<providerKey>:<streamId>).
     */
    private fun IptvLiveStream.toVideo(provider: IptvProvider): Video = Video(
        videoId = "iptv:${provider.id}:$streamId",
        title = name,
        author = provider.name,
        channelId = "",
        thumbnailUrl = iconUrl.orEmpty(),
        durationMs = 0L,
        publishedDate = 0L,
        percentWatched = 0,
        source = null,
        channelHost = null
    )
}