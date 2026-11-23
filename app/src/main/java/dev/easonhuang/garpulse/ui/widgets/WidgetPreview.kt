package dev.easonhuang.garpulse.ui.widgets

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.MetricKind
import dev.easonhuang.garpulse.data.title
import dev.easonhuang.garpulse.widget.CellPart
import dev.easonhuang.garpulse.widget.CHART_BACKDROP_RATIO
import dev.easonhuang.garpulse.widget.WidgetConfig
import dev.easonhuang.garpulse.widget.WidgetImageStore
import dev.easonhuang.garpulse.widget.WidgetLayout
import dev.easonhuang.garpulse.widget.WidgetLayoutPlanner
import dev.easonhuang.garpulse.widget.WidgetMetricState
import dev.easonhuang.garpulse.widget.WidgetRefreshStrip
import dev.easonhuang.garpulse.widget.chartBitmap
import dev.easonhuang.garpulse.widget.metricGlyph
import dev.easonhuang.garpulse.widget.textColorFor

/** Representative values so the editor can show a meaningful preview before data is refreshed. */
fun sampleWidgetStates(config: WidgetConfig, context: Context): List<WidgetMetricState> {
    val points = listOf(34f, 48f, 41f, 66f, 58f, 82f, 74f, 91f)
    return config.metrics.map { metric ->
        val value = when (metric.key) {
            "steps" -> "8,432"
            "distance" -> "5.8"
            "active_calories" -> "412"
            "total_calories" -> "1,860"
            "floors" -> "12"
            "exercise" -> "38"
            "hydration" -> "1.8"
            "heart_rate" -> "72"
            "resting_heart_rate" -> "58"
            "hrv" -> "46"
            "sleep" -> "7.2"
            "oxygen" -> "98"
            "respiratory" -> "15"
            "vo2max" -> "42"
            "weight" -> "68.5"
            "height" -> "172"
            "blood_pressure" -> "118/76"
            "blood_glucose" -> "92"
            else -> "42"
        }
        WidgetMetricState(metric.key, true, value, context.getString(metric.unitRes), points)
    }
}

/** Compose twin of the Glance renderer, used only for the in-app live preview. */
@Composable
fun WidgetPreviewCard(
    config: WidgetConfig,
    states: List<WidgetMetricState>,
    width: Dp,
    height: Dp,
    onAddImage: (() -> Unit)? = null,
) {
    val imageBitmap = WidgetImageStore.load(config.imagePath, maxDimension = 720)
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(20.dp))
            .background(config.effectiveBackground)
            .then(if (onAddImage != null && imageBitmap == null) Modifier.clickable { onAddImage() } else Modifier),
    ) {
        // Same stacking as the real widget: the picture is a full-bleed background, metric cards
        // are drawn on top, and the strip spans the full height.
        Box(Modifier.fillMaxWidth().height(height).padding(6.dp)) {
            if (imageBitmap != null) {
                // Same one-mechanism model as the real widget: transparency is baked into the
                // picture itself, so what the preview shows is exactly what the launcher shows.
                Image(
                    bitmap = imageBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = config.imageAlpha,
                    modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)),
                )
            }
            if (states.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                    if (imageBitmap == null) {
                        if (onAddImage != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.widget_editor_preview_add_image),
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.widget_tap_to_connect_data),
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else if (config.layout == WidgetLayout.ICON_ONLY) {
                val grid = WidgetLayoutPlanner.iconGrid(width.value, height.value, states.size)
                Column {
                    grid.cells.chunked(grid.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { cell ->
                                val state = states.getOrNull(cell.metricIndex) ?: return@forEach
                                Text(
                                    metricGlyph(state.metricKey),
                                    color = config.textColorFor(Metric.fromKey(state.metricKey)),
                                    fontSize = grid.bodySp.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            } else {
                val longest = states.maxOfOrNull { it.value.length + it.unit.length } ?: 6
                val grid = WidgetLayoutPlanner.plan(
                    widthDp = width.value,
                    heightDp = height.value,
                    metricCount = states.size.coerceAtLeast(1),
                    fontScale = config.fontScale,
                    showChart = config.showChart,
                    valueChars = if (config.showValue) longest else 3,
                )
                Column {
                    grid.cells.chunked(grid.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { cell ->
                                val state = states.getOrNull(cell.metricIndex) ?: return@forEach
                                PreviewMetricCell(
                                    config, state, cell.part, grid.bodySp, grid.labelSp, cell.heightDp,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (config.showRefreshStrip) {
            // Mirror the renderer exactly: a sibling of the content that spans the FULL height
            // from the left edge, width = coverage, colour/alpha = strip colour and transparency.
            // Otherwise the preview lies about the settings the user is actively dragging.
            val coverage = config.stripCoverage.coerceIn(
                WidgetConfig.MIN_STRIP_COVERAGE, WidgetConfig.MAX_STRIP_COVERAGE,
            ) / 100f
            val stripWidth = (width.value * coverage).coerceAtLeast(2f)
            val base = config.stripColor ?: Color.White
            val opacity = config.stripOpacity.coerceIn(0, 100) / 100f
            val alpha = if (config.stripColor != null) base.alpha * opacity else opacity
            if (alpha > 0f) {
                Image(
                    bitmap = WidgetRefreshStrip.bitmap(
                        colorArgb = base.copy(alpha = alpha).toArgb(),
                        widthPx = (stripWidth * 3f).toInt().coerceAtLeast(6),
                        heightPx = (height.value * 3f).toInt().coerceAtLeast(8),
                        phase = 0,
                        wave = config.stripWave,
                    ).asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.width(stripWidth.dp).height(height),
                )
            }
        }
    }
}

@Composable
private fun PreviewMetricCell(
    config: WidgetConfig,
    state: WidgetMetricState,
    part: CellPart,
    bodySp: Float,
    labelSp: Float,
    cellHeightDp: Float,
    modifier: Modifier,
) {
    val metric = Metric.fromKey(state.metricKey)
    val color = config.textColorFor(metric)
    val compact = cellHeightDp < 96f
    val chartHeight = cellHeightDp * CHART_BACKDROP_RATIO
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .background(config.effectiveCard)
            .padding(if (compact) 5.dp else 8.dp),
    ) {
        // Same stacking as the real widget: chart backdrop below, text always above it.
        if (config.showChart && state.points.size >= 2) {
            Image(
                bitmap = chartBitmap(
                    values = state.points,
                    kind = metric?.kind ?: MetricKind.LATEST,
                    colorArgb = color.toArgb(),
                    widthPx = 320,
                    heightPx = (chartHeight * 3f).toInt().coerceAtLeast(8),
                    alpha = 0.38f,
                ).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(chartHeight.dp).align(Alignment.BottomCenter),
            )
        }
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopStart),
            horizontalAlignment = Alignment.Start,
        ) {
            if (config.showSource && (!config.showLabels || metric == null)) {
                PreviewSourceTag(state.source, (labelSp * 0.8f).sp)
                Spacer(Modifier.height(2.dp))
            }
            if (config.showLabels && metric != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metric.title,
                        color = color,
                        fontSize = labelSp.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (config.showSource) {
                        Spacer(Modifier.width(4.dp))
                        PreviewSourceTag(state.source, (labelSp * 0.8f).sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.showValue) {
                    Text(state.value, color = color, fontSize = bodySp.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (state.unit.isNotBlank()) {
                        Spacer(Modifier.width(3.dp))
                        Text(state.unit, color = color.copy(alpha = 0.8f), fontSize = (bodySp * 0.55f).sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSourceTag(source: dev.easonhuang.garpulse.data.ValueSource, size: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(source.badgeColor))
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(source.shortRes),
            color = source.badgeColor.copy(alpha = 0.9f),
            fontSize = size,
            maxLines = 1,
        )
    }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
    (alpha * 255f).toInt() shl 24 or
        (red * 255f).toInt() shl 16 or
        (green * 255f).toInt() shl 8 or
        (blue * 255f).toInt()
