package dev.easonhuang.garpulse.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.easonhuang.garpulse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Invoked by the launcher after a "save first, then add" pin succeeds. Applies the staged draft
 * to the freshly assigned appWidgetId, so the user only configures a widget once.
 *
 * The broadcast can arrive before Glance has created the widget's Glance id (and some launchers
 * never send it at all — the [UniversalWidget] render-time fallback covers that case), so the
 * id lookup is retried for a few seconds instead of failing silently and leaving the default
 * config on the home screen.
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
                val glanceId = retryUntilGlanceId(app, id)
                if (glanceId == null) {
                    // Give the config back so the render-time fallback can still apply it.
                    WidgetDraftStore.stage(app, config)
                    return@launch
                }
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val mutable = prefs.toMutablePreferences()
                    WidgetConfigCodec.write(mutable, config)
                    mutable
                }
                ConfigurableWidget.refreshData(app, glanceId, WidgetConfig.DEFAULT)
                UniversalWidget().update(app, glanceId)
                android.widget.Toast.makeText(
                    app, app.getString(R.string.widget_pin_added), android.widget.Toast.LENGTH_SHORT,
                ).show()
            } catch (t: Throwable) {
                // Never crash; the render-time fallback in UniversalWidget will re-apply the
                // draft if the state write did not land.
                runCatching { WidgetDraftStore.stage(context.applicationContext, config) }
            } finally {
                pending.finish()
            }
        }
    }

    /** Glance creates the id asynchronously after pinning; poll briefly rather than giving up. */
    private suspend fun retryUntilGlanceId(context: Context, appWidgetId: Int): androidx.glance.GlanceId? {
        val manager = GlanceAppWidgetManager(context)
        repeat(20) {
            val id = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull()
            if (id != null) return id
            delay(250L)
        }
        return null
    }
}
