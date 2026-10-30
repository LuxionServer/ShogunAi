package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditorLaunchersTest {

    private val path = "/home/dev/projects/worktrees/TASK-8"

    private fun launcher(
        editor: EditorId,
        os: HostOs,
        executor: FakeShellCommandExecutor,
        files: Set<String> = emptySet(),
        xcodeTarget: String = path,
    ) = CommandEditorLauncher(
        editor,
        executor,
        os,
        ExecutableLocator(os, "/home/dev", { listOf("/usr/bin") }, "C:\\L") { it in files },
        XcodeWorkspaceFinder { xcodeTarget },
    )

    @Test
    fun `macOS opens the folder through the bundle identifier`() = runTest {
        val executor = FakeShellCommandExecutor()

        launcher(EditorId.VS_CODE, HostOs.MACOS, executor).open(path).getOrThrow()

        assertEquals(listOf(listOf("open", "-b", "com.microsoft.VSCode", path)), executor.executedCommands)
    }

    @Test
    fun `macOS tries the next bundle identifier of the IDE`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command[2] == "com.jetbrains.intellij") {
                FakeShellCommandExecutor.failure(command, stderr = "Unable to find application")
            } else {
                FakeShellCommandExecutor.success(command)
            }
        }

        launcher(EditorId.INTELLIJ_IDEA, HostOs.MACOS, executor).open(path).getOrThrow()

        assertEquals(
            listOf(
                listOf("open", "-b", "com.jetbrains.intellij", path),
                listOf("open", "-b", "com.jetbrains.intellij.ce", path),
            ),
            executor.executedCommands,
        )
    }

    @Test
    fun `macOS falls back to the launcher script when no bundle opens`() = runTest {
        val scripts = "/home/dev/Library/Application Support/JetBrains/Toolbox/scripts/studio"
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == "open") FakeShellCommandExecutor.failure(command) else FakeShellCommandExecutor.success(command)
        }

        launcher(EditorId.ANDROID_STUDIO, HostOs.MACOS, executor, setOf(scripts)).open(path).getOrThrow()

        assertEquals(listOf(scripts, path), executor.executedCommands.last())
    }

    @Test
    fun `Windows runs the launcher with the folder`() = runTest {
        val executor = FakeShellCommandExecutor()

        launcher(EditorId.VS_CODE, HostOs.WINDOWS, executor, setOf("/usr/bin\\code.cmd")).open("C:\\work\\TASK-8").getOrThrow()

        assertEquals(listOf(listOf("/usr/bin\\code.cmd", "C:\\work\\TASK-8")), executor.executedCommands)
    }

    @Test
    fun `Windows opens with the executable found in the IDE's installer folder`() = runTest {
        val executor = FakeShellCommandExecutor()
        val studio = "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe"
        val locator = ExecutableLocator(
            HostOs.WINDOWS, "/home/dev", { emptyList() }, null,
            listOf("C:\\Program Files"), listOf(".EXE"), { emptyList() }, { it == studio },
        )

        CommandEditorLauncher(EditorId.ANDROID_STUDIO, executor, HostOs.WINDOWS, locator).open("C:\\work\\TASK-8").getOrThrow()

        assertEquals(listOf(listOf(studio, "C:\\work\\TASK-8")), executor.executedCommands)
    }

    @Test
    fun `Linux starts the launcher detached with the folder as an argument`() = runTest {
        val executor = FakeShellCommandExecutor()

        launcher(EditorId.INTELLIJ_IDEA, HostOs.LINUX, executor, setOf("/usr/bin/idea")).open(path).getOrThrow()

        val command = executor.executedCommands.single()
        assertEquals(listOf("sh", "-c"), command.take(2))
        assertEquals(listOf("/usr/bin/idea", path), command.takeLast(2))
        assertTrue(command[2].contains("nohup"))
        assertTrue(!command[2].contains(path))
    }

    @Test
    fun `a missing launcher names the IDE`() = runTest {
        val executor = FakeShellCommandExecutor()

        val error = launcher(EditorId.VS_CODE, HostOs.LINUX, executor).open(path).exceptionOrNull()

        assertIs<WorktreeError.EditorLaunchFailed>(error)
        assertEquals(EditorId.VS_CODE, error.editor)
        assertTrue(error.message.orEmpty().contains("code"))
    }

    @Test
    fun `a failing launcher reports its error output`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "license expired") }

        val error = launcher(EditorId.ANDROID_STUDIO, HostOs.MACOS, executor).open(path).exceptionOrNull()

        assertIs<WorktreeError.EditorLaunchFailed>(error)
        assertTrue(error.message.orEmpty().contains("license expired"))
    }

    @Test
    fun `macOS opens Xcode with the workspace found in the worktree`() = runTest {
        val executor = FakeShellCommandExecutor()
        val workspace = "$path/iosApp/App.xcworkspace"

        launcher(EditorId.XCODE, HostOs.MACOS, executor, xcodeTarget = workspace).open(path).getOrThrow()

        assertEquals(listOf(listOf("open", "-b", "com.apple.dt.Xcode", workspace)), executor.executedCommands)
    }

    @Test
    fun `macOS opens Xcode with the folder when the worktree has no workspace`() = runTest {
        val executor = FakeShellCommandExecutor()

        launcher(EditorId.XCODE, HostOs.MACOS, executor).open(path).getOrThrow()

        assertEquals(listOf(listOf("open", "-b", "com.apple.dt.Xcode", path)), executor.executedCommands)
    }

    @Test
    fun `a failing Xcode launch reports its error output`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "Unable to find application") }

        val error = launcher(EditorId.XCODE, HostOs.MACOS, executor).open(path).exceptionOrNull()

        assertIs<WorktreeError.EditorLaunchFailed>(error)
        assertEquals(EditorId.XCODE, error.editor)
        assertTrue(error.message.orEmpty().contains("Unable to find application"))
    }

    @Test
    fun `Xcode can't be opened outside macOS`() = runTest {
        listOf(HostOs.WINDOWS, HostOs.LINUX).forEach { os ->
            val executor = FakeShellCommandExecutor()

            val error = launcher(EditorId.XCODE, os, executor).open(path).exceptionOrNull()

            assertIs<WorktreeError.EditorLaunchFailed>(error)
            assertEquals(EditorId.XCODE, error.editor)
            assertTrue(executor.executedCommands.isEmpty())
        }
    }

    @Test
    fun `shows the folder with the system's file manager`() = runTest {
        val expected = mapOf(
            HostOs.MACOS to listOf("open", path),
            HostOs.WINDOWS to listOf("explorer.exe", path),
            HostOs.LINUX to listOf("xdg-open", path),
        )
        expected.forEach { (os, command) ->
            val executor = FakeShellCommandExecutor()

            SystemFolderLauncher(executor, os).open(path).getOrThrow()

            assertEquals(listOf(command), executor.executedCommands)
        }
    }

    @Test
    fun `Explorer's exit code 1 isn't a failure but other systems' is`() = runTest {
        val failing = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 1, stderr = "no such folder") }

        SystemFolderLauncher(failing, HostOs.WINDOWS).open(path).getOrThrow()
        val error = SystemFolderLauncher(failing, HostOs.MACOS).open(path).exceptionOrNull()

        assertIs<WorktreeError.FolderOpenFailed>(error)
        assertTrue(error.message.orEmpty().contains("no such folder"))
    }

    @Test
    fun `macOS opens a new Claude Desktop session with the encoded folder`() = runTest {
        val executor = FakeShellCommandExecutor()

        MacClaudeDesktopLauncher(executor, HostOs.MACOS).open(path).getOrThrow()

        assertEquals(
            listOf(listOf("open", "claude://code/new?folder=%2Fhome%2Fdev%2Fprojects%2Fworktrees%2FTASK-8&source=shogunai")),
            executor.executedCommands,
        )
    }

    @Test
    fun `Claude Desktop link encodes spaces and non-ASCII characters`() {
        assertEquals(
            "claude://code/new?folder=%2Fhome%2Fdev%2Fmy%20projects%2Fcaf%C3%A9&source=shogunai",
            MacClaudeDesktopLauncher.link("/home/dev/my projects/café"),
        )
    }

    @Test
    fun `a failing Claude Desktop launch reports its error output`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "no application knows how to open the URL") }

        val error = MacClaudeDesktopLauncher(executor, HostOs.MACOS).open(path).exceptionOrNull()

        assertIs<WorktreeError.ClaudeDesktopLaunchFailed>(error)
        assertTrue(error.message.orEmpty().contains("no application knows how to open the URL"))
    }

    @Test
    fun `Claude Desktop isn't launched on other systems`() = runTest {
        for (os in listOf(HostOs.WINDOWS, HostOs.LINUX)) {
            val executor = FakeShellCommandExecutor()

            val error = MacClaudeDesktopLauncher(executor, os).open(path).exceptionOrNull()

            assertIs<WorktreeError.ClaudeDesktopLaunchFailed>(error)
            assertTrue(executor.executedCommands.isEmpty())
        }
    }

    @Test
    fun `on Windows other exit codes and a missing Explorer are failures`() = runTest {
        val exitTwo = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 2, stderr = "denied") }
        val missing = FakeShellCommandExecutor { throw app.luxion.shogunai.domain.executor.CommandExecutionException(it, java.io.IOException("not found")) }

        assertIs<WorktreeError.FolderOpenFailed>(SystemFolderLauncher(exitTwo, HostOs.WINDOWS).open(path).exceptionOrNull())
        assertIs<WorktreeError.FolderOpenFailed>(SystemFolderLauncher(missing, HostOs.WINDOWS).open(path).exceptionOrNull())
    }
}
