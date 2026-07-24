package app.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "feed_videos",
    primaryKeys = ["source", "videoId"],
    foreignKeys = [ForeignKey(
        entity = CachedFeedSection::class,
        parentColumns = ["source"],
        childColumns = ["source"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("source")]
)
data class CachedFeedVideo(
    val source: String,
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewCount: String,
    val position: Int
)
