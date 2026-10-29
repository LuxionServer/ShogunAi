package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DisabledReasonUiTest {

    @Test
    fun `resting on a disabled control shows why`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DisabledReason("No daemon is idle") {
                    SecondaryButton(onClick = {}, enabled = false) { Text("Stop idle daemons") }
                }
            }
        }

        onNodeWithText("Stop idle daemons").assertIsNotEnabled()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No daemon is idle")).assertExists()

        onNodeWithText("Stop idle daemons").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)

        onNodeWithText("No daemon is idle").assertIsDisplayed()
    }

    @Test
    fun `without a reason nothing is added`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DisabledReason(null) { SecondaryButton(onClick = {}) { Text("Stop idle daemons") } }
            }
        }

        onNodeWithText("Stop idle daemons").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1000)

        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertDoesNotExist()
    }
}
