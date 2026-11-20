package dev.easonhuang.heartwood.widget

import android.content.Context
import androidx.glance.GlanceId

/**
 * The original full-dashboard widget. It now shares the configurable renderer, so the settings
 * added for the smaller widgets (font, background, transparency, tap target, chart toggle)
 * work here too. Default layout keeps the familiar four-metric overview.
 */
class HeartwoodWidget : ConfigurableWidget() {

    override fun defaultConfig(context: Context): WidgetConfig = WidgetConfig.DASHBOARD

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, WidgetConfig.DASHBOARD)
        }
    }
}
