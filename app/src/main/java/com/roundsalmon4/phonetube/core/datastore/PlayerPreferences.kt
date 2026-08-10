package com.roundsalmon4.phonetube.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_preferences"
)

private object Keys {
    val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
    val LANDSCAPE_LOCK = booleanPreferencesKey("landscape_lock")
    val SHOW_MINI_PLAYER = booleanPreferencesKey("show_mini_player")
    val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
    val SPONSOR_BLOCK_CATEGORIES = stringSetPreferencesKey("sponsor_block_categories")
    val FEED_HOME = booleanPreferencesKey("feed_home")
    val FEED_TRENDING = booleanPreferencesKey("feed_trending")
    val FEED_WHAT_TO_WATCH = booleanPreferencesKey("feed_what_to_watch")
    val FEED_MUSIC = booleanPreferencesKey("feed_music")
    val FEED_SPORTS = booleanPreferencesKey("feed_sports")
    val FEED_LIVE = booleanPreferencesKey("feed_live")
    val FEED_NEWS = booleanPreferencesKey("feed_news")
    val FEED_GAMING = booleanPreferencesKey("feed_gaming")
    val FEED_KIDS = booleanPreferencesKey("feed_kids")
    val FEED_SUBSCRIPTIONS = booleanPreferencesKey("feed_subscriptions")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_AMOLED_THEME = booleanPreferencesKey("use_amoled_theme")
    val PRIMARY_COLOR = intPreferencesKey("primary_color")
    val SECONDARY_COLOR = intPreferencesKey("secondary_color")
    val COLOR_SCHEME_MODE = stringPreferencesKey("color_scheme_mode")
    val VIDEO_SEARCH_LIMIT = intPreferencesKey("video_search_limit")
    val CHANNEL_SEARCH_LIMIT = intPreferencesKey("channel_search_limit")
    val PLAYLIST_SEARCH_LIMIT = intPreferencesKey("playlist_search_limit")
    val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
    val OPEN_LINKS_IN = stringPreferencesKey("open_links_in")
    val FEED_ORDER = stringPreferencesKey("feed_order")
    val CONTINUE_PLAYING = booleanPreferencesKey("continue_playing")
    val DUPLICATE_PLAYLIST_WARNING = booleanPreferencesKey("duplicate_playlist_warning")
}

data class PreferencesUiState(
    val playbackSpeed: Float = 1.0f,
    val defaultQuality: String = "AUTO",
    val resumePlayback: Boolean = true,
    val landscapeLock: Boolean = false,
    val showMiniPlayer: Boolean = true,
    val sponsorBlockEnabled: Boolean = true,
    val sponsorBlockCategories: Map<String, String> = PlayerPreferences.DEFAULT_SPONSOR_CATEGORIES,
    val feedHome: Boolean = true,
    val feedTrending: Boolean = true,
    val feedWhatToWatch: Boolean = true,
    val feedMusic: Boolean = true,
    val feedSports: Boolean = true,
    val feedLive: Boolean = true,
    val feedNews: Boolean = true,
    val feedGaming: Boolean = true,
    val feedKids: Boolean = true,
    val feedSubscriptions: Boolean = true,
    val themeMode: String = "SYSTEM",
    val useAmoledTheme: Boolean = false,
    val primaryColor: Int = 0xFFFF0000.toInt(),
    val secondaryColor: Int = 0xFF282828.toInt(),
    val colorSchemeMode: String = "STANDARD",
    val videoSearchLimit: Int = 50,
    val channelSearchLimit: Int = 20,
    val playlistSearchLimit: Int = 10,
    val pipEnabled: Boolean = true,
    val openLinksIn: String = "browser", // "browser" or "webview"
    val feedOrder: List<String> = PlayerPreferences.DEFAULT_FEED_ORDER,
    val continuePlaying: Boolean = false,
    val duplicatePlaylistWarning: Boolean = true
)

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val uiState: Flow<PreferencesUiState> = context.playerDataStore.data.map { prefs ->
        PreferencesUiState(
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "AUTO",
            resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
            landscapeLock = prefs[Keys.LANDSCAPE_LOCK] ?: false,
            showMiniPlayer = prefs[Keys.SHOW_MINI_PLAYER] ?: true,
            sponsorBlockEnabled = prefs[Keys.SPONSOR_BLOCK_ENABLED] ?: true,
            sponsorBlockCategories = parseCategories(prefs[Keys.SPONSOR_BLOCK_CATEGORIES]),
            feedHome = prefs[Keys.FEED_HOME] ?: true,
            feedTrending = prefs[Keys.FEED_TRENDING] ?: true,
            feedWhatToWatch = prefs[Keys.FEED_WHAT_TO_WATCH] ?: true,
            feedMusic = prefs[Keys.FEED_MUSIC] ?: true,
            feedSports = prefs[Keys.FEED_SPORTS] ?: true,
            feedLive = prefs[Keys.FEED_LIVE] ?: true,
            feedNews = prefs[Keys.FEED_NEWS] ?: true,
            feedGaming = prefs[Keys.FEED_GAMING] ?: true,
            feedKids = prefs[Keys.FEED_KIDS] ?: true,
            feedSubscriptions = prefs[Keys.FEED_SUBSCRIPTIONS] ?: true,
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            useAmoledTheme = prefs[Keys.USE_AMOLED_THEME] ?: false,
            primaryColor = prefs[Keys.PRIMARY_COLOR] ?: 0xFFFF0000.toInt(),
            secondaryColor = prefs[Keys.SECONDARY_COLOR] ?: 0xFF282828.toInt(),
            colorSchemeMode = prefs[Keys.COLOR_SCHEME_MODE] ?: "STANDARD",
            videoSearchLimit = prefs[Keys.VIDEO_SEARCH_LIMIT] ?: 50,
            channelSearchLimit = prefs[Keys.CHANNEL_SEARCH_LIMIT] ?: 20,
            playlistSearchLimit = prefs[Keys.PLAYLIST_SEARCH_LIMIT] ?: 10,
            pipEnabled = prefs[Keys.PIP_ENABLED] ?: true,
            openLinksIn = prefs[Keys.OPEN_LINKS_IN] ?: "browser",
            feedOrder = parseFeedOrder(prefs[Keys.FEED_ORDER]),
            continuePlaying = prefs[Keys.CONTINUE_PLAYING] ?: false,
            duplicatePlaylistWarning = prefs[Keys.DUPLICATE_PLAYLIST_WARNING] ?: true
        )
    }

    private fun parseCategories(raw: Set<String>?): Map<String, String> {
        val defaults = PlayerPreferences.DEFAULT_SPONSOR_CATEGORIES
        if (raw.isNullOrEmpty()) return defaults
        return defaults.keys.associateWith { category ->
            raw.firstOrNull { it.startsWith("$category=") }?.substringAfter("=") ?: "skip"
        }
    }

    private fun serializeCategories(categories: Map<String, String>): Set<String> =
        categories.map { "${it.key}=${it.value}" }.toSet()

    private fun parseFeedOrder(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return PlayerPreferences.DEFAULT_FEED_ORDER
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun serializeFeedOrder(order: List<String>): String = order.joinToString(",")

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.playerDataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.RESUME_PLAYBACK] = enabled }
    }

    suspend fun setLandscapeLock(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.LANDSCAPE_LOCK] = enabled }
    }

    suspend fun setShowMiniPlayer(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.SHOW_MINI_PLAYER] = enabled }
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.SPONSOR_BLOCK_ENABLED] = enabled }
    }

    suspend fun setSponsorBlockCategory(category: String, action: String) {
        context.playerDataStore.edit { prefs ->
            val current = parseCategories(prefs[Keys.SPONSOR_BLOCK_CATEGORIES])
            prefs[Keys.SPONSOR_BLOCK_CATEGORIES] = serializeCategories(current + (category to action))
        }
    }

    suspend fun setFeedEnabled(feed: String, enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            when (feed) {
                "home" -> prefs[Keys.FEED_HOME] = enabled
                "trending" -> prefs[Keys.FEED_TRENDING] = enabled
                "what_to_watch" -> prefs[Keys.FEED_WHAT_TO_WATCH] = enabled
                "music" -> prefs[Keys.FEED_MUSIC] = enabled
                "sports" -> prefs[Keys.FEED_SPORTS] = enabled
                "live" -> prefs[Keys.FEED_LIVE] = enabled
                "news" -> prefs[Keys.FEED_NEWS] = enabled
                "gaming" -> prefs[Keys.FEED_GAMING] = enabled
                "kids" -> prefs[Keys.FEED_KIDS] = enabled
                "subscriptions" -> prefs[Keys.FEED_SUBSCRIPTIONS] = enabled
            }
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.playerDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setUseAmoledTheme(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.USE_AMOLED_THEME] = enabled }
    }

    suspend fun setPrimaryColor(color: Int) {
        context.playerDataStore.edit { it[Keys.PRIMARY_COLOR] = color }
    }

    suspend fun setSecondaryColor(color: Int) {
        context.playerDataStore.edit { it[Keys.SECONDARY_COLOR] = color }
    }

    suspend fun setColorSchemeMode(mode: String) {
        context.playerDataStore.edit { it[Keys.COLOR_SCHEME_MODE] = mode }
    }

    suspend fun setVideoSearchLimit(limit: Int) {
        context.playerDataStore.edit { it[Keys.VIDEO_SEARCH_LIMIT] = limit }
    }

    suspend fun setChannelSearchLimit(limit: Int) {
        context.playerDataStore.edit { it[Keys.CHANNEL_SEARCH_LIMIT] = limit }
    }

    suspend fun setPlaylistSearchLimit(limit: Int) {
        context.playerDataStore.edit { it[Keys.PLAYLIST_SEARCH_LIMIT] = limit }
    }

    suspend fun setPiPEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.PIP_ENABLED] = enabled }
    }

    suspend fun setOpenLinksIn(mode: String) {
        context.playerDataStore.edit { it[Keys.OPEN_LINKS_IN] = mode }
    }

    suspend fun setFeedOrder(order: List<String>) {
        context.playerDataStore.edit { it[Keys.FEED_ORDER] = serializeFeedOrder(order) }
    }

    suspend fun setContinuePlaying(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.CONTINUE_PLAYING] = enabled }
    }

    suspend fun setDuplicatePlaylistWarning(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.DUPLICATE_PLAYLIST_WARNING] = enabled }
    }

    companion object {
        val PLAYBACK_SPEEDS = listOf(
            0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f
        )

        val DEFAULT_SPONSOR_CATEGORIES = mapOf(
            "sponsor" to "skip",
            "intro" to "skip",
            "outro" to "skip",
            "interaction" to "skip",
            "selfpromo" to "skip",
            "music_offtopic" to "skip",
            "preview" to "skip",
            "poi_highlight" to "skip",
            "filler" to "skip"
        )

        val DEFAULT_FEED_ORDER = listOf(
            "home", "what_to_watch", "subscriptions", "trending",
            "sports", "gaming", "live", "news", "music", "kids"
        )
    }
}
