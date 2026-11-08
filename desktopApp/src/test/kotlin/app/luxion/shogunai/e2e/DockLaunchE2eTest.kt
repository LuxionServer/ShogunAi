package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DockLaunchE2eTest {

    @BeforeTest
    fun onlyWithALoginShell() {
        assumeTrue(HostOs.current() != HostOs.WINDOWS)
    }

    @Test
    fun `opened from the Dock, the hooks section reads a command only the login shell's PATH has as available`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        environment.installFakeShogun()
        val hostEnvironment = environment.dockHostEnvironment()
        val appContainer = AppContainer(hostEnvironment)
        val strings = setHooksSection(appContainer)

        awaitText(strings.commandLineToolStatus(CommandLineToolStatus(CommandLineToolState.AVAILABLE)))
        onNode(hasClickAction() and hasText(strings.claudeHooksInstall)).assertIsEnabled()
        assertEquals(HostPathSource.LOGIN_SHELL, hostEnvironment.path.source)
    }

    @Test
    fun `opened from the Dock, the tour creates the sample without its command step`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        environment.installFakeShogun()
        val appContainer = AppContainer(environment.dockHostEnvironment())
        val strings = setShell(appContainer)

        takeTourWithoutCommandStep(appContainer, strings)
    }
}
