package com.auralis.player.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.player.R

/** Width of the index strip; lists reserve this much space at their end so rows never sit under it. */
val AlphabetIndexWidth = 28.dp

private val MinLetterHeight = 14.dp
private val MaxLetterHeight = 20.dp

/**
 * A list with the A-Z index along its end edge (the right edge in left-to-right layouts).
 * [content] receives the end padding to give the list so its rows stay clear of the strip, which
 * is 0 when [index] is null (the current sort is not alphabetical, or the list is empty).
 *
 * [firstVisibleItem] is read to highlight the letter of the item at the top of the list;
 * [onJump] scrolls the list to a position (animated for a plain tap, instant while dragging).
 */
@Composable
fun IndexedListBox(
    index: AlphabetIndex?,
    firstVisibleItem: () -> Int,
    onJump: (position: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (endPadding: Dp) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content(if (index != null) AlphabetIndexWidth else 0.dp)
        if (index != null) {
            val latestFirstVisible by rememberUpdatedState(firstVisibleItem)
            val activeLetter by remember(index) { derivedStateOf { index.letterAt(latestFirstVisible()) } }
            AlphabetIndexBar(
                index = index,
                activeLetter = activeLetter,
                onJump = onJump,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * The vertical A-Z strip. Tap or drag along it to jump; letters with no entries are dimmed and do
 * nothing, the letter of the item at the top of the list is highlighted, and while a finger is down
 * the current letter is shown in a bubble beside the strip. On short screens neighbouring letters
 * share a row (see [indexGroups]). TalkBack gets one "Jump to X" action per available letter.
 */
@Composable
fun AlphabetIndexBar(
    index: AlphabetIndex,
    activeLetter: Char?,
    onJump: (position: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxHeight().width(AlphabetIndexWidth), contentAlignment = Alignment.Center) {
        val groups = remember(maxHeight) { indexGroups((maxHeight / MinLetterHeight).toInt()) }
        if (groups.isEmpty()) return@BoxWithConstraints

        val rowHeight = minOf(maxHeight / groups.size, MaxLetterHeight)
        val barHeight = rowHeight * groups.size
        val latestJump by rememberUpdatedState(onJump)

        var touching by remember { mutableStateOf(false) }
        var bubbleLetter by remember { mutableStateOf(' ') }

        val description = stringResource(R.string.lib_index_description)
        val jumpActions = groups.mapNotNull { group ->
            val letter = index.firstAvailableIn(group) ?: return@mapNotNull null
            val position = index.firstPositionOf(letter) ?: return@mapNotNull null
            CustomAccessibilityAction(stringResource(R.string.lib_index_jump_to, letter.toString())) {
                latestJump(position, true)
                true
            }
        }

        Column(
            Modifier
                .height(barHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AlphabetIndexWidth / 2))
                .background(
                    if (touching) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .semantics {
                    contentDescription = description
                    customActions = jumpActions
                }
                .pointerInput(groups, index) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var lastPosition = -1

                        // Resolves a touch height to a letter and jumps to it (once per distinct target).
                        fun select(y: Float, animate: Boolean) {
                            val rowPx = size.height / groups.size.toFloat()
                            val group = groups[(y / rowPx).toInt().coerceIn(0, groups.lastIndex)]
                            val letter = index.firstAvailableIn(group) ?: return
                            val position = index.firstPositionOf(letter) ?: return
                            bubbleLetter = letter
                            if (position != lastPosition) {
                                lastPosition = position
                                latestJump(position, animate)
                            }
                        }

                        touching = true
                        try {
                            select(down.position.y, animate = true)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                select(change.position.y, animate = false)
                                change.consume()
                            }
                        } finally {
                            // Also runs when the gesture is cancelled (the list changed under the finger).
                            touching = false
                        }
                    }
                },
        ) {
            groups.forEach { group ->
                val letter = group.first()
                val available = index.firstAvailableIn(group) != null
                val active = activeLetter != null && activeLetter in group
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        letter.toString(),
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        color = when {
                            active -> MaterialTheme.colorScheme.primary
                            available -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        },
                    )
                }
            }
        }

        // The bubble sits beside the strip, over the list; offset() mirrors in right-to-left layouts.
        AnimatedVisibility(
            visible = touching,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.offset(x = -(AlphabetIndexWidth + 40.dp)),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    bubbleLetter.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
