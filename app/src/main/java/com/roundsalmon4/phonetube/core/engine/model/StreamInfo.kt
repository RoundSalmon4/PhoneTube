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
    val playabilityReason: String?
)

data class StreamFormat(
    val url: String?,
    val mimeType: String?,
    val itag: String?,
    val height: Int,
    val bitrate: String?,
    val fps: String?,
    val qualityLabel: String?
)

data class SubtitleTrack(
    val baseUrl: String,
    val languageCode: String,
    val name: String,
    val mimeType: String,
    val isTranslatable: Boolean
)
