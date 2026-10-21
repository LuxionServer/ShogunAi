package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ResourcesSectionUiTest {

    private val repository = FakeResourceLimitsRepository()

    private fun preferences(showsSimulators: Boolean = true) = ResourcePreferences(
        repository,
        ResolveResourceLimitsUseCase(repository, FakeMachineCapacity()),
        showsSimulators,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(resources: ResourcePreferences) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme { Column { ResourcesSection(resources) } }
        }
    }

    @Test
    fun `Automatic is selected and shows its limits on this machine`() = runComposeUiTest {
        show(preferences())

        onNodeWithText("Automatic").assertIsDisplayed()
        onAllNodesWithText("1 emulator · 1 iOS simulator · 2 Gradle daemons", substring = true)[0].assertIsDisplayed()
        onNode(isSelectable() and isSelected() and hasText("Automatic")).assertIsDisplayed()
    }

    @Test
    fun `the iOS simulator line only appears on macOS`() = runComposeUiTest {
        show(preferences(showsSimulators = false))

        onAllNodesWithText("1 emulator · 2 Gradle daemons", substring = true)[0].assertIsDisplayed()
        assertEquals(0, onAllNodesWithText("iOS simulator", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `choosing a profile saves it`() = runComposeUiTest {
        show(preferences())

        onNodeWithText("Balanced").performClick()

        assertEquals(CapacityProfile.BALANCED, repository.settings.profile)
    }

    @Test
    fun `choosing Custom and typing a limit saves it`() = runComposeUiTest {
        show(preferences())

        onNodeWithText("Custom").performClick()
        onAllNodesWithText("Emulators")[0].assertIsDisplayed()
        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("3")

        assertEquals(CapacityProfile.CUSTOM, repository.settings.profile)
        assertEquals(3, repository.settings.custom.emulators)
    }

    @Test
    fun `an invalid limit shows the message and keeps the previous value`() = runComposeUiTest {
        repository.settings = repository.settings.copy(profile = CapacityProfile.CUSTOM, custom = ResourceLimits(2, 2, 2))
        show(preferences())

        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("0")

        onNodeWithText("Must be a positive number, or no limit").assertIsDisplayed()
        assertEquals(2, repository.settings.custom.emulators)
    }

    @Test
    fun `no limit stores a null limit`() = runComposeUiTest {
        repository.settings = repository.settings.copy(profile = CapacityProfile.CUSTOM, custom = ResourceLimits(2, 2, 2))
        show(preferences())

        onAllNodesWithText("No limit")[0].performClick()

        assertEquals(null, repository.settings.custom.emulators)
    }
}
