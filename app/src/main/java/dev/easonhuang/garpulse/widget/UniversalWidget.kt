package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.glance.GlanceId

/**
 * The single, universal home-screen widget. It can show any mix of health metrics, an icon-only
 * grid, or a user-selected picture, and it is freely resizable from 1x1 up to 4x5. Everything
 * about it comes from its own Glance state, so no size-specific variants are needed for new adds.
 */
class UniversalWidget : ConfigurableWidget() {
    override fun defaultConfig(context: Context): WidgetConfig = WidgetConfig.DEFAULT

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, WidgetConfig.DEFAULT)
        }
    }
}
