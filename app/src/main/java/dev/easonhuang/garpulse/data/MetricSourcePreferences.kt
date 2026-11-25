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

private const val KEY_OP_LOG_MAX = 2000

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
    /** Recent key save/clear operations, newest last — shown in settings and in diagnostics. */
    val keyOpLog: String = "",
) {
    val globalConfigured: Boolean get() = globalEnabled && globalKey.isNotBlank()
    val cnConfigured: Boolean get() = cnEnabled && cnKey.isNotBlank()

    /** Enabled Garmin regions in priority order. */
    val garminRegions: List<GarminRegion>
        get() = order.mapNotNull { GarminRegion.fromId(it) }
            .filter { it == GarminRegion.GLOBAL && globalConfigured || it == GarminRegion.CN && cnConfigured }

    val anyGarmin: Boolean get() = garminRegions.isNotEmpty()

    /**
     * A Garmin region is switched on, whether or not its key is present right now. Used to keep
     * Garmin-mode UI up while the user is in the middle of clearing / re-entering a key — a
     * transient empty key must not drop the app back to the Health Connect gate.
     */
    val garminIntent: Boolean get() = globalEnabled || cnEnabled

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
    private val keyOpKey = stringPreferencesKey("key_op_log")
    // Legacy keys from the single-source era; read once for migration.
    private val legacySourceKey = stringPreferencesKey("metric_source")
    private val legacyApiKeyKey = stringPreferencesKey("garmin_api_key")
    private val legacyPriorityKey = booleanPreferencesKey("garmin_priority")
    private val migratedKey = booleanPreferencesKey("source_model_migrated")

    // NOTE: never call `remove(key)` on this store. `MutablePreferences.remove` returns the
    // removed value typed as T, and for a `Key<Long>` the compiler unboxes it — removing a
    // token-expiry key that is already absent therefore throws
    // "NullPointerException: ... Number.longValue() on a null object reference" and aborts the
    // whole write transaction (the "key silently refuses to save" bug). `-=` returns Unit.

    val state: Flow<MetricSourceState> = context.metricSourceDataStore.data.map { prefs ->
        if (prefs[migratedKey] != true) migrate(prefs) else readCurrent(prefs)
    }

    /**
     * One-time upgrade from the old single-source model. It must never wipe data written by the
     * new model: old installs can carry a store where the migration stamp is missing while real
     * keys already exist (the stamp is written by the very first read after upgrading, and a
     * failed/aborted write used to leave the store half-populated). In that case we only stamp
     * and keep everything. The legacy clear() is gone for the same reason — it destroyed any
     * key that had been saved in the meantime.
     */
    private suspend fun migrate(prefs: androidx.datastore.preferences.core.Preferences): MetricSourceState {
        val hasNewModelData = prefs[hcEnabledKey] != null ||
            prefs[globalKeyKey] != null || prefs[cnKeyKey] != null ||
            prefs[globalEnabledKey] != null || prefs[cnEnabledKey] != null
        if (hasNewModelData) {
            android.util.Log.w("GarPulseSource", "migration stamp missing but new-model data exists; keeping it")
            runCatching { context.metricSourceDataStore.edit { it[migratedKey] = true } }
            return readCurrent(prefs)
        }
        val legacyKey = prefs[legacyApiKeyKey].orEmpty()
        val migrated = when (prefs[legacySourceKey]) {
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
        // Persist synchronously so later readers see a stable state. No clear(): only the
        // migrated values are written, everything unrelated survives.
        runCatching {
            kotlinx.coroutines.runBlocking {
                context.metricSourceDataStore.edit {
                    writeState(it, migrated)
                    it[migratedKey] = true
                    it -= legacySourceKey
                }
            }
        }.onFailure {
            android.util.Log.e("GarPulseSource", "migration write failed", it)
        }
        return migrated
    }

    private fun readCurrent(prefs: androidx.datastore.preferences.core.Preferences): MetricSourceState =
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
                keyOpLog = prefs[keyOpKey].orEmpty(),
            ).let { s ->
                // Older installs may have both regions enabled; keep only the one with higher
                // priority so a stale key/token is never sent to the wrong host.
                if (s.globalEnabled && s.cnEnabled) {
                    val cnFirst = s.order.indexOf(GarminRegion.CN.id) < s.order.indexOf(GarminRegion.GLOBAL.id)
                    if (cnFirst) s.copy(cnEnabled = true, globalEnabled = false)
                    else s.copy(globalEnabled = true, cnEnabled = false)
                } else s
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
        if (s.lastError.isBlank()) prefs -= lastErrorKey else prefs[lastErrorKey] = s.lastError
    }

    suspend fun snapshot(): MetricSourceState = state.first()

    suspend fun setHcEnabled(enabled: Boolean) = edit { it[hcEnabledKey] = enabled }

    /**
     * Only one Garmin region may be active at a time — the two hosts serve different accounts,
     * and mixing a key/token from one with the endpoints of the other produces 401/404.
     * Enabling a region therefore disables the other one (keys are kept for switching back).
     */
    suspend fun setRegionEnabled(region: GarminRegion, enabled: Boolean) = edit {
        when (region) {
            GarminRegion.GLOBAL -> {
                it[globalEnabledKey] = enabled
                if (enabled) it[cnEnabledKey] = false
            }
            GarminRegion.CN -> {
                it[cnEnabledKey] = enabled
                if (enabled) it[globalEnabledKey] = false
            }
        }
    }

    /**
     * Writes (or with an empty string clears) a region's key and returns how many characters the
     * committed store actually holds afterwards. Reading back inside the same transaction is the
     * only way to prove the write stuck: a mismatched return value is a storage problem, while a
     * correct return value means the value is really on disk.
     */
    suspend fun setApiKey(region: GarminRegion, key: String): Int {
        val trimmed = key.trim()
        val prefs = context.metricSourceDataStore.edit {
            if (region == GarminRegion.GLOBAL) {
                it[globalKeyKey] = trimmed
                it -= tokenGlobalKey
                it -= tokenGlobalExpiryKey
            } else {
                it[cnKeyKey] = trimmed
                it -= tokenCnKey
                it -= tokenCnExpiryKey
            }
            it -= lastErrorKey
        }
        val committed = (if (region == GarminRegion.GLOBAL) prefs[globalKeyKey] else prefs[cnKeyKey]).orEmpty()
        android.util.Log.d(
            "GarPulseSource",
            "setApiKey ${region.id}: wrote=${trimmed.length} committed=${committed.length}",
        )
        appendKeyOp("setApiKey ${region.id} wrote=${trimmed.length} committed=${committed.length}")
        return committed.length
    }

    /**
     * Plain-text trail of key operations ("save pressed with empty field", "write verified", ...).
     * Snackbars are easy to miss and vanish on recreation, so the last operations are persisted
     * and included in the diagnostics export; that is what makes a "save does nothing" report
     * actionable without a PC.
     */
    suspend fun appendKeyOp(line: String) {
        runCatching {
            context.metricSourceDataStore.edit { prefs ->
                val stamp = java.time.LocalTime.now().withNano(0)
                val prev = prefs[keyOpKey].orEmpty()
                val next = (prev + if (prev.isEmpty()) "" else "\n") + "$stamp $line"
                prefs[keyOpKey] = next.takeLast(KEY_OP_LOG_MAX)
            }
        }.onFailure { android.util.Log.e("GarPulseSource", "appendKeyOp failed", it) }
    }

    /**
     * Escape hatch for a store that has become inconsistent (a "save never sticks" report whose
     * only known workaround was wiping the whole app). Clears this data source's preferences and
     * re-stamps the migration so the next read starts from clean defaults.
     */
    suspend fun resetAll() {
        context.metricSourceDataStore.edit {
            it.clear()
            it[migratedKey] = true
        }
        appendKeyOp("resetAll")
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
        if (error.isBlank()) it -= lastErrorKey else it[lastErrorKey] = error
    }

    /**
     * Plain-text diagnostics for user reports: the raw metric-source store (secrets masked to
     * their length and first characters) plus the resolved state. Written to a user-picked file
     * from the settings screen so a "key does not save" report can be diagnosed without a PC.
     */
    suspend fun diagnosticDump(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val prefs = context.metricSourceDataStore.data.first()
        val sb = StringBuilder()
        sb.appendLine("GarPulse diagnostics")
        sb.appendLine("time=${java.time.LocalDateTime.now()}")
        sb.appendLine("version=${dev.easonhuang.garpulse.BuildConfig.VERSION_NAME}")
        sb.appendLine("build=${dev.easonhuang.garpulse.BuildConfig.BUILD_TYPE}")
        sb.appendLine("applicationId=${dev.easonhuang.garpulse.BuildConfig.APPLICATION_ID}")
        sb.appendLine("migrationStamp=${prefs[migratedKey]}")
        sb.appendLine()
        val storeDir = java.io.File(context.filesDir, "datastore")
        sb.appendLine("--- store files (${storeDir.absolutePath}) ---")
        runCatching {
            storeDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                sb.appendLine("${f.name} size=${f.length()}")
            } ?: sb.appendLine("(none)")
        }.onFailure { sb.appendLine("listing failed: ${it.message}") }
        sb.appendLine()
        sb.appendLine("--- key op log ---")
        sb.appendLine(prefs[keyOpKey].orEmpty().ifBlank { "(empty)" })
        sb.appendLine()
        sb.appendLine("--- raw metric_source store ---")
        prefs.asMap().entries.sortedBy { it.key.name }.forEach { (key, value) ->
            val text = value?.toString().orEmpty()
            val sensitive = key.name != keyOpKey.name &&
                (key.name.contains("key") || key.name.contains("token"))
            sb.appendLine(
                if (sensitive) "${key.name} = <len=${text.length} head=${text.take(4)}>"
                else "${key.name} = $text"
            )
        }
        sb.appendLine()
        sb.appendLine("--- resolved state ---")
        val s = snapshot()
        sb.appendLine("hcEnabled=${s.hcEnabled}")
        sb.appendLine("globalEnabled=${s.globalEnabled} globalKeyLen=${s.globalKey.length} tokenGlobalLen=${s.tokenGlobal.length} tokenGlobalExp=${s.tokenGlobalExpiresAt}")
        sb.appendLine("cnEnabled=${s.cnEnabled} cnKeyLen=${s.cnKey.length} tokenCnLen=${s.tokenCn.length} tokenCnExp=${s.tokenCnExpiresAt}")
        sb.appendLine("order=${s.order.joinToString(",")}")
        sb.appendLine("anyGarmin=${s.anyGarmin} hasKey=${s.hasKey}")
        sb.appendLine("lastSync=${s.lastSyncMillis}")
        sb.appendLine("lastError=${s.lastError}")
        sb.appendLine("now=${System.currentTimeMillis()}")
        sb.toString()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.metricSourceDataStore.edit(block)
    }
}
