package com.roundsalmon4.phonetube.core.di

import android.content.Context
import com.roundsalmon4.phonetube.player.PlayerEngineController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerEngineController(
        @ApplicationContext context: Context
    ): PlayerEngineController {
        return PlayerEngineController(context)
    }
}
