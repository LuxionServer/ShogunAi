package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticReportTest {

    private class FakeLog(val entries: List<DiagnosticEntry> = emptyList()) : DiagnosticsLog {
        var requestedLimit = 0
        override fun record(entry: DiagnosticEntry) = Unit
        override fun recent(limit: Int): List<DiagnosticEntry> {
            requestedLimit = limit
            return entries.takeLast(limit)
        }
    }

    private val properties = mapOf(
        "os.name" to "TestOS",
        "os.version" to "1.2",
        "os.arch" to "arm64",
        "java.runtime.version" to "21.0.1",
    )

    @Test
    fun `holds the version, system information and recent entries`() {
        val log = FakeLog(
            listOf(DiagnosticEntry(Instant.parse("2026-01-01T00:00:00Z"), DiagnosticKind.FREEZE, "UI frozen for 5000 ms", "\tat app.Foo.bar(Foo.kt:1)")),
        )

        val report = DiagnosticReport("1.2.3", log, properties::get).build()

        assertTrue(report.contains("Version: 1.2.3"))
        assertTrue(report.contains("Operating system: TestOS 1.2"))
        assertTrue(report.contains("Architecture: arm64"))
        assertTrue(report.contains("Java runtime: 21.0.1"))
        assertTrue(report.contains("[FREEZE] UI frozen for 5000 ms"))
        assertTrue(report.contains("at app.Foo.bar(Foo.kt:1)"))
        assertEquals(50, log.requestedLimit)
    }

    @Test
    fun `says nothing was recorded when the log is empty`() {
        val report = DiagnosticReport("1.2.3", FakeLog(), properties::get).build()

        assertTrue(report.contains("Version: 1.2.3"))
        assertTrue(report.endsWith("No errors were recorded."))
    }
}
