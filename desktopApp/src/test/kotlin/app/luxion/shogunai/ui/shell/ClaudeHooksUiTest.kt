package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.CommandLineTool
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

    private val launcherFiles = FakeLauncherFiles()

    private fun tool(
        found: String? = "/usr/local/bin/shogun",
        appLauncher: String? = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi",
    ) = CommandLineTool(
        os = HostOs.MACOS,
        appLauncher = appLauncher,
        home = "/home/dev",
        pathFolders = { listOf("/usr/bin", "/usr/local/bin") },
        userBinFolders = listOf("/usr/local/bin", "/home/dev/.local/bin"),
        findShogun = { found ?: launcherFiles.links.keys.firstOrNull() },
        files = launcherFiles,
        userPath = object : UserPathEntries {
            override suspend fun contains(folder: String) = false
            override suspend fun add(folder: String) = Unit
            override suspend fun remove(folder: String) = Unit
        },
    )

    private fun state(tool: CommandLineTool = tool()) = ClaudeHooksState(
        hooks = ClaudeCodeHooks(files),
        settingsPath = { path },
        tool = tool,
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
    fun `with the tool missing Install is disabled and the tool can be installed first`() = runComposeUiTest {
        launcherFiles.writable += "/usr/local/bin"
        launcherFiles.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        val state = state(tool(found = null))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state, HostOs.MACOS) } }
            }
        }

        onNodeWithText(EnglishStrings.commandLineToolNeededByHooks).assertIsDisplayed()
        onNodeWithText(EnglishStrings.claudeHooksNeedsTool).assertIsDisplayed()
        onNodeWithText("Install").assertIsNotEnabled()
        onNodeWithText(EnglishStrings.commandLineToolInstall).performClick()
        waitForIdle()

        onNodeWithText(EnglishStrings.commandLineToolStatus(state.toolStatus!!)).assertIsDisplayed()
        onNodeWithText("Install").assertIsEnabled()
        assertEquals(1, launcherFiles.links.size)
    }

    @Test
    fun `the tool is available so there is nothing to install`() = runComposeUiTest {
        val state = state()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state, HostOs.MACOS) } }
            }
        }

        onNodeWithText("Install").assertIsEnabled()
        onNodeWithText(EnglishStrings.commandLineToolInstall).assertDoesNotExist()
        onNodeWithText(EnglishStrings.commandLineToolNeededByHooks).assertDoesNotExist()
    }

    @Test
    fun `a tool outside the PATH shows the line to add and keeps Install disabled`() = runComposeUiTest {
        launcherFiles.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        launcherFiles.links["/home/dev/.local/bin/shogun"] = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        val state = state(tool(found = null))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state, HostOs.MACOS) } }
            }
        }

        onNodeWithText("export PATH=\"/home/dev/.local/bin:\$PATH\"", substring = true).assertIsDisplayed()
        onNodeWithText("Install").assertIsNotEnabled()
    }

    @Test
    fun `a build that is not an installed app shows the manual steps`() = runComposeUiTest {
        val state = state(tool(found = null, appLauncher = null))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksSection(state, HostOs.MACOS) } }
            }
        }

        onNodeWithText(EnglishStrings.commandLineToolManualSteps(HostOs.MACOS)).assertIsDisplayed()
        onNodeWithText(EnglishStrings.commandLineToolInstall).assertDoesNotExist()
    }

    @Test
    fun `the notice names the tool as a prerequisite and its action installs the tool`() = runComposeUiTest {
        launcherFiles.writable += "/usr/local/bin"
        launcherFiles.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        val state = state(tool(found = null))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ClaudeHooksNotice(state, claudeWithoutState = true, os = HostOs.MACOS) } }
            }
        }

        onNodeWithText(EnglishStrings.claudeHooksNoticeToolMessage).assertIsDisplayed()
        onNodeWithText("Install").assertIsNotEnabled()
        onNodeWithText(EnglishStrings.commandLineToolInstall).performClick()
        waitForIdle()

        onNodeWithText(EnglishStrings.claudeHooksNoticeToolMessage).assertDoesNotExist()
        onNodeWithText(EnglishStrings.claudeHooksNoticeMessage).assertIsDisplayed()
        onNodeWithText("Install").assertIsEnabled()
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
