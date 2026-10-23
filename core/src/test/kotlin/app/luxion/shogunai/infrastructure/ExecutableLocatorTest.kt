package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.HostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExecutableLocatorTest {

    private fun windows(vararg files: String, extensions: List<String> = listOf(".COM", ".EXE", ".BAT", ".CMD")) = ExecutableLocator(
        HostOs.WINDOWS, "C:\\Users\\dev", listOf("C:\\Tools"), null, emptyList(), extensions,
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
        val locator = ExecutableLocator(HostOs.MACOS, "/home/dev", listOf("/usr/bin"), null) { it == "/usr/bin/shogun.CMD" }

        assertNull(locator.find("shogun"))
    }
}
