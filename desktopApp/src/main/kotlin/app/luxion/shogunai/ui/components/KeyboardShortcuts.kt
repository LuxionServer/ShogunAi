package app.luxion.shogunai.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Lets keyboard-only users trigger a nearby action button by pressing Enter in this field. */
fun Modifier.onEnterKey(enabled: Boolean = true, action: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (enabled && event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
            action()
            true
        } else {
            false
        }
    }
