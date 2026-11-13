package com.auralis.player.ui.screens.equalizer

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.glassTextFieldColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.audio.EqualizerModel
import com.auralis.player.audio.EqualizerUiState
import com.auralis.player.ui.screens.MusicScreensViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SliderAreaHeight = 184.dp
private val ThumbPad = 12.dp

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val model: MusicScreensViewModel = hiltViewModel()
    val state by model.equalizerState.collectAsState()
    var showSavePreset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassToolbarButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.eq_back))
            }
        }
        EqualizerPanel(
            state = state,
            onEnabled = model.equalizer::setEnabled,
            onBandChanged = model.equalizer::setBandLevel,
            onBandsChangeFinished = model.equalizer::persistCurrent,
            onSelectPreset = model.equalizer::selectPreset,
            onDeletePreset = model.equalizer::deletePreset,
            onSavePreset = { showSavePreset = true },
        )
    }

    if (showSavePreset) {
        SavePresetDialog(
            onDismiss = { showSavePreset = false },
            onSave = { name, replace -> model.equalizer.saveCurrentPreset(name, replace) },
        )
    }
}

@Composable
private fun EqualizerPanel(
    state: EqualizerUiState,
    onEnabled: (Boolean) -> Unit,
    onBandChanged: (Int, Int) -> Unit,
    onBandsChangeFinished: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSavePreset: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val toggleLabel = stringResource(R.string.eq_toggle_description)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.eq_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = toggleLabel },
                )
            }
            Spacer(Modifier.height(14.dp))

            // Bands run low -> high from left to right in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.alpha(if (state.enabled) 1f else 0.5f)) {
                    Row(Modifier.fillMaxWidth()) {
                        state.bandLevelsMb.forEach { level ->
                            Text(
                                EqualizerModel.formatGain(level),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val guide = onSurface.copy(alpha = 0.08f)
                    val zeroGuide = onSurface.copy(alpha = 0.22f)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(SliderAreaHeight)
                            .drawBehind {
                                val pad = ThumbPad.toPx()
                                val span = size.height - 2 * pad
                                for (db in listOf(12, 6, 0, -6, -12)) {
                                    val y = pad + (EqualizerModel.MAX_GAIN_DB - db) / (EqualizerModel.MAX_GAIN_DB - EqualizerModel.MIN_GAIN_DB).toFloat() * span
                                    drawLine(
                                        color = if (db == 0) zeroGuide else guide,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            },
                    ) {
                        state.bandLevelsMb.forEachIndexed { band, level ->
                            GainSlider(
                                levelMb = level,
                                enabled = state.enabled,
                                description = stringResource(R.string.eq_band_slider_description, EqualizerModel.BAND_LABELS[band]),
                                onLevelChange = { onBandChanged(band, it) },
                                onChangeFinished = onBandsChangeFinished,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        EqualizerModel.BAND_LABELS.forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EqualizerModel.BUILT_IN_PRESETS.forEach { preset ->
                    PresetPill(
                        label = builtInPresetName(preset.id),
                        selected = state.selectedPresetId == preset.id,
                        enabled = state.enabled,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
                state.customPresets.forEach { preset ->
                    PresetPill(
                        label = preset.name,
                        selected = state.selectedPresetId == EqualizerModel.customId(preset.name),
                        enabled = state.enabled,
                        onClick = { onSelectPreset(EqualizerModel.customId(preset.name)) },
                        onDelete = { onDeletePreset(preset.name) },
                        deleteDescription = stringResource(R.string.eq_delete_preset, preset.name),
                    )
                }
                SavePill(onClick = onSavePreset)
            }

            Spacer(Modifier.height(10.dp))
            val hint = when {
                !state.enabled -> stringResource(R.string.eq_off_hint)
                state.selectedPresetId == null -> stringResource(R.string.eq_custom_curve)
                else -> null
            }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun builtInPresetName(id: String): String = when (id) {
    "flat" -> stringResource(R.string.eq_preset_flat)
    "rock" -> stringResource(R.string.eq_preset_rock)
    "metal" -> stringResource(R.string.eq_preset_metal)
    "hip_hop" -> stringResource(R.string.eq_preset_hip_hop)
    "pop" -> stringResource(R.string.eq_preset_pop)
    "country" -> stringResource(R.string.eq_preset_country)
    "electronic" -> stringResource(R.string.eq_preset_electronic)
    "ambient" -> stringResource(R.string.eq_preset_ambient)
    else -> id
}

@Composable
private fun PresetPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteDescription: String = "",
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = if (onDelete != null) 4.dp else 14.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            if (onDelete != null) {
                GlassToolbarButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = deleteDescription,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavePill(onClick: () -> Unit) {
    val label = stringResource(R.string.eq_save_preset)
    Surface(
        modifier = Modifier.semantics { contentDescription = label }.selectable(selected = false, role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

/**
 * Vertical -12..+12 dB slider in 1 dB steps. The filled part runs from the 0 dB
 * line to the thumb, so boosts and cuts are visible at a glance. Drag or tap;
 * TalkBack can adjust it through the standard progress semantics.
 */
@Composable
private fun GainSlider(
    levelMb: Int,
    enabled: Boolean,
    description: String,
    onLevelChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val latestChange by rememberUpdatedState(onLevelChange)
    val latestFinished by rememberUpdatedState(onChangeFinished)
    val gainText = EqualizerModel.formatGain(levelMb)

    fun levelFor(y: Float, height: Float, pad: Float): Int {
        val usable = (height - 2 * pad).coerceAtLeast(1f)
        val fraction = 1f - ((y - pad) / usable).coerceIn(0f, 1f)
        val db = EqualizerModel.MIN_GAIN_DB + fraction * (EqualizerModel.MAX_GAIN_DB - EqualizerModel.MIN_GAIN_DB)
        return db.roundToInt() * 100
    }

    Box(
        modifier
            .semantics {
                contentDescription = description
                stateDescription = gainText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = levelMb / 100f,
                    range = EqualizerModel.MIN_GAIN_DB.toFloat()..EqualizerModel.MAX_GAIN_DB.toFloat(),
                    steps = EqualizerModel.MAX_GAIN_DB - EqualizerModel.MIN_GAIN_DB - 1,
                )
                if (enabled) {
                    setProgress { target ->
                        latestChange(target.roundToInt().coerceIn(EqualizerModel.MIN_GAIN_DB, EqualizerModel.MAX_GAIN_DB) * 100)
                        latestFinished()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    latestChange(levelFor(offset.y, size.height.toFloat(), ThumbPad.toPx()))
                    latestFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset -> latestChange(levelFor(offset.y, size.height.toFloat(), ThumbPad.toPx())) },
                    onVerticalDrag = { change, _ ->
                        latestChange(levelFor(change.position.y, size.height.toFloat(), ThumbPad.toPx()))
                        change.consume()
                    },
                    onDragEnd = { latestFinished() },
                    onDragCancel = { latestFinished() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = ThumbPad.toPx()
            val cx = size.width / 2f
            val top = pad
            val bottom = size.height - pad
            val stroke = 4.dp.toPx()
            drawLine(trackColor, Offset(cx, top), Offset(cx, bottom), stroke, StrokeCap.Round)

            val fraction = (levelMb - EqualizerModel.MIN_GAIN_MB).toFloat() / (EqualizerModel.MAX_GAIN_MB - EqualizerModel.MIN_GAIN_MB)
            val y = bottom - fraction * (bottom - top)
            val zeroY = (top + bottom) / 2f
            val color = if (enabled) accent else accent.copy(alpha = 0.45f)
            drawLine(color, Offset(cx, zeroY), Offset(cx, y), stroke, StrokeCap.Round)
            drawCircle(color, 8.dp.toPx(), Offset(cx, y))
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: suspend (name: String, replaceExisting: Boolean) -> EqualizerModel.SaveResult,
) {
    var name by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<EqualizerModel.SaveResult?>(null) }
    val scope = rememberCoroutineScope()

    fun submit(replace: Boolean) {
        scope.launch {
            val result = onSave(name, replace)
            if (result == EqualizerModel.SaveResult.Saved) onDismiss() else failure = result
        }
    }

    val errorText = when (failure) {
        EqualizerModel.SaveResult.EmptyName -> stringResource(R.string.eq_error_empty)
        EqualizerModel.SaveResult.DuplicateName -> stringResource(R.string.eq_error_duplicate)
        EqualizerModel.SaveResult.ReservedName -> stringResource(R.string.eq_error_reserved)
        EqualizerModel.SaveResult.LimitReached -> stringResource(R.string.eq_error_limit)
        else -> null
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_save_preset)) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(EqualizerModel.MAX_PRESET_NAME_LENGTH); failure = null },
                    label = { Text(stringResource(R.string.eq_preset_name)) },
                    singleLine = true,
                    isError = errorText != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = glassTextFieldColors(),
                )
                if (errorText != null) {
                    Text(
                        errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (failure == EqualizerModel.SaveResult.DuplicateName) {
                    GlassTextButton(onClick = { submit(true) }) { Text(stringResource(R.string.eq_replace_preset)) }
                }
                GlassTextButton(onClick = { submit(false) }) { Text(stringResource(R.string.playlists_save)) }
            }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.playlists_cancel)) } },
    )
}
