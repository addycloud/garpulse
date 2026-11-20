package dev.easonhuang.garpulse.widget

import androidx.glance.appwidget.GlanceAppWidget

/** Receiver for the one universal, freely-resizable GarPulse widget. */
class UniversalWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UniversalWidget()
}
