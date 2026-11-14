package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HooksInstallState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.domain.usecase.CommandLineToolError
import app.luxion.shogunai.domain.model.CommandLineToolState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeHooksStateTest {

    private val path = "/home/dev/.claude/settings.json"
    private val appLauncher = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
    private val files = FakeFileManager()
    private val launcherFiles = FakeLauncherFiles()

    private fun tool(found: String? = "/usr/local/bin/shogun", appLauncher: String? = this.appLauncher) = CommandLineTool(
        os = HostOs.MACOS,
        appLauncher = appLauncher,
        home = "/home/dev",
        pathFolders = { listOf("/usr/bin", "/usr/local/bin") },
        userBinFolders = listOf("/usr/local/bin"),
        findShogun = { found },
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
        noticeDismissedStore = { false },
        rememberNoticeDismissed = {},
    )

    private fun installedByApp() {
        launcherFiles.links["/usr/local/bin/shogun"] = appLauncher
        launcherFiles.others += appLauncher
    }

    @Test
    fun `before reading anything the tool counts as ready and nothing is suggested`() {
        val state = state()

        assertTrue(state.toolReady)
        assertFalse(state.toolNeedsAttention)
        assertFalse(state.suggestsInstalling)
        assertFalse(state.canRemove)
        assertNull(state.installAction)
    }

    @Test
    fun `settings that can't be read leave no status and show the error, which can be dismissed`() = runBlocking<Unit> {
        files.writtenTexts[path] = "{ not json"
        val state = state()

        state.refresh()

        assertNull(state.status)
        assertNotNull(state.error)
        state.dismissError()
        assertNull(state.error)
    }

    @Test
    fun `installing the tool in a build that isn't an installed app fails and can be dismissed`() = runBlocking<Unit> {
        val state = state(tool(found = null, appLauncher = null))

        state.installTool()

        assertNull(state.toolInstallation)
        assertIs<CommandLineToolError.NotPackaged>(state.toolError)
        assertEquals(CommandLineToolState.MISSING, state.toolStatus?.state)
        state.dismissToolError()
        assertNull(state.toolError)
    }

    @Test
    fun `removing the tool the app installed deletes its link`() = runBlocking<Unit> {
        installedByApp()
        val state = state(tool(found = null))
        state.refresh()
        assertTrue(state.toolStatus!!.installedByApp)

        state.removeTool()

        assertNull(state.toolInstallation)
        assertNull(state.toolError)
        assertEquals(CommandLineToolState.MISSING, state.toolStatus?.state)
        assertTrue(launcherFiles.links.isEmpty())
    }

    @Test
    fun `removing the hooks is offered even when the tool isn't ready`() = runBlocking<Unit> {
        val state = state(tool(found = null))
        state.refresh()
        assertFalse(state.canChangeHooks)

        state.request(HooksAction.INSTALL)
        assertNull(state.pending)
        state.request(HooksAction.REMOVE)

        assertEquals(HooksAction.REMOVE, state.pending?.action)
    }

    @Test
    fun `a preview of settings that can't be read shows the error instead`() = runBlocking<Unit> {
        val state = state()
        state.refresh()
        files.writtenTexts[path] = "{ not json"

        state.request(HooksAction.INSTALL)

        assertNull(state.pending)
        assertNotNull(state.error)
    }

    @Test
    fun `confirming with nothing pending does nothing`() = runBlocking<Unit> {
        val state = state()
        state.refresh()

        state.confirm()

        assertEquals(HooksInstallState.MISSING, state.status?.state)
        assertTrue(files.writtenTexts.isEmpty())
    }

    @Test
    fun `a change that fails to apply shows the error and keeps the last status read`() = runBlocking<Unit> {
        val state = state()
        state.refresh()
        state.request(HooksAction.INSTALL)
        files.writtenTexts[path] = "{ not json"

        state.confirm()

        assertNotNull(state.error)
        assertEquals(HooksInstallState.MISSING, state.status?.state)
        assertNull(state.pending)
    }
}
