package app.phonetube.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.LocalSubscription
import app.phonetube.core.database.entity.PlaylistVideo
import app.phonetube.core.database.entity.WatchHistoryEntry

@Database(
    entities = [
        WatchHistoryEntry::class,
        LocalPlaylist::class,
        PlaylistVideo::class,
        LocalSubscription::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subscriptionDao(): SubscriptionDao
}
