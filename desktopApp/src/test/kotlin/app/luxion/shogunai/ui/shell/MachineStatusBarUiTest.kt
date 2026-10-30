package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineSummary
import app.luxion.shogunai.ui.machine.machineStatusItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MachineStatusBarUiTest {

    private val items = machineStatusItems(
        listOf(MachineResource.GRADLE_DAEMONS, MachineResource.XCODE_DERIVED_DATA),
        mapOf(MachineResource.GRADLE_DAEMONS to MachineSummary(9, 1_288_490_189L, poolInUse = 9, poolLimit = 3)),
        emptyMap(),
        EnglishStrings,
    )

    @Test
    fun `each item is a button labelled with its full text that opens its view`() = runComposeUiTest {
        var selected by mutableStateOf<MachineResource?>(null)
        setContent {
            MaterialTheme {
                MachineStatusBar(items, selected = selected, compact = false, onSelect = { selected = it })
            }
        }

        val gradle = onNodeWithContentDescription("Gradle daemons: 9 of 3 · 1.2 GB · over the limit")
        gradle.assertIsDisplayed()
        gradle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        gradle.performClick()
        waitForIdle()

        assertEquals(MachineResource.GRADLE_DAEMONS, selected)
        onNodeWithTag("machine-status-GRADLE_DAEMONS").assertIsSelected()
    }

    @Test
    fun `at the bottom of the window the tooltip opens above the item and lets the click through`() = runComposeUiTest {
        var selected by mutableStateOf<MachineResource?>(null)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    MachineStatusBar(
                        items,
                        selected = selected,
                        compact = false,
                        onSelect = { selected = it },
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }

        val gradle = onNodeWithTag("machine-status-GRADLE_DAEMONS")
        gradle.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)
        val tooltip = onNodeWithText("Gradle daemons: 9 of 3 · 1.2 GB · over the limit").getBoundsInRoot()
        assertTrue(tooltip.bottom <= gradle.getBoundsInRoot().top)

        gradle.performMouseInput { click(center) }
        waitForIdle()

        assertEquals(MachineResource.GRADLE_DAEMONS, selected)
    }

    @Test
    fun `the full layout shows names and figures, and a dash while not measured`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MachineStatusBar(items, selected = null, compact = false, onSelect = {})
            }
        }

        onNodeWithContentDescription("Xcode DerivedData: not measured yet").assertIsDisplayed()
        assertEquals(1, onAllNodesWithText("Gradle", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("9 of 3", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("–", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `the compact layout drops the names and keeps the figures`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MachineStatusBar(items, selected = null, compact = true, onSelect = {})
            }
        }

        assertTrue(onAllNodesWithText("Gradle", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals(1, onAllNodesWithText("9 of 3", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
