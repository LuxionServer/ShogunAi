package app.luxion.shogunai.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
}
