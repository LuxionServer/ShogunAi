package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClaudeHooksUiTest {

    private val path = "/home/dev/.claude/settings.json"
    private val files = FakeFileManager()
    private var dismissed = false
    private var dismissals = 0

    private fun state(shogunOnPath: Boolean = true) = ClaudeHooksState(
        hooks = ClaudeCodeHooks(files),
        settingsPath = { path },
        isShogunOnPath = { shogunOnPath },
        noticeDismissedStore = { dismissed },
        rememberNoticeDismissed = {
            dismissed = true
            dismissals++
        },
    ).also { runBlocking { it.refresh() } }

    @Test
    fun `installing from Settings shows what changes and writes after confirming`() = runComposeUiTest {
        val state = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state) } }
            }
        }

        onNodeWithText("ShogunAi's hooks aren't installed").assertIsDisplayed()
        onNodeWithText("Install").performClick()
        onNodeWithText("Add to Stop: shogun hook agent-state").assertIsDisplayed()
        assertTrue(files.writtenTexts.isEmpty())
        onNodeWithText("Apply").performClick()

        onNodeWithText("ShogunAi's hooks are installed").assertIsDisplayed()
        assertTrue(files.writtenTexts.getValue(path).contains("shogun hook agent-state"))
        onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun `cancelling the preview writes nothing`() = runComposeUiTest {
        val state = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state) } }
            }
        }

        onNodeWithText("Install").performClick()
        onNodeWithText("Cancel").performClick()

        assertTrue(files.writtenTexts.isEmpty())
        onNodeWithText("ShogunAi's hooks aren't installed").assertIsDisplayed()
    }

    @Test
    fun `hooks from an older version show as outdated with an Update action`() = runComposeUiTest {
        files.writeText(path, """{"hooks":{"SessionStart":[{"hooks":[{"type":"command","command":"shogun hook session-start"}]}]}}""")
        val state = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state) } }
            }
        }

        onNodeWithText("ShogunAi's hooks are out of date").assertIsDisplayed()
        onNodeWithText("Update").performClick()
        onNodeWithText("Apply").performClick()

        onNodeWithText("ShogunAi's hooks are installed").assertIsDisplayed()
    }

    @Test
    fun `removing deletes the hooks after confirming`() = runComposeUiTest {
        ClaudeCodeHooks(files).install(path)
        val state = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state) } }
            }
        }

        onNodeWithText("Remove").performClick()
        onNodeWithText("Apply").performClick()

        onNodeWithText("ShogunAi's hooks aren't installed").assertIsDisplayed()
        assertFalse(files.writtenTexts.getValue(path).contains("shogun hook"))
    }

    @Test
    fun `warns when shogun is not on the PATH`() = runComposeUiTest {
        val state = state(shogunOnPath = false)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state) } }
            }
        }

        onNodeWithText(EnglishStrings.claudeHooksPathWarning).assertIsDisplayed()
    }

    @Test
    fun `the notice is suggested once and dismissing it is remembered`() = runComposeUiTest {
        val first = state()
        var claudeWithoutState = true
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksNotice(first, claudeWithoutState) } }
            }
        }

        onNodeWithText(EnglishStrings.claudeHooksNoticeMessage).assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()

        assertEquals(1, dismissals)
        assertTrue(dismissed)
        assertFalse(state().suggestsInstalling)
    }

    @Test
    fun `the notice stays hidden when no Claude agent lacks a state or the hooks are installed`() = runComposeUiTest {
        val missing = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksNotice(missing, claudeWithoutState = false) } }
            }
        }
        onNodeWithText(EnglishStrings.claudeHooksNoticeMessage).assertDoesNotExist()

        ClaudeCodeHooks(files).install(path)
        assertFalse(state().suggestsInstalling)
    }
}
