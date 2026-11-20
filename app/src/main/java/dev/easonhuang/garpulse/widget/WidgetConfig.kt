package dev.easonhuang.garpulse.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.ValueSource

/** How a home-screen widget arranges the metrics configured for it. */
enum class WidgetLayout(val key: String) {
    SINGLE("single"),
    MULTI("multi"),
    ICON_ONLY("icon_only"),
    /** A user-chosen picture fills the widget; no metric cards are drawn. */
    IMAGE("image"),
    ;

    companion object {
        fun fromKey(key: String?): WidgetLayout? = entries.firstOrNull { it.key == key }

        /** Human-readable label resource is resolved by the UI layer. */
        val entriesKeys: List<String> get() = entries.map { it.key }
    }
}

/** Tap destination for one grid cell. Empty values mean "use the global target". */
data class TapTarget(val packageName: String? = null, val component: String? = null, val uri: String? = null) {
    val isEmpty: Boolean get() = packageName.isNullOrBlank() && component.isNullOrBlank() && uri.isNullOrBlank()
}

/**
 * User-editable appearance and behaviour for one home-screen widget instance.
 * Stored in that widget's Glance state, so every placed instance keeps its own settings.
 */
data class WidgetConfig(
    val metrics: List<Metric> = listOf(Metric.STEPS),
    val layout: WidgetLayout = WidgetLayout.SINGLE,
    val showChart: Boolean = true,
    val showLabels: Boolean = true,
    /** Show the tappable wavy refresh ribbon on the widget's left edge. */
    val showRefreshStrip: Boolean = true,
    /** Fine-tuning multiplier for the automatically cell-sized font (0.6 .. 1.6). */
    val fontScale: Float = 1f,
    /** Kept for old instances; the renderer derives its font from the cell size instead. */
    val fontSizeSp: Int = 22,
    /** Outer widget background ARGB, without the user's transparency applied. */
    val backgroundColor: Color = Color(0xFF1E2433),
    /** 0..100, percentage of opacity (100 = fully opaque). */
    val backgroundOpacity: Int = 92,
    /** Inner metric-card ARGB. Independent of [backgroundColor]. */
    val cardColor: Color = Color(0xFF2A2F3B),
    /** 0..100, percentage of opacity for the inner cards. */
    val cardOpacity: Int = 78,
    /** null means "use each metric's accent colour". */
    val accentColor: Color? = null,
    /** Explicit text colour; null means "use each metric's accent colour". */
    val textColor: Color? = null,
    /** Global package to launch on tap; null means GarPulse's own detail screen. */
    val tapPackage: String? = null,
    /** Flattened ComponentName of the chosen global launcher activity, when known. */
    val tapActivity: String? = null,
    /** Global deep-link URI (e.g. mijia://...) opened with ACTION_VIEW; highest tap priority. */
    val tapUri: String? = null,
    /** Per-metric overrides, keyed by [Metric.key]. */
    val tapTargets: Map<String, TapTarget> = emptyMap(),
    /** Internal-file path of a user-selected picture shown in the widget. Null = no picture. */
    val imagePath: String? = null,
    /** Opacity (0..100) of the picture when used as a backdrop behind metric cards. */
    val imageOpacity: Int = 60,
    /** Show a small colour-coded data-source tag on each metric card. */
    val showSource: Boolean = false,
    /** Refresh-strip coverage of the widget width from the left edge, 5..100 (%). */
    val stripCoverage: Int = 10,
    /** Refresh-strip colour incl. alpha; null keeps the original translucent white ribbon. */
    val stripColor: Color? = null,
    /** Refresh-strip wave amplitude multiplier, 0..2 (1 = original ribbon wave). */
    val stripWave: Float = 1f,
    /** Per-metric colour overrides keyed by [Metric.key]; missing keys use the metric accent. */
    val metricColors: Map<String, Color> = emptyMap(),
) {
    val effectiveBackground: Color
        get() = backgroundColor.copy(alpha = (backgroundOpacity.coerceIn(0, 100) / 100f))

    val effectiveCard: Color
        get() = cardColor.copy(alpha = (cardOpacity.coerceIn(0, 100) / 100f))

    /** Per-metric target first, then the global one. */
    fun tapFor(metricKey: String): TapTarget =
        tapTargets[metricKey]?.takeUnless { it.isEmpty }
            ?: TapTarget(tapPackage, tapActivity, tapUri)

    companion object {
        const val MIN_FONT_SP = 12
        const val MAX_FONT_SP = 42
        const val MIN_FONT_SCALE = 0.6f
        const val MAX_FONT_SCALE = 1.6f
        const val MIN_STRIP_COVERAGE = 5
        const val MAX_STRIP_COVERAGE = 100
        const val MIN_STRIP_WAVE = 0f
        const val MAX_STRIP_WAVE = 2f

        val DEFAULT = WidgetConfig()

        /** Defaults used by the legacy full-dashboard widget. */
        val DASHBOARD = WidgetConfig(
            metrics = listOf(Metric.STEPS, Metric.HEART_RATE, Metric.ACTIVE_CALORIES, Metric.SLEEP),
            layout = WidgetLayout.MULTI,
        )

        /** Defaults used by the multi-metric preset widget. */
        val MULTI = DASHBOARD

        /** Defaults used by the icon-only preset widget. */
        val ICONS = WidgetConfig(
            metrics = listOf(Metric.STEPS, Metric.HEART_RATE, Metric.DISTANCE, Metric.SLEEP),
            layout = WidgetLayout.ICON_ONLY,
        )
    }
}

/** One metric's computed data, cached in Glance state for instant recomposition. */
data class WidgetMetricState(
    val metricKey: String,
    val granted: Boolean = false,
    val value: String = "-",
    /** Unit text, already appended to [value] by the data layer for LATEST metrics. */
    val unit: String = "",
    val points: List<Float> = emptyList(),
    /** Concrete source that produced this value, for the small source badge on each cell. */
    val source: ValueSource = ValueSource.HEALTH_CONNECT,
)

/** Preference keys shared by the renderer, config activity and in-app widget manager. */
internal object WidgetPrefs {
    val METRICS = stringPreferencesKey("widget_metrics")
    val LAYOUT = stringPreferencesKey("widget_layout")
    val SHOW_CHART = booleanPreferencesKey("widget_show_chart")
    val SHOW_LABELS = booleanPreferencesKey("widget_show_labels")
    val SHOW_REFRESH_STRIP = booleanPreferencesKey("widget_show_refresh_strip")
    /** Transient animation phase; overwritten on every refresh, never part of WidgetConfig. */
    val REFRESH_FRAME = intPreferencesKey("widget_refresh_frame")
    val FONT_SCALE = floatPreferencesKey("widget_font_scale")
    val FONT_SIZE = intPreferencesKey("widget_font_size")
    val BG_COLOR = longPreferencesKey("widget_bg_color")
    val BG_OPACITY = intPreferencesKey("widget_bg_opacity")
    val CARD_COLOR = longPreferencesKey("widget_card_color")
    val CARD_OPACITY = intPreferencesKey("widget_card_opacity")
    val ACCENT_COLOR = longPreferencesKey("widget_accent_color")
    val TEXT_COLOR = longPreferencesKey("widget_text_color")
    val TAP_PACKAGE = stringPreferencesKey("widget_tap_package")
    val TAP_ACTIVITY = stringPreferencesKey("widget_tap_activity")
    val TAP_URI = stringPreferencesKey("widget_tap_uri")
    val TAP_TARGETS = stringPreferencesKey("widget_tap_targets")
    val IMAGE_PATH = stringPreferencesKey("widget_image_path")
    val IMAGE_OPACITY = intPreferencesKey("widget_image_opacity")
    val SHOW_SOURCE = booleanPreferencesKey("widget_show_source")
    val STRIP_COVERAGE = intPreferencesKey("widget_strip_coverage")
    val STRIP_COLOR = longPreferencesKey("widget_strip_color")
    val STRIP_WAVE = floatPreferencesKey("widget_strip_wave")
    val METRIC_COLORS = stringPreferencesKey("widget_metric_colors")
    val SNAPSHOTS = stringPreferencesKey("widget_snapshots")

    // Legacy keys kept so existing widget instances can still render before being reconfigured.
    val LEGACY_METRIC = stringPreferencesKey("metric_key")
    val LEGACY_VALUE = stringPreferencesKey("value")
    val LEGACY_POINTS = stringPreferencesKey("points")
}

private const val FIELD = '\u001F'
private const val RECORD = '\u001E'
private const val PAIR = '\u001D'
private const val NO_ACCENT = -1L
private const val NO_TEXT_COLOR = -1L

/** Stable serialisation for Glance Preferences (strings only, no Android resources involved). */
internal object WidgetConfigCodec {
    fun write(prefs: MutablePreferences, config: WidgetConfig) {
        prefs[WidgetPrefs.METRICS] = config.metrics.joinToString(",") { it.key }
        prefs[WidgetPrefs.LAYOUT] = config.layout.key
        prefs[WidgetPrefs.SHOW_CHART] = config.showChart
        prefs[WidgetPrefs.SHOW_LABELS] = config.showLabels
        prefs[WidgetPrefs.SHOW_REFRESH_STRIP] = config.showRefreshStrip
        prefs[WidgetPrefs.FONT_SCALE] = config.fontScale.coerceIn(
            WidgetConfig.MIN_FONT_SCALE,
            WidgetConfig.MAX_FONT_SCALE,
        )
        prefs[WidgetPrefs.FONT_SIZE] = config.fontSizeSp.coerceIn(
            WidgetConfig.MIN_FONT_SP,
            WidgetConfig.MAX_FONT_SP,
        )
        prefs[WidgetPrefs.BG_COLOR] = config.backgroundColor.toArgbLong()
        prefs[WidgetPrefs.BG_OPACITY] = config.backgroundOpacity.coerceIn(0, 100)
        prefs[WidgetPrefs.CARD_COLOR] = config.cardColor.toArgbLong()
        prefs[WidgetPrefs.CARD_OPACITY] = config.cardOpacity.coerceIn(0, 100)
        prefs[WidgetPrefs.ACCENT_COLOR] = config.accentColor?.toArgbLong() ?: NO_ACCENT
        prefs[WidgetPrefs.TEXT_COLOR] = config.textColor?.toArgbLong() ?: NO_TEXT_COLOR
        prefs[WidgetPrefs.TAP_PACKAGE] = config.tapPackage?.takeIf { it.isNotBlank() } ?: ""
        prefs[WidgetPrefs.TAP_ACTIVITY] = config.tapActivity?.takeIf { it.isNotBlank() } ?: ""
        prefs[WidgetPrefs.TAP_URI] = config.tapUri?.takeIf { it.isNotBlank() } ?: ""
        prefs[WidgetPrefs.TAP_TARGETS] = encodeTapTargets(config.tapTargets)
        prefs[WidgetPrefs.IMAGE_PATH] = config.imagePath?.takeIf { it.isNotBlank() } ?: ""
        prefs[WidgetPrefs.IMAGE_OPACITY] = config.imageOpacity.coerceIn(0, 100)
        prefs[WidgetPrefs.SHOW_SOURCE] = config.showSource
        prefs[WidgetPrefs.STRIP_COVERAGE] = config.stripCoverage.coerceIn(
            WidgetConfig.MIN_STRIP_COVERAGE, WidgetConfig.MAX_STRIP_COVERAGE,
        )
        prefs[WidgetPrefs.STRIP_COLOR] = config.stripColor?.toArgbLong() ?: NO_ACCENT
        prefs[WidgetPrefs.STRIP_WAVE] = config.stripWave.coerceIn(
            WidgetConfig.MIN_STRIP_WAVE, WidgetConfig.MAX_STRIP_WAVE,
        )
        prefs[WidgetPrefs.METRIC_COLORS] = encodeMetricColors(config.metricColors)
        // Once a full config is written the legacy single-metric keys must be gone, otherwise a
        // deliberately empty metric list would be resurrected as a "steps" snapshot on render.
        prefs.remove(WidgetPrefs.LEGACY_METRIC)
        prefs.remove(WidgetPrefs.LEGACY_VALUE)
        prefs.remove(WidgetPrefs.LEGACY_POINTS)
    }

    fun read(prefs: Preferences, defaults: WidgetConfig): WidgetConfig {
        // An EMPTY metric list is a deliberate choice (a photo-only widget), not corrupt data.
        // Only fall back to legacy/default metrics when the key is genuinely absent (old installs).
        val metrics = prefs[WidgetPrefs.METRICS]?.let { raw ->
            raw.split(',')
                .mapNotNull(String::trim)
                .mapNotNull(Metric::fromKey)
                .distinct()
        } ?: legacyMetrics(prefs, defaults)

        return WidgetConfig(
            metrics = metrics,
            layout = prefs[WidgetPrefs.LAYOUT]?.let(WidgetLayout::fromKey) ?: defaults.layout,
            showChart = prefs[WidgetPrefs.SHOW_CHART] ?: defaults.showChart,
            showLabels = prefs[WidgetPrefs.SHOW_LABELS] ?: defaults.showLabels,
            showRefreshStrip = prefs[WidgetPrefs.SHOW_REFRESH_STRIP] ?: defaults.showRefreshStrip,
            fontScale = (prefs[WidgetPrefs.FONT_SCALE] ?: defaults.fontScale).coerceIn(
                WidgetConfig.MIN_FONT_SCALE,
                WidgetConfig.MAX_FONT_SCALE,
            ),
            fontSizeSp = (prefs[WidgetPrefs.FONT_SIZE] ?: defaults.fontSizeSp).coerceIn(
                WidgetConfig.MIN_FONT_SP,
                WidgetConfig.MAX_FONT_SP,
            ),
            backgroundColor = prefs[WidgetPrefs.BG_COLOR]?.let(::colorFromLong) ?: defaults.backgroundColor,
            backgroundOpacity = (prefs[WidgetPrefs.BG_OPACITY] ?: defaults.backgroundOpacity).coerceIn(0, 100),
            cardColor = prefs[WidgetPrefs.CARD_COLOR]?.let(::colorFromLong) ?: defaults.cardColor,
            cardOpacity = (prefs[WidgetPrefs.CARD_OPACITY] ?: defaults.cardOpacity).coerceIn(0, 100),
            accentColor = prefs[WidgetPrefs.ACCENT_COLOR]
                ?.takeIf { it != NO_ACCENT }
                ?.let(::colorFromLong) ?: defaults.accentColor,
            textColor = prefs[WidgetPrefs.TEXT_COLOR]
                ?.takeIf { it != NO_TEXT_COLOR }
                ?.let(::colorFromLong) ?: defaults.textColor,
            tapPackage = prefs[WidgetPrefs.TAP_PACKAGE]?.takeIf { it.isNotBlank() } ?: defaults.tapPackage,
            tapActivity = prefs[WidgetPrefs.TAP_ACTIVITY]?.takeIf { it.isNotBlank() } ?: defaults.tapActivity,
            tapUri = prefs[WidgetPrefs.TAP_URI]?.takeIf { it.isNotBlank() } ?: defaults.tapUri,
            tapTargets = decodeTapTargets(prefs[WidgetPrefs.TAP_TARGETS]).ifEmpty { defaults.tapTargets },
            imagePath = prefs[WidgetPrefs.IMAGE_PATH]?.takeIf { it.isNotBlank() } ?: defaults.imagePath,
            imageOpacity = (prefs[WidgetPrefs.IMAGE_OPACITY] ?: defaults.imageOpacity).coerceIn(0, 100),
            showSource = prefs[WidgetPrefs.SHOW_SOURCE] ?: defaults.showSource,
            stripCoverage = (prefs[WidgetPrefs.STRIP_COVERAGE] ?: defaults.stripCoverage).coerceIn(
                WidgetConfig.MIN_STRIP_COVERAGE, WidgetConfig.MAX_STRIP_COVERAGE,
            ),
            stripColor = prefs[WidgetPrefs.STRIP_COLOR]
                ?.takeIf { it != NO_ACCENT }
                ?.let(::colorFromLong) ?: defaults.stripColor,
            stripWave = (prefs[WidgetPrefs.STRIP_WAVE] ?: defaults.stripWave).coerceIn(
                WidgetConfig.MIN_STRIP_WAVE, WidgetConfig.MAX_STRIP_WAVE,
            ),
            metricColors = decodeMetricColors(prefs[WidgetPrefs.METRIC_COLORS]).ifEmpty { defaults.metricColors },
        )
    }

    /**
     * Serialises a complete config into one string so it can be staged as a "draft" before the
     * launcher has assigned an appWidgetId (the edit-first-then-add flow). Uses the same control
     * delimiters as the other codecs; tap-targets are nested with their own delimiter set.
     */
    fun encodeDraft(config: WidgetConfig): String = listOf(
        config.metrics.joinToString(",") { it.key },
        config.layout.key,
        if (config.showChart) "1" else "0",
        if (config.showLabels) "1" else "0",
        if (config.showRefreshStrip) "1" else "0",
        config.fontScale.toString(),
        config.fontSizeSp.toString(),
        config.backgroundColor.toArgbLong().toString(),
        config.backgroundOpacity.toString(),
        config.cardColor.toArgbLong().toString(),
        config.cardOpacity.toString(),
        (config.accentColor?.toArgbLong() ?: NO_ACCENT).toString(),
        (config.textColor?.toArgbLong() ?: NO_TEXT_COLOR).toString(),
        config.tapPackage ?: "",
        config.tapActivity ?: "",
        encodeTapTargets(config.tapTargets),
        config.imagePath ?: "",
        config.imageOpacity.toString(),
        if (config.showSource) "1" else "0",
        config.tapUri ?: "",
        config.stripCoverage.toString(),
        (config.stripColor?.toArgbLong() ?: NO_ACCENT).toString(),
        config.stripWave.toString(),
        encodeMetricColors(config.metricColors),
    ).joinToString(FIELD.toString())

    fun decodeDraft(raw: String?): WidgetConfig? {
        if (raw.isNullOrBlank()) return null
        val f = raw.split(FIELD)
        if (f.size < 18) return null
        return runCatching {
            WidgetConfig(
                // Empty metric list is valid: a widget with no metrics shows only its picture.
                metrics = f[0].split(',').mapNotNull(String::trim).mapNotNull(Metric::fromKey)
                    .distinct(),
                layout = WidgetLayout.fromKey(f[1]) ?: WidgetLayout.SINGLE,
                showChart = f[2] == "1",
                showLabels = f[3] == "1",
                showRefreshStrip = f[4] == "1",
                fontScale = f[5].toFloat().coerceIn(WidgetConfig.MIN_FONT_SCALE, WidgetConfig.MAX_FONT_SCALE),
                fontSizeSp = f[6].toInt().coerceIn(WidgetConfig.MIN_FONT_SP, WidgetConfig.MAX_FONT_SP),
                backgroundColor = colorFromLong(f[7].toLong()),
                backgroundOpacity = f[8].toInt().coerceIn(0, 100),
                cardColor = colorFromLong(f[9].toLong()),
                cardOpacity = f[10].toInt().coerceIn(0, 100),
                accentColor = f[11].toLong().takeIf { it != NO_ACCENT }?.let(::colorFromLong),
                textColor = f[12].toLong().takeIf { it != NO_TEXT_COLOR }?.let(::colorFromLong),
                tapPackage = f[13].ifBlank { null },
                tapActivity = f[14].ifBlank { null },
                tapUri = f.getOrNull(19)?.ifBlank { null },
                tapTargets = decodeTapTargets(f[15]),
                imagePath = f[16].ifBlank { null },
                imageOpacity = f[17].toInt().coerceIn(0, 100),
                showSource = f.getOrNull(18) == "1",
                stripCoverage = (f.getOrNull(20)?.toIntOrNull() ?: 10)
                    .coerceIn(WidgetConfig.MIN_STRIP_COVERAGE, WidgetConfig.MAX_STRIP_COVERAGE),
                stripColor = f.getOrNull(21)?.toLongOrNull()
                    ?.takeIf { it != NO_ACCENT }
                    ?.let(::colorFromLong),
                stripWave = (f.getOrNull(22)?.toFloatOrNull() ?: 1f)
                    .coerceIn(WidgetConfig.MIN_STRIP_WAVE, WidgetConfig.MAX_STRIP_WAVE),
                metricColors = decodeMetricColors(f.getOrNull(23)),
            )
        }.getOrNull()
    }

    fun encodeSnapshots(states: List<WidgetMetricState>): String = states.joinToString(RECORD.toString()) { s ->
        listOf(
            s.metricKey,
            if (s.granted) "1" else "0",
            s.value,
            s.unit,
            s.points.joinToString(",") { it.toString() },
            s.source.name,
        ).joinToString(FIELD.toString())
    }

    fun decodeSnapshots(raw: String?): List<WidgetMetricState> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val f = record.split(FIELD)
            if (f.size < 5) return@mapNotNull null
            WidgetMetricState(
                metricKey = f[0],
                granted = f[1] == "1",
                value = f[2],
                unit = f[3],
                points = f[4].split(',').mapNotNull(String::toFloatOrNull),
                source = f.getOrNull(5)?.let { runCatching { ValueSource.valueOf(it) }.getOrNull() }
                    ?: ValueSource.HEALTH_CONNECT,
            )
        }
    }

    private fun encodeTapTargets(targets: Map<String, TapTarget>): String =
        targets.filter { !it.value.isEmpty }
            .map { (key, target) ->
                listOf(key, target.packageName.orEmpty(), target.component.orEmpty(), target.uri.orEmpty())
                    .joinToString(PAIR.toString())
            }
            .joinToString(RECORD.toString())

    internal fun decodeTapTargets(raw: String?): Map<String, TapTarget> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(RECORD).mapNotNull { record ->
            val f = record.split(PAIR)
            if (f.size < 3) return@mapNotNull null
            f[0] to TapTarget(
                f[1].ifBlank { null },
                f[2].ifBlank { null },
                f.getOrNull(3)?.ifBlank { null },
            )
        }.toMap()
    }

    /** Legacy instances only stored one metric and one value; keep them alive. */    fun legacySnapshot(prefs: Preferences, defaults: WidgetConfig): List<WidgetMetricState> {
        val key = prefs[WidgetPrefs.LEGACY_METRIC] ?: return emptyList()
        val value = prefs[WidgetPrefs.LEGACY_VALUE] ?: "-"
        return listOf(
            WidgetMetricState(
                metricKey = key,
                granted = value != "-",
                value = value,
                unit = "",
                points = prefs[WidgetPrefs.LEGACY_POINTS]?.split(',')?.mapNotNull(String::toFloatOrNull)
                    ?: emptyList(),
            ),
        )
    }

    private fun legacyMetrics(prefs: Preferences, defaults: WidgetConfig): List<Metric> =
        prefs[WidgetPrefs.LEGACY_METRIC]?.let(Metric::fromKey)?.let(::listOf) ?: defaults.metrics

    /** "key:argb" pairs joined by commas; metric keys never contain colons. */
    private fun encodeMetricColors(colors: Map<String, Color>): String =
        colors.entries.filter { it.value.alpha > 0f }
            .joinToString(",") { "${it.key}:${it.value.toArgbLong()}" }

    internal fun decodeMetricColors(raw: String?): Map<String, Color> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            if (parts.size != 2) return@mapNotNull null
            val argb = parts[1].toLongOrNull() ?: return@mapNotNull null
            parts[0] to colorFromLong(argb)
        }.toMap()
    }

    private fun Color.toArgbLong(): Long = this.toArgbCompat().toLong() and 0xFFFFFFFFL
    private fun colorFromLong(value: Long): Color = Color(value.toInt())
}

/** Avoids depending on Compose's `toArgb()` extension in plain-JVM serialization tests. */
internal fun Color.toArgbCompat(): Int =
    ((alpha * 255f).toInt() shl 24) or
        ((red * 255f).toInt() shl 16) or
        ((green * 255f).toInt() shl 8) or
        (blue * 255f).toInt()
