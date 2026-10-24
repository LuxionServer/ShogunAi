package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SelectorUiTest {

    @Test
    fun `dropdown ignores selection of a disabled option through semantics`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent {
            MaterialTheme {
                DropdownSelector(
                    options = listOf("develop", "main"),
                    selected = null,
                    onSelect = { selections += it },
                    label = { it },
                    placeholder = "Pick a branch",
                    enabled = { it != "main" },
                )
            }
        }

        onNodeWithText("Pick a branch").performClick()
        onNodeWithText("main").assertIsNotEnabled()
        onNodeWithText("main").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(emptyList(), selections)
        onNodeWithText("develop").assertIsDisplayed()
    }

    @Test
    fun `dropdown selects an enabled option and closes the menu`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent {
            MaterialTheme {
                DropdownSelector(
                    options = listOf("develop", "main"),
                    selected = null,
                    onSelect = { selections += it },
                    label = { it },
                    placeholder = "Pick a branch",
                )
            }
        }

        onNodeWithText("Pick a branch").performClick()
        onNodeWithText("develop").performClick()

        assertEquals(listOf("develop"), selections)
        onNodeWithText("develop").assertDoesNotExist()
    }

    @Test
    fun `dropdown group header toggles its options`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    options = listOf("feature/TASK-1", "main"),
                    selected = null,
                    onSelect = {},
                    label = { it },
                    placeholder = "Pick a branch",
                    groupBy = { it.substringBefore('/', "other") },
                )
            }
        }

        onNodeWithText("Pick a branch").performClick()
        onNodeWithText("feature/TASK-1").assertDoesNotExist()
        onNodeWithText("feature").performClick()
        onNodeWithText("feature/TASK-1").assertIsDisplayed()
    }

    @Test
    fun `segmented selector renders every label in full and reports clicks`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent {
            MaterialTheme {
                SegmentedSelector(
                    options = listOf("New branch", "Existing branch"),
                    selected = "New branch",
                    onSelect = { selections += it },
                    label = { it },
                )
            }
        }

        onNodeWithText("New branch").assertIsDisplayed()
        onNodeWithText("Existing branch").assertIsDisplayed()
        onNodeWithText("Existing branch").performClick()

        assertEquals(listOf("Existing branch"), selections)
    }

    @Test
    fun `segmented selector ignores clicks when disabled`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent {
            MaterialTheme {
                SegmentedSelector(
                    options = listOf("A", "B"),
                    selected = "A",
                    onSelect = { selections += it },
                    label = { it },
                    enabled = false,
                )
            }
        }

        onNodeWithText("B").assertIsNotEnabled()
        assertEquals(emptyList(), selections)
    }
}
