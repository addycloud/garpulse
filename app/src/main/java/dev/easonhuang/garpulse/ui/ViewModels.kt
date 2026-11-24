package dev.easonhuang.garpulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.garpulse.data.DashboardLayout
import dev.easonhuang.garpulse.data.DashboardPreferences
import dev.easonhuang.garpulse.data.HealthConnectManager
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.MetricDetail
import dev.easonhuang.garpulse.data.MetricSummary
import dev.easonhuang.garpulse.data.MetricSourcePreferences
import dev.easonhuang.garpulse.data.UnitPreferences
import dev.easonhuang.garpulse.data.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    private val prefs: DashboardPreferences,
    private val units: UnitPreferences,
    sourcePrefs: MetricSourcePreferences? = null,
) : ViewModel() {
    // All summaries as read from Health Connect, in catalog order (null until first load).
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)

    /**
     * Summaries arranged for display: reordered to match the user's layout and with hidden
     * cards dropped. Stays null until the first read so the screen can show its loading state.
     */
    val summaries: StateFlow<List<MetricSummary>?> =
        combine(_summaries, prefs.layout) { all, layout ->
            if (all == null) return@combine null
            val byMetric = all.associateBy { it.metric }
            layout.visible.mapNotNull { byMetric[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every summary keyed by metric, hidden ones included, for the dashboard's edit mode. */
    val allSummaries: StateFlow<Map<Metric, MetricSummary>?> =
        _summaries.map { all -> all?.associateBy { it.metric } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The persisted card order and hidden set (null until DataStore's first read). */
    val layout: StateFlow<DashboardLayout?> =
        prefs.layout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Overview grid column count (1..10, default 3), persisted in [DashboardPreferences]. */
    val columns: StateFlow<Int> =
        prefs.columns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardPreferences.DEFAULT_COLUMNS)

    fun saveLayout(order: List<Metric>, hidden: Set<Metric>) {
        viewModelScope.launch { prefs.save(order, hidden) }
    }

    fun saveColumns(count: Int) {
        viewModelScope.launch { prefs.saveColumns(count) }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    // Loads on first emission, then again whenever the unit system OR the selected data source
    // changes — a source switch in settings must immediately refresh the values on this screen.
    init {
        viewModelScope.launch { units.system.collect { load(it) } }
        if (sourcePrefs != null) {
            viewModelScope.launch {
                // Only re-read when the source, key presence OR priority actually changes, not
                // on every sync-status write (which would otherwise cause a refresh loop).
                sourcePrefs.state
                    .map { Triple(it.hcEnabled to it.globalEnabled to it.cnEnabled, it.hasKey, it.order) }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { load(units.system.first()) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(units.system.first()) }
    }

    private suspend fun load(system: UnitSystem) {
        _refreshing.value = true
        _summaries.value = manager.readDashboard(system)
        _refreshing.value = false
    }

    companion object {
        fun factory(
            manager: HealthConnectManager,
            prefs: DashboardPreferences,
            units: UnitPreferences,
            sourcePrefs: MetricSourcePreferences? = null,
        ) = viewModelFactory { initializer { DashboardViewModel(manager, prefs, units, sourcePrefs) } }
    }
}

class DetailViewModel(
    private val manager: HealthConnectManager,
    private val metric: Metric,
    units: UnitPreferences,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch { units.system.collect { _detail.value = manager.readDetail(metric, it) } }
    }

    companion object {
        fun factory(manager: HealthConnectManager, metric: Metric, units: UnitPreferences) = viewModelFactory {
            initializer { DetailViewModel(manager, metric, units) }
        }
    }
}
