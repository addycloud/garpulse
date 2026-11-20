package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.glance.GlanceId

/** Preset widget that shows only metric glyphs, with no text or chart. */
class IconWidget : ConfigurableWidget() {

    override fun defaultConfig(context: Context): WidgetConfig = WidgetConfig.ICONS

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, WidgetConfig.ICONS)
        }
    }
}
