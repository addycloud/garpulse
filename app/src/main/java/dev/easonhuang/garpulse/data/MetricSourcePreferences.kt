package dev.easonhuang.garpulse.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.metricSourceDataStore by preferencesDataStore(name = "garpulse_metric_source")

/**
 * Independent data sources. Each can be enabled/disabled separately, and the user gives them
 * a priority order; a value for a metric comes from the first source in that order which
 * actually has data (garmin-only metrics skip Health Connect entirely).
 */
enum class GarminRegion(val id: String, val baseUrl: String) {
    GLOBAL("garmin_global", "https://personal.connectapi.garmin.com"),
    CN("garmin_cn", "https://personal.connectapi.garmin.cn"),
    ;

    companion object {
        fun fromId(id: String?): GarminRegion? = entries.firstOrNull { it.id == id }
    }
}

const val SOURCE_ID_HC = "hc"

/**
 * Persisted source configuration. The token caches and last-sync/error status are per Garmin
 * region, since each region has its own key and its own 24-hour bearer token.
 */
data class MetricSourceState(
    val hcEnabled: Boolean = true,
    val globalEnabled: Boolean = false,
    val cnEnabled: Boolean = false,
    val globalKey: String = "",
    val cnKey: String = "",
    /** Priority order of source ids, e.g. "garmin_global,garmin_cn,hc". Health Connect is lowest by default. */
    val order: List<String> = listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC),
    val tokenGlobal: String = "",
    val tokenCn: String = "",
    val tokenGlobalExpiresAt: Long = 0L,
    val tokenCnExpiresAt: Long = 0L,
    val lastSyncMillis: Long = 0L,
    val lastError: String = "",
) {
    val globalConfigured: Boolean get() = globalEnabled && globalKey.isNotBlank()
    val cnConfigured: Boolean get() = cnEnabled && cnKey.isNotBlank()

    /** Enabled Garmin regions in priority order. */
    val garminRegions: List<GarminRegion>
        get() = order.mapNotNull { GarminRegion.fromId(it) }
            .filter { it == GarminRegion.GLOBAL && globalConfigured || it == GarminRegion.CN && cnConfigured }

    val anyGarmin: Boolean get() = garminRegions.isNotEmpty()
    val hcActive: Boolean get() = hcEnabled
    val hasKey: Boolean get() = globalKey.isNotBlank() || cnKey.isNotBlank()

    fun apiKey(region: GarminRegion): String =
        if (region == GarminRegion.GLOBAL) globalKey else cnKey

    fun token(region: GarminRegion): String =
        if (region == GarminRegion.GLOBAL) tokenGlobal else tokenCn

    fun tokenExpiresAt(region: GarminRegion): Long =
        if (region == GarminRegion.GLOBAL) tokenGlobalExpiresAt else tokenCnExpiresAt

    fun tokenUsable(region: GarminRegion): Boolean =
        token(region).isNotBlank() && tokenExpiresAt(region) > System.currentTimeMillis() + 60L * 60 * 1000

    /** Which source supplies Health-Connect-capable metrics first: Garmin (true) or HC (false). */
    val garminBeforeHc: Boolean
        get() {
            val g = order.indexOfFirst { GarminRegion.fromId(it) != null }
            val h = order.indexOf(SOURCE_ID_HC)
            return g in 0 until maxOf(h, 0)
        }
}

class MetricSourcePreferences(private val context: Context) {

    private val hcEnabledKey = booleanPreferencesKey("hc_enabled")
    private val globalEnabledKey = booleanPreferencesKey("garmin_global_enabled")
    private val cnEnabledKey = booleanPreferencesKey("garmin_cn_enabled")
    private val globalKeyKey = stringPreferencesKey("garmin_global_key")
    private val cnKeyKey = stringPreferencesKey("garmin_cn_key")
    private val orderKey = stringPreferencesKey("source_order")
    private val tokenGlobalKey = stringPreferencesKey("garmin_token_global")
    private val tokenCnKey = stringPreferencesKey("garmin_token_cn")
    private val tokenGlobalExpiryKey = longPreferencesKey("garmin_token_global_expires_at")
    private val tokenCnExpiryKey = longPreferencesKey("garmin_token_cn_expires_at")
    private val lastSyncKey = longPreferencesKey("garmin_last_sync")
    private val lastErrorKey = stringPreferencesKey("garmin_last_error")
    // Legacy keys from the single-source era; read once for migration.
    private val legacySourceKey = stringPreferencesKey("metric_source")
    private val legacyApiKeyKey = stringPreferencesKey("garmin_api_key")
    private val legacyPriorityKey = booleanPreferencesKey("garmin_priority")
    private val migratedKey = booleanPreferencesKey("source_model_migrated")

    val state: Flow<MetricSourceState> = context.metricSourceDataStore.data.map { prefs ->
        if (prefs[migratedKey] != true) {
            // One-time migration from the old single-source model.
            val legacyKey = prefs[legacyApiKeyKey].orEmpty()
            val legacySource = prefs[legacySourceKey]
            val migrated = when (legacySource) {
                "garmin_api" -> MetricSourceState(
                    hcEnabled = false, globalEnabled = true,
                    globalKey = legacyKey, order = listOf(GarminRegion.GLOBAL.id),
                )
                "auto" -> MetricSourceState(
                    hcEnabled = true, globalEnabled = true, globalKey = legacyKey,
                    order = if (prefs[legacyPriorityKey] == true)
                        listOf(GarminRegion.GLOBAL.id, SOURCE_ID_HC)
                    else listOf(SOURCE_ID_HC, GarminRegion.GLOBAL.id),
                )
                else -> MetricSourceState(hcEnabled = true)
            }
            // Persist the migration synchronously so later readers see a stable state.
            kotlinx.coroutines.runBlocking {
                context.metricSourceDataStore.edit {
                    it.clear()
                    writeState(it, migrated)
                    it[migratedKey] = true
                }
            }
            migrated
        } else {
            MetricSourceState(
                hcEnabled = prefs[hcEnabledKey] ?: true,
                globalEnabled = prefs[globalEnabledKey] ?: false,
                cnEnabled = prefs[cnEnabledKey] ?: false,
                globalKey = prefs[globalKeyKey].orEmpty(),
                cnKey = prefs[cnKeyKey].orEmpty(),
                order = normalizeOrder(
                    prefs[orderKey]?.split(',')
                        ?.map { it.trim() }?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC),
                ),
                tokenGlobal = prefs[tokenGlobalKey].orEmpty(),
                tokenCn = prefs[tokenCnKey].orEmpty(),
                tokenGlobalExpiresAt = prefs[tokenGlobalExpiryKey] ?: 0L,
                tokenCnExpiresAt = prefs[tokenCnExpiryKey] ?: 0L,
                lastSyncMillis = prefs[lastSyncKey] ?: 0L,
                lastError = prefs[lastErrorKey].orEmpty(),
            )
        }
    }

    /**
     * Ensures every known source id appears exactly once in the order. Old installs may have
     * stored a partial order (e.g. only "hc"), which made the priority arrows dead for the
     * missing ids — they were never in the list, so moving them was a no-op.
     */
    private fun normalizeOrder(raw: List<String>): List<String> {
        val all = raw +
            listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC).filter { it !in raw }
        // Pre-multi-source-default installs stored the old default (HC first). Move HC to the
        // lowest priority unless the user reordered it themselves.
        return if (all == listOf(SOURCE_ID_HC, GarminRegion.GLOBAL.id, GarminRegion.CN.id))
            listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC)
        else all
    }

    private fun writeState(prefs: androidx.datastore.preferences.core.MutablePreferences, s: MetricSourceState) {        prefs[hcEnabledKey] = s.hcEnabled
        prefs[globalEnabledKey] = s.globalEnabled
        prefs[cnEnabledKey] = s.cnEnabled
        prefs[globalKeyKey] = s.globalKey
        prefs[cnKeyKey] = s.cnKey
        prefs[orderKey] = s.order.joinToString(",")
        prefs[tokenGlobalKey] = s.tokenGlobal
        prefs[tokenCnKey] = s.tokenCn
        prefs[tokenGlobalExpiryKey] = s.tokenGlobalExpiresAt
        prefs[tokenCnExpiryKey] = s.tokenCnExpiresAt
        prefs[lastSyncKey] = s.lastSyncMillis
        if (s.lastError.isBlank()) prefs.remove(lastErrorKey) else prefs[lastErrorKey] = s.lastError
    }

    suspend fun snapshot(): MetricSourceState = state.first()

    suspend fun setHcEnabled(enabled: Boolean) = edit { it[hcEnabledKey] = enabled }

    suspend fun setRegionEnabled(region: GarminRegion, enabled: Boolean) = edit {
        if (region == GarminRegion.GLOBAL) it[globalEnabledKey] = enabled
        else it[cnEnabledKey] = enabled
    }

    suspend fun setApiKey(region: GarminRegion, key: String) = edit {
        if (region == GarminRegion.GLOBAL) {
            it[globalKeyKey] = key.trim()
            it.remove(tokenGlobalKey)
            it.remove(tokenGlobalExpiryKey)
        } else {
            it[cnKeyKey] = key.trim()
            it.remove(tokenCnKey)
            it.remove(tokenCnExpiryKey)
        }
        it.remove(lastErrorKey)
    }

    /** Moves [id] one position up in the priority order (earlier = higher priority). */
    suspend fun moveUp(id: String) = edit { prefs ->
        val current = normalizeOrder(
            prefs[orderKey]?.split(',')?.filter { it.isNotBlank() }
                ?: listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC),
        )
        val i = current.indexOf(id)
        if (i > 0) prefs[orderKey] = current.toMutableList().apply { add(i - 1, removeAt(i)) }.joinToString(",")
        else if (i < 0) prefs[orderKey] = (current + id).joinToString(",")
    }

    suspend fun moveDown(id: String) = edit { prefs ->
        val current = normalizeOrder(
            prefs[orderKey]?.split(',')?.filter { it.isNotBlank() }
                ?: listOf(GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC),
        )
        val i = current.indexOf(id)
        if (i in 0 until current.size - 1) prefs[orderKey] = current.toMutableList().apply { add(i + 1, removeAt(i)) }.joinToString(",")
        else if (i < 0) prefs[orderKey] = (current + id).joinToString(",")
    }

    suspend fun saveToken(region: GarminRegion, token: String, expiresAt: Long) = edit {
        if (region == GarminRegion.GLOBAL) {
            it[tokenGlobalKey] = token
            it[tokenGlobalExpiryKey] = expiresAt
        } else {
            it[tokenCnKey] = token
            it[tokenCnExpiryKey] = expiresAt
        }
    }

    suspend fun setStatus(syncMillis: Long, error: String) = edit {
        if (syncMillis > 0L) it[lastSyncKey] = syncMillis
        if (error.isBlank()) it.remove(lastErrorKey) else it[lastErrorKey] = error
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.metricSourceDataStore.edit(block)
    }
}
