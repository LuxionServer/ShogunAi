package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class DataRowUiTest {

    private val name = ColumnSpec()
    private val size = ColumnSpec(width = 80.dp, compact = CompactBehavior.Shrink(40.dp))
    private val owner = ColumnSpec(width = 120.dp, compact = CompactBehavior.Hide)

    private fun cells(row: String, sizeText: String?) = listOf(
        DataCell(name, Modifier.testTag("$row-name")) { Text(row) },
        DataCell(size, Modifier.testTag("$row-size")) { sizeText?.let { Text(it) } },
        DataCell(owner, Modifier.testTag("$row-owner")) { Text("owner-$row") },
    )

    @Test
    fun `fixed columns line up across rows even when a cell is empty`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(600.dp)) {
                    DataRow(cells("first", "1.2 GB"))
                    DataRow(cells("second", null))
                }
            }
        }

        assertEquals(
            onNodeWithTag("first-owner").getBoundsInRoot().left,
            onNodeWithTag("second-owner").getBoundsInRoot().left,
        )
    }

    @Test
    fun `compact mode hides and shrinks columns`() = runComposeUiTest {
        setContent {
            MaterialTheme { Column(Modifier.width(600.dp)) { DataRow(cells("row", "1.2 GB"), compact = true) } }
        }

        onNodeWithTag("row-size").assertWidthIsEqualTo(40.dp)
        onNodeWithText("owner-row").assertDoesNotExist()
    }

    @Test
    fun `rows have their minimum heights`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    DataRow(cells("one", null), Modifier.testTag("one-line"), lines = RowLines.ONE)
                    DataRow(cells("two", null), Modifier.testTag("two-lines"))
                }
            }
        }

        onNodeWithTag("one-line").assertHeightIsAtLeast(32.dp)
        onNodeWithTag("two-lines").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `enter selects a focused row`() = runComposeUiTest {
        var selected = 0
        setContent {
            MaterialTheme { DataRow(cells("row", null), Modifier.testTag("row"), onSelect = { selected++ }) }
        }

        onNodeWithTag("row").requestFocus()
        onNodeWithTag("row").performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, selected)
    }

    @Test
    fun `a row without selection takes no focus`() = runComposeUiTest {
        setContent {
            MaterialTheme { DataRow(cells("row", null), Modifier.testTag("row")) }
        }

        assertFalse(isFocusable().matches(onNodeWithTag("row").fetchSemanticsNode()))
    }

    @Test
    fun `the action slot shows on hover and while pinned`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    DataRow(
                        cells("hovered", null),
                        Modifier.testTag("hovered"),
                        actions = ActionSlot(width = 28.dp) { Text("hover action") },
                    )
                    DataRow(
                        cells("pinned", null),
                        actions = ActionSlot(width = 28.dp, pinned = true) { Text("pinned action") },
                    )
                }
            }
        }

        onNodeWithText("hover action").assertDoesNotExist()
        onNodeWithText("pinned action").assertIsDisplayed()
        onNodeWithTag("hovered").performMouseInput { moveTo(center) }
        onNodeWithText("hover action").assertIsDisplayed()
    }

    @Test
    fun `header labels start where their cells start`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(600.dp)) {
                    DataHeader(listOf(name to "Device", size to "Size", owner to "Worktree"))
                    DataRow(cells("row", "1.2 GB"))
                }
            }
        }

        assertEquals(
            onNodeWithText("WORKTREE").getBoundsInRoot().left,
            onNodeWithTag("row-owner").getBoundsInRoot().left,
        )
    }
}
