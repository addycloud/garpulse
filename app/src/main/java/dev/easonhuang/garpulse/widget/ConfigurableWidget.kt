package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.easonhuang.garpulse.GarPulseApp
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.MetricKind
import dev.easonhuang.garpulse.data.title
import dev.easonhuang.garpulse.data.unit
import kotlinx.coroutines.flow.first

/** Kept for source compatibility with older callers of the original single-metric widget. */
internal val METRIC_KEY = WidgetPrefs.LEGACY_METRIC

/**
 * Base class for every GarPulse home-screen widget. All appearance settings live in the
 * widget's own Glance state, so each placed instance can be configured independently, both
 * from the OS configuration activity and from the in-app widget manager.
 */
abstract class ConfigurableWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    /** Defaults applied the first time this widget kind is bound. */
    abstract fun defaultConfig(context: Context): WidgetConfig

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        refreshData(context, id, defaultConfig(context))
        provideContent {
            GlanceTheme { ConfigurableWidgetContent() }
        }
    }

    companion object {
        /** Reads config + fresh data for [id] and writes both into its Glance state. */
        suspend fun refreshData(context: Context, id: GlanceId, defaults: WidgetConfig) {
            val app = context.applicationContext as GarPulseApp
            val manager = app.healthConnect
            val system = app.units.system.first()
            val stateDefinition = PreferencesGlanceStateDefinition
            val stored = getAppWidgetState(context, stateDefinition, id)
            val config = WidgetConfigCodec.read(stored, defaults)

            val states = config.metrics.map { metric ->
                val granted = manager.isGranted(metric)
                val detail = if (granted) {
                    runCatching { manager.readDetail(metric, system) }.getOrNull()
                } else null
                val unit = metric.unit(context, system)
                val raw = detail?.headline ?: "-"
                // Data layer appends the unit for LATEST metrics; keep value and unit separate
                // so the renderer can size them independently (e.g. "72" + "bpm").
                val value = if (raw == "-" || unit.isBlank()) raw
                else raw.takeIf { it.endsWith(" $unit", ignoreCase = true) }
                    ?.dropLast(unit.length + 1)?.trim() ?: raw
                WidgetMetricState(
                    metricKey = metric.key,
                    granted = granted,
                    value = value,
                    unit = if (raw == "-") "" else unit,
                    points = detail?.points?.map { it.value } ?: emptyList(),
                    source = detail?.source ?: dev.easonhuang.garpulse.data.ValueSource.HEALTH_CONNECT,
                )
            }

            updateAppWidgetState(context, stateDefinition, id) { prefs ->
                val mutable = prefs.toMutablePreferences()
                WidgetConfigCodec.write(mutable, config)
                mutable[WidgetPrefs.SNAPSHOTS] = WidgetConfigCodec.encodeSnapshots(states)
                mutable[WidgetPrefs.REFRESH_FRAME] = 0
                mutable
            }
        }
    }
}

/** Tap action with all target data carried as parameters, resolved when the user clicks. */
internal fun widgetTapAction(target: TapTarget, metricKey: String?) =
    actionRunCallback<WidgetTapAction>(
        actionParametersOf(
            WidgetTapAction.KEY_PACKAGE.to(target.packageName ?: ""),
            WidgetTapAction.KEY_COMPONENT.to(target.component ?: ""),
            WidgetTapAction.KEY_URI.to(target.uri ?: ""),
            WidgetTapAction.KEY_METRIC.to(metricKey ?: ""),
        ),
    )

/** User-selected text colour, falling back to the metric's own accent colour. */
internal fun WidgetConfig.textColorFor(metric: Metric?): Color =
    textColor ?: accentColor ?: metric?.accent ?: Color.White

/**
 * Colour for one metric cell: the per-metric override wins, then the global text colour,
 * then the metric's own accent.
 */
internal fun WidgetConfig.colorFor(metricKey: String?, metric: Metric?): Color =
    metricKey?.let { metricColors[it] } ?: textColorFor(metric)

/** Emoji glyphs keep icon-only widgets useful without shipping 18 extra drawable assets. */
internal fun metricGlyph(key: String): String = when (key) {
    "steps" -> "\uD83D\uDC63"          // shoe
    "distance" -> "\uD83D\uDCCD"       // pin
    "active_calories" -> "\uD83D\uDD25" // fire
    "total_calories" -> "⚡"
    "floors" -> "\uD83C\uDFC5"          // stairs-ish
    "exercise" -> "\uD83C\uDFC3"        // runner
    "hydration" -> "\uD83D\uDCA7"       // droplet
    "heart_rate", "resting_heart_rate" -> "❤"
    "hrv" -> "\uD83D\uDCC8"
    "sleep" -> "\uD83C\uDF19"          // moon
    "oxygen" -> "\uD83D\uDCA8"          // wind
    "respiratory" -> "\uD83E\uDE88"     // lungs
    "vo2max" -> "\uD83D\uDE80"          // rocket
    "weight" -> "⚖"
    "height" -> "\uD83D\uDCCF"          // ruler
    "blood_pressure", "blood_glucose" -> "\uD83D\uDEA8" // siren / alert
    "body_battery" -> "\uD83D\uDD0B"     // torch
    "stress" -> "\uD83D\uDCA1"          // bulb
    "sleep_score" -> "\uD83C\uDF1F"     // star
    "intensity_minutes" -> "⏱"
    "body_fat" -> "\uD83D\uDCCA"        // chart
    else -> "\uD83D\uDCCA"              // chart
}

@Composable
internal fun ConfigurableWidgetContent() {
    val prefs = currentState<Preferences>()
    val config = WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT)
    val states = WidgetConfigCodec.decodeSnapshots(prefs[WidgetPrefs.SNAPSHOTS])
        .ifEmpty { WidgetConfigCodec.legacySnapshot(prefs, config) }
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density

    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(config.effectiveBackground))
        .cornerRadius(24.dp)

    val imageBitmap = WidgetImageStore.load(config.imagePath, maxDimension = (size.width.value * density).toInt().coerceAtLeast(360))
    val globalTap = widgetTapAction(config.tapFor(config.metrics.firstOrNull()?.key.orEmpty()), null)

    // Unified layering: the picture is ALWAYS a full-bleed background. With no metrics it is the
    // whole widget (a photo tile); with metrics their cards float on top. This is one model, so a
    // widget can switch between "photo only" and "photo + data" just by adding/removing metrics.
    // The refresh strip spans the FULL height and overlays the content instead of stealing width.
    Box(modifier = rootModifier) {
        Box(modifier = GlanceModifier.fillMaxSize().clickable(globalTap)) {
            if (imageBitmap != null) {
                Image(
                    provider = ImageProvider(imageBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
                // Darkening scrim ONLY while metric cards float on top (readability aid). A
                // photo-only tile is always the untouched original picture. Neutral black, at
                // half strength so "picture opacity" high = crisp photo, low = dimmed backdrop.
                if (!states.isEmpty() && config.imageOpacity < 100) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().background(
                            ColorProvider(Color.Black.copy(alpha = (100 - config.imageOpacity) / 200f)),
                        ),
                    ) {}
                }
            }
            if (states.isEmpty()) {
                // No metrics: show the picture alone, or a friendly prompt when nothing is set.
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (imageBitmap == null) {
                        Text(
                            text = context.getString(R.string.widget_tap_to_connect_data),
                            style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                        )
                    }
                }
            } else {
                // Layout follows the actual configuration: metric count and grid drive the
                // planner, no manual single/multi/icon-only choice any more.
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val longest = states.maxOfOrNull { it.value.length + it.unit.length } ?: 6
                    val grid = WidgetLayoutPlanner.plan(
                        widthDp = size.width.value,
                        heightDp = size.height.value,
                        metricCount = states.size.coerceAtLeast(1),
                        fontScale = config.fontScale,
                        showChart = config.showChart,
                        valueChars = longest,
                    )
                    AdaptiveGrid(GlanceModifier.fillMaxSize(), config, states, grid, context, density)
                }
            }
        }
        if (config.showRefreshStrip) {
            RefreshStrip(config, prefs.refreshFrame(), density, size.width.value, size.height.value)
        }
    }
}

@Composable
private fun RefreshStrip(
    config: WidgetConfig,
    frame: Int,
    density: Float,
    widthDp: Float,
    heightDp: Float,
) {
    // Coverage 5..100% of the widget width, measured from the left edge. Opacity comes from the
    // dedicated slider (multiplied into any custom colour's own alpha); a soft flicker is kept
    // for the default white ribbon only, and a fully transparent strip stays tappable.
    val coverage = config.stripCoverage.coerceIn(
        WidgetConfig.MIN_STRIP_COVERAGE, WidgetConfig.MAX_STRIP_COVERAGE,
    ) / 100f
    val stripWidthDp = (widthDp * coverage).coerceAtLeast(2f)
    val base = config.stripColor ?: Color.White
    val opacity = config.stripOpacity.coerceIn(0, 100) / 100f
    val alpha = if (config.stripColor != null) {
        base.alpha * opacity
    } else {
        (opacity + (frame % 4) * 0.05f).coerceAtMost(1f)
    }
    val bitmap = WidgetRefreshStrip.bitmap(
        colorArgb = base.copy(alpha = alpha).toArgb(),
        widthPx = (stripWidthDp * density).toInt().coerceAtLeast(6),
        heightPx = (heightDp * density).toInt().coerceAtLeast(8),
        phase = frame,
        wave = config.stripWave.coerceIn(WidgetConfig.MIN_STRIP_WAVE, WidgetConfig.MAX_STRIP_WAVE),
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        // FillBounds stretches the strip to the exact view box. Glance's default Fit preserves
        // the bitmap's tall aspect ratio and shows only its middle slice — the "partial strip".
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier
            .width(stripWidthDp.dp)
            .fillMaxHeight()
            .clickable(WidgetRefreshStrip.action()),
    )
}

@Composable
private fun AdaptiveGrid(
    modifier: GlanceModifier,
    config: WidgetConfig,
    states: List<WidgetMetricState>,
    grid: WidgetGrid,
    context: Context,
    density: Float,
) {
    Column(modifier = modifier) {
        if (states.isEmpty()) {
            Text(
                context.getString(R.string.widget_tap_to_connect_data),
                style = TextStyle(
                    color = ColorProvider(config.textColorFor(null)),
                    fontSize = grid.bodySp.sp,
                ),
            )
            return@Column
        }
        grid.cells.chunked(grid.columns).forEach { row ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { cell ->
                    val state = states.getOrNull(cell.metricIndex) ?: return@forEach
                    MetricCell(
                        state = state,
                        config = config,
                        cell = cell,
                        context = context,
                        density = density,
                        bodySp = grid.bodySp,
                        labelSp = grid.labelSp,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    state: WidgetMetricState,
    config: WidgetConfig,
    cell: GridCell,
    context: Context,
    density: Float,
    bodySp: Float,
    labelSp: Float,
    modifier: GlanceModifier,
) {
    val metric = Metric.fromKey(state.metricKey)
    // Per-metric custom colour first, then the global text colour, then the metric accent.
    val accent = config.colorFor(state.metricKey, metric)
    val compact = cell.heightDp < 96f || cell.widthDp < 120f
    val chartHeight = cell.heightDp * CHART_BACKDROP_RATIO

    // One card per metric. The chart is a translucent backdrop anchored to the lower part,
    // and the title + number always float above it. Because nothing is stacked vertically,
    // a tall bar can never push the digits out of the cell or clip them at the top.
    Box(
        modifier = modifier
            .background(ColorProvider(config.effectiveCard))
            .cornerRadius(if (compact) 12.dp else 16.dp)
            .padding(if (compact) 6.dp else 9.dp)
            .clickable(widgetTapAction(config.tapFor(state.metricKey), state.metricKey)),
    ) {
        if (config.showChart && state.points.size >= 2) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Image(
                    provider = ImageProvider(
                        chartBitmap(
                            values = state.points,
                            kind = metric?.kind ?: MetricKind.LATEST,
                            colorArgb = accent.toArgb(),
                            widthPx = (cell.widthDp * density).toInt().coerceAtLeast(1),
                            heightPx = (chartHeight * density).toInt().coerceAtLeast(1),
                            alpha = 0.38f,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(chartHeight.dp),
                )
            }
        }
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (config.showLabels && metric != null) {
                    Text(
                        text = metric.title(context),
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(accent),
                            fontWeight = FontWeight.Bold,
                            fontSize = labelSp.sp,
                        ),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                }
                if (state.granted) {
                    ValueRow(state.value, state.unit, accent, bodySp.sp)
                } else {
                    Text(
                        text = context.getString(R.string.widget_tap_to_connect),
                        style = TextStyle(color = ColorProvider(accent.copy(alpha = 0.75f)), fontSize = labelSp.sp),
                    )
                }
            }
        }
        // Optional source tag: a small colour-coded dot in the corner (teal = synced, blue =
        // Garmin). Kept tiny so it never competes with the number, matching the home-screen badge.
        if (config.showSource) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Box(
                    GlanceModifier
                        .padding(2.dp)
                        .width(8.dp)
                        .height(8.dp)
                        .background(ColorProvider(state.source.badgeColor))
                        .cornerRadius(4.dp),
                ) {}
            }
        }
    }
}

/** Fraction of a card reserved for the chart backdrop (kept below the text layer). */
internal const val CHART_BACKDROP_RATIO = 0.62f

@Composable
private fun ValueRow(value: String, unit: String, accent: Color, size: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, maxLines = 1, style = TextStyle(color = ColorProvider(accent), fontWeight = FontWeight.Bold, fontSize = size))
        if (unit.isNotBlank()) {
            Spacer(GlanceModifier.width(3.dp))
            Text(
                unit,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(accent.copy(alpha = 0.8f)),
                    fontSize = (size.value * 0.55f).sp,
                ),
            )
        }
    }
}

@Composable
private fun IconGrid(
    modifier: GlanceModifier,
    config: WidgetConfig,
    states: List<WidgetMetricState>,
    grid: WidgetGrid,
) {
    Column(modifier = modifier) {
        if (states.isEmpty()) {
            Text("❤", style = TextStyle(color = ColorProvider(config.textColorFor(null)), fontSize = grid.bodySp.sp))
            return@Column
        }
        grid.cells.chunked(grid.columns).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { cell ->
                    val state = states.getOrNull(cell.metricIndex) ?: return@forEach
                    val color = config.colorFor(state.metricKey, Metric.fromKey(state.metricKey))
                    Text(
                        text = metricGlyph(state.metricKey),
                        style = TextStyle(color = ColorProvider(color), fontSize = grid.bodySp.sp),
                        modifier = GlanceModifier.defaultWeight()
                            .clickable(widgetTapAction(config.tapFor(state.metricKey), state.metricKey)),
                    )
                }
            }
        }
    }
}
