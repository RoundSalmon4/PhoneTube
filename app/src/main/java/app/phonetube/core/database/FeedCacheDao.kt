package app.phonetube.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import app.phonetube.core.database.entity.CachedFeedSection
import app.phonetube.core.database.entity.CachedFeedVideo
import kotlinx.coroutines.flow.Flow

data class CachedSectionWithVideos(
    @Embedded val section: CachedFeedSection,
    @Relation(
        parentColumn = "id",
        entityColumn = "sectionId"
    )
    val videos: List<CachedFeedVideo>
)

@Dao
interface FeedCacheDao {

    @Transaction
    @Query("SELECT * FROM feed_sections ORDER BY id")
    fun getAllSections(): Flow<List<CachedSectionWithVideos>>

    @Transaction
    @Query("SELECT * FROM feed_sections WHERE source = :source")
    fun getSection(source: String): Flow<CachedSectionWithVideos?>

    @Query("SELECT fetchedAt FROM feed_sections ORDER BY fetchedAt ASC LIMIT 1")
    suspend fun getOldestFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<CachedFeedSection>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<CachedFeedVideo>)

    @Query("DELETE FROM feed_sections")
    suspend fun clearAllSections()

    @Query("DELETE FROM feed_videos")
    suspend fun clearAllVideos()
}
