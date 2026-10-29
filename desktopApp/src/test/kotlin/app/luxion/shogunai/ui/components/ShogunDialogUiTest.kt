package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShogunDialogUiTest {

    @Test
    fun `the title is a heading and the footer puts dismiss before confirm`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ShogunDialog(
                    title = "Remove project?",
                    onDismissRequest = {},
                    confirm = { DangerButton(onClick = {}) { Text("Remove") } },
                    dismiss = { NeutralTextButton(onClick = {}) { Text("Cancel") } },
                ) {
                    Text("The worktrees stay on disk.")
                }
            }
        }

        onNodeWithText("Remove project?").assert(isHeading)
        val cancel = onNodeWithText("Cancel").getBoundsInRoot()
        val remove = onNodeWithText("Remove").getBoundsInRoot()
        assertTrue(cancel.right <= remove.left)
    }

    @Test
    fun `a long body scrolls and keeps the footer visible`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ShogunDialog(
                    title = "Release notes",
                    onDismissRequest = {},
                    confirm = { PrimaryButton(onClick = {}) { Text("Update") } },
                ) {
                    Spacer(Modifier.height(5000.dp))
                    Text("End of notes")
                }
            }
        }

        onNodeWithText("Release notes").assertIsDisplayed()
        onNodeWithText("Update").assertIsDisplayed()
        onNodeWithText("End of notes").assertExists()
    }
}
