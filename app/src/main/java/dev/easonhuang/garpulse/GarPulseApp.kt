package dev.easonhuang.garpulse

import android.app.Application
import dev.easonhuang.garpulse.data.DashboardPreferences
import dev.easonhuang.garpulse.data.ExportManager
import dev.easonhuang.garpulse.data.GarminMetricRepository
import dev.easonhuang.garpulse.data.GarminPersonalApi
import dev.easonhuang.garpulse.data.GoalsRepository
import dev.easonhuang.garpulse.data.HealthConnectManager
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.data.MetricSourcePreferences
import dev.easonhuang.garpulse.data.UnitPreferences
import dev.easonhuang.garpulse.widget.WidgetUpdateScheduler

class GarPulseApp : Application() {
    val metricSource: MetricSourcePreferences by lazy { MetricSourcePreferences(this) }
    val garminApi: GarminPersonalApi by lazy { GarminPersonalApi(metricSource) }
    val garminMetrics: GarminMetricRepository by lazy { GarminMetricRepository(this, garminApi, metricSource) }
    // Unified read facade: health-connect or Garmin API depending on the user's source setting.
    val healthConnect: HealthConnectManager by lazy {
        HealthConnectManager(this, sourcePrefs = metricSource, garmin = garminMetrics)
    }
    val goals: GoalsRepository by lazy { GoalsRepository(this) }
    val units: UnitPreferences by lazy { UnitPreferences(this) }
    val exporter: ExportManager by lazy { ExportManager(this, healthConnect, units) }
    val dashboardPrefs: DashboardPreferences by lazy { DashboardPreferences(this) }
    val language: LanguagePreferences by lazy { LanguagePreferences(this) }

    override fun onCreate() {
        super.onCreate()
        LanguagePreferences.applyAtStartup(this)
        WidgetUpdateScheduler.ensureScheduled(this)
    }
}
