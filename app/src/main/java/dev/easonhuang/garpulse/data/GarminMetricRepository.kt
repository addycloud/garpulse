package dev.easonhuang.garpulse.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.easonhuang.garpulse.R

/** One calendar day of Garmin wellness data, already in the app's base units. */
data class GarminDay(
    val date: String,
    val steps: Float? = null,
    val distanceKm: Float? = null,
    val floors: Float? = null,
    val activeCalories: Float? = null,
    val totalCalories: Float? = null,
    val restingHr: Float? = null,
    val minHr: Float? = null,
    val maxHr: Float? = null,
    val stress: Float? = null,
    val maxStress: Float? = null,
    val bodyBattery: Float? = null,
    val batteryCharged: Float? = null,
    val batteryDrained: Float? = null,
    val respiration: Float? = null,
    val intensityMinutes: Float? = null,
    val hydrationLiters: Float? = null,
    val sleepHours: Float? = null,
    val sleepScore: Float? = null,
    val deepSleepHours: Float? = null,
    val lightSleepHours: Float? = null,
    val remSleepHours: Float? = null,
    val hrv: Float? = null,
    val weightKg: Float? = null,
    val bodyFat: Float? = null,
)

data class GarminSnapshot(
    val days: List<GarminDay>,
    val latestHeartRate: Float? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

// ---- JSON helpers -----------------------------------------------------------------------------

private fun JSONObject.num(name: String): Float? = when (val v = opt(name)) {
    is Number -> v.toFloat()
    is String -> v.toFloatOrNull()
    else -> null
}

private fun JSONObject.obj(name: String): JSONObject? = optJSONObject(name)

private fun JSONObject.child(vararg path: String): Float? {
    var node = this
    for (i in 0 until path.size - 1) node = node.obj(path[i]) ?: return null
    return node.num(path.last())
}

private fun positive(value: Float?): Float? = value?.takeIf { it >= 0f && !it.isNaN() }

private fun JSONObject.date(): String? = optString("calendarDate", "").takeIf { it.isNotBlank() }

/** Parses `GET /usersummary-service/v2/dailysummaries`. This one response covers most metrics. */
internal fun parseDailySummaries(json: String): List<GarminDay> {
    val array = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val date = o.date() ?: return@mapNotNull null
        val movement = o.obj("movement")
        val steps = movement?.obj("steps")
        val floors = movement?.obj("floorsAscended")
        val intensity = o.obj("intensityMinutes")
        GarminDay(
            date = date,
            steps = positive(steps?.num("value")),
            distanceKm = positive(steps?.num("distanceInMeters"))?.div(1000f),
            floors = positive(floors?.num("value")),
            activeCalories = positive(o.child("calories", "burnedActive")),
            totalCalories = positive(o.child("calories", "burnedTotal")),
            restingHr = positive(o.child("heartRate", "restingValue")),
            minHr = positive(o.child("heartRate", "minValue")),
            maxHr = positive(o.child("heartRate", "maxValue")),
            // Garmin uses -1 for "no stress recorded".
            stress = positive(o.child("stress", "avgLevel")),
            maxStress = positive(o.child("stress", "maxLevel")),
            bodyBattery = positive(o.child("bodyBattery", "latestValue")),
            batteryCharged = positive(o.child("bodyBattery", "chargedValue")),
            batteryDrained = positive(o.child("bodyBattery", "drainedValue")),
            respiration = positive(o.child("respiration", "latestValue"))
                ?: positive(o.child("respiration", "avgValue")),
            intensityMinutes = run {
                val moderate = intensity?.num("moderate") ?: return@run null
                val vigorous = intensity.num("vigorous") ?: 0f
                (moderate + 2f * vigorous).takeIf { it >= 0f }
            },
            hydrationLiters = positive(o.child("hydration", "consumedInMl"))?.div(1000f),
        )
    }
}

/** `GET /sleep-service/sleep/dailySleepSummariesByDate` — one entry per night, some days empty. */
internal fun parseSleepSummaries(json: String): Map<String, Float> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyMap()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val date = o.date() ?: return@mapNotNull null
        val seconds = positive(o.num("sleepTimeSeconds")) ?: return@mapNotNull null
        date to seconds / 3600f
    }.toMap()
}

/** `GET /sleep-service/sleep/dailySleep` — today's detailed sleep including the overall score. */
internal fun parseDailySleep(json: String): GarminDay? {
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val date = o.date() ?: return null
    val seconds = positive(o.num("sleepTimeSeconds")) ?: return null
    return GarminDay(
        date = date,
        sleepHours = seconds / 3600f,
        sleepScore = positive(o.child("sleepScores", "overall", "value")),
        deepSleepHours = positive(o.num("deepSleepSeconds"))?.div(3600f),
        lightSleepHours = positive(o.num("lightSleepSeconds"))?.div(3600f),
        remSleepHours = positive(o.num("remSleepSeconds"))?.div(3600f),
        // The daily summary often leaves respiration empty; the sleep record usually has it.
        respiration = positive(o.num("averageRespirationValue")),
    )
}

/** `GET /hrv-service/hrv/daily/{start}/{end}`. */
internal fun parseHrvSummaries(json: String): Map<String, Float> {
    val array = runCatching { JSONObject(json).optJSONArray("hrvSummaries") }.getOrNull()
        ?: return emptyMap()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val date = o.date() ?: return@mapNotNull null
        positive(o.num("lastNightAvg"))?.let { date to it }
    }.toMap()
}

/** `GET /hrv-service/hrv/{date}` — last night's average when the daily summary is not ready. */
internal fun parseDailyHrv(json: String): Float? =
    runCatching { JSONObject(json).obj("hrvSummary")?.num("lastNightAvg") }
        .getOrNull()?.let { positive(it) }

/** `GET /wellness-service/wellness/dailyHeartRate?date=` — last sample, min and max. */
internal fun parseHeartRate(json: String): Triple<Float?, Float?, Float?> {
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return Triple(null, null, null)
    val values = o.optJSONArray("heartRateValues") ?: return Triple(null, null, null)
    var last: Float? = null
    for (i in 0 until values.length()) {
        val pair = values.optJSONArray(i) ?: continue
        if (pair.length() >= 2) positive(pair.optDouble(1).toFloat())?.let { last = it }
    }
    return Triple(last, positive(o.num("minHeartRate")), positive(o.num("maxHeartRate")))
}

/** `GET /weight-service/weight/range/{start}/{end}?includeAll=true`. */
internal fun parseWeights(json: String): List<GarminDay> {
    val array = runCatching { JSONObject(json).optJSONArray("dailyWeightSummaries") }.getOrNull()
        ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val date = o.optString("summaryDate", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val latest = o.obj("latestWeight")
        GarminDay(
            date = date,
            weightKg = positive(latest?.num("weight"))?.div(1000f),
            bodyFat = positive(latest?.num("bodyFat")),
        )
    }
}

// ---- Repository -------------------------------------------------------------------------------

/**
 * Reads Garmin Personal API data and exposes it through the same [MetricSummary] / [MetricDetail]
 * types as Health Connect, so the dashboard, detail screens, summary and widgets need no changes.
 * Each endpoint is fetched independently: a missing data type must never blank the whole app.
 */
class GarminMetricRepository(
    private val context: Context,
    private val api: GarminPersonalApi,
    private val prefs: MetricSourcePreferences,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val mutex = Mutex()
    private var cached: GarminSnapshot? = null
    private var cachedAt = 0L

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun withUnit(value: String, metric: Metric, system: UnitSystem): String =
        str(R.string.value_with_unit, value, metric.unit(context, system))

    suspend fun configured(): Boolean = prefs.state.first().hasKey

    /** Returns a cached snapshot for one minute so dashboard + widget refresh share one fetch. */
    private suspend fun snapshot(force: Boolean = false): GarminSnapshot = mutex.withLock {
        val age = System.currentTimeMillis() - cachedAt
        if (!force && cached != null && age in 0..60_000L) return@withLock cached!!
        val loaded = load()
        if (loaded.days.isNotEmpty()) {
            cached = loaded
            cachedAt = System.currentTimeMillis()
        }
        loaded
    }

    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    private suspend fun load(): GarminSnapshot = coroutineScope {
        val today = LocalDate.now()
        val start = today.minusDays(6)
        val s = start.toString()
        val e = today.toString()
        val failures = mutableListOf<String>()

        suspend fun <T> call(label: String, block: suspend () -> T): T? = runCatching { block() }
            .onFailure { failures += "$label: ${it.message ?: "failed"}" }
            .getOrNull()

        val summaries = async(Dispatchers.IO) {
            call("daily summaries") {
                parseDailySummaries(api.get("/usersummary-service/v2/dailysummaries",
                    mapOf("startDate" to s, "endDate" to e)))
            }
        }
        val sleepSummaries = async(Dispatchers.IO) {
            call("sleep summaries") {
                parseSleepSummaries(api.get("/sleep-service/sleep/dailySleepSummariesByDate",
                    mapOf("startDate" to s, "endDate" to e)))
            }
        }
        val sleepToday = async(Dispatchers.IO) {
            call("sleep") {
                parseDailySleep(api.get("/sleep-service/sleep/dailySleep", mapOf("date" to e)))
            }
        }
        val hrvSummaries = async(Dispatchers.IO) {
            call("HRV") { parseHrvSummaries(api.get("/hrv-service/hrv/daily/$s/$e")) }
        }
        val hrvToday = async(Dispatchers.IO) {
            call("last night HRV") { parseDailyHrv(api.get("/hrv-service/hrv/$e")) }
        }
        val heart = async(Dispatchers.IO) {
            call("heart rate") {
                parseHeartRate(api.get("/wellness-service/wellness/dailyHeartRate", mapOf("date" to e)))
            }
        }
        val weights = async(Dispatchers.IO) {
            call("weight") {
                parseWeights(api.get("/weight-service/weight/range/$s/$e", mapOf("includeAll" to "true")))
            }
        }

        val byDate = LinkedHashMap<String, GarminDay>()
        summaries.await().orEmpty().forEach { byDate[it.date] = it }
        fun merge(day: GarminDay?) {
            if (day == null) return
            val existing = byDate[day.date]
            byDate[day.date] = if (existing == null) day else existing.copy(
                sleepHours = day.sleepHours ?: existing.sleepHours,
                sleepScore = day.sleepScore ?: existing.sleepScore,
                deepSleepHours = day.deepSleepHours ?: existing.deepSleepHours,
                lightSleepHours = day.lightSleepHours ?: existing.lightSleepHours,
                remSleepHours = day.remSleepHours ?: existing.remSleepHours,
                respiration = day.respiration ?: existing.respiration,
                hrv = day.hrv ?: existing.hrv,
                weightKg = day.weightKg ?: existing.weightKg,
                bodyFat = day.bodyFat ?: existing.bodyFat,
            )
        }
        sleepSummaries.await().orEmpty().forEach { (date, hours) -> merge(GarminDay(date, sleepHours = hours)) }
        merge(sleepToday.await())
        hrvSummaries.await().orEmpty().forEach { (date, value) -> merge(GarminDay(date, hrv = value)) }
        hrvToday.await()?.let { merge(GarminDay(e, hrv = it)) }
        weights.await().orEmpty().forEach(::merge)

        val hr = heart.await()
        val days = byDate.values.sortedBy { it.date }
        val result = GarminSnapshot(days, hr?.first)
        runCatching { prefs.setStatus(System.currentTimeMillis(), failures.distinct().joinToString("; ")) }
        result
    }

    // ---- Value mapping ------------------------------------------------------------------------

    private fun valueOf(metric: Metric, day: GarminDay, snapshot: GarminSnapshot): Float? = when (metric) {
        Metric.STEPS -> day.steps
        Metric.DISTANCE -> day.distanceKm
        Metric.FLOORS -> day.floors
        Metric.ACTIVE_CALORIES -> day.activeCalories
        Metric.TOTAL_CALORIES -> day.totalCalories
        Metric.INTENSITY_MINUTES -> day.intensityMinutes
        Metric.HYDRATION -> day.hydrationLiters
        Metric.RESTING_HR -> day.restingHr
        Metric.HEART_RATE -> snapshot.latestHeartRate
        Metric.HRV -> day.hrv
        Metric.SLEEP -> day.sleepHours
        Metric.SLEEP_SCORE -> day.sleepScore
        Metric.RESPIRATORY -> day.respiration
        Metric.STRESS -> day.stress
        Metric.BODY_BATTERY -> day.bodyBattery
        Metric.WEIGHT -> day.weightKg
        Metric.BODY_FAT -> day.bodyFat
        else -> null // no Personal API endpoint in the official client
    }

    private fun series(metric: Metric, snapshot: GarminSnapshot, system: UnitSystem): List<SeriesPoint> {
        val days = snapshot.days.takeLast(14)
        val raw = days.mapNotNull { day -> valueOf(metric, day, snapshot)?.let { day.date to it } }
        if (metric == Metric.HEART_RATE) {
            // A single latest sample still gives the chart one visible point.
            val hr = snapshot.latestHeartRate ?: return emptyList()
            return listOf(SeriesPoint(Instant.now(), metric.fromBase(hr, system), ""))
        }
        return raw.map { (date, value) ->
            val local = LocalDate.parse(date)
            SeriesPoint(
                local.atStartOfDay(zone).toInstant(),
                metric.fromBase(value, system),
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(local),
            )
        }
    }

    /** Same shape as HealthConnectManager.readDailySeries, used by the Summary screen. */
    suspend fun readDailySeries(
        metric: Metric,
        days: Int,
        system: UnitSystem = UnitSystem.METRIC,
    ): List<SeriesPoint> {
        if (!configured()) return emptyList()
        val snapshot = runCatching { snapshot() }.getOrNull() ?: return emptyList()
        return series(metric, snapshot, system).takeLast(days)
    }

    suspend fun readDashboard(system: UnitSystem = UnitSystem.METRIC): List<MetricSummary> {
        if (!configured()) return emptySummaries(system)
        val snapshot = runCatching { snapshot() }.getOrNull() ?: return emptySummaries(system)
        return Metric.entries.map { metric -> summary(metric, snapshot, system) }
    }

    private fun emptySummaries(system: UnitSystem): List<MetricSummary> =
        Metric.entries.map {
            MetricSummary(it, "-", null, hasData = false, granted = true, system = system, source = ValueSource.GARMIN_API)
        }

    private fun summary(metric: Metric, snapshot: GarminSnapshot, system: UnitSystem): MetricSummary {
        val points = series(metric, snapshot, system)
        val today = snapshot.days.lastOrNull()
        val current = today?.let { valueOf(metric, it, snapshot) }
        if (current == null && points.isEmpty()) {
            return MetricSummary(
                metric, "-", str(R.string.no_data), hasData = false, granted = true,
                system = system, source = ValueSource.GARMIN_API,
            )
        }
        val value = metric.formatValue(
            metric.fromBase(current ?: points.last().value, system), system,
        )
        val caption = when (metric.kind) {
            MetricKind.DAILY_TOTAL -> str(R.string.summary_7day_avg,
                withUnit(metric.formatValue(points.map { it.value }.takeLast(7).average().toFloat(), system), metric, system))
            MetricKind.LATEST -> str(R.string.summary_as_of,
                DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(LocalDate.now()))
        }
        return MetricSummary(
            metric, value, caption, true, true, points.map { it.value }, system,
            source = ValueSource.GARMIN_API,
        )
    }

    suspend fun readDetail(metric: Metric, system: UnitSystem = UnitSystem.METRIC): MetricDetail {
        if (!configured()) return noDetail(metric, system)
        val snapshot = runCatching { snapshot() }.getOrNull() ?: return noDetail(metric, system)
        val points = series(metric, snapshot, system)
        if (points.isEmpty()) return noDetail(metric, system)
        val values = points.map { it.value }
        fun fmt(v: Float) = withUnit(metric.formatValue(v, system), metric, system)

        val headline: String
        val caption: String
        val stats: MutableList<Pair<String, String>> = mutableListOf()
        if (metric.kind == MetricKind.DAILY_TOTAL) {
            headline = metric.formatValue(values.last(), system)
            caption = str(R.string.detail_today_unit, metric.unit(context, system))
            stats += str(R.string.stat_daily_avg) to fmt(values.average().toFloat())
            stats += str(R.string.stat_best_day) to fmt(values.max())
            stats += str(R.string.stat_14day_total) to fmt(values.sum())
        } else {
            headline = fmt(values.last())
            caption = str(R.string.detail_latest_at,
                DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(LocalDate.now()))
            stats += str(R.string.stat_average) to fmt(values.average().toFloat())
            stats += str(R.string.stat_min) to fmt(values.min())
            stats += str(R.string.stat_max) to fmt(values.max())
        }
        // Garmin-only extras make the detail screen carry the richer source data.
        val day = snapshot.days.lastOrNull()
        if (day != null) {
            when (metric) {
                Metric.HEART_RATE -> {
                    day.minHr?.let { stats += str(R.string.stat_min) to fmt(it) }
                    day.maxHr?.let { stats += str(R.string.stat_max) to fmt(it) }
                }
                Metric.SLEEP -> {
                    day.deepSleepHours?.let { stats += str(R.string.stat_sleep_deep) to fmt(it) }
                    day.lightSleepHours?.let { stats += str(R.string.stat_sleep_light) to fmt(it) }
                    day.remSleepHours?.let { stats += str(R.string.stat_sleep_rem) to fmt(it) }
                }
                Metric.STRESS -> day.maxStress?.let { stats += str(R.string.stat_max) to fmt(it) }
                Metric.BODY_BATTERY -> {
                    day.batteryCharged?.let { stats += str(R.string.stat_battery_charged) to fmt(it) }
                    day.batteryDrained?.let { stats += str(R.string.stat_battery_drained) to fmt(it) }
                }
                Metric.WEIGHT -> day.bodyFat?.let { stats += str(R.string.metric_body_fat) to fmt(it) }
                else -> Unit
            }
        }
        val recent = snapshot.days.takeLast(7).mapNotNull { d ->
            valueOf(metric, d, snapshot)?.let { v ->
                RecordRow(
                    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(LocalDate.parse(d.date)),
                    fmt(v),
                )
            }
        }
        return MetricDetail(metric, headline, caption, points, stats, recent, system, source = ValueSource.GARMIN_API)
    }

    private fun noDetail(metric: Metric, system: UnitSystem) =
        MetricDetail(
            metric, "-", str(R.string.no_data_recorded), emptyList(), system = system,
            source = ValueSource.GARMIN_API,
        )

    /** Manual "sync now" from settings bypasses the short-lived cache. */
    suspend fun refresh(): GarminSnapshot? {
        if (!configured()) return null
        invalidate()
        return runCatching { snapshot(force = true) }.getOrNull()
    }
}
