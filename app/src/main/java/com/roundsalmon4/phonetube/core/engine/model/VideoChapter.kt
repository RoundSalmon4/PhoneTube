package com.roundsalmon4.phonetube.core.engine.model

/**
 * A video chapter parsed from the description's timestamped lines.
 */
data class VideoChapter(
    val title: String,
    val startMs: Long
)