package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.SimulatorSetup
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatorSettingsTest {

    private val dispatcher = StandardTestDispatcher()
    private val kmp = "/home/dev/projects/kmp-app"
    private val web = "/home/dev/projects/web-app"
    private val iphone = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16", "iPhone 16", "iPhone")
    private val ios = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-18-5", "iOS 18.5", "18.5", "iOS", listOf(iphone))
    private val watch = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.watchOS-11-5", "watchOS 11.5", "11.5", "watchOS", emptyList())

    private class FakeSetup(
        private val repository: String,
        var wired: Boolean = false,
        var kotlinMultiplatform: Boolean = true,
    ) : SimulatorSetup {
        var runtimeReads = 0
        var installs = 0
        var runtimes: List<SimulatorRuntime> = emptyList()

        override fun isAvailable(repositoryPath: String) = repositoryPath == repository
        override suspend fun runtimes(): Result<List<SimulatorRuntime>> {
            runtimeReads++
            return Result.success(runtimes)
        }
        override fun isWired(repositoryPath: String) = wired
        override fun usesKotlinMultiplatform(repositoryPath: String) = kotlinMultiplatform
        override fun installInitScript(): Result<Unit> {
            installs++
            wired = true
            return Result.success(Unit)
        }
        override val snippet = "tasks.withType<KotlinNativeSimulatorTest>()"
        override val initScriptPath = "/home/dev/.gradle/init.d/shogunai-ios-simulator.init.gradle.kts"
    }

    private val setup = FakeSetup(kmp).also { it.runtimes = listOf(ios, watch) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun project(path: String, simulator: SimulatorSettings = SimulatorSettings()) = Project(
        id = "p",
        name = "App",
        config = ProjectConfig(baseRepositoryPath = path, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList(), simulator = simulator),
    )

    private fun viewModel(project: Project?) =
        ProjectConfigViewModel(FakeProjectRepository(), FakeRepositoryProvisioningSource(), project, simulatorSetup = setup)

    @Test
    fun `projects without the provider show no section and run no Apple tool`() = runTest(dispatcher) {
        val viewModel = viewModel(project(web))
        advanceUntilIdle()

        assertEquals(SimulatorSectionState.Hidden, viewModel.simulatorSection)
        assertEquals(0, setup.runtimeReads)
    }

    @Test
    fun `projects with the provider list the machine's iOS runtimes and the wiring`() = runTest(dispatcher) {
        val viewModel = viewModel(project(kmp))
        assertEquals(SimulatorSectionState.Loading, viewModel.simulatorSection)
        advanceUntilIdle()

        assertEquals(SimulatorSectionState.Available(listOf(ios), testsWired = false, kotlinMultiplatform = true), viewModel.simulatorSection)
    }

    @Test
    fun `repositories without Kotlin Multiplatform get no KMP wiring`() = runTest(dispatcher) {
        setup.kotlinMultiplatform = false
        val viewModel = viewModel(project(kmp))
        advanceUntilIdle()

        assertEquals(SimulatorSectionState.Available(listOf(ios), testsWired = false, kotlinMultiplatform = false), viewModel.simulatorSection)
    }

    @Test
    fun `installing the init script marks the tests as wired`() = runTest(dispatcher) {
        val viewModel = viewModel(project(kmp))
        advanceUntilIdle()

        viewModel.installSimulatorInitScript().getOrThrow()

        assertEquals(1, setup.installs)
        assertEquals(SimulatorSectionState.Available(listOf(ios), testsWired = true, kotlinMultiplatform = true), viewModel.simulatorSection)
    }

    @Test
    fun `machine settings are saved with the project and count as unsaved changes`() = runTest(dispatcher) {
        val viewModel = viewModel(project(kmp, SimulatorSettings(runtime = "iOS 18.5")))
        advanceUntilIdle()
        assertEquals("iOS 18.5", viewModel.simulatorRuntime)
        assertFalse(viewModel.hasUnsavedChanges)

        viewModel.simulatorDevice = "iPhone 16"
        assertTrue(viewModel.hasUnsavedChanges)

        val saved = viewModel.save().getOrThrow()
        assertEquals(SimulatorSettings(device = "iPhone 16", runtime = "iOS 18.5"), saved.config.simulator)
    }
}
