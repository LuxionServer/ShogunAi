package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.HostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExecutableLocatorTest {

    private fun windows(vararg files: String, extensions: List<String> = ExecutableLocator.executableExtensions(null)) = ExecutableLocator(
        HostOs.WINDOWS, "C:\\Users\\dev", listOf("C:\\Tools"), null, extensions,
    ) { it in files }

    @Test
    fun `finds a Windows command script by its extension`() {
        assertEquals("C:\\Tools\\shogun.cmd", windows("C:\\Tools\\shogun.cmd").find("shogun"))
    }

    @Test
    fun `finds a Windows executable and honors the extension order`() {
        assertEquals("C:\\Tools\\shogun.exe", windows("C:\\Tools\\shogun.cmd", "C:\\Tools\\shogun.exe").find("shogun"))
    }

    @Test
    fun `uses PATHEXT when the system defines it`() {
        val extensions = ExecutableLocator.executableExtensions(".COM;.EXE;.PS1")

        assertEquals("C:\\Tools\\shogun.ps1", windows("C:\\Tools\\shogun.ps1", extensions = extensions).find("shogun"))
        assertNull(windows("C:\\Tools\\shogun.cmd", extensions = extensions).find("shogun"))
    }

    @Test
    fun `falls back to the default extensions when PATHEXT is missing`() {
        assertEquals(listOf(".com", ".exe", ".bat", ".cmd"), ExecutableLocator.executableExtensions(null))
    }

    @Test
    fun `still finds a name that already has an extension`() {
        assertEquals("C:\\Tools\\code.cmd", windows("C:\\Tools\\code.cmd").find("code.cmd"))
    }

    @Test
    fun `does not try extensions outside Windows`() {
        val locator = ExecutableLocator(HostOs.MACOS, "/home/dev", listOf("/usr/bin"), null) { it == "/usr/bin/shogun.cmd" }

        assertNull(locator.find("shogun"))
    }
}
