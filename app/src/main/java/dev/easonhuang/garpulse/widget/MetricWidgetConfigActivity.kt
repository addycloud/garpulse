package dev.easonhuang.garpulse.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.title
import dev.easonhuang.garpulse.ui.components.HelpButton
import dev.easonhuang.garpulse.ui.theme.GarPulseTheme
import dev.easonhuang.garpulse.ui.widgets.ColorPickerDialog
import dev.easonhuang.garpulse.ui.widgets.WidgetPreviewCard
import dev.easonhuang.garpulse.ui.widgets.sampleWidgetStates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Launched from the in-app widget manager to edit an already placed widget. */
const val EXTRA_EDIT_ONLY = "dev.easonhuang.garpulse.extra.WIDGET_EDIT_ONLY"
/** Launched from the in-app manager to edit a widget BEFORE it is placed on the home screen. */
const val EXTRA_DRAFT_MODE = "dev.easonhuang.garpulse.extra.WIDGET_DRAFT_MODE"

/** Full editor used by the OS "place widget" flow, the in-app editor and the edit-first add flow. */
class MetricWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var editOnly = false
    private var draftMode = false
    private var initial = WidgetConfig.DEFAULT

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences(newBase).applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        editOnly = intent?.getBooleanExtra(EXTRA_EDIT_ONLY, false) == true
        draftMode = intent?.getBooleanExtra(EXTRA_DRAFT_MODE, false) == true

        if (!draftMode && appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        if (!editOnly && !draftMode) setResult(Activity.RESULT_CANCELED)

        lifecycleScope.launch {
            var states: List<WidgetMetricState> = emptyList()
            var actualSize: WidgetSize? = null
            initial = when {
                draftMode -> WidgetConfig.DEFAULT
                editOnly -> runCatching {
                    val glance = GlanceAppWidgetManager(this@MetricWidgetConfigActivity)
                    val id = glance.getGlanceIdBy(appWidgetId)
                    // Preselect the size the widget actually renders at, not the original preset —
                    // the launcher can resize it without changing its provider.
                    actualSize = WidgetSize.fromGlance(this@MetricWidgetConfigActivity, id)
                    val prefs = getAppWidgetState(this@MetricWidgetConfigActivity, PreferencesGlanceStateDefinition, id)
                    states = WidgetConfigCodec.decodeSnapshots(prefs[WidgetPrefs.SNAPSHOTS])
                    WidgetConfigCodec.read(prefs, defaultsForProvider())
                }.getOrElse { defaultsForProvider() }
                else -> defaultsForProvider()
            }
            if (states.isEmpty()) states = sampleWidgetStates(initial, this@MetricWidgetConfigActivity)

            setContent {
                GarPulseTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        WidgetEditor(
                            initial = initial,
                            initialStates = states,
                            initialSize = actualSize ?: WidgetSize.forMetricCount(initial.metrics.size),
                            draftMode = draftMode,
                            onSave = { config, _ -> save(config) },
                            onSaveAndAdd = ::saveDraftAndAdd,
                            onPickImage = ::onPickImage,
                        )
                    }
                }
            }
        }
    }

    private fun defaultsForProvider(): WidgetConfig {
        if (draftMode) return WidgetConfig.DEFAULT
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        return WidgetCatalog.defaultsFor(provider)
    }

    /** Stages the edited config and asks the launcher to place the universal widget. */
    private fun saveDraftAndAdd(config: WidgetConfig) {
        WidgetDraftStore.stage(this, config)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT
        val callback = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, WidgetPinCallback::class.java),
            flags,
        )
        val accepted = WidgetCatalog.pinUniversal(this, callback)
        if (accepted) {
            finish()
            return
        }
        // The launcher refused (or doesn't support) pin requests — e.g. some OEM desktops.
        // Keep the draft staged: the render-time fallback still applies it when the widget
        // lands, no matter how it got added.
        Toast.makeText(this, getString(R.string.widget_manager_pin_fallback), Toast.LENGTH_LONG).show()
        finish()
    }

    /** Imports a gallery image into private storage and returns its stable internal path. */
    private fun onPickImage(uri: Uri?): String? {
        if (uri == null) return null
        return WidgetImageStore.import(this, uri)
    }

    private fun save(config: WidgetConfig) {
        lifecycleScope.launch {
            runCatching {
                val app = applicationContext
                val provider = AppWidgetManager.getInstance(app).getAppWidgetInfo(appWidgetId)?.provider
                val defaults = WidgetCatalog.defaultsFor(provider)
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val mutable = prefs.toMutablePreferences()
                    WidgetConfigCodec.write(mutable, config)
                    mutable
                }
                ConfigurableWidget.refreshData(app, glanceId, defaults)
                WidgetCatalog.widgetFor(provider).update(app, glanceId)
            }
            if (!editOnly && !draftMode) {
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                )
            }
            finish()
        }
    }
}

private data class LauncherApp(val packageName: String, val label: String, val component: String, val uri: String? = null)

private sealed interface ColorTarget {
    data object BACKGROUND : ColorTarget
    data object CARD : ColorTarget
    data object TEXT : ColorTarget
    data class METRIC(val key: String, val metric: Metric) : ColorTarget
    data object STRIP : ColorTarget
}

private val COLOR_PRESETS = listOf(
    0xFF1E2433, 0xFF0B0F19, 0xFF243447,
    0xFF3B2A4A, 0xFF1B3A32, 0xFFFFFFFF,
)

/** Keeps a name on one line in the manager list and the editor title bar. */
private const val MAX_NAME_LENGTH = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetEditor(
    initial: WidgetConfig,
    initialStates: List<WidgetMetricState>,
    initialSize: WidgetSize,
    draftMode: Boolean,
    onSave: (WidgetConfig, WidgetSize) -> Unit,
    onSaveAndAdd: (WidgetConfig) -> Unit,
    onPickImage: (Uri?) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(initial) }
    var showMetricPicker by remember { mutableStateOf(false) }
    var appPickerMetric by remember { mutableStateOf<String?>(null) }
    var showGlobalAppPicker by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    // Size is never chosen manually: the preview (and the widget's suggested grid) always
    // follows the metric count. Users can still drag-resize the placed widget afterwards.
    val previewSize = WidgetSize.forMetricCount(config.metrics.size)

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val path = onPickImage(uri)
        // The picture is a background layer, so choosing one must never hide the metrics by
        // switching to a special layout. Any layout can show a picture behind its cards.
        if (path != null) config = config.copy(imagePath = path)
    }

    // Preview follows the current metric selection, so metrics just added in the picker show up
    // immediately (with a sample value) and removed ones disappear. Live snapshot values win;
    // anything not refreshed yet falls back to that metric's representative sample.
    val snapshotStates = remember { initialStates.associateBy { it.metricKey } }
    val sampleStates = remember { sampleWidgetStates(initial.copy(metrics = Metric.entries.toList()), context).associateBy { it.metricKey } }
    val previewStates = remember(config.metrics) {
        config.metrics.mapNotNull { metric ->
            snapshotStates[metric.key] ?: sampleStates[metric.key]
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        config.name?.trim()?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.widget_editor_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    HelpButton(R.string.help_widget_editor_title, R.array.help_widget_editor)
                    if (draftMode) {
                        FilledTonalButton(onClick = { onSaveAndAdd(config) }) {
                            Text(stringResource(R.string.widget_editor_save_and_add))
                        }
                    } else {
                        FilledTonalButton(onClick = { onSave(config, previewSize) }) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Preview + size -------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.widget_editor_preview),
                    helpTitleRes = R.string.help_widget_preview_title,
                    helpLinesRes = R.array.help_widget_preview,
                    trailing = {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${previewSize.columns} × ${previewSize.rows}",
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) },
                        )
                    },
                ) {
                    Spacer(Modifier.size(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        WidgetPreviewCard(
                            config = config,
                            states = previewStates,
                            width = (previewSize.columns * 58).dp,
                            height = (previewSize.rows * 58).dp,
                            onAddImage = { imageLauncher.launch("image/*") },
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.widget_editor_preview_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Name -----------------------------------------------------------------------
            // Purely an organizational label: it never reaches the home screen, it only makes
            // several placements distinguishable in the manager and in this title bar.
            item {
                EditorSection(
                    icon = Icons.Rounded.Edit,
                    title = stringResource(R.string.widget_editor_name),
                    helpTitleRes = R.string.help_widget_name_title,
                    helpLinesRes = R.array.help_widget_name,
                ) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = config.name.orEmpty(),
                        onValueChange = { config = config.copy(name = it.take(MAX_NAME_LENGTH)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.widget_editor_name_placeholder)) },
                        trailingIcon = {
                            if (!config.name.isNullOrBlank()) {
                                IconButton(onClick = { config = config.copy(name = null) }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.widget_tap_reset))
                                }
                            }
                        },
                    )
                    Spacer(Modifier.size(6.dp))
                    HintText(stringResource(R.string.widget_editor_name_hint))
                }
            }

            // ---- Picture -------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.Image,
                    title = stringResource(R.string.widget_editor_image),
                    helpTitleRes = R.string.help_widget_image_title,
                    helpLinesRes = R.array.help_widget_image,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                config.imagePath?.let { stringResource(R.string.widget_editor_image_selected) }
                                    ?: stringResource(R.string.widget_editor_image_none),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            HintText(
                                config.imagePath?.let { stringResource(R.string.widget_editor_image_selected) }
                                    ?: stringResource(R.string.widget_editor_preview_add_image),
                            )
                        }
                        FilledTonalButton(onClick = { imageLauncher.launch("image/*") }) {
                            Text(
                                stringResource(
                                    if (config.imagePath == null) R.string.widget_editor_add_image
                                    else R.string.widget_editor_replace_image,
                                ),
                            )
                        }
                    }
                    if (config.imagePath != null) {
                        Spacer(Modifier.size(12.dp))
                        SliderRow(
                            label = stringResource(R.string.widget_editor_image_opacity, config.imageTransparency),
                            value = config.imageTransparency.toFloat(),
                            range = 0f..100f,
                            steps = 99,
                            onChange = { config = config.copy(imageOpacity = 100 - it.roundToInt()) },
                        )
                        Spacer(Modifier.size(4.dp))
                        TextButton(onClick = {
                            config.imagePath?.let(WidgetImageStore::delete)
                            config = config.copy(imagePath = null)
                        }) { Text(stringResource(R.string.action_remove)) }
                    }
                    Spacer(Modifier.size(6.dp))
                    HintText(stringResource(R.string.widget_editor_image_hint))
                }
            }

            // ---- Tap -----------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.widget_editor_tap),
                    helpTitleRes = R.string.help_widget_tap_title,
                    helpLinesRes = R.array.help_widget_tap,
                ) {
                    val pkg = config.tapActivity?.let { ComponentName.unflattenFromString(it)?.packageName }
                        ?: config.tapPackage
                    val tapActivity = config.tapActivity
                    val label = when {
                        config.tapUri?.isNotBlank() == true -> config.tapUri!!
                        config.tapPackage != null && TapShortcuts.isShortcut(config.tapPackage!!) ->
                            stringResource(TapShortcuts.labelOf(config.tapPackage!!))
                        tapActivity != null -> stringResource(
                            R.string.widget_editor_tap_page,
                            appLabel(tapActivity.substringBefore('/')),
                            tapActivity.substringAfterLast('.'),
                        )
                        pkg != null -> stringResource(R.string.widget_editor_tap_app, appLabel(pkg))
                        else -> stringResource(R.string.widget_tap_default)
                    }
                    val hasTarget = config.tapPackage != null || config.tapActivity != null || config.tapUri != null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showGlobalAppPicker = true }
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasTarget) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Testing only makes sense when something is actually configured.
                        if (hasTarget) {
                            IconButton(onClick = {
                                if (config.tapPackage != null && TapShortcuts.isShortcut(config.tapPackage!!)) {
                                    scope.launch { TapShortcuts.launch(context, config.tapPackage!!, null) }
                                } else {
                                    runCatching { testTapIntent(context, config)?.let(context::startActivity) }
                                }
                            }) { Icon(Icons.Rounded.Visibility, stringResource(R.string.widget_tap_test)) }
                        }
                        // Clearing is always allowed: "open nothing" is a valid choice.
                        IconButton(
                            onClick = {
                                config = config.copy(tapPackage = null, tapActivity = null, tapUri = null)
                            },
                            enabled = hasTarget,
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                stringResource(R.string.widget_tap_reset),
                                tint = if (hasTarget) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    HintText(stringResource(R.string.widget_editor_tap_hint))
                }
            }
            // ---- Metrics -------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.MonitorHeart,
                    title = stringResource(R.string.widget_editor_metrics),
                    helpTitleRes = R.string.help_widget_metrics_title,
                    helpLinesRes = R.array.help_widget_metrics,
                ) {
                    if (config.metrics.isEmpty()) {
                        HintText(stringResource(R.string.widget_editor_add_metric_hint))
                        Spacer(Modifier.size(8.dp))
                    }
                    config.metrics.forEach { metric ->
                        val target = config.tapFor(metric.key)
                        val pkg = target.component?.let { ComponentName.unflattenFromString(it)?.packageName }
                            ?: target.packageName
                        val tapLabel = when {
                            !target.uri.isNullOrBlank() -> target.uri!!
                            target.packageName != null && TapShortcuts.isShortcut(target.packageName!!) ->
                                stringResource(TapShortcuts.labelOf(target.packageName!!))
                            target.component != null -> stringResource(
                                R.string.widget_editor_tap_page,
                                appLabel(target.component.substringBefore('/')),
                                target.component.substringAfterLast('.'),
                            )
                            pkg != null -> stringResource(R.string.widget_editor_tap_app, appLabel(pkg))
                            else -> stringResource(R.string.widget_tap_default)
                        }
                        MetricRow(
                            title = metric.title,
                            accent = config.metricColors[metric.key]
                                ?: Color(metric.accent.toArgb()),
                            tapLabel = tapLabel,
                            onSetApp = { appPickerMetric = metric.key },
                            onSetColor = { colorTarget = ColorTarget.METRIC(metric.key, metric) },
                            onRemove = {
                                config = config.copy(
                                    metrics = config.metrics - metric,
                                    tapTargets = config.tapTargets - metric.key,
                                    metricColors = config.metricColors - metric.key,
                                )
                            },
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    OutlinedButton(onClick = { showMetricPicker = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.widget_editor_add_metric))
                    }
                }
            }

            // ---- Appearance ----------------------------------------------------------------
            item {
                // There is no such thing as "the appearance of a metric that isn't there", so the
                // whole metric-dependent half of this section collapses when the list is empty.
                val hasMetrics = config.metrics.isNotEmpty()
                EditorSection(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.widget_editor_appearance),
                    helpTitleRes = R.string.help_widget_appearance_title,
                    helpLinesRes = R.array.help_widget_appearance,
                ) {
                    // Switches come FIRST: they decide which elements exist, and every element
                    // specific setting below only shows while its element is actually present.
                    if (hasMetrics) {
                        ToggleRow(stringResource(R.string.widget_editor_show_source), config.showSource) {
                            config = config.copy(showSource = it)
                        }
                        ToggleRow(stringResource(R.string.widget_editor_show_chart), config.showChart) {
                            config = config.copy(showChart = it)
                        }
                        ToggleRow(stringResource(R.string.widget_editor_show_labels), config.showLabels) {
                            config = config.copy(showLabels = it)
                        }
                        ToggleRow(stringResource(R.string.widget_editor_show_value), config.showValue) {
                            config = config.copy(showValue = it)
                        }
                    }
                    ToggleRow(stringResource(R.string.widget_editor_refresh_strip), config.showRefreshStrip) {
                        config = config.copy(showRefreshStrip = it)
                    }
                    if (config.showRefreshStrip) {
                        // Child settings of the switch above — visually nested on purpose.
                        ChildSettings(stringResource(R.string.widget_editor_strip_settings)) {
                            SliderRow(
                                label = stringResource(R.string.widget_editor_strip_coverage, config.stripCoverage),
                                value = config.stripCoverage.toFloat(),
                                range = WidgetConfig.MIN_STRIP_COVERAGE.toFloat()..WidgetConfig.MAX_STRIP_COVERAGE.toFloat(),
                                steps = WidgetConfig.MAX_STRIP_COVERAGE - WidgetConfig.MIN_STRIP_COVERAGE - 1,
                                onChange = { config = config.copy(stripCoverage = it.roundToInt()) },
                            )
                            SliderRow(
                                label = stringResource(
                                    R.string.widget_editor_strip_wave,
                                    (config.stripWave * 100f).roundToInt(),
                                ),
                                value = config.stripWave,
                                range = WidgetConfig.MIN_STRIP_WAVE..WidgetConfig.MAX_STRIP_WAVE,
                                steps = 199,
                                onChange = { config = config.copy(stripWave = it) },
                            )
                            SliderRow(
                                label = stringResource(R.string.widget_editor_strip_opacity, config.stripTransparency),
                                value = config.stripTransparency.toFloat(),
                                range = 0f..100f,
                                steps = 99,
                                onChange = { config = config.copy(stripOpacity = 100 - it.roundToInt()) },
                            )
                            ColorSettingRow(
                                label = stringResource(R.string.widget_editor_strip_color),
                                color = config.stripColor ?: Color.White,
                                onCustom = { colorTarget = ColorTarget.STRIP },
                                autoLabel = stringResource(R.string.widget_editor_auto_short),
                                autoActive = config.stripColor == null,
                                onAuto = { config = config.copy(stripColor = null) },
                            )
                            Spacer(Modifier.size(4.dp))
                            HintText(stringResource(R.string.widget_editor_strip_hint))
                        }
                    }
                    if (hasMetrics) {
                        Spacer(Modifier.size(6.dp))
                        SliderRow(
                            label = stringResource(
                                R.string.widget_editor_font_scale,
                                (config.fontScale * 100).roundToInt(),
                            ),
                            value = config.fontScale,
                            range = WidgetConfig.MIN_FONT_SCALE..WidgetConfig.MAX_FONT_SCALE,
                            steps = 99, // 1% precision across 60%..160%
                            onChange = { config = config.copy(fontScale = it) },
                        )
                        val textColorNow = config.textColor
                            ?: Color(config.accentColor?.toArgb() ?: 0xFFFFFFFF.toInt())
                        ColorSettingRow(
                            label = stringResource(R.string.widget_editor_text_color),
                            color = textColorNow,
                            onCustom = { colorTarget = ColorTarget.TEXT },
                            autoLabel = stringResource(R.string.widget_editor_auto_short),
                            autoActive = config.textColor == null,
                            onAuto = { config = config.copy(textColor = null) },
                        )
                        SliderRow(
                            label = stringResource(R.string.widget_editor_card_opacity, config.cardTransparency),
                            value = config.cardTransparency.toFloat(),
                            range = 0f..100f,
                            steps = 99,
                            onChange = { config = config.copy(cardOpacity = 100 - it.roundToInt()) },
                        )
                        ColorSettingRow(
                            label = stringResource(R.string.widget_editor_card_color),
                            color = config.cardColor,
                            presets = COLOR_PRESETS,
                            onSelect = { config = config.copy(cardColor = it.copy(alpha = 1f)) },
                            onCustom = { colorTarget = ColorTarget.CARD },
                        )
                    }
                    SliderRow(
                        label = stringResource(R.string.widget_editor_opacity, config.backgroundTransparency),
                        value = config.backgroundTransparency.toFloat(),
                        range = 0f..100f,
                        steps = 99,
                        onChange = { config = config.copy(backgroundOpacity = 100 - it.roundToInt()) },
                    )
                    ColorSettingRow(
                        label = stringResource(R.string.widget_editor_background_color),
                        color = config.backgroundColor,
                        presets = COLOR_PRESETS,
                        onSelect = { config = config.copy(backgroundColor = it.copy(alpha = 1f)) },
                        onCustom = { colorTarget = ColorTarget.BACKGROUND },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showMetricPicker) {
        AlertDialog(
            onDismissRequest = { showMetricPicker = false },
            title = { Text(stringResource(R.string.widget_editor_add_metric)) },
            text = {
                LazyColumn {
                    items(Metric.entries.toList()) { metric ->
                        val selected = metric in config.metrics
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // Toggle in place; keep the dialog open so several metrics can
                                // be added or removed in one pass.
                                config = if (selected) {
                                    config.copy(
                                        metrics = config.metrics - metric,
                                        tapTargets = config.tapTargets - metric.key,
                                    )
                                } else {
                                    config.copy(metrics = config.metrics + metric)
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(metric.title)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMetricPicker = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }

    appPickerMetric?.let { metricKey ->
        val current = config.tapTargets[metricKey]?.let {
            it.component?.let(ComponentName::unflattenFromString)?.packageName ?: it.packageName
        }
        AppPickerDialog(
            current = current,
            onPick = { app ->
                val target = when {
                    app == null -> TapTarget()
                    app.uri != null -> TapTarget(uri = app.uri)
                    else -> TapTarget(app.packageName, app.component.takeIf { it.isNotBlank() })
                }
                config = config.copy(tapTargets = config.tapTargets + (metricKey to target))
                appPickerMetric = null
            },
            onDismiss = { appPickerMetric = null },
        )
    }

    if (showGlobalAppPicker) {
        AppPickerDialog(
            current = config.tapActivity?.let { ComponentName.unflattenFromString(it)?.packageName } ?: config.tapPackage,
            onPick = { app ->
                config = when {
                    app == null -> config.copy(tapPackage = null, tapActivity = null, tapUri = null)
                    app.uri != null -> config.copy(tapUri = app.uri, tapPackage = null, tapActivity = null)
                    else -> config.copy(
                        tapPackage = app.packageName,
                        tapActivity = app.component.takeIf { it.isNotBlank() },
                        tapUri = null,
                    )
                }
                showGlobalAppPicker = false
            },
            onDismiss = { showGlobalAppPicker = false },
        )
    }

    colorTarget?.let { target ->
        val initialColor = when (target) {
            ColorTarget.BACKGROUND -> config.backgroundColor
            ColorTarget.CARD -> config.cardColor
            ColorTarget.TEXT -> config.textColor ?: Color.White
            ColorTarget.STRIP -> config.stripColor ?: Color.White.copy(alpha = 0.3f)
            is ColorTarget.METRIC ->
                config.metricColors[target.key] ?: Color(target.metric.accent.toArgb())
        }
        val autoLabel = when (target) {
            is ColorTarget.METRIC -> stringResource(R.string.widget_editor_metric_color_auto, target.metric.title)
            ColorTarget.STRIP -> stringResource(R.string.widget_editor_strip_color_auto)
            else -> stringResource(R.string.widget_editor_auto_color)
        }
        val onAuto: (() -> Unit)? = when (target) {
            ColorTarget.TEXT -> { { config = config.copy(textColor = null) } }
            ColorTarget.STRIP -> { { config = config.copy(stripColor = null) } }
            is ColorTarget.METRIC -> { { config = config.copy(metricColors = config.metricColors - target.key) } }
            else -> null
        }
        ColorPickerDialog(
            initial = initialColor,
            onColor = { picked ->
                config = when (target) {
                    ColorTarget.BACKGROUND -> config.copy(
                        backgroundColor = picked.copy(alpha = 1f),
                        backgroundOpacity = (picked.alpha * 100f).roundToInt().coerceIn(0, 100),
                    )
                    ColorTarget.CARD -> config.copy(
                        cardColor = picked.copy(alpha = 1f),
                        cardOpacity = (picked.alpha * 100f).roundToInt().coerceIn(0, 100),
                    )
                    ColorTarget.TEXT -> config.copy(textColor = picked)
                    ColorTarget.STRIP -> config.copy(stripColor = picked)
                    is ColorTarget.METRIC -> config.copy(
                        metricColors = config.metricColors + (target.key to picked),
                    )
                }
                colorTarget = null
            },
            autoLabel = autoLabel,
            onAuto = onAuto?.let { handler -> { handler(); colorTarget = null } },
            onDismiss = { colorTarget = null },
        )
    }
}

/**
 * One colour setting = ONE row: label on the left, swatches inline, and the selected swatch
 * marked with a ring. Compact on purpose — three colour settings used to consume most of a
 * screen, which buried the switches that actually matter.
 */
@Composable
private fun ColorSettingRow(
    label: String,
    color: Color,
    onCustom: () -> Unit,
    presets: List<Long> = emptyList(),
    onSelect: (Color) -> Unit = {},
    autoLabel: String? = null,
    /** True when the setting is following the metric palette, i.e. no explicit colour. */
    autoActive: Boolean = false,
    onAuto: (() -> Unit)? = null,
) {
    val currentArgb = color.toArgb()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The colour in use is always the first swatch, so the row never hides the truth.
            Swatch(
                color = color,
                selected = presets.isEmpty() || presets.none { it.toInt() == currentArgb },
                onClick = onCustom,
            )
            presets.forEach { argb ->
                val preset = argb.toInt()
                Swatch(
                    color = Color(preset),
                    selected = preset == currentArgb,
                    onClick = { onSelect(Color(preset)) },
                )
            }
            IconButton(onClick = onCustom, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Palette,
                    stringResource(R.string.color_picker_title),
                    Modifier.size(18.dp),
                )
            }
        }
        if (autoLabel != null && onAuto != null) {
            TextButton(
                onClick = onAuto,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    autoLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (autoActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (autoActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A colour chip; the selected one gets a tinted outer ring instead of a tick. */
@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (selected) ring else Color.Transparent)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color)
                .then(if (selected) Modifier else Modifier.border(1.dp, ring, CircleShape)),
        )
    }
}

/**
 * Container for settings that only exist because a switch above is ON. Tinted surface + outline
 * + its own small header make ownership obvious at a glance; the same pattern is used for every
 * element-specific group in the editor, so it reads as one design language.
 */
@Composable
private fun ChildSettings(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 12.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(6.dp))
        content()
    }
}

@Composable
private fun AppPickerDialog(current: String?, onPick: (LauncherApp?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Loading the launcher list touches PackageManager and can take a beat on slow devices —
    // do it off the first frame and show a spinner meanwhile.
    var appsLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            pm.queryIntentActivities(intent, 0)
                .map { info ->
                    LauncherApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        component = ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString(),
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
        appsLoading = false
    }
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Export helpers: the lists are plain-text snapshots so the user can save them and hand
    // them to an AI assistant when composing a tap target.
    val saveAppsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = writeTextFile(context, uri, exportAppsText(context, apps))
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.widget_tap_export_done else R.string.widget_tap_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val savePagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val pkg = expandedPkg
        if (uri != null && pkg != null) {
            val appLabel = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
            scope.launch {
                val ok = writeTextFile(context, uri, exportPagesText(context, appLabel, pages))
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.widget_tap_export_done else R.string.widget_tap_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // In-app pages the other app EXPORTS to the outside world. Not exported (e.g. Mi Home's
    // internal device screens) can never be opened by a third-party widget — Android's sandbox.
    //
    // Display name: an Activity's own label is the ONLY human-readable name Android exposes.
    // Most internal pages don't declare one and just inherit the app label ("米家"), which
    // would repeat the same word on every row — for those we fall back to the short class name.
    fun loadPages(pkg: String) {
        val pm = context.packageManager
        val infos = runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val appLabel = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault("")
        pages = infos.asSequence()
            .filter { it.packageName == pkg && it.exported }
            .distinctBy { it.name }
            .map { info ->
                val own = info.loadLabel(pm).toString().trim()
                val simple = info.name.substringAfterLast('.')
                val displayName = if (own.isNotBlank() && own != appLabel) own else simple
                LauncherApp(info.packageName, displayName, ComponentName(info.packageName, info.name).flattenToString())
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    var linkInput by remember { mutableStateOf("") }
    // Space-separated tokens narrow the search with AND semantics: "xiaomi home" matches only
    // entries containing BOTH words, e.g. com.xiaomi.smarthome's Mi Home.
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    fun matchesTokens(vararg fields: String) = tokens.all { token -> fields.any { it.contains(token, ignoreCase = true) } }
    val filtered = apps.filter { matchesTokens(it.label, it.packageName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_editor_tap)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.widget_editor_search_app)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { saveAppsLauncher.launch("garpulse-launchable-apps.txt") }) {
                        Text(stringResource(R.string.widget_tap_export_apps))
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    if (appsLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    // Built-in destinations (default / GarPulse screens) only make sense when
                    // not searching — with a query they would sit between results forever.
                    if (tokens.isEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(null) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == null, onClick = null)
                            Text(stringResource(R.string.widget_tap_default))
                        }
                    }
                    // Built-in destinations: refresh or jump to one of GarPulse's own screens.
                    item {
                        Text(
                            stringResource(R.string.tap_shortcut_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(TapShortcuts.all) { shortcut ->
                        val label = stringResource(TapShortcuts.labelOf(shortcut))
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onPick(LauncherApp(shortcut, label, ""))
                            }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == shortcut, onClick = null)
                            Text(label)
                        }
                    }
                    } // end built-ins (hidden while searching)
                    if (tokens.size == 1 && filtered.none { it.packageName == query.trim() }) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(LauncherApp(query, query, "")) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = current == query, onClick = null)
                                Text(stringResource(R.string.widget_tap_manual) + ": " + query)
                            }
                        }
                    }
                    items(filtered) { app ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(app) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = app.packageName == current, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(app.label)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = {
                                    if (expandedPkg == app.packageName) {
                                        expandedPkg = null
                                    } else {
                                        expandedPkg = app.packageName
                                        loadPages(app.packageName)
                                    }
                                }) { Text(stringResource(R.string.widget_tap_page_pick)) }
                            }
                            if (expandedPkg == app.packageName) {
                                // The search box filters pages too — some apps export dozens.
                                val shownPages = pages.filter { matchesTokens(it.label, it.component) }
                                when {
                                    pages.isEmpty() -> Text(
                                        stringResource(R.string.widget_tap_no_pages),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                                    )
                                    shownPages.isEmpty() -> Text(
                                        stringResource(R.string.widget_tap_pages_filtered),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                                    )
                                    else -> shownPages.forEach { page ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable { onPick(page) }
                                                .padding(start = 40.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = page.component == current, onClick = null)
                                            Column(Modifier.weight(1f)) {
                                                Text(page.label, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    page.component,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (pages.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        val appLabel = apps.firstOrNull { it.packageName == app.packageName }?.label ?: app.packageName
                                        savePagesLauncher.launch(safeFileName(appLabel) + "-pages.txt")
                                    },
                                    modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                                ) { Text(stringResource(R.string.widget_tap_export_pages)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Deep link: reaches app-registered URI screens (e.g. mijia://...) that the
                // page list above cannot see, because they live behind a scheme, not an export.
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    label = { Text(stringResource(R.string.widget_tap_uri_label)) },
                    placeholder = { Text(stringResource(R.string.widget_tap_uri_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        if (linkInput.isNotBlank()) {
                            onPick(LauncherApp("", linkInput, "", uri = linkInput.trim()))
                        }
                    },
                    enabled = linkInput.isNotBlank(),
                ) { Text(stringResource(R.string.widget_tap_uri_use)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---- Tap-target list exports (plain text, meant to be handed to an AI assistant) --------------

private fun exportAppsText(context: Context, apps: List<LauncherApp>): String = buildString {
    appendLine(context.getString(R.string.widget_tap_export_apps_title))
    appendLine(context.getString(R.string.widget_tap_export_count, apps.size))
    apps.forEach { appendLine("${it.label} | ${it.packageName} | ${it.component}") }
}

private fun exportPagesText(context: Context, appLabel: String, pages: List<LauncherApp>): String = buildString {
    appendLine(context.getString(R.string.widget_tap_export_pages_title, appLabel))
    appendLine(context.getString(R.string.widget_tap_export_count, pages.size))
    pages.forEach { appendLine("${it.label} | ${it.component}") }
}

private suspend fun writeTextFile(context: Context, uri: Uri, text: String): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
    }

/** Keeps an app label usable as a file name on every device. */
private fun safeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40)

@Composable
private fun appLabel(packageName: String): String {
    val pm = LocalContext.current.packageManager
    return runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        info.loadLabel(pm).toString()
    }.getOrDefault(packageName)
}

// ---- Editor design system --------------------------------------------------------------------
//
// Design intent: a SECTION HEADER is structural and non-interactive, an ACTION is obviously
// tappable. To make that unmistakable we use two distinct visual languages:
//   • Header — neutral `onSurface` title + a tinted icon badge, never a coloured button.
//   • Action — tonal/outlined buttons with icons (or chips), always in `primary`.
// Previously both were primary-blue bold text, so users could not tell labels from buttons.

/** A card that groups one logical set of settings under a clearly non-clickable header. */
@Composable
private fun EditorSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    helpTitleRes: Int? = null,
    helpLinesRes: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (helpTitleRes != null && helpLinesRes != null) {
                    HelpButton(helpTitleRes, helpLinesRes, modifier = Modifier.size(36.dp))
                }
                if (trailing != null) trailing()
            }
            Spacer(Modifier.size(16.dp))
            Column(content = content)
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MetricRow(
    title: String,
    accent: Color,
    tapLabel: String,
    onSetApp: () -> Unit,
    onSetColor: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The swatch doubles as the colour-customisation button for this metric.
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onSetColor),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(tapLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSetColor) {
            Icon(Icons.Rounded.Palette, stringResource(R.string.widget_editor_metric_color))
        }
        OutlinedButton(onClick = onSetApp) { Text(stringResource(R.string.widget_editor_cell_tap)) }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.size(8.dp))
}

