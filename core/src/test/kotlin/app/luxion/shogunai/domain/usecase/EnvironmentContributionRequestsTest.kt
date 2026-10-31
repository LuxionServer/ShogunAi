package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EnvironmentContributionRequestsTest {

    @Test
    fun `an emulator port also needs the next port and reports when none is left`() {
        val request = emulatorContribution.ports.single()

        assertEquals(listOf(5555), request.alsoFree(5554))
        assertIs<WorktreeError.NoEmulatorPort>(request.exhausted())
    }

    @Test
    fun `declared and compose ports report when the range is exhausted`() {
        val compose = dockerComposeContributions(DockerComposeProvider(portVariables = listOf("WEB_PORT"))).flatMap { it.ports }.single()

        assertIs<WorktreeError.NoFreePort>(compose.exhausted())
        assertTrue(declaredPortRequests(EnvironmentDeclaration()).isEmpty())
    }

    @Test
    fun `discarding a simulator deletes it and discarding the compilation cache keeps the shared file`() = runTest {
        val simulators = FakeAppleSimulators().also { it.addExisting("UDID-1", "ShogunAi · main-repo · TASK-123") }
        val files = FakeFileManager()

        iosSimulatorContribution(simulators, IosSimulatorProvider(), SimulatorSettings(), SimulatorSettings()).resources.single().discard("UDID-1")
        xcodeCompilationCacheContribution(files, "/home/dev").resources.single().discard("/home/dev/.shogunai/xcode/compilation-cache.xcconfig")

        assertEquals(listOf("UDID-1"), simulators.deleted)
        assertTrue(files.writtenTexts.isEmpty())
    }

    @Test
    fun `a compilation cache file with other content doesn't count as existing`() = runTest {
        val files = FakeFileManager()
        val resource = xcodeCompilationCacheContribution(files, "/home/dev").resources.single()
        val path = xcodeCompilationCachePath("/home/dev")

        assertFalse(resource.exists(path))
        files.writeText(path, "other")
        assertFalse(resource.exists(path))
    }
}
