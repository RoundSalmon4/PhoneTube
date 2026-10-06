package com.roundsalmon4.phonetube

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.NetworkFetcher
import com.roundsalmon4.phonetube.core.engine.HttpNetworkClient
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import com.liskovsoft.sharedutils.rx.RxHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhoneTubeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App build: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) " +
            "buildType=${BuildConfig.BUILD_TYPE} commit=${BuildConfig.GIT_HASH} pkg=${BuildConfig.APPLICATION_ID}")
        // Has to happen before anything asks for a client: OkHttpManager only keeps this
        // flag on first use, and getClient() copies it into OkHttpCommons at build time.
        // The profiler prints a second copy of every request and response as raw
        // compressed bytes. In the last capture that was 446 KB of unreadable output,
        // 40 percent of the main logcat buffer, and it pushed the app's own lines out
        // before a capture could take them. The logging interceptor already prints the
        // decoded body, so nothing readable is lost here.
        OkHttpManager.instance(false)
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
