package dev.easonhuang.garpulse.widget

import androidx.glance.appwidget.GlanceAppWidget

class GarPulseWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GarPulseWidget()
}
