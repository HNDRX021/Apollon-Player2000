package com.auralis.player.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auralis.player.R
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.glassTextFieldColors

private data class AccentPreset(@StringRes val nameRes: Int, val argb: Long)

/**
 * A balanced spread of hues. Purple is the app's default accent, and blue, green, orange and pink keep
 * the exact values earlier versions offered, so an existing choice still shows as selected.
 */
private val AccentPresets = listOf(
    AccentPreset(R.string.accent_red, 0xFFE53935L),
    AccentPreset(R.string.accent_orange, 0xFFFF7043L),
    AccentPreset(R.string.accent_amber, 0xFFFFB300L),
    AccentPreset(R.string.accent_green, 0xFF00A884L),
    AccentPreset(R.string.accent_blue, 0xFF03A9F4L),
    AccentPreset(R.string.accent_purple, 0xFF7C5CFCL),
    AccentPreset(R.string.accent_pink, 0xFFE91E63L),
)

/** The display name of the selected accent: the preset's name, or "Custom" for any other colour. */
@Composable
fun accentDisplayName(selected: Long): String {
    val current = opaqueAccent(selected)
    val preset = AccentPresets.firstOrNull { it.argb == current }
    return stringResource(preset?.nameRes ?: R.string.accent_custom)
}

/**
 * Accent presets plus a "Custom" swatch that opens a colour picker, laid out as one compact row of
 * circles. The current choice is ringed and ticked (and announced as selected to screen readers).
 * [onSelect] is called straight away for a preset and when Apply is pressed for a custom colour; the
 * theme re-derives from it immediately.
 */
@Composable
fun AccentColorPicker(selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val current = opaqueAccent(selected)
    val customSelected = AccentPresets.none { it.argb == current }

    // null stands for the trailing "Custom" swatch.
    val entries: List<AccentPreset?> = AccentPresets + null
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            if (entry != null) {
                AccentSwatch(
                    label = stringResource(entry.nameRes),
                    selected = current == entry.argb,
                    onClick = { onSelect(entry.argb) },
                    modifier = Modifier.weight(1f),
                ) { SwatchFill(Color(entry.argb), current == entry.argb) }
            } else {
                AccentSwatch(
                    label = stringResource(R.string.accent_custom),
                    selected = customSelected,
                    onClick = { showCustomDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    if (customSelected) SwatchFill(Color(current), selected = true) else RainbowFill()
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomAccentDialog(
            initial = current,
            onDismiss = { showCustomDialog = false },
            onConfirm = { color ->
                showCustomDialog = false
                onSelect(color)
            },
        )
    }
}

@Composable
private fun AccentSwatch(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: @Composable () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { fill() }
}

/** A colour circle; the selected one gets a gap-separated ring and a tick, and the others a hairline edge. */
@Composable
private fun SwatchFill(color: Color, selected: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val innerSize by animateDpAsState(if (selected) 26.dp else 30.dp, label = "swatchSize")
    Box(
        Modifier
            .size(34.dp)
            .then(if (selected) Modifier.border(2.dp, ink, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(color)
                .then(if (selected) Modifier else Modifier.border(0.5.dp, ink.copy(alpha = 0.18f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RainbowFill() {
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(HueStops + HueStops.first())),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Colorize, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

private val HueStops: List<Color> = (0 until 6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
private val HueBarStops: List<Color> = HueStops + HueStops.first()

@Composable
private fun CustomAccentDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    // What the user is typing in the hex box; null while the hex text just mirrors the picker.
    var hexInput by remember { mutableStateOf<String?>(null) }

    val argb = opaqueAccent(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
    val picked = Color(argb)
    val typed = hexInput
    val hexValid = typed == null || parseHexColor(typed) != null

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accent_custom_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValueArea(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v -> saturation = s; value = v; hexInput = null },
                )
                HueBar(hue = hue, onChange = { hue = it; hexInput = null })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(picked)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    TextField(
                        value = typed ?: formatHexColor(argb),
                        onValueChange = { raw ->
                            val cleaned = sanitizeHexInput(raw)
                            hexInput = cleaned
                            parseHexColor(cleaned)?.let { parsed ->
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed.toInt(), hsv)
                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                            }
                        },
                        label = { Text(stringResource(R.string.accent_hex_label)) },
                        prefix = { Text("#") },
                        singleLine = true,
                        isError = !hexValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            GlassTextButton(onClick = { onConfirm(argb) }, enabled = hexValid) { Text(stringResource(R.string.accent_apply)) }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.accent_cancel)) } },
    )
}

@Composable
private fun SaturationValueArea(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val description = stringResource(R.string.accent_shade_area)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .pointerPosition { x, y, width, height ->
                onChange((x / width).coerceIn(0f, 1f), 1f - (y / height).coerceIn(0f, 1f))
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 12.5.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val description = stringResource(R.string.accent_hue_bar)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = description }
            .pointerPosition { x, _, width, _ -> onChange((x / width).coerceIn(0f, 1f) * 360f) },
    ) {
        drawRect(Brush.horizontalGradient(HueBarStops))
        val center = Offset((hue / 360f).coerceIn(0f, 1f) * size.width, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 12.5.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    }
}

/** Reports the touch position (and the size of the touched area) on press and while dragging. */
@Composable
private fun Modifier.pointerPosition(
    onPosition: (x: Float, y: Float, width: Float, height: Float) -> Unit,
): Modifier {
    val latest by rememberUpdatedState(onPosition)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            latest(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                latest(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                change.consume()
            }
        }
    }
}
