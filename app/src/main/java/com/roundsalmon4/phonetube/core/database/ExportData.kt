package com.roundsalmon4.phonetube.core.database

import com.roundsalmon4.phonetube.core.datastore.PlayerPreferences
import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val preferences: PreferencesExport? = null,
    val playlists: List<LocalPlaylistExport>? = null,
    val subscriptions: List<LocalSubscriptionExport>? = null
)

@Serializable
data class PreferencesExport(
    val playbackSpeed: Float = 1.0f,
    val defaultQuality: String = "AUTO",
    val resumePlayback: Boolean = true,
    val landscapeLock: Boolean = false,
    val showMiniPlayer: Boolean = true,
    val sponsorBlockEnabled: Boolean = true,
    val sponsorBlockCategories: Map<String, String> = emptyMap(),
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
    val feedInvidious: Boolean = false,
    val themeMode: String = "SYSTEM",
    val useAmoledTheme: Boolean = false,
    val primaryColor: Int = 0xFFFF0000.toInt(),
    val secondaryColor: Int = 0xFF282828.toInt(),
    val colorSchemeMode: String = "STANDARD",
    val videoSearchLimit: Int = 50,
    val channelSearchLimit: Int = 20,
    val pipEnabled: Boolean = true,
    val openLinksIn: String = "browser",
    val playlistSearchLimit: Int = 10,
    val feedOrder: List<String> = PlayerPreferences.DEFAULT_FEED_ORDER,
    val continuePlaying: Boolean = false,
    val duplicatePlaylistWarning: Boolean = true,
    val screenProtection: Boolean = false,
    val incognitoMode: Boolean = false
)

@Serializable
data class LocalPlaylistExport(
    val name: String,
    val createdAt: Long,
    val videos: List<PlaylistVideoExport>
)

@Serializable
data class PlaylistVideoExport(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val position: Int
)

@Serializable
data class LocalSubscriptionExport(
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val subscribedAt: Long
)
