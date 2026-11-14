package app.luxion.shogunai.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowShortcutsTest {

    @Test
    fun `cmd w closes the window on macos`() {
        assertTrue(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, meta = true), mac = true))
    }

    @Test
    fun `ctrl w closes the window elsewhere`() {
        assertTrue(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, ctrl = true), mac = false))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, meta = true), mac = false))
    }

    @Test
    fun `w without the command key, with other modifiers or on release doesn't close`() {
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown), mac = true))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, meta = true, shift = true), mac = true))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, meta = true, alt = true), mac = true))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyUp, meta = true), mac = true))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.Q, KeyEventType.KeyDown, meta = true), mac = true))
    }

    @Test
    fun `ctrl and cmd together don't close the window`() {
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, ctrl = true, meta = true), mac = true))
        assertFalse(isCloseWindowShortcut(KeyPress(Key.W, KeyEventType.KeyDown, ctrl = true, meta = true), mac = false))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a key event becomes a key press with its modifiers`() = runComposeUiTest {
        val presses = mutableListOf<KeyPress>()
        setContent {
            Box(Modifier.size(10.dp).testTag("target").onPreviewKeyEvent { presses += it.toKeyPress(); false }.focusable())
        }
        onNodeWithTag("target").requestFocus()

        onNodeWithTag("target").performKeyInput {
            keyDown(Key.MetaLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.W)
        }

        assertTrue(KeyPress(Key.W, KeyEventType.KeyDown, meta = true, shift = true) in presses)
        assertFalse(isCloseWindowShortcut(presses.last { it.key == Key.W }))
    }
}
