package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.flow.first

/**
 * The single, universal home-screen widget. It can show any mix of health metrics, an icon-only
 * grid, or a user-selected picture, and it is freely resizable from 1x1 up to 4x5. Everything
 * about it comes from its own Glance state, so no size-specific variants are needed for new adds.
 */
class UniversalWidget : ConfigurableWidget() {
    override fun defaultConfig(context: Context): WidgetConfig = WidgetConfig.DEFAULT

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        applyPendingDraft(context, id)
        super.provideGlance(context, id)
    }

    /**
     * Fallback for the edit-first-then-add flow: some launchers never deliver the pin-success
     * broadcast to [WidgetPinCallback], leaving the default config on screen. When this widget
     * renders while a *fresh* draft is staged and this instance is still unconfigured (no
     * METRICS key in its state — true only for a brand-new placement), the draft belongs to it.
     */
    private suspend fun applyPendingDraft(context: Context, id: GlanceId) {
        val (config, stagedAt) = WidgetDraftStore.peek(context) ?: return
        if (!WidgetDraftStore.isFresh(stagedAt)) return
        val configured = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetPrefs.METRICS] != null
        }.getOrDefault(false)
        if (configured) return
        val draftConfig = WidgetDraftStore.consume(context) ?: config
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            val mutable = prefs.toMutablePreferences()
            WidgetConfigCodec.write(mutable, draftConfig)
            mutable
        }
    }

    companion object {
        suspend fun refreshData(context: Context, id: GlanceId) {
            ConfigurableWidget.refreshData(context, id, WidgetConfig.DEFAULT)
        }
    }
}
