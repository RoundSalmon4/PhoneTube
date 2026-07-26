package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "feed_videos",
    primaryKeys = ["sectionId", "videoId"],
    foreignKeys = [ForeignKey(
        entity = CachedFeedSection::class,
        parentColumns = ["id"],
        childColumns = ["sectionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sectionId")]
)
data class CachedFeedVideo(
    val sectionId: Long,
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewCount: String,
    val position: Int
)
