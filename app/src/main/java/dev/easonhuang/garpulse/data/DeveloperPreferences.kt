package dev.easonhuang.garpulse.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.easonhuang.garpulse.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.developerDataStore by preferencesDataStore(name = "garpulse_developer")

/** Where the source badge sits on an Overview metric card. */
enum class SourceBadgeLayout(val key: String, @StringRes val labelRes: Int) {
    /** Title moves up to the header row (after the icon); the badge follows the title. */
    AFTER_TITLE("after_title", R.string.src_layout_after_title),
    /** Badge directly after the icon on the header row. */
    AFTER_ICON("after_icon", R.string.src_layout_after_icon),
    /** Badge on the title's own row, before the title. */
    SECOND_START("second_start", R.string.src_layout_second_start),
    /** Badge on the title's own row, after the title (end-aligned). */
    SECOND_END("second_end", R.string.src_layout_second_end),
    ;

    companion object {
        fun fromKey(key: String?): SourceBadgeLayout =
            entries.firstOrNull { it.key == key } ?: AFTER_TITLE
    }
}

/**
 * Developer tuning knobs, exposed in Settings → 开发者 behind a master switch. Lets the user
 * adjust rendering effects (refresh-strip wave speed/duration, source badge layout on the
 * Overview) without rebuilding the app.
 */
class DeveloperPreferences(private val context: Context) {

    /** Master switch: when off, every tunable falls back to its built-in default. */
    val enabled: Flow<Boolean> = context.developerDataStore.data.map { prefs ->
        prefs[ENABLED_KEY] ?: DEFAULT_ENABLED
    }

    /** Refresh-strip wave animation speed multiplier (1 = original 90 ms/frame). */
    val refreshAnimSpeed: Flow<Float> = context.developerDataStore.data.map { prefs ->
        (prefs[SPEED_KEY] ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /** Number of wave frames — together with the per-frame delay this is the animation duration. */
    val refreshAnimFrames: Flow<Int> = context.developerDataStore.data.map { prefs ->
        (prefs[FRAMES_KEY] ?: DEFAULT_FRAMES).coerceIn(MIN_FRAMES, MAX_FRAMES)
    }

    /** Source badge placement on Overview metric cards. */
    val sourceBadgeLayout: Flow<SourceBadgeLayout> = context.developerDataStore.data.map { prefs ->
        SourceBadgeLayout.fromKey(prefs[LAYOUT_KEY])
    }

    suspend fun setEnabled(value: Boolean) = context.developerDataStore.edit { prefs ->
        prefs[ENABLED_KEY] = value
    }

    suspend fun setRefreshAnimSpeed(speed: Float) = context.developerDataStore.edit { prefs ->
        prefs[SPEED_KEY] = speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    suspend fun setRefreshAnimFrames(frames: Int) = context.developerDataStore.edit { prefs ->
        prefs[FRAMES_KEY] = frames.coerceIn(MIN_FRAMES, MAX_FRAMES)
    }

    suspend fun setSourceBadgeLayout(layout: SourceBadgeLayout) = context.developerDataStore.edit { prefs ->
        prefs[LAYOUT_KEY] = layout.key
    }

    companion object {
        private val ENABLED_KEY = booleanPreferencesKey("dev_enabled")
        private val SPEED_KEY = floatPreferencesKey("dev_refresh_anim_speed")
        private val FRAMES_KEY = intPreferencesKey("dev_refresh_anim_frames")
        private val LAYOUT_KEY = stringPreferencesKey("dev_source_badge_layout")

        const val DEFAULT_ENABLED = true
        const val DEFAULT_SPEED = 5f   // original wave ran at 90 ms/frame; 5× = 18 ms
        const val MIN_SPEED = 1f
        const val MAX_SPEED = 10f
        const val DEFAULT_FRAMES = 4
        const val MIN_FRAMES = 2
        const val MAX_FRAMES = 12
    }
}
