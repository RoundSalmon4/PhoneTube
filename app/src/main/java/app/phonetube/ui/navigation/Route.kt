package app.phonetube.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class Player(val videoId: String) : Route
    @Serializable data object Search : Route
    @Serializable data class Channel(val channelId: String) : Route
    @Serializable data object Library : Route
    @Serializable data object Settings : Route
    @Serializable data object License : Route
    @Serializable data object Credits : Route
    @Serializable data class PlaylistDetail(val playlistId: Long) : Route
}
