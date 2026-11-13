package com.auralis.player.ui.screens.playlists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-and-drop reordering for the track rows of a LazyColumn.
 *
 * The dragged row follows the finger through [dragOffset] while every other row stays in the list's own
 * layout; whenever the dragged row's centre crosses into another row's slot, [onMove] is called with the
 * two track positions (relative to the first track row) so the caller can swap them in its own list.
 * [onDragEnd] runs once, when the finger lifts, and is where the new order is saved.
 *
 * @param firstTrackIndex the LazyColumn index of the first track row (the rows before it are headers).
 */
@Stable
internal class ReorderState(
    private val listState: LazyListState,
    private val firstTrackIndex: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) {
    /** Key of the row being dragged, or null when nothing is. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /** How far, in pixels, the dragged row is displaced from where the list lays it out. */
    var dragOffset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggedKey != null

    fun start(key: Any) {
        draggedKey = key
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        if (draggedKey == null) return
        dragOffset += deltaY
        swapIfNeeded()
    }

    fun end() {
        if (draggedKey == null) return
        draggedKey = null
        dragOffset = 0f
        onDragEnd()
    }

    /** Pixels to scroll this frame while the dragged row is near the top or bottom edge; 0 elsewhere. */
    fun autoScrollAmount(): Float {
        val key = draggedKey ?: return 0f
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val center = item.offset + item.size / 2f + dragOffset
        val zone = item.size.toFloat().coerceAtLeast(1f)
        val top = info.viewportStartOffset + zone
        val bottom = info.viewportEndOffset - zone
        return when {
            center < top -> -((top - center) / zone).coerceIn(0f, 1f) * MAX_SCROLL_PER_FRAME
            center > bottom -> ((center - bottom) / zone).coerceIn(0f, 1f) * MAX_SCROLL_PER_FRAME
            else -> 0f
        }
    }

    /** Called after the list was scrolled by [pixels] to keep the dragged row under the finger. */
    fun scrolledBy(pixels: Float) {
        if (draggedKey == null) return
        dragOffset += pixels
        swapIfNeeded()
    }

    private fun swapIfNeeded() {
        val key = draggedKey ?: return
        val firstTrack = firstTrackIndex()
        val rows = listState.layoutInfo.visibleItemsInfo
        val dragged = rows.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val target = rows.firstOrNull { row ->
            row.index != dragged.index && row.index >= firstTrack &&
                center >= row.offset && center <= row.offset + row.size
        } ?: return
        onMove(dragged.index - firstTrack, target.index - firstTrack)
        // The dragged row is now laid out in the target's slot; shift the offset so it stays under the finger.
        dragOffset += (dragged.offset - target.offset)
    }

    private companion object {
        const val MAX_SCROLL_PER_FRAME = 26f
    }
}
