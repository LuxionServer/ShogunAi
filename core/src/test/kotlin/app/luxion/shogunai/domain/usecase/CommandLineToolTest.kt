package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.LauncherFiles
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeLauncherFiles : LauncherFiles {
    val texts = mutableMapOf<String, String>()
    val links = mutableMapOf<String, String>()
    val others = mutableSetOf<String>()
    val writable = mutableSetOf<String>()
    var failWrites = false

    override fun exists(path: String) = path in texts || path in others
    override fun isWritableFolder(folder: String) = folder in writable
    override fun writeText(path: String, text: String) {
        if (failWrites) throw java.io.IOException("denied")
        texts[path] = text
    }
    override fun readText(path: String) = texts[path]
    override fun linkTarget(link: String) = links[link]
    override fun createLink(target: String, link: String) {
        if (failWrites) throw java.io.IOException("denied")
        links[link] = target
    }
    override fun delete(path: String) {
        texts.remove(path)
        links.remove(path)
        others.remove(path)
    }
}

private class FakeUserPath(val entries: MutableList<String> = mutableListOf()) : UserPathEntries {
    override suspend fun contains(folder: String) = entries.any { it.equals(folder, ignoreCase = true) }
    override suspend fun add(folder: String) {
        entries += folder
    }
    override suspend fun remove(folder: String) {
        entries.removeAll { it.equals(folder, ignoreCase = true) }
    }
}

class CommandLineToolTest {

    private val files = FakeLauncherFiles()
    private val userPath = FakeUserPath()

    private fun unix(
        launcher: String? = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi",
        path: List<String> = listOf("/usr/bin", "/usr/local/bin"),
        found: String? = null,
    ) = CommandLineTool(
        os = HostOs.MACOS,
        appLauncher = launcher,
        home = "/home/dev",
        pathFolders = path,
        userBinFolders = listOf("/opt/homebrew/bin", "/usr/local/bin", "/home/dev/.local/bin", "/home/dev/.cargo/bin"),
        findShogun = { found ?: files.links.keys.firstOrNull { it.endsWith("/shogun") } },
        files = files,
        userPath = userPath,
    )

    private fun windows(
        launcher: String? = "C:\\Users\\dev\\AppData\\Local\\ShogunAi\\ShogunAi.exe",
        path: List<String> = listOf("C:\\Windows"),
        found: String? = null,
    ) = CommandLineTool(
        os = HostOs.WINDOWS,
        appLauncher = launcher,
        home = "C:\\Users\\dev",
        pathFolders = path,
        userBinFolders = emptyList(),
        findShogun = { found },
        files = files,
        userPath = userPath,
    )

    @Test
    fun `is missing when nothing provides shogun`() = runTest {
        val status = unix().status()

        assertEquals(CommandLineToolState.MISSING, status.state)
        assertTrue(status.canInstall)
        assertFalse(status.usableByHooks)
    }

    @Test
    fun `is available when shogun is on PATH, whoever installed it`() = runTest {
        val status = unix(found = "/usr/local/bin/shogun").status()

        assertEquals(CommandLineToolState.AVAILABLE, status.state)
        assertTrue(status.usableByHooks)
    }

    @Test
    fun `is installed but not on PATH when its folder isn't on PATH`() = runTest {
        val status = unix(found = "/home/dev/.local/bin/shogun").status()

        assertEquals(CommandLineToolState.INSTALLED_NOT_ON_PATH, status.state)
        assertEquals("/home/dev/.local/bin", status.folder)
        assertFalse(status.usableByHooks)
    }

    @Test
    fun `is broken when the link it created points at an app that is gone`() = runTest {
        files.links["/usr/local/bin/shogun"] = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"

        assertEquals(CommandLineToolState.BROKEN, unix().status().state)
    }

    @Test
    fun `installs a link in a writable folder already on PATH`() = runTest {
        files.writable += setOf("/usr/local/bin", "/home/dev/.local/bin")

        val installed = unix().install().getOrThrow()

        assertEquals("/usr/local/bin/shogun", installed.launcher)
        assertEquals("/Applications/ShogunAi.app/Contents/MacOS/ShogunAi", files.links["/usr/local/bin/shogun"])
        assertTrue(installed.folderOnPath)
    }

    @Test
    fun `falls back to the local bin folder when no folder on PATH is writable`() = runTest {
        val installed = unix().install().getOrThrow()

        assertEquals("/home/dev/.local/bin/shogun", installed.launcher)
        assertFalse(installed.folderOnPath)
    }

    @Test
    fun `installing twice leaves a single link`() = runTest {
        files.writable += "/usr/local/bin"
        files.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        val tool = unix()

        tool.install().getOrThrow()
        tool.install().getOrThrow()

        assertEquals(listOf("/usr/local/bin/shogun"), files.links.keys.toList())
    }

    @Test
    fun `never overwrites a shogun it did not create`() = runTest {
        files.writable += "/usr/local/bin"
        files.others += "/usr/local/bin/shogun"

        val error = unix().install().exceptionOrNull()

        assertIs<CommandLineToolError.ForeignFile>(error)
    }

    @Test
    fun `reports the path it could not write`() = runTest {
        files.writable += "/usr/local/bin"
        files.failWrites = true

        val error = unix().install().exceptionOrNull()

        assertIs<CommandLineToolError.WriteFailed>(error)
        assertEquals("/usr/local/bin/shogun", error.path)
    }

    @Test
    fun `cannot install from a build that is not an installed app`() = runTest {
        val tool = unix(launcher = null)

        assertFalse(tool.status().canInstall)
        assertIs<CommandLineToolError.NotPackaged>(tool.install().exceptionOrNull())
    }

    @Test
    fun `writes a Windows script that runs the bundled runtime and adds its folder to the user PATH`() = runTest {
        val installed = windows().install().getOrThrow()

        val body = files.texts["C:\\Users\\dev\\.shogunai\\bin\\shogun.cmd"]
        assertEquals(
            "@\"C:\\Users\\dev\\AppData\\Local\\ShogunAi\\runtime\\bin\\java.exe\" -cp \"C:\\Users\\dev\\AppData\\Local\\ShogunAi\\app\\*\" app.luxion.shogunai.MainKt %*\r\n",
            body,
        )
        assertEquals(listOf("C:\\Users\\dev\\.shogunai\\bin"), userPath.entries)
        assertTrue(installed.restartNeeded)
    }

    @Test
    fun `installing on Windows twice adds the folder to PATH once`() = runTest {
        val tool = windows()

        tool.install().getOrThrow()
        tool.install().getOrThrow()

        assertEquals(1, userPath.entries.size)
    }

    @Test
    fun `Windows script is usable by the hooks once its folder is in the user PATH`() = runTest {
        windows().install().getOrThrow()
        files.others += "C:\\Users\\dev\\AppData\\Local\\ShogunAi\\runtime\\bin\\java.exe"

        val status = windows().status()

        assertEquals(CommandLineToolState.INSTALLED_NOT_ON_PATH, status.state)
        assertTrue(status.restartNeeded)
        assertTrue(status.usableByHooks)
    }

    @Test
    fun `Windows script is available once the running PATH has its folder`() = runTest {
        windows().install().getOrThrow()
        files.others += "C:\\Users\\dev\\AppData\\Local\\ShogunAi\\runtime\\bin\\java.exe"

        val status = windows(path = listOf("C:\\Windows", "c:\\users\\dev\\.shogunai\\bin\\")).status()

        assertEquals(CommandLineToolState.AVAILABLE, status.state)
    }

    @Test
    fun `Windows script is broken when the runtime it points at is gone`() = runTest {
        windows().install().getOrThrow()

        assertEquals(CommandLineToolState.BROKEN, windows().status().state)
    }

    @Test
    fun `does not overwrite a Windows script it did not write`() = runTest {
        files.texts["C:\\Users\\dev\\.shogunai\\bin\\shogun.cmd"] = "@echo hi"

        assertIs<CommandLineToolError.ForeignFile>(windows().install().exceptionOrNull())
    }

    @Test
    fun `removes the Windows script and its PATH entry`() = runTest {
        val tool = windows()
        tool.install().getOrThrow()

        tool.remove().getOrThrow()

        assertNull(files.texts["C:\\Users\\dev\\.shogunai\\bin\\shogun.cmd"])
        assertTrue(userPath.entries.isEmpty())
    }

    @Test
    fun `removing leaves a shogun from another install method untouched`() = runTest {
        files.others += "/opt/homebrew/bin/shogun"

        unix().remove().getOrThrow()

        assertTrue("/opt/homebrew/bin/shogun" in files.others)
    }

    @Test
    fun `removes the link it created`() = runTest {
        files.links["/home/dev/.local/bin/shogun"] = "/opt/shogunai/bin/ShogunAi"

        unix().remove().getOrThrow()

        assertTrue(files.links.isEmpty())
    }
}
