package dev.easonhuang.garpulse.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

import dev.easonhuang.garpulse.data.DeveloperPreferences

/**
 * A slim wavy ribbon drawn on the left edge of a widget. Tapping it refreshes that widget only;
 * every other area keeps the configured tap target. The wave phase is stored in Glance state so
 * a few cheap updates produce a subtle "working" animation without a full re-bind.
 */
internal object WidgetRefreshStrip {
    const val FRAME_COUNT = 4

    /** Delay per animation frame at 1× speed; the developer setting multiplies this. */
    const val BASE_FRAME_DELAY_MS = 90L

    /**
     * Animates the wave and then asks the normal worker to re-read health data. Shared by the
     * strip's own tap callback and the '@refresh' tap shortcut.
     */
    suspend fun performRefresh(context: Context, glanceId: GlanceId?) {
        val app = context.applicationContext
        if (glanceId == null) {
            // No specific widget context (e.g. an '@refresh' tap with a null id): refresh all.
            WidgetUpdateScheduler.refreshNow(app)
            return
        }
        val appWidgetId = runCatching { GlanceAppWidgetManager(app).getAppWidgetId(glanceId) }.getOrNull()
        val provider = appWidgetId?.let {
            AppWidgetManager.getInstance(app).getAppWidgetInfo(it)?.provider
        }
        val widget = provider?.let { WidgetCatalog.widgetFor(it) }

        // Speed multiplier from the developer settings; 1× = the original gentle wave.
        val speed = DeveloperPreferences(app).refreshAnimSpeed.first()
        val delayMs = (BASE_FRAME_DELAY_MS / speed).toLong().coerceAtLeast(8L)

        setFrame(app, glanceId, 0)
        widget?.update(app, glanceId)
        repeat(FRAME_COUNT) { frame ->
            delay(delayMs)
            setFrame(app, glanceId, frame + 1)
            widget?.update(app, glanceId)
        }
        setFrame(app, glanceId, 0)
        widget?.update(app, glanceId)
        WidgetUpdateScheduler.refreshNow(app)
    }

    private suspend fun setFrame(context: Context, glanceId: GlanceId, frame: Int) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val mutable: MutablePreferences = prefs.toMutablePreferences()
            mutable[WidgetPrefs.REFRESH_FRAME] = frame % FRAME_COUNT
            mutable
        }
    }

    /**
     * Draws a single continuous ribbon spanning the widget's full height. A faint rounded rail
     * underlies the whole strip so the gaps between metric cards never chop it into pieces;
     * the gentle wave on top is the part that animates. [phase] shifts only that wave.
     */
    fun bitmap(
        colorArgb: Int,
        widthPx: Int,
        heightPx: Int,
        phase: Int,
        /** Amplitude multiplier for the wave; 0 flattens it, 1 is the original ribbon. */
        wave: Float = 1f,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(2)
        val h = heightPx.coerceAtLeast(2)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Continuous translucent rail: one calm, uninterrupted line from top to bottom.
        val railAlpha = (android.graphics.Color.alpha(colorArgb) * 0.45f).toInt().coerceAtLeast(1)
        canvas.drawRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(), w * 0.5f, w * 0.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(colorArgb, railAlpha)
                style = Paint.Style.FILL
            },
        )

        // Soft wave sitting on the rail; amplitude scales with the user's multiplier so it can
        // be flattened out or exaggerated. Low amplitude reads as a ribbon, not a zig-zag.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.FILL
        }
        val heightF = h.toFloat()
        val baseX = w * 0.52f
        val amplitude = w * 0.14f * wave
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(baseX, 0f)
            val steps = 48
            for (i in 0..steps) {
                val y = heightF * i / steps.toFloat()
                val angle = (y / heightF) * 2.6f * PI.toFloat() + phase.toFloat() * (PI.toFloat() / 2f)
                lineTo(baseX + amplitude * sin(angle), y)
            }
            lineTo(0f, heightF)
            close()
        }
        canvas.drawPath(path, paint)
        return result
    }

    fun action(): Action = actionRunCallback<WidgetRefreshCallback>()
}

/** Advances the wave for a few frames, then asks the normal worker to re-read health data. */
object WidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefreshStrip.performRefresh(context, glanceId)
    }
}

/** The wave phase currently rendered for [glanceId]; 0 is the resting state. */
internal fun Preferences.refreshFrame(): Int =
    (this[WidgetPrefs.REFRESH_FRAME] ?: 0) % WidgetRefreshStrip.FRAME_COUNT
