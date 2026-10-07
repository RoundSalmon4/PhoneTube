package com.roundsalmon4.phonetube.core.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.logging.HttpLoggingInterceptor
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
        reduceHttpLogNoise()
        initialized = true
    }

    /**
     * Lowers the core's http log from body to basics.
     *
     * The interceptor the core adds prints every response body, and in the last capture
     * that came to 2248 KB out of a 2259 KB main buffer, so the buffer rolled inside a
     * minute and a quarter and the app's own lines went with it. The request line, url
     * and response code stay, which is what a capture actually gets read for. Basics
     * rather than headers: headers also print cookies and authorization.
     *
     * Runs after GlobalPreferences.instance() on purpose: building the client is what
     * reads that setting for the DNS choice, and building it earlier would freeze the
     * preference at the default because OkHttpManager caches the client after first use.
     */
    private fun reduceHttpLogNoise() {
        try {
            val client = OkHttpManager.instance().getClient()
            var changed = false
            client.interceptors().forEach { interceptor ->
                if (interceptor is HttpLoggingInterceptor) {
                    interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
                    changed = true
                }
            }
            Log.d(TAG, "reduceHttpLogNoise: body logging lowered to basics, matched=$changed")
        } catch (e: Exception) {
            Log.w(TAG, "reduceHttpLogNoise failed: $e")
        }
    }

    suspend fun warmup() {
        if (warmedUp) return
        warmupMutex.withLock {
            if (warmedUp) return
            withContext(Dispatchers.IO) {
                try {
                    init()
                    // Clear visitor data before fetching new data if the privacy toggle is on.
                    // This makes the visitor identity non-persistent across sessions.
                    if (getVisitorClearFlag()) {
                        Log.d(TAG, "warmup: clearing visitor data (incognito exit mode)")
                        MediaServiceData.instance().visitorCookie = null
                    }
                    Log.d(TAG, "warmup: fetching visitor data and player info")
                    YouTubeServiceManager.instance().refreshCacheIfNeeded()
                    Log.d(TAG, "warmup: visitor data refreshed")
                    YouTubeServiceManager.instance().contentService.enableHistory(true)
                    Log.d(TAG, "warmup: watch history enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "warmup failed", e)
                }
            }
            warmedUp = true
        }
    }

    private fun getVisitorClearFlag(): Boolean {
        return try {
            val prefs: SharedPreferences = context.getSharedPreferences("phonetube_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("clear_visitor_on_exit", false)
        } catch (_: Exception) {
            false
        }
    }
}
