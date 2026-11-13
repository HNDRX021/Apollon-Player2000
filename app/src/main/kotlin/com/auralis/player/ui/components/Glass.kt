package com.auralis.player.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/*
 * Liquid Glass kit: the one place that defines how translucent surfaces, menus, dialogs and buttons look,
 * so every screen shares the same radii, edges, shadows and press feedback. All colours come from
 * MaterialTheme, so the chosen accent colour and the light/dark theme flow through automatically.
 *
 * About blur: Compose has no "blur what is behind me" modifier. Dialogs ask the window for a real blur of
 * the app behind them (Android 12+ when the device allows cross-window blur; otherwise they just dim).
 * Dropdown menus are small popup windows whose blur would show as a hard rectangle behind the rounded
 * corners, so they use a translucent tinted fill, a lit top edge and a soft shadow instead.
 */

object GlassTokens {
    val MenuRadius = 22.dp
    val DialogRadius = 28.dp
    val ItemRadius = 14.dp
    val MenuMinWidth = 220.dp
    val MenuItemMinHeight = 46.dp
    val ButtonMinHeight = 44.dp
    val IconButtonSize = 40.dp
    val Hairline = 0.5.dp
}

/** The colours of one glass surface family, derived from the current theme. */
@Immutable
internal class GlassPalette(
    val isDark: Boolean,
    /** Solid-ish tint used as the Surface colour of menus (kept single-coloured so no seams show). */
    val base: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val edgeTop: Color,
    val edgeBottom: Color,
    val ink: Color,
    val accent: Color,
    val divider: Color,
    val sectionGap: Color,
    val hover: Color,
    val pressed: Color,
    val selected: Color,
)

@Composable
internal fun rememberGlassPalette(): GlassPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return remember(scheme.surface, scheme.surfaceVariant, scheme.primary, scheme.onSurface, dark) {
        val ink = scheme.onSurface
        val accent = scheme.primary
        if (dark) {
            // Charcoal glass with a faint wash of the accent colour bleeding in from below.
            val tint = lerp(scheme.surfaceVariant, accent, 0.14f)
            GlassPalette(
                isDark = true,
                base = tint.copy(alpha = 0.86f),
                panelTop = lerp(tint, Color.White, 0.10f).copy(alpha = 0.86f),
                panelBottom = lerp(tint, accent, 0.10f).copy(alpha = 0.92f),
                edgeTop = Color.White.copy(alpha = 0.34f),
                edgeBottom = Color.White.copy(alpha = 0.07f),
                ink = ink,
                accent = accent,
                divider = Color.White.copy(alpha = 0.10f),
                sectionGap = Color.Black.copy(alpha = 0.20f),
                hover = ink.copy(alpha = 0.08f),
                pressed = ink.copy(alpha = 0.16f),
                selected = accent.copy(alpha = 0.16f),
            )
        } else {
            val tint = lerp(Color.White, accent, 0.07f)
            GlassPalette(
                isDark = false,
                base = tint.copy(alpha = 0.88f),
                panelTop = Color.White.copy(alpha = 0.92f),
                panelBottom = lerp(Color.White, accent, 0.10f).copy(alpha = 0.90f),
                edgeTop = Color.White.copy(alpha = 0.95f),
                edgeBottom = ink.copy(alpha = 0.10f),
                ink = ink,
                accent = accent,
                divider = ink.copy(alpha = 0.10f),
                sectionGap = ink.copy(alpha = 0.06f),
                hover = ink.copy(alpha = 0.06f),
                pressed = ink.copy(alpha = 0.12f),
                selected = accent.copy(alpha = 0.14f),
            )
        }
    }
}

internal fun GlassPalette.panelBrush(): Brush = Brush.verticalGradient(listOf(panelTop, panelBottom))

internal fun GlassPalette.edgeBrush(): Brush = Brush.verticalGradient(listOf(edgeTop, edgeBottom))

/** A very faint diagonal highlight, like light catching the top-left of a glass pane. */
internal fun GlassPalette.sheenBrush(): Brush = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = if (isDark) 0.08f else 0.30f), Color.Transparent),
    start = Offset.Zero,
    end = Offset(520f, 520f),
)

/** Clips to [shape] and paints the full glass look: gradient fill, sheen and a thin lit edge. */
internal fun Modifier.glassPanel(palette: GlassPalette, shape: Shape): Modifier =
    this
        .clip(shape)
        .background(palette.panelBrush(), shape)
        .background(palette.sheenBrush(), shape)
        .border(GlassTokens.Hairline, palette.edgeBrush(), shape)

// ---------------------------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------------------------

/**
 * One entry of a [GlassDropdownMenu]. Icons are chosen automatically from well-known labels
 * ("Play Next", "Add to Queue", "Delete"...) unless [autoIcon] is off (sort and filter choices);
 * [trailingIcon] given without a [leadingIcon] is shown at the start so every menu has one icon column.
 * [selected] marks the current choice with a check and the accent colour; [highlighted] only tints.
 */
class GlassMenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val leadingIcon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val selected: Boolean = false,
    val highlighted: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    /** Sets this entry apart as its own group with a heavier separator above it. */
    val dividerBefore: Boolean = false,
    val autoIcon: Boolean = true,
)

internal fun glassIconFor(label: String): ImageVector? {
    val text = label.trim().lowercase()
    return when {
        text.startsWith("play next") -> Icons.Outlined.SkipNext
        text.contains("queue") -> Icons.AutoMirrored.Outlined.QueueMusic
        text.startsWith("add to playlist") || text.startsWith("add songs") -> Icons.AutoMirrored.Outlined.PlaylistAdd
        text.contains("shuffle") -> Icons.Outlined.Shuffle
        text.startsWith("play") -> Icons.Outlined.PlayArrow
        text.contains("folder") -> Icons.Outlined.FolderOpen
        text.contains("refresh") -> Icons.Outlined.Refresh
        text.contains("favorite") || text.contains("favourite") -> Icons.Outlined.FavoriteBorder
        text.startsWith("edit") || text.startsWith("rename") -> Icons.Outlined.Edit
        text.contains("artwork") && !text.startsWith("remove") -> Icons.Outlined.Image
        text.startsWith("delete") || text.startsWith("remove artwork") -> Icons.Outlined.Delete
        text.startsWith("remove") -> Icons.Outlined.RemoveCircleOutline
        text.startsWith("move to top") -> Icons.Outlined.VerticalAlignTop
        text.startsWith("move to bottom") -> Icons.Outlined.VerticalAlignBottom
        text.startsWith("move up") -> Icons.Outlined.ArrowUpward
        text.startsWith("move down") -> Icons.Outlined.ArrowDownward
        else -> null
    }
}

/**
 * The floating glass container used by every dropdown, three-dot, sort, filter and contextual menu.
 * Entries get a consistent icon + text layout, hairline dividers between them, comfortable row height
 * and a soft hover / pressed / selected highlight. Choosing an entry closes the menu first
 * ([onDismissRequest]) and then runs the entry's action.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<GlassMenuEntry>,
    modifier: Modifier = Modifier,
) {
    val palette = rememberGlassPalette()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = GlassTokens.MenuMinWidth)
            .background(palette.sheenBrush()),
        shape = RoundedCornerShape(GlassTokens.MenuRadius),
        containerColor = palette.base,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(GlassTokens.Hairline, palette.edgeBrush()),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                if (entry.dividerBefore) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(palette.sectionGap))
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .height(GlassTokens.Hairline)
                            .background(palette.divider),
                    )
                }
            }
            GlassMenuRow(entry, palette) {
                onDismissRequest()
                entry.onClick()
            }
        }
    }
}

@Composable
private fun GlassMenuRow(entry: GlassMenuEntry, palette: GlassPalette, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            !entry.enabled -> Color.Transparent
            pressed -> palette.pressed
            hovered -> palette.hover
            entry.selected -> palette.selected
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "glassMenuRow",
    )
    val tint = when {
        entry.destructive -> scheme.error
        entry.highlighted || entry.selected -> scheme.primary
        else -> scheme.onSurface
    }
    val icon = entry.leadingIcon ?: entry.trailingIcon ?: if (entry.autoIcon) glassIconFor(entry.label) else null
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(GlassTokens.ItemRadius))
            .background(background)
            .alpha(if (entry.enabled) 1f else 0.38f)
            .hoverable(interaction, enabled = entry.enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = entry.enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = GlassTokens.MenuItemMinHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.selected) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

enum class GlassButtonStyle { Primary, Secondary }

/**
 * A pill button. [GlassButtonStyle.Primary] is accent-tinted glass (soft top light, thin edge),
 * [GlassButtonStyle.Secondary] is a quiet translucent surface. Hover brightens a little, pressed
 * darkens and shrinks by 3 %; nothing is flashy.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val ink = scheme.onSurface
    val accent = scheme.primary

    val topTarget: Color
    val bottomTarget: Color
    val contentColor: Color
    val edge: Brush
    if (style == GlassButtonStyle.Primary) {
        val shift = when {
            pressed -> Color.Black.copy(alpha = 0.16f)
            hovered -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        }
        topTarget = lerp(lerp(accent, Color.White, 0.16f), shift.copy(alpha = 1f), shift.alpha)
        bottomTarget = lerp(accent, shift.copy(alpha = 1f), shift.alpha)
        contentColor = scheme.onPrimary
        edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f)))
    } else {
        val strength = when {
            pressed -> 0.18f
            hovered -> 0.14f
            else -> 0.10f
        }
        topTarget = ink.copy(alpha = strength + 0.02f)
        bottomTarget = ink.copy(alpha = strength - 0.02f)
        contentColor = ink
        edge = Brush.verticalGradient(listOf(ink.copy(alpha = 0.20f), ink.copy(alpha = 0.06f)))
    }
    val top by animateColorAsState(topTarget, tween(120), label = "glassButtonTop")
    val bottom by animateColorAsState(bottomTarget, tween(120), label = "glassButtonBottom")
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(100), label = "glassButtonScale")

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)), CircleShape)
            .border(GlassTokens.Hairline, edge, CircleShape)
            .hoverable(interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = GlassTokens.ButtonMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
                Row(
                    Modifier.padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = content,
                )
            }
        }
    }
}

/**
 * A round icon button with a fixed [size] (40 dp by default) so icons line up everywhere. It is
 * invisible at rest, gets a faint glass disc on hover and a stronger one while pressed. [filled] keeps a
 * soft disc at rest, for buttons that sit on top of artwork. The icon takes [contentColor].
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = GlassTokens.IconButtonSize,
    enabled: Boolean = true,
    filled: Boolean = false,
    contentColor: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val disc by animateColorAsState(
        when {
            pressed -> ink.copy(alpha = 0.18f)
            hovered -> ink.copy(alpha = 0.10f)
            filled -> ink.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "glassIconDisc",
    )
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, tween(100), label = "glassIconScale")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(disc)
            .hoverable(interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** The borderless "Cancel" / "Save" button of dialogs: accent text, a soft accent pill while pressed. */
@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Secondary actions (Rescan, Remove): neutral text instead of the accent colour. */
    muted: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            pressed -> accent.copy(alpha = 0.18f)
            hovered -> accent.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "glassTextButton",
    )
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/** A filter / sort chip: quiet translucent pill; accent-tinted when [selected] (an active filter). */
@Composable
fun GlassChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val accent = scheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val tintBase = if (selected) accent else ink
    val fill by animateColorAsState(
        tintBase.copy(alpha = if (selected) (if (pressed) 0.30f else if (hovered) 0.24f else 0.18f) else (if (pressed) 0.18f else if (hovered) 0.13f else 0.09f)),
        animationSpec = tween(120),
        label = "glassChip",
    )
    val shape = CircleShape
    Row(
        modifier
            .clip(shape)
            .background(fill, shape)
            .border(GlassTokens.Hairline, tintBase.copy(alpha = if (selected) 0.40f else 0.14f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(start = 14.dp, end = if (trailingIcon != null) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) accent else ink,
            maxLines = 1,
        )
        if (trailingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides (if (selected) accent else scheme.onSurfaceVariant)) { trailingIcon() }
        }
    }
}

/** Text-field colours matching the glass look: a soft translucent fill, no underline, accent cursor. */
@Composable
fun glassTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    return TextFieldDefaults.colors(
        focusedContainerColor = ink.copy(alpha = 0.10f),
        unfocusedContainerColor = ink.copy(alpha = 0.08f),
        disabledContainerColor = ink.copy(alpha = 0.05f),
        errorContainerColor = ink.copy(alpha = 0.08f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = scheme.primary,
    )
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

/**
 * Drop-in for AlertDialog with the glass look: a floating glass card over a dimmed, blurred app.
 * On Android 12+ the window itself blurs what is behind it (when the device allows it); elsewhere the
 * dim alone is used. Tapping outside the card dismisses, like the platform dialog.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = rememberGlassPalette()
    val shape = RoundedCornerShape(GlassTokens.DialogRadius)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "glassDialogIn")
    val outside = remember { MutableInteractionSource() }
    val inside = remember { MutableInteractionSource() }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlurBehindDialogWindow()
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .clickable(interactionSource = outside, indication = null, onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.94f + 0.06f * progress
                        scaleY = 0.94f + 0.06f * progress
                    }
                    .shadow(24.dp, shape)
                    .glassPanel(palette, shape)
                    .clickable(interactionSource = inside, indication = null, onClick = {})
                    .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
            ) {
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) {
                            Box(Modifier.padding(end = 8.dp)) { title() }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Box(Modifier.padding(end = 8.dp)) { text() }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** Dims the app behind a dialog window and, on Android 12+, asks the system to blur it as well. */
@Composable
private fun BlurBehindDialogWindow(blurRadius: Dp = 24.dp, dim: Float = 0.30f) {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { blurRadius.roundToPx() }
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(dim)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = window.attributes
                params.blurBehindRadius = radiusPx
                window.attributes = params
            }
        }
    }
}
