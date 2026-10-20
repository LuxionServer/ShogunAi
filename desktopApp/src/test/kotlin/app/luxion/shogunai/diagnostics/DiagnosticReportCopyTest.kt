package app.luxion.shogunai.diagnostics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticReportCopyTest {

    @Test
    fun `copies the built report and then confirms`() = runBlocking {
        val events = mutableListOf<String>()

        copyDiagnosticReport(
            build = { "the report" },
            copy = { events += "copied: $it" },
            confirm = { events += "confirmed" },
        )

        assertEquals(listOf("copied: the report", "confirmed"), events)
    }
}
