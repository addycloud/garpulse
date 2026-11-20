package dev.easonhuang.heartwood.widget

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.LanguagePreferences
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.title
import dev.easonhuang.heartwood.ui.theme.HeartwoodTheme
import dev.easonhuang.heartwood.ui.widgets.ColorPickerDialog
import dev.easonhuang.heartwood.ui.widgets.WidgetPreviewCard
import dev.easonhuang.heartwood.ui.widgets.sampleWidgetStates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Launched from the in-app widget manager to edit an already placed widget. */
const val EXTRA_EDIT_ONLY = "dev.easonhuang.heartwood.extra.WIDGET_EDIT_ONLY"
/** Launched from the in-app manager to edit a widget BEFORE it is placed on the home screen. */
const val EXTRA_DRAFT_MODE = "dev.easonhuang.heartwood.extra.WIDGET_DRAFT_MODE"

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
                HeartwoodTheme {
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
        if (!accepted) {
            WidgetDraftStore.clear(this)
            Toast.makeText(this, getString(R.string.widget_manager_pin_unsupported), Toast.LENGTH_LONG).show()
        }
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

private enum class ColorTarget { BACKGROUND, CARD, TEXT }

private val COLOR_PRESETS = listOf(
    0xFF1E2433, 0xFF0B0F19, 0xFF243447,
    0xFF3B2A4A, 0xFF1B3A32, 0xFFFFFFFF,
)

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
    var config by remember { mutableStateOf(initial) }
    var showMetricPicker by remember { mutableStateOf(false) }
    var appPickerMetric by remember { mutableStateOf<String?>(null) }
    var showGlobalAppPicker by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var previewSize by remember { mutableStateOf(initialSize) }
    var sizeFollowsMetrics by remember { mutableStateOf(draftMode) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val path = onPickImage(uri)
        // The picture is a background layer, so choosing one must never hide the metrics by
        // switching to a special layout. Any layout can show a picture behind its cards.
        if (path != null) config = config.copy(imagePath = path)
    }

    // In draft mode the grid size follows the metric count automatically until the user picks a
    // size manually; that way adding a widget needs no size decision up front.
    LaunchedEffect(config.metrics.size, sizeFollowsMetrics) {
        if (sizeFollowsMetrics) previewSize = WidgetSize.forMetricCount(config.metrics.size)
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
                title = { Text(stringResource(R.string.widget_editor_title)) },
                actions = {
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
                    trailing = {
                        if (sizeFollowsMetrics) {
                            AssistChip(
                                onClick = { sizeFollowsMetrics = false },
                                label = { Text(stringResource(R.string.widget_editor_size_auto)) },
                                leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) },
                            )
                        } else {
                            TextButton(onClick = { sizeFollowsMetrics = true }) {
                                Text(stringResource(R.string.widget_editor_size_auto_reset))
                            }
                        }
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetSize.entries.chunked(4).forEach { chunk ->
                            Column(Modifier.weight(1f)) {
                                chunk.forEach { size ->
                                    FilterChip(
                                        selected = size == previewSize,
                                        onClick = { previewSize = size; sizeFollowsMetrics = false },
                                        label = { Text(stringResource(size.labelRes)) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
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

            // ---- Layout --------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.GridView,
                    title = stringResource(R.string.widget_editor_layout),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetLayout.entries.forEach { layout ->
                            FilterChip(
                                selected = config.layout == layout,
                                onClick = { config = config.copy(layout = layout) },
                                label = { Text(stringResource(layout.labelRes())) },
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    HintText(stringResource(R.string.widget_editor_layout_hint))
                }
            }

            // ---- Metrics -------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.MonitorHeart,
                    title = stringResource(R.string.widget_editor_metrics),
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
                            accent = Color(metric.accent.toArgb()),
                            tapLabel = tapLabel,
                            onSetApp = { appPickerMetric = metric.key },
                            onRemove = {
                                config = config.copy(
                                    metrics = config.metrics - metric,
                                    tapTargets = config.tapTargets - metric.key,
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
                EditorSection(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.widget_editor_appearance),
                ) {
                    SliderRow(
                        label = stringResource(R.string.widget_editor_font_scale, (config.fontScale * 100).roundToInt()),
                        value = config.fontScale,
                        range = WidgetConfig.MIN_FONT_SCALE..WidgetConfig.MAX_FONT_SCALE,
                        steps = 10,
                        onChange = { config = config.copy(fontScale = it) },
                    )
                    SliderRow(
                        label = stringResource(R.string.widget_editor_card_opacity, config.cardOpacity),
                        value = config.cardOpacity.toFloat(),
                        range = 0f..100f,
                        steps = 9,
                        onChange = { config = config.copy(cardOpacity = it.toInt()) },
                    )
                    SliderRow(
                        label = stringResource(R.string.widget_editor_opacity, config.backgroundOpacity),
                        value = config.backgroundOpacity.toFloat(),
                        range = 0f..100f,
                        steps = 9,
                        onChange = { config = config.copy(backgroundOpacity = it.toInt()) },
                    )
                    Spacer(Modifier.size(4.dp))
                    val textColorNow = config.textColor ?: Color(config.accentColor?.toArgb() ?: 0xFFFFFFFF.toInt())
                    SettingLabel(stringResource(R.string.widget_editor_text_color))
                    ColorRow(
                        color = textColorNow,
                        presets = emptyList(),
                        onPreset = {},
                        onPick = { colorTarget = ColorTarget.TEXT },
                        onAuto = { config = config.copy(textColor = null) },
                        autoLabel = stringResource(R.string.widget_editor_auto_color),
                        showAuto = true,
                    )
                    Spacer(Modifier.size(10.dp))
                    SettingLabel(stringResource(R.string.widget_editor_card_color))
                    ColorRow(
                        color = config.cardColor,
                        presets = COLOR_PRESETS,
                        onPreset = { config = config.copy(cardColor = it.copy(alpha = 1f)) },
                        onPick = { colorTarget = ColorTarget.CARD },
                    )
                    Spacer(Modifier.size(10.dp))
                    SettingLabel(stringResource(R.string.widget_editor_background_color))
                    ColorRow(
                        color = config.backgroundColor,
                        presets = COLOR_PRESETS,
                        onPreset = { config = config.copy(backgroundColor = it.copy(alpha = 1f)) },
                        onPick = { colorTarget = ColorTarget.BACKGROUND },
                    )
                    Spacer(Modifier.size(8.dp))
                    ToggleRow(stringResource(R.string.widget_editor_show_chart), config.showChart) {
                        config = config.copy(showChart = it)
                    }
                    ToggleRow(stringResource(R.string.widget_editor_show_labels), config.showLabels) {
                        config = config.copy(showLabels = it)
                    }
                    ToggleRow(stringResource(R.string.widget_editor_refresh_strip), config.showRefreshStrip) {
                        config = config.copy(showRefreshStrip = it)
                    }
                    ToggleRow(stringResource(R.string.widget_editor_show_source), config.showSource) {
                        config = config.copy(showSource = it)
                    }
                }
            }

            // ---- Picture -------------------------------------------------------------------
            item {
                EditorSection(
                    icon = Icons.Rounded.Image,
                    title = stringResource(R.string.widget_editor_image),
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
                            label = stringResource(R.string.widget_editor_image_opacity, config.imageOpacity),
                            value = config.imageOpacity.toFloat(),
                            range = 0f..100f,
                            steps = 9,
                            onChange = { config = config.copy(imageOpacity = it.toInt()) },
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
                ) {
                    val pkg = config.tapActivity?.let { ComponentName.unflattenFromString(it)?.packageName }
                        ?: config.tapPackage
                    val tapActivity = config.tapActivity
                    val label = when {
                        config.tapUri?.isNotBlank() == true -> config.tapUri!!
                        tapActivity != null -> stringResource(
                            R.string.widget_editor_tap_page,
                            appLabel(tapActivity.substringBefore('/')),
                            tapActivity.substringAfterLast('.'),
                        )
                        pkg != null -> stringResource(R.string.widget_editor_tap_app, appLabel(pkg))
                        else -> stringResource(R.string.widget_tap_default)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showGlobalAppPicker = true }
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = {
                            runCatching { testTapIntent(context, config)?.let(context::startActivity) }
                        }) { Icon(Icons.Rounded.Visibility, stringResource(R.string.widget_tap_test)) }
                        IconButton(onClick = {
                            config = config.copy(tapPackage = null, tapActivity = null, tapUri = null)
                        }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.widget_tap_reset))
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    HintText(stringResource(R.string.widget_editor_tap_hint))
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
                }
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
        )
    }
}

@Composable
private fun ColorRow(
    color: Color,
    presets: List<Long>,
    onPreset: (Color) -> Unit,
    onPick: () -> Unit,
    autoLabel: String? = null,
    onAuto: (() -> Unit)? = null,
    showAuto: Boolean = false,
) {
    val currentArgb = color.toArgb()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Always show the colour actually in use, so the picker never hides the current choice.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color))
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.color_picker_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "#%06X".format(currentArgb and 0xFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(onClick = onPick) { Text(stringResource(R.string.color_picker_title)) }
            if (showAuto && onAuto != null && autoLabel != null) {
                Spacer(Modifier.size(6.dp))
                TextButton(onClick = onAuto) { Text(autoLabel) }
            }
        }
        if (presets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                presets.forEach { argb ->
                    val preset = Color(argb.toInt())
                    val isCurrent = currentArgb == argb.toInt()
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(preset)
                                .clickable { onPreset(preset) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(current: String?, onPick: (LauncherApp?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps: List<LauncherApp> = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
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
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

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
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(null) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == null, onClick = null)
                            Text(stringResource(R.string.widget_tap_default))
                        }
                    }
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
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(6.dp))
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
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(tapLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onSetApp) { Text(stringResource(R.string.widget_editor_cell_tap)) }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.size(8.dp))
}

