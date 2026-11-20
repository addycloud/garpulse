package dev.easonhuang.heartwood.widget

import androidx.glance.appwidget.GlanceAppWidget

class MetricWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetricWidget()
}
