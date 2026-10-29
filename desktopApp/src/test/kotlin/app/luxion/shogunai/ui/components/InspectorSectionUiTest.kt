package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class InspectorSectionUiTest {

    @Test
    fun `each state draws its own content and the label is a heading`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    InspectorSection(id = "loading", title = "Status", state = SectionState.Loading) { Text("status body") }
                    InspectorSection(id = "empty", title = "Agent", state = SectionState.Empty("No agent running")) { Text("agent body") }
                    InspectorSection(id = "content", title = "Environment") { Text("PORT 4123") }
                    InspectorSection(id = "none", title = "Provisioning", state = SectionState.NotApplicable) { Text("provisioning body") }
                }
            }
        }

        onNodeWithContentDescription("Loading").assertIsDisplayed()
        onNodeWithText("status body").assertDoesNotExist()
        onNodeWithText("No agent running").assertIsDisplayed()
        onNodeWithText("agent body").assertDoesNotExist()
        onNodeWithText("PORT 4123").assertIsDisplayed()
        onNodeWithText("PROVISIONING").assertDoesNotExist()
        onNodeWithText("provisioning body").assertDoesNotExist()
        listOf("STATUS", "AGENT", "ENVIRONMENT").forEach { onNodeWithText(it).assert(isHeading) }
    }

    @Test
    fun `collapsing hides the content and keeps the summary`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                InspectorSection(id = "environment", title = "Environment", summary = "2 ports · 5 vars") { Text("PORT 4123") }
            }
        }

        onNodeWithText("ENVIRONMENT").performClick()
        onNodeWithText("PORT 4123").assertDoesNotExist()
        onNodeWithText("2 ports · 5 vars").assertIsDisplayed()

        onNodeWithText("ENVIRONMENT").performClick()
        onNodeWithText("PORT 4123").assertIsDisplayed()
    }
}
