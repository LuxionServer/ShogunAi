package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorExecutableResolverTest {

    private val programFiles = "C:\\Program Files"
    private val localAppData = "C:\\Users\\dev\\AppData\\Local"

    private fun resolver(
        files: Set<String> = emptySet(),
        folders: Set<String> = emptySet(),
        path: List<String> = listOf("C:\\Tools"),
        pathExtensions: List<String> = listOf(".EXE", ".BAT", ".CMD"),
        registry: List<String> = emptyList(),
    ): EditorExecutableResolver {
        val locator = ExecutableLocator(
            HostOs.WINDOWS,
            "C:\\Users\\dev",
            path,
            localAppData,
            listOf(programFiles, "C:\\Program Files (x86)"),
            pathExtensions,
            { parent -> folders.filter { it.startsWith("$parent\\") }.map { it.removePrefix("$parent\\") }.filter { '\\' !in it } },
            { it in files },
        )
        return EditorExecutableResolver(HostOs.WINDOWS, locator) { registry }
    }

    @Test
    fun `finds Android Studio installed by the official installer`() = runTest {
        val studio = "$programFiles\\Android\\Android Studio\\bin\\studio64.exe"

        val found = resolver(files = setOf(studio), folders = setOf("$programFiles\\Android\\Android Studio")).find(EditorId.ANDROID_STUDIO)

        assertEquals(studio, found)
    }

    @Test
    fun `uses the highest IntelliJ IDEA version`() = runTest {
        val folders = setOf("$programFiles\\JetBrains\\IntelliJ IDEA 2024.3", "$programFiles\\JetBrains\\IntelliJ IDEA 2025.2")
        val files = folders.map { "$it\\bin\\idea64.exe" }.toSet()

        assertEquals("$programFiles\\JetBrains\\IntelliJ IDEA 2025.2\\bin\\idea64.exe", resolver(files, folders).find(EditorId.INTELLIJ_IDEA))
    }

    @Test
    fun `finds IntelliJ IDEA Community Edition`() = runTest {
        val folder = "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition 2025.2"

        assertEquals("$folder\\bin\\idea64.exe", resolver(setOf("$folder\\bin\\idea64.exe"), setOf(folder)).find(EditorId.INTELLIJ_IDEA))
    }

    @Test
    fun `finds VS Code for the current user and for all users`() = runTest {
        val user = "$localAppData\\Programs\\Microsoft VS Code\\Code.exe"
        val system = "$programFiles\\Microsoft VS Code\\bin\\code.cmd"

        assertEquals(user, resolver(files = setOf(user)).find(EditorId.VS_CODE))
        assertEquals(system, resolver(files = setOf(system)).find(EditorId.VS_CODE))
    }

    @Test
    fun `prefers the VS Code launcher script over Code exe`() = runTest {
        val folder = "$programFiles\\Microsoft VS Code"

        assertEquals("$folder\\bin\\code.cmd", resolver(files = setOf("$folder\\Code.exe", "$folder\\bin\\code.cmd")).find(EditorId.VS_CODE))
    }

    @Test
    fun `finds shims on the path with any PATHEXT extension`() = runTest {
        assertEquals("C:\\Tools\\code.exe", resolver(files = setOf("C:\\Tools\\code.exe")).find(EditorId.VS_CODE))
        assertEquals("C:\\Tools\\idea.bat", resolver(files = setOf("C:\\Tools\\idea.bat")).find(EditorId.INTELLIJ_IDEA))
        assertEquals("C:\\Tools\\studio.cmd", resolver(files = setOf("C:\\Tools\\studio.cmd")).find(EditorId.ANDROID_STUDIO))
    }

    @Test
    fun `an executable on the path wins over an installer folder`() = runTest {
        val shim = "C:\\Tools\\code.cmd"
        val installed = "$programFiles\\Microsoft VS Code\\bin\\code.cmd"

        assertEquals(shim, resolver(files = setOf(shim, installed)).find(EditorId.VS_CODE))
    }

    @Test
    fun `finds the Toolbox launcher scripts`() = runTest {
        val script = "$localAppData\\JetBrains\\Toolbox\\scripts\\idea.cmd"

        assertEquals(script, resolver(files = setOf(script)).find(EditorId.INTELLIJ_IDEA))
    }

    @Test
    fun `finds the newest build in the Toolbox apps folder when scripts are disabled`() = runTest {
        val apps = "$localAppData\\JetBrains\\Toolbox\\apps\\AndroidStudio\\ch-0"
        val folders = setOf("$localAppData\\JetBrains\\Toolbox\\apps\\AndroidStudio", apps, "$apps\\243.100.1", "$apps\\251.200.2")
        val files = setOf("$apps\\243.100.1\\bin\\studio64.exe", "$apps\\251.200.2\\bin\\studio64.exe")

        assertEquals("$apps\\251.200.2\\bin\\studio64.exe", resolver(files, folders).find(EditorId.ANDROID_STUDIO))
    }

    @Test
    fun `falls back to the install location recorded in the registry`() = runTest {
        val location = "D:\\Dev\\Android Studio"

        val found = resolver(files = setOf("$location\\bin\\studio64.exe"), registry = listOf("D:\\Other", location)).find(EditorId.ANDROID_STUDIO)

        assertEquals("$location\\bin\\studio64.exe", found)
    }

    @Test
    fun `ignores a registry location whose executable is gone`() = runTest {
        assertNull(resolver(registry = listOf("D:\\Dev\\Android Studio")).find(EditorId.ANDROID_STUDIO))
    }

    @Test
    fun `reads the registry once per snapshot and only when needed`() = runTest {
        var reads = 0
        val locator = ExecutableLocator(HostOs.WINDOWS, "C:\\Users\\dev", emptyList(), null, emptyList(), listOf(".EXE"), { emptyList() }, { false })
        val resolver = EditorExecutableResolver(HostOs.WINDOWS, locator) { reads++; emptyList() }
        val snapshot = resolver.snapshot()

        resolver.find(EditorId.VS_CODE, snapshot)
        resolver.find(EditorId.ANDROID_STUDIO, snapshot)

        assertEquals(1, reads)
    }

    @Test
    fun `nothing is found when the IDE isn't installed`() = runTest {
        EditorId.entries.forEach { assertNull(resolver().find(it)) }
    }

    @Test
    fun `parses the install locations out of reg query output`() {
        val output = """
            HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Android Studio
                InstallLocation    REG_SZ    C:\Program Files\Android\Android Studio

            HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Other
                InstallLocation    REG_SZ    
            HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Tool
                InstallLocation    REG_SZ    D:\Tool\
        """.trimIndent()

        assertEquals(listOf("C:\\Program Files\\Android\\Android Studio", "D:\\Tool\\"), RegistryUninstallLocations.parse(output))
    }

    @Test
    fun `an unreadable registry yields no locations`() = runTest {
        val failing = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("no reg")) }
        val unsuccessful = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }

        assertEquals(emptyList(), RegistryUninstallLocations(failing).find())
        assertEquals(emptyList(), RegistryUninstallLocations(unsuccessful).find())
    }

    @Test
    fun `queries the uninstall keys with reg`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "    InstallLocation    REG_SZ    C:\\A\n") }

        assertEquals(listOf("C:\\A"), RegistryUninstallLocations(executor).find())
        assertEquals(3, executor.executedCommands.size)
        assertEquals(listOf("reg", "query"), executor.executedCommands.first().take(2))
    }
}
