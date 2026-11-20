package dev.easonhuang.garpulse.ui.widgets

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.easonhuang.garpulse.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Minimal colour picker: one circular hue wheel plus an opacity slider. Hue is picked by
 * tapping/dragging the ring; the disc centre previews the current colour. Deliberately no
 * saturation/value field, presets or hex input — the two controls cover everyday tuning.
 */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onColor: (Color) -> Unit,
    onDismiss: () -> Unit,
    /** Optional "back to automatic" action, shown as an extra dismiss-side button. */
    autoLabel: String? = null,
    onAuto: (() -> Unit)? = null,
) {
    val hsv = remember(initial.toArgb()) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }

    fun current(): Color = Color(
        AndroidColor.HSVToColor(
            (alpha * 255f).roundToInt().coerceIn(0, 255),
            floatArrayOf(hue, 1f, 1f),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HueWheel(hue = hue, onChange = { hue = it })
                Spacer(Modifier.height(14.dp))
                SliderBar(
                    progress = alpha,
                    onChange = { alpha = it },
                    brush = {
                        val solid = current().copy(alpha = 1f)
                        Brush.horizontalGradient(listOf(solid.copy(alpha = 0f), solid))
                    },
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(current()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColor(current()) }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            if (autoLabel != null && onAuto != null) {
                TextButton(onClick = onAuto) { Text(autoLabel) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Hue from a point inside the wheel, measured clockwise from the +x axis (sweep-gradient order). */
private fun hueAt(pos: Offset, width: Float, height: Float): Float {
    val dx = pos.x - width / 2f
    val dy = pos.y - height / 2f
    var degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (degrees < 0f) degrees += 360f
    return degrees
}

@Composable
private fun HueWheel(hue: Float, onChange: (Float) -> Unit) {
    Canvas(
        Modifier
            .size(210.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(hueAt(change.position, size.width.toFloat(), size.height.toFloat()))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onChange(hueAt(pos, size.width.toFloat(), size.height.toFloat()))
                }
            },
    ) {
        val stroke = 40.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Hue ring: 37 stops so the sweep wraps seamlessly back to red.
        val hues = (0..36).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 10f, 1f, 1f))) }
        drawCircle(
            brush = Brush.sweepGradient(hues),
            radius = radius + stroke / 2f,
            center = center,
            style = Stroke(width = stroke),
        )
        // Marker at the selected hue, sitting on the ring's midline.
        val radians = Math.toRadians(hue.toDouble())
        val marker = center + Offset(
            (cos(radians) * radius).toFloat(),
            (sin(radians) * radius).toFloat(),
        )
        drawCircle(Color.White, radius = 11.dp.toPx(), center = marker)
        drawCircle(Color.Black, radius = 8.dp.toPx(), center = marker)
        // Centre preview of the current fully-opaque colour.
        drawCircle(
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            radius = radius - stroke / 2f - 6.dp.toPx(),
            center = center,
        )
    }
}

@Composable
private fun SliderBar(progress: Float, onChange: (Float) -> Unit, brush: @Composable () -> Brush) {
    val fill = brush()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        drawRect(fill, size = size)
        val x = progress * size.width
        drawLine(Color.White, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 2.dp.toPx())
        drawLine(Color.Black, start = Offset(x + 2, 0f), end = Offset(x + 2, size.height), strokeWidth = 1.dp.toPx())
    }
}
