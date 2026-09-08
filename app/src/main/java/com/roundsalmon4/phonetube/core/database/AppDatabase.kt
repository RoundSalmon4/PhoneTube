package com.roundsalmon4.phonetube.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roundsalmon4.phonetube.core.database.entity.CachedFeedSection
import com.roundsalmon4.phonetube.core.database.entity.CachedFeedVideo
import com.roundsalmon4.phonetube.core.database.entity.InvidiousInstance
import com.roundsalmon4.phonetube.core.database.entity.IptvFavorite
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.LocalSubscription
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.database.entity.WatchHistoryEntry

@Database(
    entities = [
        WatchHistoryEntry::class,
        LocalPlaylist::class,
        PlaylistVideo::class,
        LocalSubscription::class,
        CachedFeedSection::class,
        CachedFeedVideo::class,
        InvidiousInstance::class,
        IptvProvider::class,
        IptvFavorite::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun invidiousDao(): InvidiousDao
    abstract fun iptvDao(): IptvDao
    abstract fun iptvFavoriteDao(): IptvFavoriteDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS feed_videos")
                db.execSQL("DROP TABLE IF EXISTS feed_sections")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS feed_sections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        source TEXT NOT NULL,
                        title TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS feed_videos (
                        sectionId INTEGER NOT NULL,
                        videoId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        channelId TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        viewCount TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(sectionId, videoId),
                        FOREIGN KEY(sectionId) REFERENCES feed_sections(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feed_videos_sectionId ON feed_videos(sectionId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feed_videos ADD COLUMN percentWatched INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN sourcePlaylistId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feed_videos ADD COLUMN publishedDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS invidious_instances (
                        host TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(host)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS iptv_providers (
                        id TEXT NOT NULL,
                        host TEXT NOT NULL DEFAULT '',
                        username TEXT NOT NULL DEFAULT '',
                        password TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE iptv_providers ADD COLUMN scheme TEXT NOT NULL DEFAULT 'https'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE iptv_providers ADD COLUMN timezone TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS iptv_favorites (
                        videoId TEXT NOT NULL,
                        title TEXT NOT NULL DEFAULT '',
                        providerName TEXT NOT NULL DEFAULT '',
                        iconUrl TEXT NOT NULL DEFAULT '',
                        addedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(videoId)
                    )
                """.trimIndent())
            }
        }
    }
}
