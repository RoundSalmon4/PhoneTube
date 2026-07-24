package app.phonetube.core.di

import android.content.Context
import androidx.room.Room
import app.phonetube.core.database.AppDatabase
import app.phonetube.core.database.FeedCacheDao
import app.phonetube.core.database.HistoryDao
import app.phonetube.core.database.PlaylistDao
import app.phonetube.core.database.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "phonetube.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
    }

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideSubscriptionDao(database: AppDatabase): SubscriptionDao {
        return database.subscriptionDao()
    }

    @Provides
    fun provideFeedCacheDao(database: AppDatabase): FeedCacheDao {
        return database.feedCacheDao()
    }
}
