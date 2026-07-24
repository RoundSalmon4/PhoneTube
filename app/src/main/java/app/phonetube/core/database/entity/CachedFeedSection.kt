package app.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_sections")
data class CachedFeedSection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val title: String,
    val fetchedAt: Long
)
