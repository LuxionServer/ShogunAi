package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PropertyListUiTest {

    @Test
    fun `copying a property hands its value over and confirms in place`() = runComposeUiTest {
        val copied = mutableListOf<String>()
        setContent {
            MaterialTheme {
                PropertyList(
                    listOf(Property("PORT", "4123", copyable = true), Property("APP_SUFFIX", "-TASK-123", copyable = true)),
                    onCopy = { copied += it },
                )
            }
        }

        onNodeWithText("-TASK-123").performMouseInput { moveTo(center) }
        onNodeWithContentDescription("Copy APP_SUFFIX").performClick()

        assertEquals(listOf("-TASK-123"), copied)
        onNodeWithContentDescription("Copied").assertIsDisplayed()
    }

    @Test
    fun `a truncated key and value show their full text in a tooltip`() = runComposeUiTest {
        val key = "COMPOSE_PROJECT_NAME_OF_THE_WORKTREE"
        val value = "main-repo-cli-task-123-with-a-long-suffix"
        setContent {
            MaterialTheme {
                Column(Modifier.width(240.dp)) { PropertyList(listOf(Property(key, value))) }
            }
        }

        onNodeWithText(value).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)
        onAllNodesWithText(value).assertCountEquals(2)
        onNodeWithText(key).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)
        onAllNodesWithText(key).assertCountEquals(2)
    }

    @Test
    fun `a property that fits shows no tooltip`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) { PropertyList(listOf(Property("PORT", "4123"))) }
            }
        }

        onNodeWithText("4123").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)
        onAllNodesWithText("4123").assertCountEquals(1)
    }

    @Test
    fun `a property that isn't copyable offers no copy action`() = runComposeUiTest {
        setContent {
            MaterialTheme { PropertyList(listOf(Property("Changes", "4"))) }
        }

        onNodeWithText("4").performMouseInput { moveTo(center) }
        onNodeWithContentDescription("Copy Changes").assertDoesNotExist()
    }

    @Test
    fun `a free row aligns its value with the other values`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    PropertyList(listOf(Property("Disk", "1.8 GB")))
                    PropertyRow("Emulator") { Text("emulator-5554") }
                }
            }
        }

        assertEquals(onNodeWithText("1.8 GB").getBoundsInRoot().left, onNodeWithText("emulator-5554").getBoundsInRoot().left)
    }
}
