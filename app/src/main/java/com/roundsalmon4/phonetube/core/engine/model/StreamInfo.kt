package com.roundsalmon4.phonetube.core.engine.model

data class StreamInfo(
    val title: String,
    val author: String,
    val channelId: String,
    val lengthSeconds: Long,
    val isLive: Boolean,
    val isLiveContent: Boolean,
    val adaptiveFormats: List<StreamFormat>,
    val urlFormats: List<StreamFormat>,
    val subtitles: List<SubtitleTrack>,
    val dashManifestUrl: String?,
    val hlsManifestUrl: String?,
    val isUnplayable: Boolean,
    val playabilityReason: String?,
    val thumbnailUrl: String? = null
)

data class StreamFormat(
    val url: String?,
    val mimeType: String?,
    val height: Int,
    val bitrate: String?,
    val fps: String?,
    val qualityLabel: String?
)

data class SubtitleTrack(
    val baseUrl: String,
    val languageCode: String,
    val name: String,
    val mimeType: String
)

/**
 * Picks the best playable URL for the TV, following the same priority as the
 * local player (live HLS first, then DASH, then HLS, then a progressive URL).
 * Used both when starting a cast and when handing off a new video while a
 * cast session is active.
 */
fun StreamInfo.bestCastUrl(): String? {
    if (isLive || isLiveContent) {
        hlsManifestUrl?.let { return it }
    }
    dashManifestUrl?.let { return it }
    hlsManifestUrl?.let { return it }
    urlFormats.firstOrNull { !it.url.isNullOrBlank() }?.let { return it.url }
    return null
}
