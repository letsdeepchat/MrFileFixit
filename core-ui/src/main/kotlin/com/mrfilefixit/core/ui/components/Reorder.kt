package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.sign

/**
 * Drag-to-reorder for a LazyColumn. Items are found by key, so [keys] must be the reorderable items'
 * stable keys in list order. Each swap is reported through `onMove(from, to)` as it happens, so the
 * ViewModel stays the single source of truth. Pair with move-up/down semantics actions for TalkBack.
 */
@Stable
class ListReorderState internal constructor(
    private val listState: LazyListState,
    private val keys: State<List<Any>>,
    private val onMove: State<(Int, Int) -> Unit>,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        offsetY = 0f
    }

    fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        offsetY += deltaY
        val order = keys.value
        val index = order.indexOf(key)
        val targetIndex = when {
            offsetY > 0 && index in 0 until order.lastIndex -> index + 1
            offsetY < 0 && index > 0 -> index - 1
            else -> return
        }
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == order[targetIndex] } ?: return
        if (abs(offsetY) > target.size / 2f) {
            // The dragged item's slot moves by the target's height; keep it under the finger.
            offsetY -= sign(offsetY) * target.size
            onMove.value(index, targetIndex)
        }
    }

    fun end() {
        draggingKey = null
        offsetY = 0f
    }
}

@Composable
fun rememberListReorderState(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (Int, Int) -> Unit,
): ListReorderState {
    val currentKeys = rememberUpdatedState(keys)
    val currentOnMove = rememberUpdatedState(onMove)
    return remember(listState) { ListReorderState(listState, currentKeys, currentOnMove) }
}

/** Lifts the item being dragged and moves it with the finger. */
fun Modifier.reorderableItem(state: ListReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offsetY else 0f
            shadowElevation = if (dragging) LIFT_ELEVATION else 0f
        }
}

/** Put on the drag handle of the item with [key]. */
fun Modifier.reorderHandle(state: ListReorderState, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}

/**
 * Long-press-and-drag reordering for a LazyVerticalGrid. While dragging, the item under the finger
 * swaps places with the dragged one.
 */
@Stable
class GridReorderState internal constructor(
    private val gridState: LazyGridState,
    private val keys: State<List<Any>>,
    private val onMove: State<(Int, Int) -> Unit>,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    fun start(key: Any) {
        draggingKey = key
    }

    /** [local] is the pointer position inside the dragged item. */
    fun drag(local: Offset) {
        val key = draggingKey ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        val x = current.offset.x + local.x
        val y = current.offset.y + local.y
        val target = visible.firstOrNull { item ->
            item.key != key && item.key in keys.value && item.contains(x, y)
        } ?: return
        val order = keys.value
        onMove.value(order.indexOf(key), order.indexOf(target.key))
    }

    fun end() {
        draggingKey = null
    }
}

private fun LazyGridItemInfo.contains(x: Float, y: Float): Boolean =
    x >= offset.x && x < offset.x + size.width && y >= offset.y && y < offset.y + size.height

@Composable
fun rememberGridReorderState(
    gridState: LazyGridState,
    keys: List<Any>,
    onMove: (Int, Int) -> Unit,
): GridReorderState {
    val currentKeys = rememberUpdatedState(keys)
    val currentOnMove = rememberUpdatedState(onMove)
    return remember(gridState) { GridReorderState(gridState, currentKeys, currentOnMove) }
}

/** Long-press to pick the item up, then drag it over another cell to swap. */
fun Modifier.gridReorderable(state: GridReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            val scale = if (dragging) LIFT_SCALE else 1f
            scaleX = scale
            scaleY = scale
            shadowElevation = if (dragging) LIFT_ELEVATION else 0f
        }
        .pointerInput(state, key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
                onDrag = { change, _ ->
                    change.consume()
                    state.drag(change.position)
                },
            )
        }
}

private const val LIFT_ELEVATION = 16f
private const val LIFT_SCALE = 1.06f
