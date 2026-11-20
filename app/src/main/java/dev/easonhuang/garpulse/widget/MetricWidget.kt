package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.glance.GlanceId
import dev.easonhuang.garpulse.data.Metric

/** Single-metric widget: the original GarPulse widget, now fully configurable. */
class MetricWidget : ConfigurableWidget() {

    override fun defaultConfig(context: Context): WidgetConfig =
        WidgetConfig(metrics = listOf(Metric.STEPS), layout = WidgetLayout.SINGLE)

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, MetricWidget().defaultConfig(context))
        }
    }
}
