package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex

class SidebarDragState {
    var keys: List<String> = emptyList()
    var spacing: Float = 0f
    var draggedKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private val tops = mutableStateMapOf<String, Float>()
    private val heights = mutableStateMapOf<String, Float>()

    fun place(key: String, top: Float, height: Float) {
        tops[key] = top
        heights[key] = height
    }

    fun start(key: String) {
        draggedKey = key
        offset = 0f
    }

    fun drag(delta: Float) {
        offset += delta
    }

    fun stop() {
        draggedKey = null
        offset = 0f
    }

    fun targetIndex(): Int {
        val dragged = draggedKey ?: return -1
        val top = tops[dragged] ?: return keys.indexOf(dragged)
        val leadingEdge = top + offset + if (offset > 0) heights[dragged].orZero() else 0f
        return keys.filter { it != dragged }.count { other -> tops[other].orZero() + heights[other].orZero() / 2 < leadingEdge }
    }

    fun shiftOf(key: String): Float {
        val dragged = draggedKey ?: return 0f
        if (key == dragged) return offset
        val from = keys.indexOf(dragged)
        val index = keys.indexOf(key)
        if (from < 0 || index < 0) return 0f
        val target = targetIndex()
        val rest = if (index < from) index else index - 1
        val moved = if (rest < target) rest else rest + 1
        val pitch = heights[dragged].orZero() + spacing
        return when {
            moved < index -> -pitch
            moved > index -> pitch
            else -> 0f
        }
    }

    private fun Float?.orZero(): Float = this ?: 0f
}

@Composable
fun Modifier.reorderable(state: SidebarDragState, key: String, onMove: (String, Int) -> Unit): Modifier {
    val currentOnMove by rememberUpdatedState(onMove)
    val dragging = state.draggedKey == key
    return this
        .onGloballyPositioned { state.place(key, it.positionInParent().y, it.size.height.toFloat()) }
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = state.shiftOf(key) }
        .alpha(if (dragging) DraggedAlpha else 1f)
        .pointerInput(key) {
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = {
                    val target = state.targetIndex()
                    state.stop()
                    if (target >= 0) currentOnMove(key, target)
                },
                onDragCancel = { state.stop() },
            )
        }
}

private const val DraggedAlpha = 0.85f
