package dev.easonhuang.garpulse.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.title

/** The four original widget kinds, kept for existing placements and the system picker. */
enum class WidgetKind(
    val receiver: Class<*>,
    val widget: Class<out ConfigurableWidget>,
    @StringRes val labelRes: Int,
    val defaults: WidgetConfig,
) {
    DASHBOARD(
        GarPulseWidgetReceiver::class.java,
        GarPulseWidget::class.java,
        R.string.widget_dashboard_name,
        WidgetConfig.DASHBOARD,
    ),
    SINGLE(
        MetricWidgetReceiver::class.java,
        MetricWidget::class.java,
        R.string.widget_metric_name,
        WidgetConfig.DEFAULT,
    ),
    MULTI(
        MultiWidgetReceiver::class.java,
        MultiWidget::class.java,
        R.string.widget_multi_name,
        WidgetConfig.MULTI,
    ),
    ICONS(
        IconWidgetReceiver::class.java,
        IconWidget::class.java,
        R.string.widget_icons_name,
        WidgetConfig.ICONS,
    ),
    /** Single, freely-resizable widget used for every new add. */
    UNIVERSAL(
        UniversalWidgetReceiver::class.java,
        UniversalWidget::class.java,
        R.string.widget_universal_name,
        WidgetConfig.DEFAULT,
    ),
    ;

    companion object {
        fun fromProvider(provider: ComponentName?): WidgetKind? =
            entries.firstOrNull { it.receiver.name == provider?.className }
    }
}

/** One widget instance currently placed on a home screen. */
data class WidgetInstance(
    val appWidgetId: Int,
    val kind: WidgetKind,
    val config: WidgetConfig,
    val size: WidgetSize? = null,
)

data class PlacedWidget(val appWidgetId: Int, val provider: ComponentName, val glanceId: GlanceId)

/** Reads all placed GarPulse widgets so they can be managed from inside the app. */
object WidgetCatalog {

    private val receivers: List<Class<*>> =
        WidgetKind.entries.map { it.receiver } + WidgetSize.entries.map { it.receiver }

    /** Enumerates every provider explicitly so flexible-size variants are included too. */
    fun placed(context: Context): List<PlacedWidget> {
        val glanceManager = GlanceAppWidgetManager(context)
        val manager = AppWidgetManager.getInstance(context)
        val result = mutableListOf<PlacedWidget>()
        receivers.forEach { receiver ->
            val component = ComponentName(context, receiver)
            manager.getAppWidgetIds(component).forEach { id ->
                val glanceId = runCatching { glanceManager.getGlanceIdBy(id) }.getOrNull() ?: return@forEach
                result += PlacedWidget(id, component, glanceId)
            }
        }
        return result.distinctBy { it.appWidgetId }
    }

    suspend fun list(context: Context): List<WidgetInstance> = placed(context).mapNotNull { placed ->
        val prefs = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, placed.glanceId)
        }.getOrNull() ?: return@mapNotNull null
        // The provider never changes when the user drag-resizes a widget (resizeMode is on for
        // every receiver), so derive the displayed size from Glance's live render size instead.
        WidgetInstance(
            appWidgetId = placed.appWidgetId,
            kind = WidgetKind.fromProvider(placed.provider) ?: WidgetKind.SINGLE,
            config = WidgetConfigCodec.read(prefs, defaultsFor(placed.provider)),
            size = WidgetSize.fromGlance(context, placed.glanceId) ?: WidgetSize.fromProvider(placed.provider.className),
        )
    }.sortedBy { it.appWidgetId }

    fun defaultsFor(provider: ComponentName?): WidgetConfig =
        WidgetKind.fromProvider(provider)?.defaults ?: WidgetConfig.DEFAULT

    fun widgetFor(provider: ComponentName?): ConfigurableWidget = when (WidgetKind.fromProvider(provider)) {
        WidgetKind.DASHBOARD -> GarPulseWidget()
        WidgetKind.MULTI -> MultiWidget()
        WidgetKind.ICONS -> IconWidget()
        WidgetKind.UNIVERSAL -> UniversalWidget()
        else -> MetricWidget()
    }

    /**
     * Pins the one universal widget. [successIntent] is delivered with the assigned appWidgetId so
     * [WidgetPinCallback] can apply a config that was edited (and staged) before the add.
     */
    fun pinUniversal(context: Context, successIntent: PendingIntent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(
                ComponentName(context, UniversalWidgetReceiver::class.java),
                null,
                successIntent,
            )
        }.getOrDefault(false)
    }

    /** Legacy pin used only by older callers that select a concrete size up front. */
    fun pin(context: Context, size: WidgetSize): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(ComponentName(context, size.receiver), null, null)
        }.getOrDefault(false)
    }

    /** Human-readable label for the widget's actual rendered grid, e.g. "2 × 2". */
    fun sizeLabel(context: Context, instance: WidgetInstance): String =
        instance.size?.let { context.getString(it.labelRes) }
            ?: context.getString(R.string.widget_size_unknown)

    /** Convenience used by the in-app editor to describe a configuration in one line. */
    fun summary(context: Context, instance: WidgetInstance): String {
        val config = instance.config
        val metricNames = config.metrics.joinToString(", ") { it.title(context) }
        return listOfNotNull(
            sizeLabel(context, instance),
            metricNames.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    /**
     * Label shown for one placement: the user's own name when set, otherwise the widget kind plus
     * an ordinal so identical layouts stay apart ("Heatmap 2"). [ordinal] is 1-based.
     */
    fun displayName(context: Context, instance: WidgetInstance, ordinal: Int = 1): String {
        instance.config.name?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val kindLabel = context.getString(instance.kind.labelRes)
        return if (ordinal <= 1) kindLabel
        else context.getString(R.string.widget_name_auto, kindLabel, ordinal)
    }

    /**
     * Ordinal of each instance among widgets of the same kind, keyed by appWidgetId. Clamped to
     * at least 1 so [displayName] never formats a "0".
     */
    fun ordinals(instances: List<WidgetInstance>): Map<Int, Int> {
        val seen = mutableMapOf<WidgetKind, Int>()
        return instances.associate { instance ->
            val next = (seen[instance.kind] ?: 0) + 1
            seen[instance.kind] = next
            instance.appWidgetId to next
        }.mapValues { (_, value) -> value.coerceAtLeast(1) }
    }
}

internal fun WidgetLayout.labelRes(): Int = when (this) {
    WidgetLayout.SINGLE -> R.string.widget_layout_single
    WidgetLayout.MULTI -> R.string.widget_layout_multi
    WidgetLayout.ICON_ONLY -> R.string.widget_layout_icons
    WidgetLayout.IMAGE -> R.string.widget_layout_image
}
