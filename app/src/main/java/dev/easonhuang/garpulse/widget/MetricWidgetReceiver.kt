package dev.easonhuang.garpulse.widget

import androidx.glance.appwidget.GlanceAppWidget

class MetricWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetricWidget()
}
