package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.machine.MachineViewFrame
import app.luxion.shogunai.ui.worktree.StateGroupHeader
import app.luxion.shogunai.ui.worktree.StateGroupKind
import kotlin.test.Test

val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

@OptIn(ExperimentalTestApi::class)
class TitlesUiTest {

    @Test
    fun `view, section and group titles are headings`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    ViewTitle("All worktrees")
                    SectionTitle("General")
                    GroupTitle("main-repo")
                    Text("Plain text")
                }
            }
        }

        onNodeWithText("All worktrees").assert(isHeading)
        onNodeWithText("General").assert(isHeading)
        onNodeWithText("main-repo").assert(isHeading)
        onNodeWithText("Plain text").assert(!isHeading)
    }

    @Test
    fun `a section card, a machine view and a state group header declare their titles`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    SectionCard("Worktree files") { Text("Body") }
                    StateGroupHeader(StateGroupKind.MERGED, count = 1, canCleanUp = true, onCleanUp = {})
                }
            }
        }

        onNodeWithText("Worktree files").assert(isHeading)
        onNodeWithText("Merged").assert(isHeading)
    }

    @Test
    fun `a machine view title is a heading`() = runComposeUiTest {
        setContent {
            MaterialTheme { MachineViewFrame("Gradle daemons") { Text("Body") } }
        }

        onNodeWithText("Gradle daemons").assert(isHeading)
    }
}
