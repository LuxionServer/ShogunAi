package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MachineOverviewViewUiTest {

    @Test
    fun `lists the free desktop and the declared resources, each opening its view`() = runComposeUiTest {
        val items = machineStatusItems(
            listOf(MachineResource.DESKTOP_TURN, MachineResource.GRADLE_DAEMONS),
            mapOf(
                MachineResource.DESKTOP_TURN to MachineSummary(0),
                MachineResource.GRADLE_DAEMONS to MachineSummary(2, 1_073_741_824L, poolInUse = 2, poolLimit = 3),
            ),
            emptyMap(),
            EnglishStrings,
        )
        val opened = mutableListOf<MachineResource>()
        setContent {
            MaterialTheme {
                MachineOverviewView(items, providersDeclared = true, onOpen = { opened += it })
            }
        }

        onNodeWithText("Nobody holds the desktop.").assertIsDisplayed()
        onNodeWithText("2/3").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("machine-overview-no-providers").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("machine-overview-GRADLE_DAEMONS").performClick()
        waitForIdle()

        assertEquals(listOf(MachineResource.GRADLE_DAEMONS), opened)
    }

    @Test
    fun `without providers it explains how to get resources`() = runComposeUiTest {
        val items = machineStatusItems(listOf(MachineResource.DESKTOP_TURN), mapOf(MachineResource.DESKTOP_TURN to MachineSummary(0)), emptyMap(), EnglishStrings)
        setContent {
            MaterialTheme {
                MachineOverviewView(items, providersDeclared = false, onOpen = {})
            }
        }

        onNodeWithTag("machine-overview-DESKTOP_TURN").assertIsDisplayed()
        onNodeWithTag("machine-overview-no-providers").assertIsDisplayed()
        onNodeWithText(EnglishStrings.machineOverviewNoProviders).assertIsDisplayed()
    }
}
