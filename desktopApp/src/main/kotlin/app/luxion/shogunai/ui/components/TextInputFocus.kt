package app.luxion.shogunai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

object TextInputFocus {
    private var focusedFields by mutableIntStateOf(0)

    val isActive: Boolean
        get() = focusedFields > 0

    fun enter() {
        focusedFields++
    }

    fun leave() {
        focusedFields = (focusedFields - 1).coerceAtLeast(0)
    }
}

@Composable
fun Modifier.trackTextInputFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(focused) {
        val entered = focused
        if (entered) TextInputFocus.enter()
        onDispose { if (entered) TextInputFocus.leave() }
    }
    return onFocusChanged { focused = it.isFocused }
}
