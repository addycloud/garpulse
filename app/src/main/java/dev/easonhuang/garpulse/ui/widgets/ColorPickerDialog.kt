package dev.easonhuang.garpulse.ui.widgets

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.easonhuang.garpulse.R
import kotlin.math.roundToInt

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF1E2433, 0xFF0B0F19, 0xFF243447,
    0xFFB3261E, 0xFFF59E0B, 0xFF1B8A5A, 0xFF2563EB,
    0xFF7C3AED, 0xFFEC4899, 0xFF06B6D4, 0xFF84CC16,
)

/** HSV colour picker with a saturation/value field, hue and opacity sliders and hex input. */
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
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }
    var hex by remember { mutableStateOf("%08X".format(initial.toArgb())) }
    var invalid by remember { mutableStateOf(false) }

    fun current(): Color = Color(
        AndroidColor.HSVToColor(
            (alpha * 255f).roundToInt().coerceIn(0, 255),
            floatArrayOf(hue, saturation, brightness),
        ),
    )

    fun syncFrom(color: Color) {
        val argb = color.toArgb()
        val values = FloatArray(3)
        AndroidColor.colorToHSV(argb, values)
        hue = values[0]
        saturation = values[1]
        brightness = values[2]
        alpha = color.alpha
        hex = "%08X".format(argb)
        invalid = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                brightness = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                hex = "%08X".format(current().toArgb())
                            }
                        }
                        .pointerInput(hue) {
                            detectTapGestures { pos ->
                                saturation = (pos.x / size.width).coerceIn(0f, 1f)
                                brightness = (1f - pos.y / size.height).coerceIn(0f, 1f)
                                hex = "%08X".format(current().toArgb())
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                        drawRect(SolidColor(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))), size = size)
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White)),
                            size = size,
                        )
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                            size = size,
                        )
                        val center = Offset(saturation * size.width, (1f - brightness) * size.height)
                        drawCircle(Color.White, radius = 10.dp.toPx(), center = center)
                        drawCircle(Color.Black, radius = 7.dp.toPx(), center = center)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SliderBar(
                    progress = hue / 360f,
                    onChange = { changed ->
                        hue = changed * 360f
                        hex = "%08X".format(current().toArgb())
                    },
                    brush = {
                        Brush.horizontalGradient((0..12).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) })
                    },
                )
                Spacer(Modifier.height(8.dp))
                SliderBar(
                    progress = alpha,
                    onChange = { changed ->
                        alpha = changed
                        hex = "%08X".format(current().toArgb())
                    },
                    brush = {
                        val solid = current().copy(alpha = 1f)
                        Brush.horizontalGradient(listOf(solid.copy(alpha = 0f), solid))
                    },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val cleaned = input.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(8)
                        hex = cleaned
                        if (cleaned.length == 6 || cleaned.length == 8) {
                            runCatching {
                                val value = ("FF" + cleaned).takeLast(8).toLong(16).toInt()
                                syncFrom(Color(value))
                            }.onFailure { invalid = true }
                        } else invalid = cleaned.isNotEmpty()
                    },
                    label = { Text(stringResource(R.string.color_picker_hex)) },
                    singleLine = true,
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) {
                    Text(
                        stringResource(R.string.color_picker_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.color_picker_presets), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SWATCHES.forEach { argb ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb.toInt()))
                                .clickable { syncFrom(Color(argb.toInt())) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
