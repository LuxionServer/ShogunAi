package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AgentIndicatorUiTest {

    private val model = AgentIndicatorModel(
        phase = AgentPhase.NEEDS_YOU,
        label = "Claude · needs you",
        moreCount = 1,
        tooltip = "Claude · needs you\nCodex · working\nState from Orca",
    )

    @Test
    fun `the full indicator shows the label and the count`() = runComposeUiTest {
        setContent { MaterialTheme { AgentIndicator(model) } }

        onNodeWithContentDescription("Claude · needs you +1").assertIsDisplayed()
    }

    @Test
    fun `the compact indicator keeps the label for assistive technologies and the tooltip`() = runComposeUiTest {
        setContent { MaterialTheme { AgentIndicator(model, compact = true) } }

        onNodeWithText("Claude · needs you").assertDoesNotExist()
        onNodeWithContentDescription("Claude · needs you +1").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        onNodeWithText(model.tooltip).assertIsDisplayed()
    }
}
