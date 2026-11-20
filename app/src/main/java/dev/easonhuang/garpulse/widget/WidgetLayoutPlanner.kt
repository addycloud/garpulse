package dev.easonhuang.garpulse.widget

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Which part of a metric a single grid cell renders. */
internal enum class CellPart { BOTH, VALUE, CHART }

/** One renderable grid cell. */
internal data class GridCell(
    val metricIndex: Int,
    val part: CellPart,
    val widthDp: Float,
    val heightDp: Float,
)

/** The complete adaptive grid for a widget of a given size. */
internal data class WidgetGrid(
    val columns: Int,
    val rows: Int,
    val cells: List<GridCell>,
    /** Font size derived from the cell size, already multiplied by the user's fine-tune. */
    val bodySp: Float,
    val labelSp: Float,
) {
    val isEmpty: Boolean get() = cells.isEmpty()
}

/**
 * Turns a widget's actual size plus the configured metric count into a grid of cells.
 *
 * One configured metric always gets exactly one cell that shows its name, value and chart
 * together. Splitting value from chart is only allowed for a *single* metric that has two or
 * more cells available — multiple metrics are never split, no matter how large the widget is.
 * Extra metrics simply add rows, so nothing is ever dropped or squeezed into a half cell.
 */
internal object WidgetLayoutPlanner {

    /** Approximate size of one home-screen cell in dp; used only to derive the initial grid. */
    private const val CELL_DP = 72f

    fun plan(
        widthDp: Float,
        heightDp: Float,
        metricCount: Int,
        fontScale: Float,
        showChart: Boolean,
        /** Longest "value + unit" the renderer needs to fit, used to shrink digits safely. */
        valueChars: Int = 6,
    ): WidgetGrid {
        val n = max(1, metricCount)
        val w = widthDp.coerceAtLeast(40f)
        val h = heightDp.coerceAtLeast(40f)

        // Every metric gets exactly one square-ish card. The chart is drawn *inside* that card
        // as a translucent backdrop, so it never steals a cell or pushes the number around.
        // Among all grids that fit every metric, prefer cells closest to square and few empties.
        var cols = 1
        var rows = n
        var best = Float.MAX_VALUE
        for (c in 1..n) {
            val r = ceil(n.toFloat() / c).toInt()
            val cw = w / c
            val ch = h / r
            val ratio = max(cw / ch, ch / cw)
            val waste = (c * r - n).toFloat()
            val score = ratio + waste * 0.18f
            if (score < best) {
                best = score
                cols = c
                rows = r
            }
        }

        val cellW = w / cols
        val cellH = h / rows
        val cells = (0 until n).map { GridCell(it, CellPart.BOTH, cellW, cellH) }

        val smallest = min(cellW, cellH)
        val autoSp = (smallest / 3.1f).coerceIn(9f, 34f)
        // Reserve padding plus a little breathing room so digits are never clipped.
        val pad = if (smallest < 96f) 14f else 20f
        val availableW = (cellW - pad).coerceAtLeast(16f)
        val em = valueChars.coerceAtLeast(1) * 0.62f + 0.8f
        val byValue = availableW / em
        val body = (min(autoSp, byValue) * fontScale.coerceIn(0.6f, 1.6f)).coerceIn(8f, 42f)
        return WidgetGrid(
            columns = cols,
            rows = rows,
            cells = cells,
            bodySp = body,
            labelSp = (body * 0.56f).coerceIn(7f, 22f),
        )
    }

    /** Fallback grid for icon-only widgets: one square-ish cell per metric. */
    fun iconGrid(widthDp: Float, heightDp: Float, metricCount: Int): WidgetGrid {
        val n = max(1, metricCount)
        val w = widthDp.coerceAtLeast(40f)
        val h = heightDp.coerceAtLeast(40f)
        val cols = when {
            w >= h * 1.5f -> min(n, max(2, (w / CELL_DP).roundToInt()))
            h >= w * 1.5f -> 1
            else -> sqrt(n.toFloat()).roundToInt().coerceIn(1, n)
        }
        val rows = ceil(n.toFloat() / cols).toInt()
        val cellW = w / cols
        val cellH = h / rows
        val cells = (0 until n).map { GridCell(it, CellPart.BOTH, cellW, cellH) }
        val iconSp = (min(cellW, cellH) * 0.5f).coerceIn(18f, 56f)
        return WidgetGrid(cols, rows, cells, iconSp, iconSp)
    }
}
