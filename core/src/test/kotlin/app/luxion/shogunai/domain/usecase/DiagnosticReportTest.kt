package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `holds the version, system information and recent entries`() = runBlocking {
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
        assertFalse(report.contains("Installation:"))
    }

    @Test
    fun `says nothing was recorded when the log is empty`() = runBlocking {
        val report = DiagnosticReport("1.2.3", FakeLog(), properties::get).build()

        assertTrue(report.contains("Version: 1.2.3"))
        assertTrue(report.endsWith("No errors were recorded."))
    }

    private val hooksCallMissingShogun = InstallationReport(
        listOf(
            InstallationCheck(CheckState.PASSED, InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")),
            InstallationCheck(
                CheckState.FAILED,
                InstallationFinding.Hooks("/Users/jane/.claude/settings.json", HooksInstallState.INSTALLED, shogun = CommandLineToolState.MISSING),
            ),
        ),
    )

    @Test
    fun `holds the installation check's results with their fixes, before the log`() = runBlocking {
        val report = DiagnosticReport("1.2.3", FakeLog(), properties::get, installation = { hooksCallMissingShogun }).build()

        val section = report.substringAfter("Installation:\n").substringBefore("\n\n")
        assertEquals(
            listOf(
                "ok    Git 2.45.1 (/usr/bin/git)",
                "fail  Claude Code hooks run 'shogun', which isn't installed",
                "      fix: The hooks run 'shogun'. Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide",
            ),
            section.lines(),
        )
        assertTrue(report.endsWith("No errors were recorded."))
    }

    @Test
    fun `the installation section is redacted`() = runBlocking {
        val base = "/Users/jane/projects/main-repo"
        val missing = UnavailableLocalFile("local.properties", "$base/local.properties", LocalFileOrigin.PROJECT, UnavailableReason.Missing)
        val installation = InstallationReport(
            listOf(InstallationCheck(CheckState.FAILED, InstallationFinding.ProjectFiles("main-repo", base, false, 1, listOf(missing)))),
        )
        val redactor = Redactor("/Users/jane", "jane", { listOf("main-repo") }, { emptyList() })

        val report = DiagnosticReport("1.2.3", FakeLog(), properties::get, installation = { installation }, redact = redactor::redact).build()

        assertTrue(report.contains("Installation:"))
        assertFalse(report.contains("/Users/jane"))
        assertFalse(report.contains("jane"))
        assertFalse(report.contains("main-repo"))
    }

    @Test
    fun `a check that throws still gives a report`() = runBlocking {
        val report = DiagnosticReport("1.2.3", FakeLog(), properties::get, installation = { throw IllegalStateException("projects.json is unreadable") }).build()

        assertTrue(report.contains("Installation:\nThe installation check couldn't run: projects.json is unreadable"))
        assertTrue(report.endsWith("No errors were recorded."))
    }
}
