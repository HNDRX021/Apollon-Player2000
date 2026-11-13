package com.auralis.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Small additions to the Liquid Glass kit (Glass.kt). Kept in their own file so Glass.kt stays untouched.
 */

/**
 * The glass icon button for toolbars and headers: same look as [GlassIconButton] but 44 dp, which is a
 * closer match for the 48 dp Material IconButton it replaces (an explicit size in [modifier] still wins).
 */
@Composable
fun GlassToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassIconButton(onClick = onClick, modifier = modifier, size = 44.dp, enabled = enabled, content = content)
}

/**
 * Replaces the Material tab row: one translucent pill with a glass edge, the selected tab as an
 * accent-tinted pill inside it. Same inputs as before (labels, selected index, a callback), so it drops
 * into the Library and Search screens without changing their logic.
 */
@Composable
fun GlassSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberGlassPalette()
    val shape = CircleShape
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(palette.ink.copy(alpha = if (palette.isDark) 0.08f else 0.06f), shape)
            .border(GlassTokens.Hairline, palette.edgeBrush(), shape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val fill by animateColorAsState(
                when {
                    selected -> palette.accent.copy(alpha = if (pressed) 0.30f else 0.22f)
                    pressed -> palette.pressed
                    else -> Color.Transparent
                },
                animationSpec = tween(120),
                label = "glassTabFill",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(fill, shape)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
