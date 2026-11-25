package dev.easonhuang.garpulse.ui.settings

import android.app.Activity
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import dev.easonhuang.garpulse.BuildConfig
import dev.easonhuang.garpulse.GarPulseApp
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.ExportFormat
import dev.easonhuang.garpulse.data.ExportManager
import dev.easonhuang.garpulse.data.AppLanguage
import dev.easonhuang.garpulse.data.DashboardPreferences
import dev.easonhuang.garpulse.data.GarminRegion
import dev.easonhuang.garpulse.data.SOURCE_ID_HC
import dev.easonhuang.garpulse.data.HealthConnectManager
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.UnitPreferences
import dev.easonhuang.garpulse.data.UnitSystem
import dev.easonhuang.garpulse.ui.components.HelpButton
import dev.easonhuang.garpulse.update.AppUpdateChecker
import dev.easonhuang.garpulse.widget.WidgetUpdateScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDateTime
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val REPO_URL = "https://github.com/addycloud/garpulse"

/** Where users create a Garmin Personal API key (consumer key/secret pair). */
private const val GARMIN_KEY_URL = "https://connectus.garmin.cn/modern/settings/personalApiToken"
// Garmin China (国区) account issue page; offered as a second link since accounts differ.
private const val GARMIN_KEY_URL_CN = "https://connect.garmin.cn/app/settings/personalApiToken"

/** Persistent, on-screen outcome of the Garmin connection test. */
private data class TestResult(val success: Boolean, val message: String, val region: GarminRegion? = null)

/** The six selectable launcher logos, each an activity alias carrying the LAUNCHER intent-filter.
 *  The Dark/Light alias names are retained for upgrade compatibility; they now carry the heart
 *  and ribbon marks. The base [dev.easonhuang.garpulse.MainActivity] is never a launcher
 *  component and never disabled, so explicit-component intents (widget taps, deep links)
 *  always keep working. */
private enum class LauncherIcon(
    val component: String,
    @StringRes val labelRes: Int,
    @DrawableRes val foregroundRes: Int,
    @ColorRes val backgroundRes: Int,
) {
    DEFAULT("dev.easonhuang.garpulse.GarPulseIconDefault", R.string.icon_default, R.drawable.ic_launcher_foreground, R.color.ic_launcher_background),
    HEART("dev.easonhuang.garpulse.GarPulseIconDark", R.string.icon_heart, R.drawable.ic_launcher_heart_foreground, R.color.ic_launcher_background_heart),
    RIBBON("dev.easonhuang.garpulse.GarPulseIconLight", R.string.icon_ribbon, R.drawable.ic_launcher_ribbon_foreground, R.color.ic_launcher_background_ribbon),
    WATCH("dev.easonhuang.garpulse.GarPulseIconWatch", R.string.icon_watch, R.drawable.ic_launcher_watch_foreground, R.color.ic_launcher_background_watch),
    STOPWATCH("dev.easonhuang.garpulse.GarPulseIconStopwatch", R.string.icon_stopwatch, R.drawable.ic_launcher_stopwatch_foreground, R.color.ic_launcher_background_stopwatch),
    DIAMOND("dev.easonhuang.garpulse.GarPulseIconDiamond", R.string.icon_diamond, R.drawable.ic_launcher_diamond_foreground, R.color.ic_launcher_background_diamond),
}

/** The icon variant currently enabled for the launcher (aliases default to disabled). */
private fun currentLauncherIcon(context: Context): LauncherIcon =
    LauncherIcon.entries.firstOrNull { entry ->
        context.packageManager.getComponentEnabledSetting(
            ComponentName(context.packageName, entry.component),
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: LauncherIcon.DEFAULT
/** Enables [icon]'s alias and disables the others; the home screen picks the change up shortly. */
private fun applyLauncherIcon(context: Context, icon: LauncherIcon) {
    val pm = context.packageManager
    LauncherIcon.entries.forEach { entry ->
        val component = ComponentName(context.packageName, entry.component)
        val state = if (entry == icon) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: HealthConnectManager,
    exporter: ExportManager,
    units: UnitPreferences,
    language: LanguagePreferences,
    dashboardPrefs: DashboardPreferences,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = LocalLocale.current.platformLocale
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showFormatDialog by remember { mutableStateOf(false) }
    var showUnitsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showColumnsDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showTimesDialog by remember { mutableStateOf(false) }
    var timesInput by remember { mutableStateOf("") }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateReady by remember { mutableStateOf<Pair<File, String>?>(null) }
    var currentIcon by remember { mutableStateOf(currentLauncherIcon(context)) }
    val overviewColumns by dashboardPrefs.columns.collectAsStateWithLifecycle(
        initialValue = DashboardPreferences.DEFAULT_COLUMNS,
    )
    val unitSystem by units.system.collectAsStateWithLifecycle(initialValue = null)

    val app = context.applicationContext as GarPulseApp
    val sourceState by app.metricSource.state.collectAsStateWithLifecycle(initialValue = null)
    val sparkBehindCollect by dashboardPrefs.sparkBehind.collectAsStateWithLifecycle(initialValue = false)
    val hasSavedKey = sourceState?.hasKey == true
    // Per-region key inputs: editing one region's key must never echo into the other's field,
    // and a test on one region must not persist the other's draft.
    var keyInputs by remember { mutableStateOf(mapOf<GarminRegion, String>()) }
    fun keyInput(region: GarminRegion): String = keyInputs[region].orEmpty()
    var editingRegion by remember { mutableStateOf<GarminRegion?>(null) }
    // Which region's connection test is in flight — null when idle. Per-region so a test on
    // one region never shows a loading state on the other's button.
    var testingRegion by remember { mutableStateOf<GarminRegion?>(null) }
    // Result is kept on screen (not a transient snackbar) so the user can actually read it.
    var testResult by remember { mutableStateOf<TestResult?>(null) }

    // Any failure in the save/clear path must surface as a message, never crash the app:
    // an uncaught exception in this coroutine scope takes the whole process down.
    suspend fun showFailure(error: Throwable, what: Int) {
        android.util.Log.e("GarPulseSettings", "key operation failed", error)
        snackbar.showSnackbar(resources.getString(what, error.message ?: error.javaClass.simpleName))
    }

    suspend fun saveKey(region: GarminRegion) {
        val input = keyInput(region)
        if (input.isBlank()) return
        try {
            app.metricSource.setApiKey(region, input)
            // Read-back verification: if the write did not stick (disk, storage, store issue)
            // tell the user immediately instead of silently showing "not configured" later.
            val check = app.metricSource.snapshot()
            val persisted = check.apiKey(region).isNotBlank()
            val enabled = when (region) {
                GarminRegion.GLOBAL -> check.globalEnabled
                GarminRegion.CN -> check.cnEnabled
            }
            android.util.Log.d("GarPulseSource",
                "save ${region.id}: persisted=$persisted enabled=$enabled keyLen=${check.apiKey(region).length}")
            if (!persisted) {
                snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_save_verify_failed))
                return
            }
            keyInputs = keyInputs - region
            editingRegion = null
            testResult = null
            app.garminMetrics.invalidate()
            WidgetUpdateScheduler.refreshNow(context)
            snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_saved))
        } catch (error: Exception) {
            showFailure(error, R.string.garmin_api_key_save_failed)
        }
    }

    suspend fun clearKey(region: GarminRegion) {
        try {
            app.metricSource.setApiKey(region, "")
            keyInputs = keyInputs - region
            editingRegion = null
            testResult = null
            app.garminMetrics.invalidate()
            WidgetUpdateScheduler.refreshNow(context)
            snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_cleared))
        } catch (error: Exception) {
            showFailure(error, R.string.garmin_api_key_save_failed)
        }
    }

    suspend fun testGarmin(region: GarminRegion) {
        // Test always uses the stored key. If the user typed a new one, persist it first.
        val input = keyInput(region)
        try {
            if (input.isNotBlank()) {
                app.metricSource.setApiKey(region, input)
                app.garminMetrics.invalidate()
            }
        } catch (error: Exception) {
            showFailure(error, R.string.garmin_api_key_save_failed)
            return
        }
        if (app.metricSource.snapshot().apiKey(region).isBlank()) {
            testResult = TestResult(false, resources.getString(R.string.garmin_api_no_key), region)
            return
        }
        testingRegion = region
        val result = runCatching {
            val profile = app.garminApi.testConnection(region)
            val name = profile.optString("displayName", "").ifBlank {
                profile.optString("ownerFullName", "Garmin")
            }
            app.garminMetrics.refresh(region)
            WidgetUpdateScheduler.refreshNow(context)
            TestResult(
                success = true,
                message = resources.getString(R.string.garmin_api_test_success, name) +
                    "\n" + resources.getString(R.string.garmin_api_test_success_hint),
                region = region,
            )
        }.getOrElse { error -> TestResult(false, resources.getString(R.string.garmin_api_error, error.message.orEmpty()), region) }
        testingRegion = null
        keyInputs = keyInputs - region
        editingRegion = null
        testResult = result
    }

    fun checkForUpdates() {
        if (updateChecking) return
        scope.launch {
            updateChecking = true
            updateStatus = resources.getString(R.string.update_checking)
            try {
                val probes = AppUpdateChecker.probe()
                val fastest = probes.fastest
                if (fastest == null) {
                    updateStatus = resources.getString(R.string.update_no_network)
                    return@launch
                }
                val debugChannel = BuildConfig.BUILD_TYPE == "debug"
                var latest = AppUpdateChecker.fetchLatest(fastest, debugChannel)
                val via = fastest
                if (latest == null) {
                    val other = if (via == "github") "gitee" else "github"
                    latest = AppUpdateChecker.fetchLatest(other, debugChannel)
                }
                if (latest == null) {
                    updateStatus = resources.getString(R.string.update_no_network)
                    return@launch
                }
                if (AppUpdateChecker.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
                    updateStatus = resources.getString(R.string.update_found, latest.versionName, latest.mirror)
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val apk = AppUpdateChecker.download(latest.apkUrl, File(dir, "garpulse-${latest.versionName}.apk"))
                    if (apk != null) {
                        updateReady = apk to latest.mirror
                        updateStatus = null
                    } else {
                        updateStatus = resources.getString(R.string.update_download_failed)
                    }
                } else {
                    updateStatus = resources.getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                }
            } finally {
                updateChecking = false
            }
        }
    }

    suspend fun runExport(uri: Uri, format: ExportFormat) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val metrics = Metric.entries.filter { manager.permissionFor(it) in granted }
        if (metrics.isEmpty()) {
            snackbar.showSnackbar(resources.getString(R.string.export_nothing)); return
        }
        exporter.export(uri, metrics, format).fold(
            onSuccess = { count -> snackbar.showSnackbar(
                    resources.getQuantityString(R.plurals.export_success, count, count, format.label)
                ) },
            onFailure = { snackbar.showSnackbar(resources.getString(R.string.export_failed, it.message)) },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON) } } }
    // Support tool: dumps the data-source store (secrets masked) so a "settings do not save"
    // report can be diagnosed from a file on the phone.
    val diagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { app.metricSource.diagnosticDump() }
                .getOrElse { "diagnostics failed: ${it.message}" }
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                } != null
            }.getOrDefault(false)
            snackbar.showSnackbar(
                resources.getString(if (ok) R.string.settings_diagnostics_saved else R.string.settings_diagnostics_failed)
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                actions = { HelpButton(R.string.help_settings_title, R.array.help_settings) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_data),
                    R.string.help_settings_data_title,
                    R.array.help_settings_data,
                )
            }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.settings_manage_access),
                        subtitle = stringResource(R.string.settings_manage_access_summary),
                        onClick = onManagePermissions,
                    )
                    SettingRow(
                        icon = Icons.Rounded.MonitorHeart,
                        title = stringResource(R.string.settings_open_hc),
                        subtitle = stringResource(R.string.settings_open_hc_summary),
                        onClick = {
                            runCatching { context.startActivity(HealthConnectManager.settingsIntent()) }
                                .onFailure { scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_open_hc_failed)) } }
                        },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_widget_updates),
                    R.string.help_widget_updates_title,
                    R.array.help_widget_updates,
                )
            }
            item {
                SettingsCard {
                    val interval = WidgetUpdateScheduler.intervalMinutes(context)
                    val times = WidgetUpdateScheduler.fixedTimes(context)
                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        title = stringResource(R.string.settings_widget_interval),
                        subtitle = stringResource(R.string.settings_widget_interval_summary, interval),
                        onClick = { showIntervalDialog = true },
                    )
                    SettingsDivider()
                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        title = stringResource(R.string.settings_widget_fixed),
                        subtitle = times.joinToString(", ").ifBlank { stringResource(R.string.settings_widget_fixed_off) },
                        onClick = {
                            timesInput = times.joinToString(",")
                            showTimesDialog = true
                        },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_data_source),
                    R.string.help_settings_source_title,
                    R.array.help_settings_source,
                )
            }
            item {
                SettingsCard {
                    val current = sourceState
                    // Summary banner: enabled sources in priority order.
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.settings_current_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        val enabledNames = current?.order.orEmpty().mapNotNull { id ->
                            when (id) {
                                SOURCE_ID_HC -> if (current?.hcEnabled == true)
                                    stringResource(R.string.source_health_connect) else null
                                GarminRegion.GLOBAL.id -> if (current?.globalEnabled == true)
                                    stringResource(R.string.source_garmin_global) else null
                                GarminRegion.CN.id -> if (current?.cnEnabled == true)
                                    stringResource(R.string.source_garmin_cn) else null
                                else -> null
                            }
                        }
                        Text(
                            enabledNames.joinToString(" → ").ifBlank { stringResource(R.string.settings_no_source_enabled) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.settings_source_priority_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsDivider()

                    // One row per source: enable switch + priority arrows. Rows render in the
                    // actual priority order so moving up/down visibly swaps them on screen.
                    val rowsByLabel = mapOf(
                        SOURCE_ID_HC to Triple(SOURCE_ID_HC, R.string.source_health_connect, Icons.Rounded.MonitorHeart),
                        GarminRegion.GLOBAL.id to Triple(GarminRegion.GLOBAL.id, R.string.source_garmin_global, Icons.Rounded.CloudSync),
                        GarminRegion.CN.id to Triple(GarminRegion.CN.id, R.string.source_garmin_cn, Icons.Rounded.CloudSync),
                    )
                    val orderedIds = (current?.order ?: listOf(
                        GarminRegion.GLOBAL.id, GarminRegion.CN.id, SOURCE_ID_HC,
                    )).filter { it in rowsByLabel }
                    orderedIds.forEach { id ->
                        val (_, labelRes, icon) = rowsByLabel.getValue(id)
                        val enabled = when (id) {
                            SOURCE_ID_HC -> current?.hcEnabled ?: true
                            GarminRegion.GLOBAL.id -> current?.globalEnabled ?: false
                            else -> current?.cnEnabled ?: false
                        }
                        SourceRow(
                            title = stringResource(labelRes),
                            icon = icon,
                            enabled = enabled,
                            isFirst = id == orderedIds.first(),
                            isLast = id == orderedIds.last(),
                            onToggle = { on ->
                                scope.launch {
                                    try {
                                        when (id) {
                                            SOURCE_ID_HC -> app.metricSource.setHcEnabled(on)
                                            GarminRegion.GLOBAL.id -> app.metricSource.setRegionEnabled(GarminRegion.GLOBAL, on)
                                            else -> app.metricSource.setRegionEnabled(GarminRegion.CN, on)
                                        }
                                        app.garminMetrics.invalidate()
                                        WidgetUpdateScheduler.refreshNow(context)
                                    } catch (error: Exception) {
                                        showFailure(error, R.string.garmin_api_key_save_failed)
                                    }
                                }
                            },
                            onMoveUp = {
                                scope.launch {
                                    try { app.metricSource.moveUp(id); WidgetUpdateScheduler.refreshNow(context) }
                                    catch (error: Exception) { showFailure(error, R.string.garmin_api_key_save_failed) }
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    try { app.metricSource.moveDown(id); WidgetUpdateScheduler.refreshNow(context) }
                                    catch (error: Exception) { showFailure(error, R.string.garmin_api_key_save_failed) }
                                }
                            },
                        )
                        if (id != orderedIds.last()) SettingsDivider()

                        // Garmin rows: key management per region, shown only when enabled.
                        val region = GarminRegion.fromId(id)
                        if (region != null && enabled) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                val saved = current?.apiKey(region)?.isNotBlank() == true
                                // Direct jumps to the region's key-issue page.
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                            if (region == GarminRegion.GLOBAL) GARMIN_KEY_URL else GARMIN_KEY_URL_CN
                                        )))
                                    }
                                }) {
                                    Text(stringResource(if (region == GarminRegion.GLOBAL) R.string.garmin_api_key_link else R.string.garmin_api_key_link_cn))
                                }
                                if (saved && editingRegion != region) {
                                    KeyStatusRow(onChange = { editingRegion = region })
                                } else {
                                    OutlinedTextField(
                                        value = keyInput(region),
                                        onValueChange = { keyInputs = keyInputs + (region to it) },
                                        label = { Text(stringResource(R.string.garmin_api_key_label)) },
                                        placeholder = { Text(stringResource(R.string.garmin_api_key_placeholder)) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { scope.launch { saveKey(region) } },
                                            enabled = keyInput(region).isNotBlank(),
                                        ) { Text(stringResource(R.string.garmin_api_key_save)) }
                                        TextButton(onClick = {
                                            keyInputs = keyInputs - region
                                            editingRegion = null
                                        }) { Text(stringResource(R.string.garmin_api_key_cancel)) }
                                    }
                                }

                                Spacer(Modifier.size(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { scope.launch { testGarmin(region) } },
                                        enabled = testingRegion == null,
                                    ) {
                                        Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(if (testingRegion == region) R.string.garmin_api_testing else R.string.garmin_api_test))
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            app.garminMetrics.refresh(region)
                                            WidgetUpdateScheduler.refreshNow(context)
                                        }
                                    }) { Text(stringResource(R.string.garmin_api_sync_now)) }
                                    if (saved) {
                                        TextButton(onClick = { scope.launch { clearKey(region) } }) {
                                            Text(stringResource(R.string.garmin_api_key_clear))
                                        }
                                    }
                                }

                                testResult?.let { result ->
                                    if (result.region == region) {
                                        Spacer(Modifier.size(10.dp))
                                        TestResultPanel(result)
                                    }
                                }

                                val synced = sourceState?.lastSyncMillis ?: 0L
                                val status = when {
                                    synced > 0L -> stringResource(
                                        R.string.garmin_api_last_sync,
                                        DateTimeFormatter.ofPattern("MMM d HH:mm", currentLocale)
                                            .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(synced), ZoneId.systemDefault())),
                                    )
                                    !saved -> stringResource(R.string.garmin_api_no_key)
                                    else -> stringResource(R.string.garmin_api_status_idle)
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SettingsDivider()
                        }
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_display),
                    R.string.help_settings_display_title,
                    R.array.help_settings_display,
                )
            }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.ViewWeek,
                        title = stringResource(R.string.settings_overview_columns),
                        subtitle = stringResource(R.string.settings_overview_columns_summary, overviewColumns),
                        onClick = { showColumnsDialog = true },
                    )
                    SettingsDivider()
                    // Trend chart placement: its own slot below the value, or a backdrop behind
                    // the whole card content.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_overview_spark),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(
                                    if (sparkBehindCollect) R.string.settings_overview_spark_behind
                                    else R.string.settings_overview_spark_separated,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = sparkBehindCollect,
                            onCheckedChange = { scope.launch { dashboardPrefs.saveSparkBehind(it) } },
                        )
                    }
                    SettingsDivider()
                    SettingRow(
                        icon = Icons.Rounded.Straighten,
                        title = stringResource(R.string.settings_units),
                        subtitle = unitSystem?.let {
                            stringResource(R.string.units_summary, stringResource(it.labelRes), stringResource(it.exampleRes))
                        }.orEmpty(),
                        onClick = { showUnitsDialog = true },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = stringResource(R.string.settings_language_summary),
                        onClick = { showLanguageDialog = true },
                    )
                    SettingRow(
                        icon = Icons.Rounded.AcUnit,
                        title = stringResource(R.string.settings_app_icon),
                        subtitle = stringResource(R.string.settings_app_icon_summary),
                        onClick = { showIconDialog = true },
                    )
                    Text(
                        stringResource(R.string.settings_app_icon_widget_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_export),
                    R.string.help_settings_export_title,
                    R.array.help_settings_export,
                )
            }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_summary),
                        onClick = { showFormatDialog = true },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_about),
                    R.string.help_settings_about_title,
                    R.array.help_settings_about,
                )
            }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        subtitle = stringResource(R.string.settings_version_summary),
                        onClick = {},
                    )
                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        title = stringResource(
                            if (updateChecking) R.string.update_checking else R.string.settings_check_updates,
                        ),
                        subtitle = updateStatus
                            ?: stringResource(R.string.settings_check_updates_summary),
                        onClick = { checkForUpdates() },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_export_diagnostics),
                        subtitle = stringResource(R.string.settings_export_diagnostics_summary),
                        onClick = { diagnosticsLauncher.launch("garpulse-diagnostics.txt") },
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = stringResource(R.string.settings_open_source),
                        subtitle = stringResource(R.string.settings_open_source_summary),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showColumnsDialog) {
        var sliderValue by remember { mutableStateOf(overviewColumns.toFloat()) }
        AlertDialog(
            onDismissRequest = { showColumnsDialog = false },
            title = { Text(stringResource(R.string.settings_overview_columns)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_overview_columns_summary, sliderValue.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it.roundToInt().toFloat() },
                        valueRange = DashboardPreferences.MIN_COLUMNS.toFloat()..DashboardPreferences.MAX_COLUMNS.toFloat(),
                        steps = DashboardPreferences.MAX_COLUMNS - DashboardPreferences.MIN_COLUMNS - 1,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showColumnsDialog = false
                    scope.launch { dashboardPrefs.saveColumns(sliderValue.toInt()) }
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showColumnsDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showIntervalDialog) {
        val options = listOf(15, 30, 60, 120, 240)
        val currentInterval = WidgetUpdateScheduler.intervalMinutes(context)
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.settings_widget_interval)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text(
                        stringResource(R.string.settings_widget_interval_summary, currentInterval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    options.forEach { minutes ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = currentInterval == minutes,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showIntervalDialog = false
                                        WidgetUpdateScheduler.setSchedule(context, minutes, WidgetUpdateScheduler.fixedTimes(context))
                                        WidgetUpdateScheduler.refreshNow(context)
                                    },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currentInterval == minutes, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (minutes >= 60) "${minutes / 60} h" + (if (minutes % 60 != 0) " ${minutes % 60} min" else "")
                                else "$minutes min",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    if (showTimesDialog) {
        AlertDialog(
            onDismissRequest = { showTimesDialog = false },
            title = { Text(stringResource(R.string.settings_widget_fixed)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = timesInput,
                        onValueChange = { timesInput = it },
                        label = { Text(stringResource(R.string.settings_widget_fixed)) },
                        placeholder = { Text("08:00, 22:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.settings_widget_fixed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTimesDialog = false
                    val parsed = timesInput.split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
                    WidgetUpdateScheduler.setSchedule(context, WidgetUpdateScheduler.intervalMinutes(context), parsed)
                    WidgetUpdateScheduler.refreshNow(context)
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimesDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text(stringResource(R.string.settings_app_icon)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    LauncherIcon.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = option == currentIcon,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showIconDialog = false
                                        if (option != currentIcon) {
                                            applyLauncherIcon(context, option)
                                            currentIcon = option
                                            scope.launch {
                                                snackbar.showSnackbar(
                                                    resources.getString(R.string.settings_app_icon_applied),
                                                )
                                            }
                                        }
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == currentIcon, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colorResource(option.backgroundRes)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(option.foregroundRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text(stringResource(R.string.export_dialog_title)) },
            text = { Text(stringResource(R.string.export_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    csvLauncher.launch("garpulse-export.csv")
                }) { Text("CSV") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    jsonLauncher.launch("garpulse-export.json")
                }) { Text("JSON") }
            },
        )
    }

    if (showUnitsDialog) {
        AlertDialog(
            onDismissRequest = { showUnitsDialog = false },
            title = { Text(stringResource(R.string.settings_units)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    UnitSystem.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = option == unitSystem,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showUnitsDialog = false
                                        scope.launch {
                                            units.set(option)
                                            // Widgets render from their own stored state, so redraw them now.
                                            WidgetUpdateScheduler.refreshNow(context)
                                        }
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == unitSystem, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(option.exampleRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnitsDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = option == language.current,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showLanguageDialog = false
                                        language.set(option)
                                        WidgetUpdateScheduler.refreshNow(context)
                                        (context as? Activity)?.recreate()
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == language.current, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    updateReady?.let { (apk, mirror) ->
        AlertDialog(
            onDismissRequest = { updateReady = null },
            title = { Text(stringResource(R.string.update_found_title)) },
            text = { Text(stringResource(R.string.update_ready_body, mirror)) },
            confirmButton = {
                TextButton(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.update_permission_needed),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        return@TextButton
                    }
                    updateReady = null
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", apk,
                    )
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = { updateReady = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    @StringRes helpTitleRes: Int? = null,
    @ArrayRes helpLinesRes: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (helpTitleRes != null && helpLinesRes != null) {
            HelpButton(helpTitleRes, helpLinesRes, modifier = Modifier.size(36.dp))
        }
    }
}

/** Colour token for a data-source row. */
private fun sourceColorFor(id: String): androidx.compose.ui.graphics.Color = when (id) {
    SOURCE_ID_HC -> androidx.compose.ui.graphics.Color(0xFF2BB79C)
    else -> androidx.compose.ui.graphics.Color(0xFF1B8FD6)
}

@Composable
private fun SourcePill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One data-source row: icon + name + priority arrows + enable switch. */
@Composable
private fun SourceRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (enabled) {
            TextButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("↑", style = MaterialTheme.typography.titleMedium) }
            TextButton(
                onClick = onMoveDown,
                enabled = !isLast,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("↓", style = MaterialTheme.typography.titleMedium) }
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun KeyStatusRow(onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Key, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.garmin_api_key_configured),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "••••••••••••",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.garmin_api_key_change)) }
    }
}

@Composable
private fun TestResultPanel(result: TestResult) {
    val color = if (result.success) androidx.compose.ui.graphics.Color(0xFF2BB79C)
    else MaterialTheme.colorScheme.error
    val icon = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp).animateContentSize()) { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(9.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
