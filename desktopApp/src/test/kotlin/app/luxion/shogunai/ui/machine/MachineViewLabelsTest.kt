package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachineViewLabelsTest {

    private fun project(emulator: EmulatorSettings = EmulatorSettings(), simulator: SimulatorSettings = SimulatorSettings()) = Project(
        id = "p",
        name = "Main repo",
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/main-repo",
            worktreesRoot = "/home/dev/projects",
            secretFiles = emptyList(),
            emulator = emulator,
            simulator = simulator,
        ),
    )

    private val worktreePath = "/home/dev/projects/TASK-1"

    @Test
    fun `column labels are shown for the device views`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { MachineColumnHeader() }
            }
        }

        onNodeWithText("DEVICE").assertIsDisplayed()
        onNodeWithText("WORKTREE").assertIsDisplayed()
        onNodeWithText("STATE").assertIsDisplayed()
    }

    @Test
    fun `a stopped emulator shows the project's AVD or automatic`() {
        val stopped = MachineEmulator("p", worktreePath, null)

        assertEquals("Pixel_8_API_35", emulatorDeviceLabel(stopped, project(EmulatorSettings(avd = "Pixel_8_API_35")), EnglishStrings))
        assertEquals("Automatic", emulatorDeviceLabel(stopped, project(), EnglishStrings))
    }

    @Test
    fun `a running emulator shows its serial`() {
        val running = MachineEmulator("p", worktreePath, WorktreeEmulator("emulator-5554", EmulatorState.RUNNING))

        assertEquals("emulator-5554", emulatorDeviceLabel(running, project(EmulatorSettings(avd = "Pixel_8_API_35")), EnglishStrings))
    }

    @Test
    fun `a missing simulator shows not created with the configured device type`() {
        val missing = MachineSimulator("p", worktreePath, null)

        assertEquals("Not created · iPhone 16", simulatorDeviceLabel(missing, project(simulator = SimulatorSettings(device = "iPhone 16")), EnglishStrings))
        assertEquals("Not created", simulatorDeviceLabel(missing, project(), EnglishStrings))
    }

    @Test
    fun `an existing simulator shows its name`() {
        val existing = MachineSimulator("p", worktreePath, WorktreeSimulator("udid", "ShogunAi · main-repo · TASK-1", SimulatorState.SHUTDOWN))

        assertEquals("ShogunAi · main-repo · TASK-1", simulatorDeviceLabel(existing, project(), EnglishStrings))
    }
}
