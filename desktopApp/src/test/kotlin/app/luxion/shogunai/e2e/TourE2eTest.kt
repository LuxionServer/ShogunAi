package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.domain.usecase.TOUR_PROJECT_NAME
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TourE2eTest {

    @Test
    fun `without the command the tour shows its command step, creates the sample and ending it removes the sample`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = setShell(appContainer)

        awaitText(strings.tourTakeTour)
        onNodeWithText(strings.tourTakeTour).performClick()
        awaitText(strings.tourStart)
        onNodeWithText(strings.tourStart).performClick()
        awaitText(strings.tourSkip)
        awaitText(strings.commandLineToolStatus(CommandLineToolStatus(CommandLineToolState.MISSING)))
        onNodeWithText(strings.tourSkip).performClick()
        awaitCondition("the tour's open-agent step") {
            appContainer.tour.error?.let { throw AssertionError("The tour failed", it) }
            onAllNodesWithText(strings.tourLater).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(strings.tourLater).performClick()

        val tourFolder = environment.shogunaiFolder.resolve("tour")
        assertTrue(tourFolder.resolve("sample-repo").resolve(".git").exists())
        assertTrue(appContainer.projectRepository.list().any { it.name == TOUR_PROJECT_NAME })
        awaitText(TOUR_PROJECT_NAME)
        awaitText("needs-you", substring = true)

        clickUntil(strings.tourEnd, "the end-tour confirmation") { onAllNodesWithText(strings.tourEnd).fetchSemanticsNodes().size > 1 }
        onAllNodesWithText(strings.tourEnd).onLast().performClick()

        awaitCondition("the sample folder to be removed") {
            appContainer.tour.error?.let { throw AssertionError("Ending the tour failed", it) }
            !tourFolder.exists()
        }
        awaitNoText(TOUR_PROJECT_NAME)
        assertFalse(appContainer.projectRepository.list().any { it.name == TOUR_PROJECT_NAME })
        awaitText(strings.tourTakeTour)
    }

    @Test
    fun `with the command installed the tour creates the sample without its command step`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        environment.installFakeShogun()
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = setShell(appContainer)

        takeTourWithoutCommandStep(appContainer, strings)

        assertTrue(environment.shogunaiFolder.resolve("tour").resolve("sample-repo").resolve(".git").exists())
    }
}
