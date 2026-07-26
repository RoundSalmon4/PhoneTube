package app.phonetube.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_preferences"
)

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    }

    val playbackSpeed: Flow<Float> = context.playerDataStore.data.map { prefs ->
        prefs[Keys.PLAYBACK_SPEED] ?: 1.0f
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerDataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_SPEED] = speed
        }
    }
}
