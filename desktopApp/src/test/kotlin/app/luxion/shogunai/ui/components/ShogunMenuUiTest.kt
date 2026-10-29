package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShogunMenuUiTest {

    private val ran = mutableListOf<String>()

    private fun entries() = listOf(
        MenuEntry.Item("Open in Herdr", { ran += "herdr" }, shortcut = "⌘O", mark = "default"),
        MenuEntry.Submenu(
            "Open in Orca",
            listOf(
                MenuEntry.Item("Claude", { ran += "orca:Claude" }, checked = true),
                MenuEntry.Item("Codex", { ran += "orca:Codex" }),
            ),
            onClick = { ran += "orca" },
        ),
        MenuEntry.Section("With agent"),
        MenuEntry.Item("Unavailable", { ran += "unavailable" }, disabledReason = "The worktree's folder is missing"),
        MenuEntry.Separator,
        MenuEntry.Item("Delete worktree…", { ran += "delete" }, danger = true),
    )

    @Test
    fun `the keyboard moves into a submenu and runs its item`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent { MaterialTheme { ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries(), focusFirst = true) } }

        onNodeWithText("Open in Herdr").performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithText("Open in Herdr").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Codex").assertIsDisplayed()
        onNode(hasText("Claude") and isSelected()).assertIsDisplayed()
        onNodeWithText("Claude").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(listOf("orca:Claude"), ran)
        assertFalse(expanded)
    }

    @Test
    fun `escape closes the submenu, then the menu, without running anything`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent { MaterialTheme { ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries(), focusFirst = true) } }

        onNodeWithText("Open in Herdr").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
        }
        onNodeWithText("Codex").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithText("Codex").assertDoesNotExist()
        assertTrue(expanded)

        onNodeWithText("Open in Herdr").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertFalse(expanded)
        assertTrue(ran.isEmpty())
    }

    @Test
    fun `arrows skip sections, separators and disabled items`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent { MaterialTheme { ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries(), focusFirst = true) } }

        onNodeWithText("Open in Herdr").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals(listOf("delete"), ran)
    }

    @Test
    fun `a disabled item explains why and doesn't run`() = runComposeUiTest {
        setContent { MaterialTheme { ShogunMenu(expanded = true, onDismiss = {}, entries = entries()) } }

        onNode(
            hasText("Unavailable") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "The worktree's folder is missing"),
        ).assertExists()
        onNodeWithText("Unavailable").performClick()

        assertTrue(ran.isEmpty())
    }

    @Test
    fun `a submenu entry that is an action runs on click`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent { MaterialTheme { ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries()) } }

        onNodeWithText("Open in Orca").performClick()
        waitForIdle()

        assertEquals(listOf("orca"), ran)
        assertFalse(expanded)
    }

    @Test
    fun `the window's escape closes the topmost menu first`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent { MaterialTheme { ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries()) } }
        onNodeWithText("Open in Orca").performMouseInput { moveTo(center) }
        onNodeWithText("Codex").assertIsDisplayed()

        assertTrue(OpenMenus.handleEscape(now = Long.MAX_VALUE))
        waitForIdle()
        onNodeWithText("Codex").assertDoesNotExist()
        assertTrue(expanded)

        assertTrue(OpenMenus.handleEscape(now = Long.MAX_VALUE))
        waitForIdle()
        assertFalse(expanded)
    }
}
