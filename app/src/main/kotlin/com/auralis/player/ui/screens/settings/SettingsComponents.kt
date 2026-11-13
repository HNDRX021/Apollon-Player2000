package com.auralis.player.ui.screens.settings

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Check
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.GlassTextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Design tokens shared by every Settings row and card, so spacing, radii and sizes stay consistent
 * from one place. Colours always come from [MaterialTheme], so the selected accent colour (and the
 * light/dark theme) flows through automatically.
 */
internal object SettingsTokens {
    val CardRadius = 20.dp
    val RowMinHeight = 56.dp
    val RowHorizontalPadding = 16.dp
    val IconTileSize = 34.dp
    val IconTileRadius = 10.dp
    val IconGlyphSize = 20.dp
    val IconGap = 14.dp

    /** Where text starts inside a row; dividers and secondary action rows are inset by this much. */
    val TextInset = RowHorizontalPadding + IconTileSize + IconGap
}

/** Small muted heading placed above a card (iOS / Apple Music style grouping). */
@Composable
internal fun SettingsSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * A rounded charcoal card with a very subtle top-lit gradient and a hairline edge. Each entry of
 * [rows] is one row; thin inset separators are drawn between them. Nothing is drawn for an empty list.
 */
@Composable
internal fun SettingsCard(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(SettingsTokens.CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(surface, ink, 0.07f), lerp(surface, ink, 0.03f))))
            .border(0.5.dp, ink.copy(alpha = 0.07f), shape),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = SettingsTokens.TextInset),
                    thickness = 0.5.dp,
                    color = ink.copy(alpha = 0.08f),
                )
            }
            row()
        }
    }
}

/** The accent-tinted rounded square that holds a row's line icon. */
@Composable
internal fun SettingsIconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(SettingsTokens.IconTileSize)
            .clip(RoundedCornerShape(SettingsTokens.IconTileRadius))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(SettingsTokens.IconGlyphSize))
    }
}

/**
 * The standard settings row: icon tile, title with optional subtitle, then an optional [value] and
 * chevron (or any [trailing] control) on the right. Clickable only when [onClick] is given.
 * Rows are at least 56 dp tall so every touch target is comfortable.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsTokens.RowMinHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = SettingsTokens.RowHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile(icon)
        Spacer(Modifier.width(SettingsTokens.IconGap))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp).size(22.dp),
            )
        }
    }
}

/** A switch styled with the active accent colour. Pass a null [onCheckedChange] when a parent row toggles it. */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.onSurface.copy(alpha = 0.16f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** A settings row whose whole surface toggles the switch, announced to screen readers as one switch. */
@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = { SettingsSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * A single-choice dialog. Picking an option applies it straight away ([onSelect]); the caller closes
 * the dialog. Works for any option type, so new languages or sort modes only need a new entry.
 */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (value == selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Preview of the chosen app background image (a content URI), decoded at roughly the size it is drawn.
 * Shows a neutral placeholder while loading, when no image is set, or when the image can no longer be read.
 */
@Composable
internal fun WallpaperThumbnail(
    uri: String?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    maxDecodeSize: Dp = 320.dp,
) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { maxDecodeSize.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uri, key2 = targetPx) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(shape)
            .background(ink.copy(alpha = 0.08f))
            .border(0.5.dp, ink.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Wallpaper,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
}
