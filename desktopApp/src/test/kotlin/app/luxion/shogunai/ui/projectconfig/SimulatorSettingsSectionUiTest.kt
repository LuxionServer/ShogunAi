package app.luxion.shogunai.ui.projectconfig

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SimulatorSettingsSectionUiTest {

    private val basePath = "/home/dev/projects/kmp-app"
    private val strings = EnglishStrings
    private val iphone = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16", "iPhone 16", "iPhone")
    private val ipad = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPad-Air", "iPad Air", "iPad")
    private val watch = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.Watch", "Apple Watch", "Apple Watch")
    private val ios18 = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-18-5", "iOS 18.5", "18.5", "iOS", listOf(iphone, watch))
    private val ios17 = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-17-5", "iOS 17.5", "17.5", "iOS", listOf(ipad))

    private fun viewModel(setup: UiTestSimulatorSetup, simulator: SimulatorSettings = SimulatorSettings()) = ProjectConfigViewModel(
        FakeProjectRepository(),
        FakeRepositoryProvisioningSource(),
        Project("p", "App", ProjectConfig(basePath, "/home/dev/projects", emptyList(), simulator = simulator)),
        simulatorSetup = setup,
    )

    @Test
    fun `the section shows while the runtimes load and shows a failure to list them`() = runComposeUiTest {
        val loading = viewModel(UiTestSimulatorSetup(runtimes = { awaitCancellation() }))
        val failing = viewModel(UiTestSimulatorSetup(runtimes = { Result.failure(IOException("simctl failed")) }))
        waitUntil { failing.simulatorSection is SimulatorSectionState.Failed }
        setSettingsContent {
            SimulatorSettingsSection(loading)
            SimulatorSettingsSection(failing)
        }

        assertEquals(2, onAllNodesWithText(strings.simulatorSection).fetchSemanticsNodes().size)
        assertEquals(SimulatorSectionState.Loading, loading.simulatorSection)
    }

    @Test
    fun `runtimes and devices offer what this machine has and name what it lacks`() = runComposeUiTest {
        val setup = UiTestSimulatorSetup(kotlinMultiplatform = false, runtimes = { Result.success(listOf(ios18, ios17)) })
        val viewModel = viewModel(setup, SimulatorSettings(device = "iPhone 12", runtime = "iOS 16.4"))
        waitUntil { viewModel.simulatorSection is SimulatorSectionState.Available }
        setSettingsContent { SimulatorSettingsSection(viewModel) }

        onNodeWithText(strings.notOnThisMachine("iOS 16.4")).performClick()
        onNodeWithText(strings.simulatorAutomatic).assertIsDisplayed()
        onNodeWithText("iOS 18.5").performClick()
        onNodeWithText(strings.notOnThisMachine("iPhone 12")).performClick()
        onNodeWithText("Apple Watch").assertDoesNotExist()
        onNodeWithText("iPhone 16").performClick()

        assertEquals("iOS 18.5", viewModel.simulatorRuntime)
        assertEquals("iPhone 16", viewModel.simulatorDevice)
        onNodeWithText(strings.simulatorTestsNotWired).assertDoesNotExist()
    }

    @Test
    fun `without a runtime chosen every runtime's devices are offered`() = runComposeUiTest {
        val setup = UiTestSimulatorSetup(kotlinMultiplatform = false, runtimes = { Result.success(listOf(ios18, ios17)) })
        val viewModel = viewModel(setup)
        waitUntil { viewModel.simulatorSection is SimulatorSectionState.Available }
        setSettingsContent { SimulatorSettingsSection(viewModel) }

        onAllNodesWithText(strings.simulatorAutomatic)[1].performClick()
        onNodeWithText("iPad Air").performClick()

        assertEquals("iPad Air", viewModel.simulatorDevice)
    }

    @Test
    fun `a KMP project without the init script offers the snippet and installs the script after confirming`() = runComposeUiTest {
        val setup = UiTestSimulatorSetup(runtimes = { Result.success(listOf(ios18)) }, install = Result.failure(IOException("read-only home")))
        val viewModel = viewModel(setup)
        waitUntil { viewModel.simulatorSection is SimulatorSectionState.Available }
        setSettingsContent { SimulatorSettingsSection(viewModel) }

        onNodeWithText(strings.simulatorTestsNotWired).assertIsDisplayed()
        onNodeWithText(strings.copySnippet).performClick()
        onNodeWithText(strings.installInitScript).performClick()
        onNodeWithText(strings.cancel).performClick()
        onNodeWithText(strings.installInitScriptTitle).assertDoesNotExist()
        onNodeWithText(strings.installInitScript).performClick()
        clickOutsideDialog()
        onNodeWithText(strings.installInitScriptTitle).assertDoesNotExist()
        onNodeWithText(strings.installInitScript).performClick()
        onNodeWithText(strings.install).performClick()
        onNodeWithContentDescription(strings.closeError).performClick()

        assertFalse((viewModel.simulatorSection as SimulatorSectionState.Available).testsWired)
    }

    @Test
    fun `a wired KMP project with the init script uninstalls it after confirming`() = runComposeUiTest {
        val setup = UiTestSimulatorSetup(runtimes = { Result.success(listOf(ios18)) }, wired = true, initScriptInstalled = true, uninstall = Result.failure(IOException("read-only home")))
        val viewModel = viewModel(setup)
        waitUntil { viewModel.simulatorSection is SimulatorSectionState.Available }
        setSettingsContent { SimulatorSettingsSection(viewModel) }

        onNodeWithText(strings.simulatorTestsWired).assertIsDisplayed()
        onNodeWithText(strings.uninstallInitScript).performClick()
        onNodeWithText(strings.cancel).performClick()
        onNodeWithText(strings.uninstallInitScript).performClick()
        clickOutsideDialog()
        onNodeWithText(strings.uninstallInitScriptTitle).assertDoesNotExist()
        onNodeWithText(strings.uninstallInitScript).performClick()
        onNodeWithText(strings.uninstall).performClick()
        onNodeWithContentDescription(strings.closeError).performClick()

        assertTrue((viewModel.simulatorSection as SimulatorSectionState.Available).initScriptInstalled)
    }

    @Test
    fun `a wired KMP project without the init script has nothing to uninstall`() = runComposeUiTest {
        val setup = UiTestSimulatorSetup(runtimes = { Result.success(listOf(ios18)) }, wired = true)
        val viewModel = viewModel(setup)
        waitUntil { viewModel.simulatorSection is SimulatorSectionState.Available }
        setSettingsContent { SimulatorSettingsSection(viewModel) }

        onNodeWithText(strings.simulatorTestsWired).assertIsDisplayed()
        onNodeWithText(strings.uninstallInitScript).assertDoesNotExist()
    }
}
