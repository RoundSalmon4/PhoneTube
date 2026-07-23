package app.phonetube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.NetworkFetcher
import app.phonetube.core.engine.HttpNetworkClient
import com.liskovsoft.sharedutils.rx.RxHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhoneTubeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
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
}
