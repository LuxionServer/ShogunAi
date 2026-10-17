package app.luxion.shogunai.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.luxion.shogunai.ui.isMacOs

fun Modifier.onEnterKey(enabled: Boolean = true, action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (enabled && event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            action()
            true
        } else {
            false
        }
    }

fun Modifier.onNewItemShortcut(enabled: Boolean = true, action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val shortcutModifierPressed = if (isMacOs) event.isMetaPressed else event.isCtrlPressed
        if (enabled && event.type == KeyEventType.KeyDown && event.key == Key.N && shortcutModifierPressed && !event.isShiftPressed) {
            action()
            true
        } else {
            false
        }
    }
