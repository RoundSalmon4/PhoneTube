package com.roundsalmon4.phonetube

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.NetworkFetcher
import com.roundsalmon4.phonetube.core.engine.HttpNetworkClient
import com.liskovsoft.sharedutils.rx.RxHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhoneTubeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App build: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) " +
            "buildType=${BuildConfig.BUILD_TYPE} commit=${BuildConfig.GIT_HASH} pkg=${BuildConfig.APPLICATION_ID}")
        RxHelper.setupGlobalErrorHandler()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(NetworkFetcher.Factory(
                    networkClient = { HttpNetworkClient() }
                ))
            }
            .build()
    }

    private companion object {
        const val TAG = "PhoneTubeApp"
    }
}
