package dev.easonhuang.heartwood.widget

import androidx.glance.appwidget.GlanceAppWidget

/** Receiver for the one universal, freely-resizable Heartwood widget. */
class UniversalWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UniversalWidget()
}
