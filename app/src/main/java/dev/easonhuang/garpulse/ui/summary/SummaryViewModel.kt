package dev.easonhuang.garpulse.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.garpulse.data.GoalCatalog
import dev.easonhuang.garpulse.data.GoalsRepository
import dev.easonhuang.garpulse.data.HealthConnectManager
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.SeriesPoint
import dev.easonhuang.garpulse.data.UnitPreferences
import dev.easonhuang.garpulse.data.UnitSystem
import dev.easonhuang.garpulse.data.ValueSource
import dev.easonhuang.garpulse.data.WeeklyStat
import dev.easonhuang.garpulse.data.fromBase
import dev.easonhuang.garpulse.data.toBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SummaryState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

/** Daily series per metric, already converted into [system]. */
private data class LoadedSeries(val system: UnitSystem, val byMetric: Map<Metric, List<SeriesPoint>>)

class SummaryViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val units: UnitPreferences,
) : ViewModel() {

    // Null until first load completes.
    private val series = MutableStateFlow<LoadedSeries?>(null)

    /** Per-metric source labels, refreshed alongside the series. */
    private var sourceCache: Map<Metric, ValueSource> = emptyMap()

    val state = combine(series, goalsRepo.goals) { loaded, goals ->
        if (loaded == null) {
            SummaryState(loading = true)
        } else {
            val system = loaded.system
            // Every metric the Overview can show, not just goal-capable ones. Metrics without
            // any data (no permission / nothing recorded) still get a card so the Insights
            // screen mirrors the Overview's full set.
            val stats = Metric.entries.map { metric ->
                val pts = loaded.byMetric[metric]
                val thisWeek = pts.orEmpty().takeLast(7)
                val lastWeek = pts.orEmpty().dropLast(7).takeLast(7)
                // Goals are stored metric; show them in the same units as the series.
                val goalBase = goals[metric] ?: GoalCatalog.defaults[metric] ?: 0f
                WeeklyStat(
                    metric = metric,
                    perDay = thisWeek,
                    thisWeekAvg = if (thisWeek.isEmpty()) 0f else thisWeek.map { it.value }.average().toFloat(),
                    lastWeekAvg = if (lastWeek.isEmpty()) 0f else lastWeek.map { it.value }.average().toFloat(),
                    goal = metric.fromBase(goalBase, system),
                    system = system,
                    source = sourceCache[metric] ?: ValueSource.HEALTH_CONNECT,
                )
            }
            SummaryState(loading = false, stats = stats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())

    // Loads on first emission, then again whenever the user switches unit system.
    init { viewModelScope.launch { units.system.collect { load(it) } } }

    fun refresh() {
        viewModelScope.launch { load(units.system.first()) }
    }

    private suspend fun load(system: UnitSystem) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val byMetric = Metric.entries
            .filter { manager.permissionFor(it) in granted }
            .associateWith { runCatching { manager.readDailySeries(it, 14, system) }.getOrDefault(emptyList()) }
        sourceCache = Metric.entries.associateWith { metric ->
            runCatching { manager.sourceFor(metric, system) }.getOrDefault(ValueSource.HEALTH_CONNECT)
        }
        series.value = LoadedSeries(system, byMetric)
    }

    /** [value] is in [system]'s unit, as typed into the goal dialog. */
    fun setGoal(metric: Metric, value: Float, system: UnitSystem) {
        viewModelScope.launch { goalsRepo.setGoal(metric, metric.toBase(value, system)) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, units: UnitPreferences) =
            viewModelFactory { initializer { SummaryViewModel(manager, goalsRepo, units) } }
    }
}
