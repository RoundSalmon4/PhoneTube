package app.phonetube.core.di

import android.content.Context
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.YouTubeInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideYouTubeInitializer(
        @ApplicationContext context: Context
    ): YouTubeInitializer {
        return YouTubeInitializer(context)
    }

    @Provides
    @Singleton
    fun provideYouTubeEngine(
        initializer: YouTubeInitializer
    ): YouTubeEngine {
        return YouTubeEngine(initializer)
    }
}
