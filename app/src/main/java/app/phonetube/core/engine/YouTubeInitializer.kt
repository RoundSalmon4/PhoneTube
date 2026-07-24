package app.phonetube.core.engine

import android.content.Context
import android.util.Log
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeInitializer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "YouTubeInitializer"
    }

    private var initialized = false
    private val warmupMutex = Mutex()
    private var warmedUp = false

    fun init() {
        if (initialized) return
        GlobalPreferences.instance(context)
        // Use embedded TV client for browse — standard TV client returns empty home feed for anonymous users
        YouTubeServiceManager.instance().contentService
            .configureBrowseClient("TVHTML5_SIMPLY_EMBEDDED_PLAYER")
        initialized = true
    }

    suspend fun warmup() {
        if (warmedUp) return
        warmupMutex.withLock {
            if (warmedUp) return
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "warmup: fetching visitor data and player info")
                    YouTubeServiceManager.instance().refreshCacheIfNeeded()
                } catch (e: Exception) {
                    Log.e(TAG, "warmup failed", e)
                }
            }
            warmedUp = true
        }
    }
}
