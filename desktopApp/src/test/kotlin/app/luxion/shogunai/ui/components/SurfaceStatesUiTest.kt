package app.luxion.shogunai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SurfaceStatesUiTest {

    @Test
    fun `an empty state offers one action and a link`() = runComposeUiTest {
        var created = 0
        var seen = 0
        setContent {
            MaterialTheme {
                EmptyState(
                    icon = Icons.Default.Inbox,
                    message = "No worktree has activity",
                    action = StateAction("New worktree") { created++ },
                    link = StateAction("See all worktrees") { seen++ },
                )
            }
        }

        onNodeWithText("No worktree has activity").assertIsDisplayed()
        onNodeWithText("New worktree").performClick()
        onNodeWithText("See all worktrees").performClick()

        assertEquals(1, created)
        assertEquals(1, seen)
    }

    @Test
    fun `an error state folds the raw message until technical details are opened`() = runComposeUiTest {
        var retries = 0
        setContent {
            MaterialTheme {
                ErrorState(
                    message = "Docker isn't available",
                    action = StateAction("Try again") { retries++ },
                    technicalDetails = "Cannot connect to the Docker daemon at unix:///var/run/docker.sock",
                )
            }
        }

        onNodeWithText("Docker isn't available").assertIsDisplayed()
        onNodeWithText("Cannot connect", substring = true).assertDoesNotExist()

        onNodeWithText("Technical details").performClick()
        onNodeWithText("Cannot connect", substring = true).assertIsDisplayed()

        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `skeleton rows are one loading node with nothing to click`() = runComposeUiTest {
        setContent {
            MaterialTheme { SkeletonRows(description = "Loading worktrees…") }
        }

        onAllNodesWithContentDescription("Loading worktrees…").assertCountEquals(1)
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
