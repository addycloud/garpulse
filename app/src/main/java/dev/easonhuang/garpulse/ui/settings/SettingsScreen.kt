package dev.easonhuang.garpulse.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.easonhuang.garpulse.data.DeveloperPreferences
import dev.easonhuang.garpulse.data.HealthConnectManager
import dev.easonhuang.garpulse.data.SourceBadgeLayout
import dev.easonhuang.garpulse.data.SourceBadgeStyle
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.MetricSource
import dev.easonhuang.garpulse.data.UnitPreferences
import dev.easonhuang.garpulse.data.UnitSystem
import dev.easonhuang.garpulse.widget.WidgetUpdateScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val REPO_URL = "https://github.com/addycloud/garpulse"

/** Where users create a Garmin Personal API key (consumer key/secret pair). */
private const val GARMIN_KEY_URL = "https://developer.garmin.com/garmin-connect-api/"

/** Persistent, on-screen outcome of the Garmin connection test. */
private data class TestResult(val success: Boolean, val message: String)

/** The three launcher-icon variants exposed as activity aliases in the manifest. */
private enum class LauncherIcon(val component: String, @StringRes val labelRes: Int) {
    DEFAULT("dev.easonhuang.garpulse.MainActivity", R.string.icon_default),
    DARK("dev.easonhuang.garpulse.GarPulseIconDark", R.string.icon_dark),
    LIGHT("dev.easonhuang.garpulse.GarPulseIconLight", R.string.icon_light),
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

/** Decodes [uri] and pins a home-screen shortcut to GarPulse carrying the image as its icon. */
private suspend fun pinCustomIconShortcut(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bitmap = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        ?: return@withContext false
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return@withContext false
    val shortcut = ShortcutInfoCompat.Builder(context, "custom_icon_${System.currentTimeMillis()}")
        .setShortLabel(context.getString(R.string.app_name))
        .setIcon(IconCompat.createWithBitmap(bitmap))
        .setIntent(launch)
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
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
    var showSourceDialog by remember { mutableStateOf(false) }
    var showColumnsDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    val currentIcon = remember { currentLauncherIcon(context) }

    // Picking an arbitrary image cannot change the launcher's app icon directly (Android only
    // allows static manifest icons), so the custom option pins a home-screen shortcut that
    // opens GarPulse and carries the chosen image as its icon.
    val customIconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching { pinCustomIconShortcut(context, uri) }.isSuccess
                snackbar.showSnackbar(
                    resources.getString(if (ok) R.string.icon_custom_applied else R.string.icon_custom_failed),
                )
            }
        }
    }
    val overviewColumns by dashboardPrefs.columns.collectAsStateWithLifecycle(
        initialValue = DashboardPreferences.DEFAULT_COLUMNS,
    )
    val unitSystem by units.system.collectAsStateWithLifecycle(initialValue = null)

    val app = context.applicationContext as GarPulseApp
    val sourceState by app.metricSource.state.collectAsStateWithLifecycle(initialValue = null)
    val devSpeedCollect by app.developerPrefs.refreshAnimSpeed.collectAsStateWithLifecycle(
        initialValue = DeveloperPreferences.DEFAULT_SPEED,
    )
    val devFramesCollect by app.developerPrefs.refreshAnimFrames.collectAsStateWithLifecycle(
        initialValue = DeveloperPreferences.DEFAULT_FRAMES,
    )
    val devEnabledCollect by app.developerPrefs.enabled.collectAsStateWithLifecycle(
        initialValue = DeveloperPreferences.DEFAULT_ENABLED,
    )
    val devLayoutCollect by app.developerPrefs.sourceBadgeLayout.collectAsStateWithLifecycle(
        initialValue = SourceBadgeLayout.AFTER_TITLE,
    )
    val devStyleCollect by app.developerPrefs.sourceBadgeStyle.collectAsStateWithLifecycle(
        initialValue = SourceBadgeStyle.TEXT,
    )
    val sparkBehindCollect by dashboardPrefs.sparkBehind.collectAsStateWithLifecycle(initialValue = false)
    val hasSavedKey = sourceState?.hasKey == true
    // The key is never pre-filled into a visible field. Editing starts from blank; leaving the
    // field blank on save means "keep the current key", so its value is never echoed in the UI.
    var keyInput by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // Result is kept on screen (not a transient snackbar) so the user can actually read it.
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    LaunchedEffect(sourceState?.apiKey) { keyInput = "" }
    LaunchedEffect(sourceState?.source) { testResult = null }

    suspend fun applySourceChange(source: MetricSource) {
        app.metricSource.setSource(source)
        app.garminMetrics.invalidate()
        WidgetUpdateScheduler.refreshNow(context)
        snackbar.showSnackbar(resources.getString(R.string.garmin_api_source_switched))
    }

    suspend fun saveKey() {
        if (keyInput.isBlank()) return
        app.metricSource.setApiKey(keyInput)
        keyInput = ""
        editingKey = false
        testResult = null
        app.garminMetrics.invalidate()
        WidgetUpdateScheduler.refreshNow(context)
        snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_saved))
    }

    suspend fun clearKey() {
        app.metricSource.setApiKey("")
        keyInput = ""
        editingKey = false
        testResult = null
        app.garminMetrics.invalidate()
        WidgetUpdateScheduler.refreshNow(context)
        snackbar.showSnackbar(resources.getString(R.string.garmin_api_key_cleared))
    }

    suspend fun testGarmin() {
        // Test always uses the stored key. If the user typed a new one, persist it first.
        if (keyInput.isNotBlank()) {
            app.metricSource.setApiKey(keyInput)
            app.garminMetrics.invalidate()
        }
        if (!app.metricSource.state.first().hasKey) {
            testResult = TestResult(false, resources.getString(R.string.garmin_api_no_key))
            return
        }
        testing = true
        val result = runCatching {
            val profile = app.garminApi.testConnection()
            val name = profile.optString("displayName", "").ifBlank {
                profile.optString("ownerFullName", "Garmin")
            }
            app.garminMetrics.refresh()
            WidgetUpdateScheduler.refreshNow(context)
            TestResult(
                success = true,
                message = resources.getString(R.string.garmin_api_test_success, name) +
                    "\n" + resources.getString(R.string.garmin_api_test_success_hint),
            )
        }.getOrElse { error -> TestResult(false, resources.getString(R.string.garmin_api_error, error.message.orEmpty())) }
        testing = false
        keyInput = ""
        editingKey = false
        testResult = result
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

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel(stringResource(R.string.settings_section_data)) }
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
            item { SectionLabel(stringResource(R.string.settings_data_source)) }
            item {
                SettingsCard {
                    val selected = sourceState?.source ?: MetricSource.HEALTH_CONNECT
                    // Prominent, colour-coded "where values come from" banner.
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.settings_current_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        SourcePill(
                            label = stringResource(selected.labelRes),
                            color = sourceColor(selected),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(selected.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsDivider()
                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        title = stringResource(R.string.settings_data_source),
                        subtitle = stringResource(R.string.settings_garmin_api_hint),
                        onClick = { showSourceDialog = true },
                    )
                    if (selected == MetricSource.AUTO) {
                        SettingsDivider()
                        val garminPriority = sourceState?.garminPriority == true
                        var priorityMenu by remember { mutableStateOf(false) }

                        // Switching priority must take effect everywhere at once: the in-app
                        // dashboard re-reads via the invalidated repository, the widgets refresh
                        // immediately, and the user gets a visible confirmation.
                        suspend fun applyPriority(garminFirst: Boolean) {
                            app.metricSource.setGarminPriority(garminFirst)
                            app.garminMetrics.invalidate()
                            WidgetUpdateScheduler.refreshNow(context)
                            snackbar.showSnackbar(
                                resources.getString(
                                    if (garminFirst) R.string.settings_source_priority_garmin
                                    else R.string.settings_source_priority_hc,
                                ),
                            )
                        }

                        SettingRow(
                            icon = Icons.Rounded.SwapVert,
                            title = stringResource(R.string.settings_source_priority),
                            subtitle = stringResource(
                                if (garminPriority) R.string.settings_source_priority_garmin
                                else R.string.settings_source_priority_hc,
                            ),
                            onClick = { priorityMenu = true },
                            trailing = {
                                Box {
                                    Text(
                                        stringResource(
                                            if (garminPriority) R.string.priority_garmin
                                            else R.string.priority_hc,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    DropdownMenu(
                                        expanded = priorityMenu,
                                        onDismissRequest = { priorityMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.priority_garmin)) },
                                            onClick = {
                                                priorityMenu = false
                                                scope.launch { applyPriority(true) }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.priority_hc)) },
                                            onClick = {
                                                priorityMenu = false
                                                scope.launch { applyPriority(false) }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }

                    if (selected != MetricSource.HEALTH_CONNECT) {
                        SettingsDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            SectionCaption(stringResource(R.string.settings_garmin_api))
                            Spacer(Modifier.size(10.dp))

                            // Direct jump to the page where the personal key is issued.
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GARMIN_KEY_URL)))
                                }
                            }) {
                                Text(stringResource(R.string.garmin_api_key_link))
                            }
                            Spacer(Modifier.size(6.dp))

                            if (hasSavedKey && !editingKey) {
                                // Key is stored but never displayed. Only an explicit action reveals
                                // an (empty) input, so the value cannot leak onto the screen.
                                KeyStatusRow(onChange = { editingKey = true })
                            } else {
                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { keyInput = it },
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
                                        onClick = { scope.launch { saveKey() } },
                                        enabled = keyInput.isNotBlank(),
                                    ) { Text(stringResource(R.string.garmin_api_key_save)) }
                                    TextButton(onClick = {
                                        keyInput = ""
                                        editingKey = hasSavedKey
                                    }) { Text(stringResource(R.string.garmin_api_key_cancel)) }
                                }
                            }

                            Spacer(Modifier.size(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { scope.launch { testGarmin() } },
                                    enabled = !testing,
                                ) {
                                    Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(if (testing) R.string.garmin_api_testing else R.string.garmin_api_test))
                                }
                                TextButton(onClick = {
                                    scope.launch { app.garminMetrics.refresh(); WidgetUpdateScheduler.refreshNow(context) }
                                }) { Text(stringResource(R.string.garmin_api_sync_now)) }
                                if (hasSavedKey) {
                                    TextButton(onClick = { scope.launch { clearKey() } }) {
                                        Text(stringResource(R.string.garmin_api_key_clear))
                                    }
                                }
                            }

                            // Persistent, colour-coded result — stays on screen so it can be read.
                            testResult?.let { result ->
                                Spacer(Modifier.size(12.dp))
                                TestResultPanel(result)
                            }

                            val synced = sourceState?.lastSyncMillis ?: 0L
                            val status = when {
                                synced > 0L -> stringResource(
                                    R.string.garmin_api_last_sync,
                                    DateTimeFormatter.ofPattern("MMM d HH:mm", currentLocale)
                                        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(synced), ZoneId.systemDefault())),
                                )
                                !hasSavedKey -> stringResource(R.string.garmin_api_no_key)
                                else -> stringResource(R.string.garmin_api_status_idle)
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_section_display)) }
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
                }
            }
            item { SectionLabel(stringResource(R.string.settings_section_export)) }
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
            item { SectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        subtitle = stringResource(R.string.settings_version_summary),
                        onClick = {},
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
            item { SectionLabel(stringResource(R.string.dev_section)) }
            item {
                SettingsCard {
                    // Master switch — when off every tunable falls back to its default.
                    var devEnabled by remember(devEnabledCollect) { mutableStateOf(devEnabledCollect) }
                    // Refresh wave speed (1×–10×) and duration in frames (2–12).
                    var devSpeed by remember(devSpeedCollect) { mutableStateOf(devSpeedCollect) }
                    var devFrames by remember(devFramesCollect) { mutableStateOf(devFramesCollect) }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.dev_master_switch),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = devEnabled, onCheckedChange = {
                                devEnabled = it
                                scope.launch { app.developerPrefs.setEnabled(it) }
                            })
                        }
                    }
                    if (devEnabled) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                stringResource(R.string.dev_refresh_speed, devSpeed),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.size(8.dp))
                            Slider(
                                value = devSpeed,
                                onValueChange = {
                                    devSpeed = it
                                    scope.launch { app.developerPrefs.setRefreshAnimSpeed(it) }
                                },
                                valueRange = DeveloperPreferences.MIN_SPEED..DeveloperPreferences.MAX_SPEED,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.dev_refresh_frames, devFrames),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.size(8.dp))
                            Slider(
                                value = devFrames.toFloat(),
                                onValueChange = {
                                    devFrames = it.roundToInt()
                                    scope.launch { app.developerPrefs.setRefreshAnimFrames(it.roundToInt()) }
                                },
                                valueRange = DeveloperPreferences.MIN_FRAMES.toFloat()..DeveloperPreferences.MAX_FRAMES.toFloat(),
                                steps = DeveloperPreferences.MAX_FRAMES - DeveloperPreferences.MIN_FRAMES - 1,
                            )
                            Text(
                                stringResource(R.string.dev_refresh_speed_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    // Source badge placement on Overview metric cards (title row placement etc.).
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.dev_source_layout),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.size(8.dp))
                        SourceBadgeLayout.entries.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .selectable(
                                        selected = option == devLayoutCollect,
                                        role = Role.RadioButton,
                                        onClick = {
                                            scope.launch { app.developerPrefs.setSourceBadgeLayout(option) }
                                        },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option == devLayoutCollect, onClick = null)
                                Spacer(Modifier.size(12.dp))
                                Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.dev_source_style),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(R.string.dev_source_style_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        SourceBadgeStyle.entries.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .selectable(
                                        selected = option == devStyleCollect,
                                        role = Role.RadioButton,
                                        onClick = {
                                            scope.launch { app.developerPrefs.setSourceBadgeStyle(option) }
                                        },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option == devStyleCollect, onClick = null)
                                Spacer(Modifier.size(12.dp))
                                Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
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

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text(stringResource(R.string.settings_app_icon)) },
            text = {
                Column(Modifier.selectableGroup()) {
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
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    SettingsDivider()
                    // Custom image: pins a home-screen shortcut with the picked picture as icon.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = false, role = Role.Button, onClick = {
                                showIconDialog = false
                                customIconLauncher.launch("image/*")
                            })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(stringResource(R.string.icon_custom), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.icon_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

    if (showSourceDialog) {
        val currentSource = sourceState?.source ?: MetricSource.HEALTH_CONNECT
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(stringResource(R.string.settings_data_source)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    MetricSource.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = option == currentSource,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showSourceDialog = false
                                        scope.launch { applySourceChange(option) }
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == currentSource, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(option.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

/** Colour token shared by the settings badge and the dashboard source labels. */
@Composable
private fun sourceColor(source: MetricSource): androidx.compose.ui.graphics.Color = when (source) {
    MetricSource.HEALTH_CONNECT -> androidx.compose.ui.graphics.Color(0xFF2BB79C)
    MetricSource.AUTO -> MaterialTheme.colorScheme.primary
    MetricSource.GARMIN_API -> androidx.compose.ui.graphics.Color(0xFF1B8FD6)
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
        Column(Modifier.padding(vertical = 6.dp)) { content() }
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
