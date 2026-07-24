package app.phonetube.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "feed_sections",
    primaryKeys = ["source"]
)
data class CachedFeedSection(
    val source: String,
    val title: String,
    val fetchedAt: Long
)
