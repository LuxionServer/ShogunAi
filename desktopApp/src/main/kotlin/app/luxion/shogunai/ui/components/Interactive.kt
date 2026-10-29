package app.luxion.shogunai.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.RowShape
import app.luxion.shogunai.ui.theme.ShogunTheme

val FocusRingWidth = 2.dp
private const val PointerFocusWindowMillis = 500L
val InteractiveShape: Shape = RowShape

fun Modifier.focusRing(shape: Shape = InteractiveShape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var focusedByPointer by remember { mutableStateOf(false) }
    var lastPressMillis by remember { mutableStateOf(0L) }
    val ring = if (focused && !focusedByPointer) {
        Modifier.border(FocusRingWidth, ShogunTheme.colors.accent, shape)
    } else {
        Modifier
    }
    then(ring)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                        lastPressMillis = System.currentTimeMillis()
                    }
                }
            }
        }
        .onFocusChanged {
            if (it.isFocused && !focused) {
                focusedByPointer = System.currentTimeMillis() - lastPressMillis < PointerFocusWindowMillis
            }
            focused = it.isFocused
        }
}

fun Modifier.handPointer(enabled: Boolean = true): Modifier =
    if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

fun Modifier.interactive(
    enabled: Boolean = true,
    shape: Shape = InteractiveShape,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    focusRing(shape)
        .clip(shape)
        .handPointer(enabled)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
}
