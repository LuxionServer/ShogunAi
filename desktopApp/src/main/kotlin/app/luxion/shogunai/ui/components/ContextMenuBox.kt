package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round

@Composable
fun ContextMenuBox(
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var position by remember { mutableStateOf<IntOffset?>(null) }
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        position = event.changes.first().position.round()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        content()
        position?.let { at ->
            ShogunMenu(expanded = true, onDismiss = { position = null }, entries = entries(), offset = at)
        }
    }
}
