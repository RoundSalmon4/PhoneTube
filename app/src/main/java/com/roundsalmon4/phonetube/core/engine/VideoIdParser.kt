package com.roundsalmon4.phonetube.core.engine

/**
 * Parses PhoneTube's internal synthetic video ids used by history, playlists,
 * deep links and favorites, e.g. "iptv:<providerId>:<streamId>".
 */
object VideoIdParser {

    /**
     * Splits "iptv:<providerId>:<streamId>". Provider ids are "host|username"
     * and hosts may include a port ("host:8080|username"), so the stream id is
     * everything after the LAST colon. Returns null when the id is not an IPTV
     * id or is malformed.
     */
    fun parseIptv(videoId: String): Pair<String, String>? {
        val rest = videoId.removePrefix("iptv:")
        if (rest == videoId) return null
        val provider = rest.substringBeforeLast(':')
        val stream = rest.substringAfterLast(':')
        // substringAfterLast returns the whole string when no delimiter exists,
        // so treat a missing ":" as malformed.
        if (stream == rest || provider.isBlank() || stream.isBlank()) return null
        return provider to stream
    }
}