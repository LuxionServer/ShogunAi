package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FieldUiTest {

    @Test
    fun `the label and the help are shown around the input`() = runComposeUiTest {
        setContent {
            MaterialTheme { Field(value = "", onValueChange = {}, label = "Name", help = "Shown in the sidebar") }
        }

        onNodeWithText("Name").assertIsDisplayed()
        onNodeWithText("Shown in the sidebar").assertIsDisplayed()
    }

    @Test
    fun `an error replaces the help`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Field(value = "", onValueChange = {}, label = "Name", help = "Shown in the sidebar", error = "Enter a name")
            }
        }

        onNodeWithText("Name").assertIsDisplayed()
        onNodeWithText("Enter a name").assertIsDisplayed()
        onNodeWithText("Shown in the sidebar").assertDoesNotExist()
    }

    @Test
    fun `typing goes through the input`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme {
                Field(value = value, onValueChange = { value = it }, label = "Task", inputModifier = Modifier.testTag("task"))
            }
        }

        onNodeWithTag("task").performTextInput("TASK-123")

        assertEquals("TASK-123", value)
    }

    @Test
    fun `a path field offers browse`() = runComposeUiTest {
        var browsed = 0
        setContent {
            MaterialTheme {
                Column { PathField(value = "~/projects/main-repo", onValueChange = {}, onBrowse = { browsed++ }, label = "Folder") }
            }
        }

        onNodeWithText("Browse…").performClick()

        assertEquals(1, browsed)
    }
}
