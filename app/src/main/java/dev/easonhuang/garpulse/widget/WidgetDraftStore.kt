package dev.easonhuang.garpulse.widget

import android.content.Context

/**
 * Holds one "staged" widget configuration between the in-app editor (edit-first-then-add) and the
 * moment the launcher assigns a real appWidgetId. Persisted to SharedPreferences so it survives a
 * process restart while the launcher is placing the widget.
 *
 * [WidgetPinCallback] consumes it once the widget id is known and writes it into that widget's
 * Glance state.
 */
internal object WidgetDraftStore {
    private const val PREFS = "garpulse_widget_draft"
    private const val KEY_CONFIG = "config"

    fun stage(context: Context, config: WidgetConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, WidgetConfigCodec.encodeDraft(config))
            .apply()
    }

    /** Returns and clears the staged config, if any. */
    fun consume(context: Context): WidgetConfig? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val config = WidgetConfigCodec.decodeDraft(prefs.getString(KEY_CONFIG, null))
        prefs.edit().remove(KEY_CONFIG).apply()
        return config
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CONFIG).apply()
    }
}
