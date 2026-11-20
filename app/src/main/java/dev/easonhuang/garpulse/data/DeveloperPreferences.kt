package dev.easonhuang.garpulse.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.developerDataStore by preferencesDataStore(name = "garpulse_developer")

/**
 * Developer tuning knobs, exposed in Settings → 开发者. Lets the user adjust rendering
 * effects (currently the refresh-strip wave animation speed) without rebuilding the app.
 */
class DeveloperPreferences(private val context: Context) {

    /** Refresh-strip wave animation speed multiplier (1 = original 90 ms/frame). */
    val refreshAnimSpeed: Flow<Float> = context.developerDataStore.data.map { prefs ->
        (prefs[SPEED_KEY] ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    suspend fun setRefreshAnimSpeed(speed: Float) = context.developerDataStore.edit { prefs ->
        prefs[SPEED_KEY] = speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    companion object {
        private val SPEED_KEY = floatPreferencesKey("dev_refresh_anim_speed")

        const val DEFAULT_SPEED = 5f   // original wave ran at 90 ms/frame; 5× = 18 ms
        const val MIN_SPEED = 1f
        const val MAX_SPEED = 10f
    }
}
