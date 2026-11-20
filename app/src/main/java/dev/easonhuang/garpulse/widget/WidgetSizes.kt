package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.easonhuang.garpulse.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** Home-screen cell sizes offered by the in-app widget manager. */
enum class WidgetSize(
    val key: String,
    val columns: Int,
    val rows: Int,
    @StringRes val labelRes: Int,
    @StringRes val nameRes: Int,
    val receiver: Class<out LocalizedWidgetReceiver>,
) {
    S1X1("1x1", 1, 1, R.string.widget_size_1x1, R.string.widget_sized_1x1_name, Widget1x1Receiver::class.java),
    S1X2("1x2", 1, 2, R.string.widget_size_1x2, R.string.widget_sized_1x2_name, Widget1x2Receiver::class.java),
    S1X3("1x3", 1, 3, R.string.widget_size_1x3, R.string.widget_sized_1x3_name, Widget1x3Receiver::class.java),
    S1X4("1x4", 1, 4, R.string.widget_size_1x4, R.string.widget_sized_1x4_name, Widget1x4Receiver::class.java),
    S2X2("2x2", 2, 2, R.string.widget_size_2x2, R.string.widget_sized_2x2_name, Widget2x2Receiver::class.java),
    S2X3("2x3", 2, 3, R.string.widget_size_2x3, R.string.widget_sized_2x3_name, Widget2x3Receiver::class.java),
    S3X3("3x3", 3, 3, R.string.widget_size_3x3, R.string.widget_sized_3x3_name, Widget3x3Receiver::class.java),
    S4X4("4x4", 4, 4, R.string.widget_size_4x4, R.string.widget_sized_4x4_name, Widget4x4Receiver::class.java),
    S1X5("1x5", 1, 5, R.string.widget_size_1x5, R.string.widget_sized_1x5_name, Widget1x5Receiver::class.java),
    S4X5("4x5", 4, 5, R.string.widget_size_4x5, R.string.widget_sized_4x5_name, Widget4x5Receiver::class.java),
    ;

    companion object {
        /**
         * Approximate dp per launcher cell on common 4–5 column home screens. Used to translate
         * the live DpSize into one of the declared column/row presets.
         */
        private const val CELL_DP = 80f

        fun fromProvider(name: String?): WidgetSize? = entries.firstOrNull { it.receiver.name == name }

        /**
         * Picks a sensible starting grid for a widget showing [count] metrics. The editor's
         * preview (and the suggested placement size) is driven purely by the metric count —
         * there is no manual size selection any more, and no 1×5 ceiling: more metrics simply
         * map to a bigger grid, up to the 4×5 preset. The placed widget stays freely resizable.
         */
        fun forMetricCount(count: Int): WidgetSize = when {
            count <= 1 -> S2X2
            count == 2 -> S1X2
            count == 3 -> S1X3
            count == 4 -> S2X2
            count <= 6 -> S2X3
            count <= 9 -> S3X3
            count <= 12 -> S4X4
            else -> S4X5
        }

        /**
         * Resolves the widget's ACTUAL size from the sizes Glance reports for this instance —
         * the same values the widget renders with. Every receiver declares
         * `resizeMode=horizontal|vertical`, so drag-resizing never changes the provider and
         * [fromProvider] alone would keep reporting the original preset.
         */
        suspend fun fromGlance(context: Context, glanceId: GlanceId): WidgetSize? {
            val sizes = runCatching {
                GlanceAppWidgetManager(context).getAppWidgetSizes(glanceId)
            }.getOrNull().orEmpty()
            return fromDpSizes(sizes)
        }

        /** Maps one or more live DpSizes to a declared preset, preferring the largest. */
        fun fromDpSizes(sizes: List<DpSize>): WidgetSize? {
            val size = sizes.maxByOrNull { it.width.value * it.height.value } ?: return null
            val columns = (size.width.value / CELL_DP).roundToInt().coerceIn(1, 4)
            val rows = (size.height.value / CELL_DP).roundToInt().coerceIn(1, 5)
            return entries.firstOrNull { it.columns == columns && it.rows == rows }
                ?: entries.minByOrNull { abs(it.columns - columns) + abs(it.rows - rows) }
        }
    }
}

/** All flexible-size variants share the configurable single-metric renderer. */
open class SizedWidgetReceiver : LocalizedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MetricWidget()
}

class Widget1x1Receiver : SizedWidgetReceiver()
class Widget1x2Receiver : SizedWidgetReceiver()
class Widget1x3Receiver : SizedWidgetReceiver()
class Widget1x4Receiver : SizedWidgetReceiver()
class Widget2x2Receiver : SizedWidgetReceiver()
class Widget2x3Receiver : SizedWidgetReceiver()
class Widget3x3Receiver : SizedWidgetReceiver()
class Widget4x4Receiver : SizedWidgetReceiver()
class Widget1x5Receiver : SizedWidgetReceiver()
class Widget4x5Receiver : SizedWidgetReceiver()
