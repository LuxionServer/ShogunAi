package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.usecase.TOUR_PROJECT_NAME
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TourE2eTest {

    @Test
    fun `the tour creates the sample project and ending it removes the sample`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer()
        val strings = setShell(appContainer)

        awaitText(strings.tourTakeTour)
        onNodeWithText(strings.tourTakeTour).performClick()
        awaitText(strings.tourStart)
        onNodeWithText(strings.tourStart).performClick()
        awaitCondition("the tour's command step or its open-agent step") {
            appContainer.tour.error?.let { throw AssertionError("The tour failed", it) }
            listOf(strings.tourSkip, strings.tourLater).any { label ->
                onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (onAllNodesWithText(strings.tourSkip).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithText(strings.tourSkip).performClick()
        }
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

        onNodeWithText(strings.tourEnd).performClick()
        awaitCondition("the end-tour confirmation") { onAllNodesWithText(strings.tourEnd).fetchSemanticsNodes().size > 1 }
        onAllNodesWithText(strings.tourEnd).onLast().performClick()

        awaitCondition("the sample folder to be removed") { !tourFolder.exists() }
        awaitNoText(TOUR_PROJECT_NAME)
        assertFalse(appContainer.projectRepository.list().any { it.name == TOUR_PROJECT_NAME })
        awaitText(strings.tourTakeTour)
    }
}
