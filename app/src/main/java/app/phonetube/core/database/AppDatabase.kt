package app.phonetube.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.phonetube.core.database.entity.CachedFeedSection
import app.phonetube.core.database.entity.CachedFeedVideo
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.LocalSubscription
import app.phonetube.core.database.entity.PlaylistVideo
import app.phonetube.core.database.entity.WatchHistoryEntry

@Database(
    entities = [
        WatchHistoryEntry::class,
        LocalPlaylist::class,
        PlaylistVideo::class,
        LocalSubscription::class,
        CachedFeedSection::class,
        CachedFeedVideo::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun feedCacheDao(): FeedCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS feed_sections (
                        source TEXT NOT NULL,
                        title TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(source)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS feed_videos (
                        source TEXT NOT NULL,
                        videoId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        channelId TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        viewCount TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(source, videoId),
                        FOREIGN KEY(source) REFERENCES feed_sections(source) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feed_videos_source ON feed_videos(source)")
            }
        }
    }
}
