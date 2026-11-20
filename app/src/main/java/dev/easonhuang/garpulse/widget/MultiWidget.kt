package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.glance.GlanceId

/** Preset widget showing several metrics at once (up to four, two per row). */
class MultiWidget : ConfigurableWidget() {

    override fun defaultConfig(context: Context): WidgetConfig = WidgetConfig.MULTI

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, WidgetConfig.MULTI)
        }
    }
}
