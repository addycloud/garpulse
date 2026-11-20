package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.easonhuang.heartwood.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.metricSourceDataStore by preferencesDataStore(name = "heartwood_metric_source")

/** Where metric values are read from. The choice applies to the app and every widget. */
enum class MetricSource(val key: String, @StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    /** Read from Health Connect only. */
    HEALTH_CONNECT("health_connect", R.string.source_health_connect, R.string.source_health_connect_desc),
    /**
     * Mirrored mode: every metric is read from BOTH sources independently. Whatever each source
     * actually has data for is shown, so Health Connect-only and Garmin-only metrics coexist
     * without the user having to flip a switch. The app and widgets label each tile with the
     * source that supplied its value.
     */
    AUTO("auto", R.string.source_auto, R.string.source_auto_desc),
    /** Read from the Garmin Personal API only. */
    GARMIN_API("garmin_api", R.string.source_garmin_api, R.string.source_garmin_api_desc),
    ;

    companion object {
        fun fromKey(key: String?): MetricSource = entries.firstOrNull { it.key == key } ?: HEALTH_CONNECT
    }
}

/**
 * Persisted source selection, the user's Garmin Personal API key, and the short-lived bearer
 * token derived from it. The token is cached so normal widget refreshes do not re-exchange the
 * key on every run; it is silently renewed once it has less than an hour left.
 */
data class MetricSourceState(
    val source: MetricSource = MetricSource.HEALTH_CONNECT,
    val apiKey: String = "",
    val token: String = "",
    val tokenExpiresAt: Long = 0L,
    val lastSyncMillis: Long = 0L,
    val lastError: String = "",
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
    val tokenUsable: Boolean
        get() = token.isNotBlank() && tokenExpiresAt > System.currentTimeMillis() + 60L * 60 * 1000
}

class MetricSourcePreferences(private val context: Context) {

    private val sourceKey = stringPreferencesKey("metric_source")
    private val apiKeyKey = stringPreferencesKey("garmin_api_key")
    private val tokenKey = stringPreferencesKey("garmin_bearer_token")
    private val tokenExpiryKey = longPreferencesKey("garmin_token_expires_at")
    private val lastSyncKey = longPreferencesKey("garmin_last_sync")
    private val lastErrorKey = stringPreferencesKey("garmin_last_error")

    val state: Flow<MetricSourceState> = context.metricSourceDataStore.data.map { prefs ->
        MetricSourceState(
            source = MetricSource.fromKey(prefs[sourceKey]),
            apiKey = prefs[apiKeyKey].orEmpty(),
            token = prefs[tokenKey].orEmpty(),
            tokenExpiresAt = prefs[tokenExpiryKey] ?: 0L,
            lastSyncMillis = prefs[lastSyncKey] ?: 0L,
            lastError = prefs[lastErrorKey].orEmpty(),
        )
    }

    suspend fun setSource(source: MetricSource) = context.metricSourceDataStore.edit { prefs ->
        prefs[sourceKey] = source.key
    }

    /** Changing the key invalidates any token issued for the previous key. */
    suspend fun setApiKey(key: String) = context.metricSourceDataStore.edit { prefs ->
        prefs[apiKeyKey] = key.trim()
        prefs.remove(tokenKey)
        prefs.remove(tokenExpiryKey)
        prefs.remove(lastErrorKey)
    }

    suspend fun saveToken(token: String, expiresAt: Long) = context.metricSourceDataStore.edit { prefs ->
        prefs[tokenKey] = token
        prefs[tokenExpiryKey] = expiresAt
    }

    suspend fun setStatus(syncMillis: Long, error: String) = context.metricSourceDataStore.edit { prefs ->
        if (syncMillis > 0L) prefs[lastSyncKey] = syncMillis
        if (error.isBlank()) prefs.remove(lastErrorKey) else prefs[lastErrorKey] = error
    }
}
