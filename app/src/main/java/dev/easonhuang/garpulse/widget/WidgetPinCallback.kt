package dev.easonhuang.garpulse.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Invoked by the launcher after a "save first, then add" pin succeeds. Applies the staged draft
 * to the freshly assigned appWidgetId, so the user only configures a widget once.
 */
class WidgetPinCallback : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val config = WidgetDraftStore.consume(context) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(id)
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val mutable = prefs.toMutablePreferences()
                    WidgetConfigCodec.write(mutable, config)
                    mutable
                }
                ConfigurableWidget.refreshData(app, glanceId, WidgetConfig.DEFAULT)
                UniversalWidget().update(app, glanceId)
            } finally {
                pending.finish()
            }
        }
    }
}
