package app.luxion.shogunai.ui.projectconfig

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EmulatorSettingsSectionUiTest {

    private val basePath = "/home/dev/projects/android-app"
    private val strings = EnglishStrings

    private fun viewModel(avd: String? = null, loadAvds: suspend (String) -> Result<List<String>>) = ProjectConfigViewModel(
        FakeProjectRepository(),
        FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        ),
        Project(
            id = "p",
            name = "App",
            config = ProjectConfig(basePath, "/home/dev/projects", emptyList(), emulator = EmulatorSettings(avd = avd)),
        ),
        loadAvds = loadAvds,
    )

    @Test
    fun `the section shows while the AVDs load`() = runComposeUiTest {
        val viewModel = viewModel { awaitCancellation() }
        setSettingsContent { EmulatorSettingsSection(viewModel) }

        onNodeWithText(strings.emulatorSection).assertIsDisplayed()
        onNodeWithText(strings.machineOnlyLabel).assertIsDisplayed()
        assertEquals(EmulatorSectionState.Loading, viewModel.emulatorSection)
    }

    @Test
    fun `a missing Android SDK names the folders checked`() = runComposeUiTest {
        val viewModel = viewModel { Result.failure(WorktreeError.AndroidSdkNotFound(listOf("/home/dev/Library/Android/sdk"))) }
        waitUntil { viewModel.emulatorSection is EmulatorSectionState.SdkNotFound }
        setSettingsContent { EmulatorSettingsSection(viewModel) }

        onNodeWithText(strings.emulatorSection).assertIsDisplayed()
    }

    @Test
    fun `an AVD listing failure is shown`() = runComposeUiTest {
        val viewModel = viewModel { Result.failure(IOException("adb failed")) }
        waitUntil { viewModel.emulatorSection is EmulatorSectionState.Failed }
        setSettingsContent { EmulatorSettingsSection(viewModel) }

        onNodeWithText(strings.emulatorSection).assertIsDisplayed()
    }

    @Test
    fun `choosing an AVD and the no window switch update the settings`() = runComposeUiTest {
        val viewModel = viewModel(avd = "Old_Phone") { Result.success(listOf("Pixel_8")) }
        waitUntil { viewModel.emulatorSection is EmulatorSectionState.Available }
        setSettingsContent { EmulatorSettingsSection(viewModel) }

        onNodeWithText(strings.emulatorMissingAvd("Old_Phone")).performClick()
        onNodeWithText(strings.emulatorAutomaticAvd).assertIsDisplayed()
        onNodeWithText("Pixel_8").performClick()
        onNode(isToggleable()).performClick()

        assertEquals("Pixel_8", viewModel.emulatorAvd)
        assertTrue(viewModel.emulatorNoWindow)
    }

    @Test
    fun `a hidden section shows nothing`() = runComposeUiTest {
        val viewModel = ProjectConfigViewModel(FakeProjectRepository(), FakeRepositoryProvisioningSource(), null)
        setSettingsContent { EmulatorSettingsSection(viewModel) }

        onNodeWithText(strings.emulatorSection).assertDoesNotExist()
    }
}
