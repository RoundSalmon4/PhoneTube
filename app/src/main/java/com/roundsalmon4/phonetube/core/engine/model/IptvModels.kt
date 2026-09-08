package com.roundsalmon4.phonetube.core.engine.model

/**
 * A live channel category from the Xtream Codes player API
 * (action=get_live_categories).
 */
data class IptvCategory(
    val categoryId: String,
    val name: String
)

/**
 * A live channel from the Xtream Codes player API (action=get_live_streams).
 */
data class IptvLiveStream(
    val streamId: String,
    val name: String,
    val iconUrl: String?,
    val categoryId: String
)

/**
 * Result of authenticating against a provider's player_api.php endpoint.
 */
data class XtreamAuthInfo(
    val auth: Boolean,
    val status: String,
    val expDate: Long,
    val serverName: String?,
    val scheme: String?,
    val timezone: String?
)

/**
 * A program from the short EPG (action=get_short_epg). Timestamps are epoch
 * millis in UTC so they can be matched against the device clock.
 */
data class IptvProgram(
    val title: String,
    val startEpoch: Long,
    val endEpoch: Long
)