package app.phonetube.core.engine

import android.content.Context
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeInitializer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var initialized = false

    fun init() {
        if (initialized) return
        GlobalPreferences.instance(context)
        initialized = true
    }
}
