package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.ui.actions.ActionShortcut
import app.luxion.shogunai.ui.actions.catalogFor
import app.luxion.shogunai.ui.actions.menuBarEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorktreeMenuShortcutsTest {

    private val otherMenuShortcuts = listOf(
        ActionShortcut("N"),
        ActionShortcut("S"),
        ActionShortcut("N", shift = true),
        ActionShortcut(","),
        ActionShortcut("Q"),
        ActionShortcut("1"),
        ActionShortcut("2"),
        ActionShortcut("F"),
        ActionShortcut("K"),
        ActionShortcut("R"),
        ActionShortcut("\\"),
        ActionShortcut("E"),
    )

    @Test
    fun `the worktree menu's shortcuts are open, open with, copy and delete, and collide with nothing`() {
        val shortcuts = catalogFor().menuBarEntries().map { assertNotNull(it.shortcut) }

        assertEquals(listOf(ActionShortcut("O"), ActionShortcut("O", shift = true), ActionShortcut("C", shift = true), ActionShortcut("⌫")), shortcuts)
        assertEquals(shortcuts.size, shortcuts.toSet().size)
        assertTrue(shortcuts.none { it in otherMenuShortcuts })
        shortcuts.forEach { assertNotNull(keyShortcut(it)) }
    }
}
