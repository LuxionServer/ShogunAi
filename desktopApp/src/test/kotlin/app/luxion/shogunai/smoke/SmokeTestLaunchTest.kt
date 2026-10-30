package app.luxion.shogunai.smoke

import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.ui.shell.TourStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmokeTestLaunchTest {

    @Test
    fun `the report variable starts the smoke test when there are no arguments`() {
        assertEquals("/tmp/report.json", smokeTestReportPath(emptyList(), mapOf(SMOKE_TEST_REPORT_VARIABLE to "/tmp/report.json")::get))
    }

    @Test
    fun `arguments run the command line even with the report variable set`() {
        assertNull(smokeTestReportPath(listOf("status"), mapOf(SMOKE_TEST_REPORT_VARIABLE to "/tmp/report.json")::get))
    }

    @Test
    fun `without the report variable the app starts as before`() {
        assertNull(smokeTestReportPath(emptyList()) { null })
        assertNull(smokeTestReportPath(emptyList(), mapOf(SMOKE_TEST_REPORT_VARIABLE to " ")::get))
    }

    @Test
    fun `local files are a comma-separated list`() {
        assertEquals(listOf(".env.local", "keys/dev.pem"), smokeTestLocalFiles(mapOf(SMOKE_TEST_LOCAL_FILES_VARIABLE to " .env.local, ,keys/dev.pem")::get))
        assertEquals(emptyList(), smokeTestLocalFiles { null })
    }

    @Test
    fun `the report lists every step and what the app saw`() {
        val report = SmokeTestReport(
            steps = listOf(
                SmokeStep(SmokeStepName.INSTALL_COMMAND, SmokeStepStatus.PASSED, "AVAILABLE at /opt/homebrew/bin/shogun"),
                SmokeStep(SmokeStepName.INSTALL_HOOKS, SmokeStepStatus.SKIPPED, "not offered"),
            ),
            commandLineTool = CommandLineToolStatus(CommandLineToolState.AVAILABLE, "/opt/homebrew/bin/shogun", "/opt/homebrew/bin", canInstall = true, installedByApp = true),
            hooksState = HooksInstallState.INSTALLED,
            tourReached = TourStep.OPEN_AGENT,
            worktreePath = "/projects/main-repo-worktrees/smoke-app",
            copiedFiles = listOf(".env.local"),
        )

        val json = Json.parseToJsonElement(report.toJson()).jsonObject

        assertTrue(json.getValue("passed").jsonPrimitive.boolean)
        val steps = json.getValue("steps").jsonArray.map { it.jsonObject }
        assertEquals(listOf("install-command", "install-hooks"), steps.map { it.getValue("name").jsonPrimitive.content })
        assertEquals(listOf("passed", "skipped"), steps.map { it.getValue("status").jsonPrimitive.content })
        val tool = json.getValue("commandLineTool").jsonObject
        assertEquals("AVAILABLE", tool.getValue("state").jsonPrimitive.content)
        assertEquals("/opt/homebrew/bin", tool.getValue("folder").jsonPrimitive.content)
        assertTrue(tool.getValue("usableByHooks").jsonPrimitive.boolean)
        assertEquals("INSTALLED", json.getValue("hooks").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals("OPEN_AGENT", json.getValue("tour").jsonObject.getValue("reached").jsonPrimitive.content)
        assertEquals(".env.local", json.getValue("worktree").jsonObject.getValue("copiedFiles").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `a failed step makes the exit status non-zero`() {
        val report = SmokeTestReport(listOf(SmokeStep(SmokeStepName.CHECK_FILES, SmokeStepStatus.FAILED, ".env.local wasn't copied")))

        assertFalse(report.passed)
        assertEquals(1, report.exitStatus)
        assertFalse(Json.parseToJsonElement(report.toJson()).jsonObject.getValue("passed").jsonPrimitive.boolean)
    }

    @Test
    fun `skipped steps don't fail the report`() {
        assertEquals(0, SmokeTestReport(listOf(SmokeStep(SmokeStepName.INSTALL_HOOKS, SmokeStepStatus.SKIPPED))).exitStatus)
    }

    @Test
    fun `the report is written creating its folder`() {
        val root = Files.createTempDirectory("smoke-report")
        val path = root.resolve("reports/smoke.json")

        writeSmokeTestReport(path.toString(), SmokeTestReport(emptyList()))

        assertTrue(Files.readString(path).contains("\"passed\": true"))
        root.toFile().deleteRecursively()
    }
}
