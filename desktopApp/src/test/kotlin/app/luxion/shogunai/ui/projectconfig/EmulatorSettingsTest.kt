package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
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

class EmulatorSettingsTest {

    private val dispatcher = StandardTestDispatcher()
    private val android = "/home/dev/projects/android-app"
    private val web = "/home/dev/projects/web-app"
    private val provisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(android to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
    )
    private val projectRepository = FakeProjectRepository()
    private val avdLoads = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun project(path: String, emulator: EmulatorSettings = EmulatorSettings()) = Project(
        id = "p",
        name = "App",
        config = ProjectConfig(baseRepositoryPath = path, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList(), emulator = emulator),
    )

    private fun viewModel(project: Project?, avds: Result<List<String>> = Result.success(listOf("Medium_Phone_API_35"))) =
        ProjectConfigViewModel(projectRepository, provisioning, project, loadAvds = { path -> avdLoads += path; avds })

    @Test
    fun `projects without the provider show no section and run no Android tool`() = runTest(dispatcher) {
        val viewModel = viewModel(project(web))
        advanceUntilIdle()

        assertEquals(EmulatorSectionState.Hidden, viewModel.emulatorSection)
        assertTrue(avdLoads.isEmpty())
    }

    @Test
    fun `projects with the provider list the machine's AVDs`() = runTest(dispatcher) {
        val viewModel = viewModel(project(android))
        assertEquals(EmulatorSectionState.Loading, viewModel.emulatorSection)
        advanceUntilIdle()

        assertEquals(EmulatorSectionState.Available(listOf("Medium_Phone_API_35")), viewModel.emulatorSection)
        assertEquals(listOf(android), avdLoads)
    }

    @Test
    fun `a missing SDK is reported with the places checked`() = runTest(dispatcher) {
        val viewModel = viewModel(project(android), Result.failure(WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME (/missing)"))))
        advanceUntilIdle()

        assertEquals(EmulatorSectionState.SdkNotFound(listOf("ANDROID_HOME (/missing)")), viewModel.emulatorSection)
    }

    @Test
    fun `the section follows the base repository`() = runTest(dispatcher) {
        val viewModel = viewModel(project(web))
        advanceUntilIdle()

        viewModel.baseRepositoryPath = android
        advanceUntilIdle()
        assertEquals(EmulatorSectionState.Available(listOf("Medium_Phone_API_35")), viewModel.emulatorSection)

        viewModel.baseRepositoryPath = web
        assertEquals(EmulatorSectionState.Hidden, viewModel.emulatorSection)
    }

    @Test
    fun `machine settings are saved with the project and count as unsaved changes`() = runTest(dispatcher) {
        val viewModel = viewModel(project(android))
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges)

        viewModel.emulatorAvd = "Medium_Phone_API_35"
        viewModel.emulatorNoWindow = true
        assertTrue(viewModel.hasUnsavedChanges)

        val saved = viewModel.save().getOrThrow()
        assertEquals(EmulatorSettings(avd = "Medium_Phone_API_35", noWindow = true), saved.config.emulator)
    }

    @Test
    fun `existing settings are loaded`() = runTest(dispatcher) {
        val viewModel = viewModel(project(android, EmulatorSettings(avd = "Pixel_8_API_35", noWindow = true)))

        assertEquals("Pixel_8_API_35", viewModel.emulatorAvd)
        assertTrue(viewModel.emulatorNoWindow)
    }
}
