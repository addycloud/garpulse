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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
    var showResetSourcesDialog by remember { mutableStateOf(false) }
    var timesInput by remember { mutableStateOf("") }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    // The whole update flow (offer → download with progress → ready → install) lives in one
    // state object so the dialog can never show a stale "ready" for a previous version and the
    // user always sees what is happening right now.
    var updateState by remember { mutableStateOf<UpdateState?>(null) }
    var updatingTag by remember { mutableStateOf<String?>(null) }
    // "Get the latest APK" — lists both channels regardless of whether an update exists, so a
    // user can always fetch the debug or release package on demand.
    var apkDialogOpen by remember { mutableStateOf(false) }
    var apkChannels by remember { mutableStateOf<List<ChannelApk>?>(null) }
    var apkLoadFailed by remember { mutableStateOf(false) }
    var apkBusyTag by remember { mutableStateOf<String?>(null) }
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
    // Saveable so a configuration change / process restart while the user is typing does not
    // silently empty the field (a blank field made the save button do nothing at all).
    var keyInputs by rememberSaveable(
        stateSaver = Saver<Map<String, String>, ArrayList<String>>(
            save = { m -> ArrayList(m.entries.flatMap { listOf(it.key, it.value) }) },
            restore = { flat -> flat.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] } },
        ),
    ) { mutableStateOf(mapOf<String, String>()) }
    fun keyInput(region: GarminRegion): String = keyInputs[region.id].orEmpty()
    var editingRegionId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingRegion: GarminRegion? = GarminRegion.fromId(editingRegionId)
    // Which region's connection test is in flight — null when idle. Per-region so a test on
    // one region never shows a loading state on the other's button.
    var testingRegion by remember { mutableStateOf<GarminRegion?>(null) }
    // "Sync now" also has to say something: a silent no-op looked like a dead button.
    var syncingRegion by remember { mutableStateOf<GarminRegion?>(null) }
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
        // An empty field used to `return` silently — the button looked dead and nothing was
        // written. Always say something and always leave a trace in the persisted op log.
        if (input.isBlank()) {
            app.metricSource.appendKeyOp("save ${region.id}: empty input, ignored")
            snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_empty))
            return
        }
        try {
            app.metricSource.appendKeyOp("save ${region.id}: pressed len=${input.trim().length}")
            // Committed length is read back inside the same write transaction: 0 means storage
            // refused the value, which is a real failure rather than a UI problem.
            val committed = app.metricSource.setApiKey(region, input)
            val snapshotLen = app.metricSource.snapshot().apiKey(region).length
            android.util.Log.d(
                "GarPulseSource",
                "save ${region.id}: committed=$committed snapshot=$snapshotLen",
            )
            app.metricSource.appendKeyOp("save ${region.id}: committed=$committed snapshot=$snapshotLen")
            if (committed == 0 || snapshotLen == 0) {
                snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_save_verify_failed))
                return
            }
            keyInputs = keyInputs - region.id
            editingRegionId = null
            testResult = null
            app.garminMetrics.invalidate()
            WidgetUpdateScheduler.refreshNow(context)
            snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_saved))
        } catch (error: Exception) {
            app.metricSource.appendKeyOp(
                "save ${region.id}: FAILED ${error.javaClass.simpleName}: ${error.message}"
            )
            showFailure(error, R.string.garmin_api_key_save_failed)
        }
    }

    suspend fun clearKey(region: GarminRegion) {
        try {
            val committed = app.metricSource.setApiKey(region, "")
            app.metricSource.appendKeyOp("clear ${region.id}: committed=$committed")
            keyInputs = keyInputs - region.id
            editingRegionId = null
            testResult = null
            app.garminMetrics.invalidate()
            WidgetUpdateScheduler.refreshNow(context)
            snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_cleared))
        } catch (error: Exception) {
            app.metricSource.appendKeyOp(
                "clear ${region.id}: FAILED ${error.javaClass.simpleName}: ${error.message}"
            )
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
            app.metricSource.appendKeyOp("test ${region.id}: no key")
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
        keyInputs = keyInputs - region.id
        editingRegionId = null
        testResult = result
    }

    /** Checks only. Downloading is a separate, explicit step so the dialog can appear at once. */
    fun checkForUpdates() {
        if (updateChecking) return
        scope.launch {
            updateChecking = true
            updateStatus = resources.getString(R.string.update_checking)
            runCatching { app.metricSource.appendKeyOp("update check start") }
            try {
                val debugChannel = BuildConfig.BUILD_TYPE == "debug"
                val latest = AppUpdateChecker.findLatest(debugChannel)
                if (latest == null) {
                    updateStatus = resources.getString(R.string.update_no_network)
                    snackbar.showSnackbar(resources.getString(R.string.update_no_network))
                    runCatching { app.metricSource.appendKeyOp("update check FAILED no mirror reachable") }
                    return@launch
                }
                runCatching { app.metricSource.appendKeyOp("update check: ${latest.tagName} via ${latest.mirror}") }
                if (AppUpdateChecker.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
                    updateStatus = resources.getString(R.string.update_found, latest.versionName, latest.mirror)
                    // Show the offer immediately — the old flow downloaded the whole APK first,
                    // which is what made the dialog take minutes to appear.
                    updateState = UpdateState.Offer(latest)
                } else {
                    updateStatus = resources.getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                    snackbar.showSnackbar(resources.getString(R.string.update_latest, BuildConfig.VERSION_NAME))
                }
            } catch (error: Throwable) {
                android.util.Log.e("GarPulseSettings", "update check failed", error)
                updateStatus = resources.getString(R.string.update_no_network)
                snackbar.showSnackbar(resources.getString(R.string.update_no_network))
            } finally {
                updateChecking = false
            }
        }
    }

    /** Downloads one build with live progress; the dialog reflects it and stays dismissable. */
    fun startUpdateDownload(latest: AppUpdateChecker.Latest) {
        if (updatingTag != null) return
        scope.launch {
            updatingTag = latest.tagName
            updateState = UpdateState.Downloading(latest, 0)
            updateStatus = resources.getString(R.string.update_downloading_label, latest.tagName, 0)
            runCatching { app.metricSource.appendKeyOp("update download start ${latest.tagName}") }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "garpulse-${latest.tagName}.apk")
            fun report(percent: Int) {
                // Called from the IO thread; snapshot state writes are safe and Compose picks
                // the new percentage up on the next frame.
                updateState = UpdateState.Downloading(latest, percent)
                updateStatus = resources.getString(R.string.update_downloading_label, latest.tagName, percent)
            }
            var apk = AppUpdateChecker.download(latest.apkUrl, target) { report(it) }
            if (apk == null) {
                // The mirror that answered the probe fastest is not always the one that can
                // carry 15–30 MB; fall back to the other one before giving up.
                val other = if (latest.mirror == "github") "gitee" else "github"
                updateStatus = resources.getString(R.string.update_retry_other_mirror, other)
                val alt = runCatching {
                    AppUpdateChecker.fetchLatest(other, BuildConfig.BUILD_TYPE == "debug")
                }.getOrNull()
                if (alt != null) {
                    report(0)
                    apk = AppUpdateChecker.download(alt.apkUrl, target) { report(it) }
                }
            }
            updatingTag = null
            if (apk == null) {
                updateState = UpdateState.Failed(latest, resources.getString(R.string.update_download_failed))
                updateStatus = resources.getString(R.string.update_download_failed)
                snackbar.showSnackbar(resources.getString(R.string.update_download_failed))
                runCatching { app.metricSource.appendKeyOp("update download FAILED ${latest.tagName}") }
            } else {
                updateState = UpdateState.Ready(latest, apk)
                updateStatus = resources.getString(R.string.update_downloaded, latest.tagName)
                snackbar.showSnackbar(resources.getString(R.string.update_downloaded, latest.tagName))
                runCatching {
                    app.metricSource.appendKeyOp("update downloaded ${latest.tagName} bytes=${apk.length()}")
                }
            }
        }
    }

    /**
     * Hands the APK to the system installer. Every step is verified first (the file exists, it is
     * a parseable APK, unknown sources are allowed) because a botched hand-off used to surface as
     * "tap install and the app disappears" with no explanation.
     */
    fun installApk(latest: AppUpdateChecker.Latest, apk: File) {
        // appendKeyOp is suspending; fire-and-forget so the install path never has to await a
        // diagnostic write before it can hand over the package.
        fun logUpdate(line: String) {
            scope.launch { runCatching { app.metricSource.appendKeyOp(line) } }
        }
        fun fail(message: String, line: String) {
            logUpdate(line)
            scope.launch { snackbar.showSnackbar(message) }
        }

        if (!apk.exists() || apk.length() < 1_000_000L) {
            runCatching { apk.delete() }
            updateState = UpdateState.Failed(latest, resources.getString(R.string.update_invalid_apk))
            fail(resources.getString(R.string.update_invalid_apk), "install ABORT file missing/short")
            return
        }
        val archive = runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_ACTIVITIES)
        }.getOrNull()
        if (archive == null) {
            runCatching { apk.delete() }
            updateState = UpdateState.Failed(latest, resources.getString(R.string.update_invalid_apk))
            fail(resources.getString(R.string.update_invalid_apk), "install ABORT apk unparsable")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.update_permission_needed),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            logUpdate("install ABORT unknown-sources denied")
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val launched = runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                // The installer reads the URI from another process: grant it explicitly and
                // carry the URI in the clip data as well (some OEM installers only honour that).
                clipData = android.content.ClipData.newRawUri("apk", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        if (launched.isSuccess) {
            logUpdate("install launched ${latest.tagName}")
            updateState = null
            updateStatus = resources.getString(R.string.update_install_opened)
            return
        }
        val error = launched.exceptionOrNull()
        android.util.Log.e("GarPulseSettings", "install intent failed", error)
        logUpdate("install intent FAILED ${error?.javaClass?.simpleName}: ${error?.message}")
        // No installer activity could be opened — let the browser take over rather than leaving
        // the user with a dead button.
        val browser = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(latest.apkUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (browser.isFailure) {
            updateState = UpdateState.Failed(latest, resources.getString(R.string.update_install_failed))
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.update_install_failed)) }
        } else {
            updateState = null
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.update_open_browser)) }
        }
    }

    /** Loads both channels for the "get the latest APK" dialog; they stay downloadable even if
     *  the installed build is already the newest one. */
    fun loadApkChannels() {
        apkChannels = null
        apkLoadFailed = false
        scope.launch {
            val stable = runCatching { AppUpdateChecker.findLatest(false) }.getOrNull()
            val dev = runCatching { AppUpdateChecker.findLatest(true) }.getOrNull()
            if (stable == null && dev == null) apkLoadFailed = true
            apkChannels = listOf(ChannelApk(false, stable), ChannelApk(true, dev))
        }
    }

    fun downloadChannel(channel: ChannelApk) {
        val latest = channel.latest ?: return
        if (updatingTag != null) return
        apkDialogOpen = false
        startUpdateDownload(latest)
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
                                        // Enabling a region without a key does nothing useful —
                                        // say so instead of leaving it looking configured.
                                        val regionRow = GarminRegion.fromId(id)
                                        if (on && regionRow != null &&
                                            app.metricSource.snapshot().apiKey(regionRow).isBlank()
                                        ) {
                                            snackbar.showSnackbar(
                                                resources.getString(R.string.garmin_api_enabled_no_key)
                                            )
                                        }
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
                                    KeyStatusRow(onChange = { editingRegionId = region.id })
                                } else {
                                    OutlinedTextField(
                                        value = keyInput(region),
                                        onValueChange = { keyInputs = keyInputs + (region.id to it) },
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
                                        // Always enabled: tapping it with an empty field now
                                        // explains itself instead of appearing to do nothing.
                                        Button(
                                            onClick = { scope.launch { saveKey(region) } },
                                        ) { Text(stringResource(R.string.garmin_api_key_save)) }
                                        TextButton(onClick = {
                                            keyInputs = keyInputs - region.id
                                            editingRegionId = null
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
                                    TextButton(
                                        enabled = syncingRegion == null,
                                        onClick = {
                                            scope.launch {
                                                syncingRegion = region
                                                val snapshot = runCatching { app.garminMetrics.refresh(region) }.getOrNull()
                                                WidgetUpdateScheduler.refreshNow(context)
                                                syncingRegion = null
                                                snackbar.showSnackbar(
                                                    resources.getString(
                                                        if (snapshot != null) R.string.garmin_api_sync_done
                                                        else R.string.garmin_api_sync_failed
                                                    )
                                                )
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (syncingRegion == region) R.string.garmin_api_syncing
                                                else R.string.garmin_api_sync_now
                                            )
                                        )
                                    }
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
                                // Latest persisted key operation for this region — stays on screen
                                // (unlike a snackbar) so a "save did nothing" report is readable
                                // straight from the phone.
                                // Surface the last sync error instead of only "last synced at":
                                // a failing key/network used to look like a stale widget.
                                val lastError = current?.lastError.orEmpty()
                                if (lastError.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.garmin_api_last_error, lastError),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                val lastOp = current?.keyOpLog
                                    ?.lineSequence()
                                    ?.lastOrNull { it.contains(region.id) }
                                if (!lastOp.isNullOrBlank()) {
                                    Text(
                                        stringResource(R.string.garmin_api_key_last_op, lastOp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
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
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_get_apk),
                        subtitle = stringResource(R.string.settings_get_apk_summary),
                        onClick = {
                            apkDialogOpen = true
                            loadApkChannels()
                        },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_export_diagnostics),
                        subtitle = stringResource(R.string.settings_export_diagnostics_summary),
                        onClick = { diagnosticsLauncher.launch("garpulse-diagnostics.txt") },
                    )
                    SettingRow(
                        icon = Icons.Rounded.SwapVert,
                        title = stringResource(R.string.settings_reset_sources),
                        subtitle = stringResource(R.string.settings_reset_sources_summary),
                        onClick = { showResetSourcesDialog = true },
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
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                resources.getString(R.string.settings_widget_interval_saved, minutes)
                                            )
                                        }
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
                    val parts = timesInput.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    val parsed = parts.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
                    if (parts.isEmpty()) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_widget_times_empty)) }
                        return@TextButton
                    }
                    if (parsed.size != parts.size) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_widget_times_invalid)) }
                        return@TextButton
                    }
                    showTimesDialog = false
                    WidgetUpdateScheduler.setSchedule(context, WidgetUpdateScheduler.intervalMinutes(context), parsed)
                    WidgetUpdateScheduler.refreshNow(context)
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_widget_times_saved)) }
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimesDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showResetSourcesDialog) {
        AlertDialog(
            onDismissRequest = { showResetSourcesDialog = false },
            title = { Text(stringResource(R.string.settings_reset_sources)) },
            text = { Text(stringResource(R.string.settings_reset_sources_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetSourcesDialog = false
                    scope.launch {
                        runCatching { app.metricSource.resetAll() }
                            .onFailure { showFailure(it, R.string.garmin_api_key_save_failed) }
                        app.garminMetrics.invalidate()
                        WidgetUpdateScheduler.refreshNow(context)
                        snackbar.showSnackbar(resources.getString(R.string.settings_reset_sources_done))
                    }
                }) { Text(stringResource(R.string.settings_reset_sources_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetSourcesDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
    updateState?.let { state ->
        val latest = state.latest
        val busy = state is UpdateState.Downloading
        AlertDialog(
            onDismissRequest = { if (!busy) updateState = null },
            title = {
                Text(
                    stringResource(
                        when (state) {
                            is UpdateState.Downloading -> R.string.update_title_downloading
                            is UpdateState.Ready -> R.string.update_title_ready
                            is UpdateState.Failed -> R.string.update_title_failed
                            is UpdateState.Offer -> R.string.update_found_title
                        }
                    )
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.update_dialog_version, latest.tagName, latest.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.update_dialog_mirror, latest.mirror),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                    when (state) {
                        is UpdateState.Offer -> Text(
                            stringResource(R.string.update_offer_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is UpdateState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { state.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.update_progress, state.percent),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (latest.notes.isNotBlank()) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    latest.notes.take(400),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is UpdateState.Ready -> Text(
                            stringResource(R.string.update_ready_body_new),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is UpdateState.Failed -> Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                when (state) {
                    is UpdateState.Offer -> TextButton(onClick = { startUpdateDownload(latest) }) {
                        Text(stringResource(R.string.update_download_and_install))
                    }
                    is UpdateState.Downloading -> TextButton(
                        enabled = false,
                        onClick = {},
                    ) { Text(stringResource(R.string.update_downloading_short)) }
                    is UpdateState.Ready -> TextButton(onClick = {
                        updateState = null
                        installApk(latest, state.apk)
                    }) { Text(stringResource(R.string.update_install)) }
                    is UpdateState.Failed -> TextButton(onClick = { startUpdateDownload(latest) }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { updateState = null },
                ) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    if (apkDialogOpen) {
        AlertDialog(
            onDismissRequest = { apkDialogOpen = false },
            title = { Text(stringResource(R.string.settings_get_apk)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_get_apk_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    when {
                        apkLoadFailed -> Text(
                            stringResource(R.string.settings_get_apk_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        apkChannels == null -> Text(
                            stringResource(R.string.settings_get_apk_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> apkChannels?.forEach { channel ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(
                                            if (channel.debug) R.string.settings_get_apk_dev
                                            else R.string.settings_get_apk_stable
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        channel.latest?.let { latest ->
                                            if (channel.debug) "${latest.versionName}-dev" else latest.versionName
                                        } ?: stringResource(R.string.settings_get_apk_none),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    enabled = channel.latest != null && apkBusyTag == null,
                                    onClick = { downloadChannel(channel) },
                                ) {
                                    Text(
                                        stringResource(
                                            if (channel.latest != null && apkBusyTag == channel.latest.tagName)
                                                R.string.settings_get_apk_downloading
                                            else R.string.action_download
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { apkDialogOpen = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private data class ChannelApk(val debug: Boolean, val latest: AppUpdateChecker.Latest?)

/** One screen state for the whole update flow, so the dialog always matches reality. */
private sealed interface UpdateState {
    val latest: AppUpdateChecker.Latest
    data class Offer(override val latest: AppUpdateChecker.Latest) : UpdateState
    data class Downloading(override val latest: AppUpdateChecker.Latest, val percent: Int) : UpdateState
    data class Ready(override val latest: AppUpdateChecker.Latest, val apk: File) : UpdateState
    data class Failed(override val latest: AppUpdateChecker.Latest, val message: String) : UpdateState
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
