package dev.easonhuang.heartwood.widget

import androidx.glance.appwidget.GlanceAppWidget

class HeartwoodWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeartwoodWidget()
}
