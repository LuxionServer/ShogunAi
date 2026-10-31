package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.TerminalId
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalToolDetectorTest {

    private fun detector(
        os: HostOs,
        files: Set<String> = emptySet(),
        directories: Set<String> = emptySet(),
        bundles: Set<String> = emptySet(),
        sessions: Set<String> = emptySet(),
        programFolders: List<String> = emptyList(),
        registry: List<String> = emptyList(),
        runners: Set<RunnerId> = emptySet(),
    ): LocalToolDetector {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command.first() == "mdfind" ->
                    FakeShellCommandExecutor.success(command, if (bundles.any { command[1].contains("'$it'") }) "/Applications/App.app\n" else "")
                command.first() in sessions -> FakeShellCommandExecutor.success(command, "main: 1 windows\n")
                else -> FakeShellCommandExecutor.failure(command)
            }
        }
        val locator = ExecutableLocator(
            os, "/home/dev", { listOf("/usr/bin") }, "C:\\Users\\dev\\AppData\\Local",
            programFolders, listOf(".EXE", ".CMD"), subfolders = { emptyList() }, isFile = { it in files },
        )
        return LocalToolDetector(
            executor = executor,
            os = os,
            home = "/home/dev",
            locator = locator,
            editorResolver = EditorExecutableResolver(os, locator) { registry },
            isDirectory = { it in directories },
            isRunnerInstalled = { it in runners },
        )
    }

    @Test
    fun `reports which runners are installed`() = runTest {
        assertEquals(setOf(RunnerId.ORCA), detector(HostOs.MACOS, runners = setOf(RunnerId.ORCA)).detect().runners)
        assertEquals(emptySet(), detector(HostOs.LINUX).detect().runners)
    }

    @Test
    fun `finds macOS terminals and IDEs by bundle identifier`() = runTest {
        val tools = detector(
            HostOs.MACOS,
            bundles = setOf("com.googlecode.iterm2", "com.apple.Terminal", "com.google.android.studio"),
        ).detect()

        assertEquals(listOf(TerminalId.TERMINAL, TerminalId.ITERM2), tools.terminals)
        assertEquals(listOf(EditorId.ANDROID_STUDIO), tools.editors)
    }

    @Test
    fun `falls back to the application folders when Spotlight doesn't know the bundle`() = runTest {
        val tools = detector(
            HostOs.MACOS,
            directories = setOf("/home/dev/Applications/Visual Studio Code.app", "/System/Applications/Utilities/Terminal.app"),
        ).detect()

        assertEquals(listOf(TerminalId.TERMINAL), tools.terminals)
        assertEquals(listOf(EditorId.VS_CODE), tools.editors)
    }

    @Test
    fun `finds an IDE through the Toolbox scripts on macOS`() = runTest {
        val tools = detector(
            HostOs.MACOS,
            files = setOf("/home/dev/Library/Application Support/JetBrains/Toolbox/scripts/idea"),
        ).detect()

        assertEquals(listOf(EditorId.INTELLIJ_IDEA), tools.editors)
    }

    @Test
    fun `offers tmux and Zellij only while they have a running session`() = runTest {
        val files = setOf("/usr/bin/tmux", "/usr/bin/zellij")

        assertEquals(
            listOf(TerminalId.TMUX),
            detector(HostOs.LINUX, files, sessions = setOf("tmux")).detect().terminals,
        )
        assertEquals(
            DetectedTools(runners = emptySet()),
            detector(HostOs.LINUX, files).detect(),
        )
        assertEquals(
            listOf(TerminalId.TMUX, TerminalId.ZELLIJ),
            detector(HostOs.MACOS, files, sessions = setOf("tmux", "zellij")).detect().terminals,
        )
    }

    @Test
    fun `finds Linux IDEs on the path and in the Toolbox folder`() = runTest {
        val tools = detector(
            HostOs.LINUX,
            files = setOf("/usr/bin/code", "/home/dev/.local/share/JetBrains/Toolbox/scripts/studio"),
        ).detect()

        assertEquals(emptyList(), tools.terminals)
        assertEquals(listOf(EditorId.VS_CODE, EditorId.ANDROID_STUDIO), tools.editors)
    }

    @Test
    fun `finds Windows Terminal and the IDE launchers on Windows`() = runTest {
        val tools = detector(
            HostOs.WINDOWS,
            files = setOf(
                "/usr/bin\\wt.exe",
                "/usr/bin\\code.cmd",
                "C:\\Users\\dev\\AppData\\Local\\JetBrains\\Toolbox\\scripts\\idea.cmd",
            ),
        ).detect()

        assertEquals(listOf(TerminalId.WINDOWS_TERMINAL), tools.terminals)
        assertEquals(listOf(EditorId.VS_CODE, EditorId.INTELLIJ_IDEA), tools.editors)
    }

    @Test
    fun `macOS terminals aren't offered on other systems`() = runTest {
        val tools = detector(HostOs.LINUX, bundles = setOf("com.apple.Terminal"), directories = setOf("/Applications/Terminal.app")).detect()

        assertEquals(emptyList(), tools.terminals)
    }

    @Test
    fun `a missing command counts as not installed`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("not found")) }
        val detector = LocalToolDetector(
            executor = executor,
            os = HostOs.MACOS,
            home = "/home/dev",
            locator = ExecutableLocator(HostOs.MACOS, "/home/dev", { listOf("/usr/bin") }, null) { it == "/usr/bin/tmux" },
            isDirectory = { false },
        )

        assertEquals(DetectedTools(runners = emptySet()), detector.detect())
    }

    @Test
    fun `finds Xcode on macOS by bundle identifier and by application folder`() = runTest {
        val byBundle = detector(HostOs.MACOS, bundles = setOf("com.apple.dt.Xcode")).detect()
        val byFolder = detector(HostOs.MACOS, directories = setOf("/Applications/Xcode.app")).detect()

        assertEquals(listOf(EditorId.XCODE), byBundle.editors)
        assertEquals(listOf(EditorId.XCODE), byFolder.editors)
    }

    @Test
    fun `doesn't offer Xcode on a Mac without it`() = runTest {
        val tools = detector(HostOs.MACOS, bundles = setOf("com.microsoft.VSCode")).detect()

        assertEquals(listOf(EditorId.VS_CODE), tools.editors)
    }

    @Test
    fun `never offers Xcode outside macOS`() = runTest {
        listOf(HostOs.WINDOWS, HostOs.LINUX).forEach { os ->
            val tools = detector(os, files = setOf("/usr/bin/xcode", "/usr/bin/xcode.cmd"), directories = setOf("/Applications/Xcode.app")).detect()

            assertEquals(emptyList(), tools.editors)
        }
    }

    @Test
    fun `finds Claude Desktop on macOS by bundle identifier or application folder`() = runTest {
        assertEquals(true, detector(HostOs.MACOS, bundles = setOf("com.anthropic.claudefordesktop")).detect().claudeDesktop)
        assertEquals(true, detector(HostOs.MACOS, directories = setOf("/Applications/Claude.app")).detect().claudeDesktop)
        assertEquals(false, detector(HostOs.MACOS).detect().claudeDesktop)
    }

    @Test
    fun `doesn't offer Claude Desktop on Windows or Linux`() = runTest {
        val folders = setOf("/Applications/Claude.app")
        val bundles = setOf("com.anthropic.claudefordesktop")

        assertEquals(false, detector(HostOs.WINDOWS, directories = folders, bundles = bundles).detect().claudeDesktop)
        assertEquals(false, detector(HostOs.LINUX, directories = folders, bundles = bundles).detect().claudeDesktop)
    }

    @Test
    fun `finds an IDE installed by its official installer on Windows`() = runTest {
        val tools = detector(
            HostOs.WINDOWS,
            files = setOf("C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe"),
            programFolders = listOf("C:\\Program Files"),
        ).detect()

        assertEquals(listOf(EditorId.ANDROID_STUDIO), tools.editors)
    }

    @Test
    fun `finds an IDE through the registry on Windows`() = runTest {
        val tools = detector(
            HostOs.WINDOWS,
            files = setOf("D:\\Dev\\Android Studio\\bin\\studio64.exe"),
            registry = listOf("D:\\Dev\\Android Studio"),
        ).detect()

        assertEquals(listOf(EditorId.ANDROID_STUDIO), tools.editors)
    }

    @Test
    fun `a failing Spotlight finds no macOS app`() = runTest {
        val locator = ExecutableLocator(
            HostOs.MACOS, "/home/dev", { emptyList() }, subfolders = { emptyList() }, isFile = { false },
        )
        val detector = LocalToolDetector(
            executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) },
            os = HostOs.MACOS,
            home = "/home/dev",
            locator = locator,
            editorResolver = EditorExecutableResolver(HostOs.MACOS, locator) { emptyList() },
            isDirectory = { false },
            isRunnerInstalled = { false },
        )

        assertEquals(DetectedTools(emptyList(), emptyList(), false, emptySet()), detector.detect())
    }
}
