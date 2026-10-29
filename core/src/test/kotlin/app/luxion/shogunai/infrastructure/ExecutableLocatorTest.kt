package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.HostOs
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExecutableLocatorTest {

    private fun windows(vararg files: String, extensions: List<String> = listOf(".COM", ".EXE", ".BAT", ".CMD")) = ExecutableLocator(
        HostOs.WINDOWS, "C:\\Users\\dev", { listOf("C:\\Tools") }, null, emptyList(), extensions,
    ) { it in files }

    @Test
    fun `finds a Windows command script by its extension`() {
        assertEquals("C:\\Tools\\shogun.CMD", windows("C:\\Tools\\shogun.CMD").find("shogun"))
    }

    @Test
    fun `finds a Windows executable and honors the extension order`() {
        assertEquals("C:\\Tools\\shogun.EXE", windows("C:\\Tools\\shogun.CMD", "C:\\Tools\\shogun.EXE").find("shogun"))
    }

    @Test
    fun `uses PATHEXT when the system defines it`() {
        val extensions = listOf(".COM", ".EXE", ".PS1")

        assertEquals("C:\\Tools\\shogun.PS1", windows("C:\\Tools\\shogun.PS1", extensions = extensions).find("shogun"))
        assertNull(windows("C:\\Tools\\shogun.CMD", extensions = extensions).find("shogun"))
    }

    @Test
    fun `still finds a name that already has an extension`() {
        assertEquals("C:\\Tools\\code.cmd", windows("C:\\Tools\\code.cmd").find("code.cmd"))
    }

    @Test
    fun `does not try extensions outside Windows`() {
        val locator = ExecutableLocator(HostOs.MACOS, "/home/dev", { listOf("/usr/bin") }, null) { it == "/usr/bin/shogun.CMD" }

        assertNull(locator.find("shogun"))
    }

    @Test
    fun `built from the host environment, looks in its PATH before the usual install folders`() {
        val root = createTempDirectory("executable-locator").toRealPath()
        val home = root.resolve("home")
        val tools = root.resolve("tools/bin").createDirectories()
        tools.resolve("example-tool").createFile()
        home.resolve(".local/bin").createDirectories().resolve("example-tool").createFile()
        val environment = SystemHostEnvironment(HostOs.LINUX, mapOf("PATH" to tools.toString()), home.toString(), loginShell = null)

        assertEquals(tools.resolve("example-tool").toString(), ExecutableLocator(environment).find("example-tool"))
        Files.delete(tools.resolve("example-tool"))
        assertEquals(home.resolve(".local/bin/example-tool").toString(), ExecutableLocator(environment).find("example-tool"))
    }

    @Test
    fun `built from the host environment, takes the Windows folders and extensions from its variables`() {
        val environment = SystemHostEnvironment(
            HostOs.WINDOWS,
            mapOf(
                "LOCALAPPDATA" to "C:\\Users\\dev\\AppData\\Local",
                "ProgramFiles" to "C:\\Program Files",
                "PATHEXT" to ".EXE;.CMD",
            ),
            "C:\\Users\\dev",
            loginShell = null,
        )

        val locator = ExecutableLocator(environment)

        assertEquals("C:\\Users\\dev\\AppData\\Local", locator.localAppData)
        assertEquals(listOf("C:\\Program Files"), locator.programFolders)
        assertEquals(listOf(".EXE", ".CMD"), locator.pathExtensions)
    }
}
