package app.phonetube

import android.app.Application
import com.liskovsoft.sharedutils.rx.RxHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhoneTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RxHelper.setupGlobalErrorHandler()
    }
}
