package com.roundsalmon4.phonetube.core.engine.model

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String,
    val action: String
)
