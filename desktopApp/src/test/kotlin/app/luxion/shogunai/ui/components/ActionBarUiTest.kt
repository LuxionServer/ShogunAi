package app.luxion.shogunai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
class ActionBarUiTest {

    private val ran = mutableListOf<String>()

    private fun icons() = listOf(
        BarIcon(Icons.Default.ContentCopy, "Copy launch command", { ran += "copy" }, confirmsInPlace = true),
        BarIcon(Icons.Default.Terminal, "Open in iTerm2", { ran += "terminal" }),
        BarIcon(Icons.Default.FolderOpen, "Open folder", { ran += "folder" }, disabledReason = "The worktree's folder is missing"),
    )

    @Test
    fun `every icon-only control has a label, and the split button opens its menu`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionBar(
                    primary = {
                        SplitButton(
                            label = "Open in Herdr",
                            onClick = { ran += "herdr" },
                            menuLabel = "Open with…",
                            menu = listOf(MenuEntry.Item("Open in Orca", { ran += "orca" })),
                        )
                    },
                    icons = icons(),
                    moreLabel = "More actions",
                    more = listOf(MenuEntry.Item("Copy path", { ran += "path" })),
                )
            }
        }

        val iconOnly = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
                SemanticsMatcher("has no text") { SemanticsProperties.Text !in it.config },
        ).fetchSemanticsNodes()
        iconOnly.forEach { node ->
            assert(node.config.contains(SemanticsProperties.ContentDescription)) { "icon-only control without a label" }
        }
        assertEquals(5, iconOnly.size)

        onNodeWithText("Open in Herdr").performClick()
        onNodeWithContentDescription("Copy launch command").performClick()
        onNodeWithContentDescription("Copied").assertIsDisplayed()
        onNodeWithContentDescription("Open folder").assertIsNotEnabled()
        onNodeWithContentDescription("Open with…").performClick()
        onNodeWithText("Open in Orca").performClick()
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Copy path").performClick()

        assertEquals(listOf("herdr", "copy", "orca", "path"), ran)
    }

    @Test
    fun `an action bar holds at most three icons`() {
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        ActionBar(primary = {}, icons = icons() + icons().first(), moreLabel = "More actions", more = emptyList())
                    }
                }
            }
        }
    }
}
