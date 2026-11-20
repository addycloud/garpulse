package dev.easonhuang.garpulse.widget

import android.content.Context
import android.os.SystemClock

/**
 * Holds one "staged" widget configuration between the in-app editor (edit-first-then-add) and the
 * moment the launcher assigns a real appWidgetId. Persisted to SharedPreferences so it survives a
 * process restart while the launcher is placing the widget.
 *
 * [WidgetPinCallback] consumes it once the widget id is known and writes it into that widget's
 * Glance state. Some launchers never deliver the pin-success broadcast, so [UniversalWidget]
 * also consumes any *fresh* draft the first time a brand-new, still-unconfigured widget renders
 * — that fallback path guarantees the staged config always lands on the newly added widget.
 */
internal object WidgetDraftStore {
    private const val PREFS = "garpulse_widget_draft"
    private const val KEY_CONFIG = "config"
    private const val KEY_STAGED_AT = "staged_at"

    /** A draft older than this is never auto-consumed on render (it belongs to the past). */
    private const val FRESH_WINDOW_MS = 2 * 60 * 1000L

    fun stage(context: Context, config: WidgetConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, WidgetConfigCodec.encodeDraft(config))
            .putLong(KEY_STAGED_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    /** Returns the staged config without clearing it, or null if none is staged. */
    fun peek(context: Context): Pair<WidgetConfig, Long>? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return null
        val config = WidgetConfigCodec.decodeDraft(raw) ?: return null
        return config to (prefs.getLong(KEY_STAGED_AT, 0L))
    }

    fun isFresh(stagedAt: Long): Boolean =
        stagedAt > 0L && SystemClock.elapsedRealtime() - stagedAt <= FRESH_WINDOW_MS

    /** Returns and clears the staged config, if any. */
    fun consume(context: Context): WidgetConfig? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val config = WidgetConfigCodec.decodeDraft(prefs.getString(KEY_CONFIG, null))
        prefs.edit().remove(KEY_CONFIG).remove(KEY_STAGED_AT).apply()
        return config
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CONFIG).remove(KEY_STAGED_AT).apply()
    }
}
