package dev.easonhuang.heartwood.data

import androidx.compose.ui.graphics.Color
import dev.easonhuang.heartwood.R
import java.time.Instant

/**
 * The source that actually supplied a value. Distinct from [MetricSource], which is the user's
 * *selection*: in mirrored (AUTO) mode a single screen can show tiles from both sources, so each
 * value carries the concrete origin it was read from.
 */
enum class ValueSource(val labelRes: Int, val shortRes: Int) {
    HEALTH_CONNECT(R.string.source_health_connect, R.string.source_short_health_connect),
    GARMIN_API(R.string.source_garmin_api, R.string.source_short_garmin),
    ;

    /** Brand token for the source badge: teal = synced Health Connect, Garmin blue = API. */
    val badgeColor: Color
        get() = when (this) {
            HEALTH_CONNECT -> Color(0xFF2BB79C)
            GARMIN_API -> Color(0xFF1B8FD6)
        }
}

/** One dashboard tile: the headline value for a metric plus a sparkline of recent points. */
data class MetricSummary(
    val metric: Metric,
    val value: String,          // formatted headline, e.g. "8,432" or "-"
    val caption: String?,       // secondary line, e.g. "avg 72, 54-138 bpm"
    val hasData: Boolean,
    val granted: Boolean,
    val spark: List<Float> = emptyList(),
    val system: UnitSystem = UnitSystem.METRIC,  // value, caption and spark are in this
    val source: ValueSource = ValueSource.HEALTH_CONNECT,
)

/** A single charted data point. */
data class SeriesPoint(
    val time: Instant,
    val value: Float,
    val label: String,          // x-axis label, e.g. "Mon" or "14:30"
    val value2: Float? = null,  // optional paired reading, e.g. diastolic for blood pressure
)

/** Full detail payload for one metric. */
data class MetricDetail(
    val metric: Metric,
    val headline: String,
    val caption: String?,
    val points: List<SeriesPoint>,
    val stats: List<Pair<String, String>> = emptyList(),  // label -> value rows
    val recent: List<RecordRow> = emptyList(),
    val system: UnitSystem = UnitSystem.METRIC,  // every value above is in this
    val source: ValueSource = ValueSource.HEALTH_CONNECT,
)

/** A row in the "recent records" list on a detail screen. */
data class RecordRow(
    val primary: String,
    val secondary: String,
)
