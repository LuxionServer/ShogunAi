package app.luxion.shogunai.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.luxion.shogunai.ui.isMacOs

data class KeyPress(
    val key: Key,
    val type: KeyEventType,
    val meta: Boolean = false,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

fun KeyEvent.toKeyPress(): KeyPress = KeyPress(key, type, isMetaPressed, isCtrlPressed, isShiftPressed, isAltPressed)

fun isCloseWindowShortcut(press: KeyPress, mac: Boolean = isMacOs): Boolean {
    val command = if (mac) press.meta && !press.ctrl else press.ctrl && !press.meta
    return press.type == KeyEventType.KeyDown && press.key == Key.W && command && !press.shift && !press.alt
}
