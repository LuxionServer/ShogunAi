package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ResourcesSectionUiTest {

    private val repository = FakeResourceLimitsRepository()

    private fun preferences(showsSimulators: Boolean = true, showsQuota: Boolean = true) = ResourcePreferences(
        repository,
        ResolveResourceLimitsUseCase(repository, FakeMachineCapacity()),
        showsSimulators,
        showsQuota,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(resources: ResourcePreferences) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme { Column { ResourcesSection(resources) } }
        }
    }

    @Test
    fun `Automatic is selected and shows its limits on this machine`() = runComposeUiTest {
        show(preferences())

        onAllNodesWithText("Automatic")[0].assertIsDisplayed()
        onAllNodesWithText("1 emulator · 1 iOS simulator · 2 Gradle daemons", substring = true)[0].assertIsDisplayed()
        onNode(isSelectable() and isSelected() and hasText("Automatic") and hasText("Gradle daemons", substring = true)).assertIsDisplayed()
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

    private val automaticQuotaHint = "A quarter of the free disk space plus what DerivedData takes, between 20 and 200 GB"

    @Test
    fun `the automatic quota is chosen by default and explained`() = runComposeUiTest {
        show(preferences())

        onNodeWithText(automaticQuotaHint, substring = true).assertIsSelected()
        assertEquals(DerivedDataQuotaMode.AUTOMATIC, repository.settings.quotaMode)
        assertTrue(onAllNodesWithText("GB").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `choosing a fixed size and typing it stores the quota`() = runComposeUiTest {
        show(preferences())

        onNodeWithText("Fixed size").performClick()
        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("40")

        assertEquals(DerivedDataQuotaMode.FIXED, repository.settings.quotaMode)
        assertEquals(40, repository.settings.derivedDataQuotaGigabytes)
    }

    @Test
    fun `an invalid quota shows the message and keeps the previous one`() = runComposeUiTest {
        repository.settings = repository.settings.copy(derivedDataQuotaGigabytes = 30)
        show(preferences())

        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("0")

        onNodeWithText("Must be a positive whole number, or no quota").assertIsDisplayed()
        assertEquals(30, repository.settings.derivedDataQuotaGigabytes)
    }

    @Test
    fun `going back to automatic keeps the fixed size for later`() = runComposeUiTest {
        repository.settings = repository.settings.copy(derivedDataQuotaGigabytes = 40)
        show(preferences())

        onNodeWithText(automaticQuotaHint, substring = true).performClick()
        assertEquals(DerivedDataQuotaMode.AUTOMATIC, repository.settings.quotaMode)
        onNodeWithText("Fixed size").performClick()

        assertEquals(DerivedDataQuotaMode.FIXED, repository.settings.quotaMode)
        onNodeWithText("40").assertIsDisplayed()
    }

    @Test
    fun `no quota stores the no quota mode`() = runComposeUiTest {
        repository.settings = repository.settings.copy(derivedDataQuotaGigabytes = 30)
        show(preferences())

        onNodeWithText("No quota").performClick()

        assertEquals(DerivedDataQuotaMode.NONE, repository.settings.quotaMode)
    }

    @Test
    fun `changing the profile keeps the quota`() = runComposeUiTest {
        repository.settings = repository.settings.copy(derivedDataQuotaGigabytes = 50)
        show(preferences())

        onNodeWithText("Light").performClick()

        assertEquals(CapacityProfile.LIGHT, repository.settings.profile)
        assertEquals(50, repository.settings.derivedDataQuotaGigabytes)
    }

    @Test
    fun `the quota is hidden off macOS`() = runComposeUiTest {
        show(preferences(showsQuota = false))

        assertEquals(0, onAllNodesWithText("DerivedData quota").fetchSemanticsNodes().size)
    }

    @Test
    fun `the lease duration shows its default and stores a new value immediately`() = runComposeUiTest {
        show(preferences(showsQuota = false))

        onNodeWithText("Device lease").assertIsDisplayed()
        onNodeWithText("20").assertIsDisplayed()
        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("30")

        assertEquals(30, repository.settings.leaseMinutes)
    }

    @Test
    fun `an invalid lease duration shows the message and keeps the previous one`() = runComposeUiTest {
        repository.settings = repository.settings.copy(leaseMinutes = 25)
        show(preferences(showsQuota = false))

        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput("0")

        onNodeWithText("Must be a positive whole number of minutes").assertIsDisplayed()
        assertEquals(25, repository.settings.leaseMinutes)
    }

    @Test
    fun `changing the profile keeps the lease duration`() = runComposeUiTest {
        repository.settings = repository.settings.copy(leaseMinutes = 25)
        show(preferences())

        onNodeWithText("Light").performClick()

        assertEquals(25, repository.settings.leaseMinutes)
    }

    @Test
    fun `the desktop turn duration shows its default and stores a new value immediately`() = runComposeUiTest {
        show(preferences(showsQuota = false))

        onNodeWithText("Desktop turn").assertIsDisplayed()
        onNodeWithText("10").assertIsDisplayed()
        onAllNodes(hasSetTextAction())[1].performTextClearance()
        onAllNodes(hasSetTextAction())[1].performTextInput("15")

        assertEquals(15, repository.settings.desktopTurnMinutes)
        assertEquals(20, repository.settings.leaseMinutes)
    }

    @Test
    fun `an invalid desktop turn duration shows the message and keeps the previous one`() = runComposeUiTest {
        show(preferences(showsQuota = false))

        onAllNodes(hasSetTextAction())[1].performTextClearance()
        onAllNodes(hasSetTextAction())[1].performTextInput("0")

        onNodeWithText("Must be a positive whole number of minutes").assertIsDisplayed()
        assertEquals(10, repository.settings.desktopTurnMinutes)
    }
}
