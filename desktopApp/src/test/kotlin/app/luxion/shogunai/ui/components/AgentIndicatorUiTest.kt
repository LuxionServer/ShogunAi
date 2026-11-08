package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `the compact indicator measures the icon of each size`() {
        AgentIndicatorSize.entries.forEach { size ->
            runComposeUiTest {
                setContent { MaterialTheme { AgentIndicator(model, compact = true, size = size) } }

                onNodeWithContentDescription("Claude · needs you +1")
                    .assertWidthIsEqualTo(size.icon)
                    .assertHeightIsEqualTo(size.icon)
            }
        }
    }

    @Test
    fun `the icon is drawn in the state color`() = runComposeUiTest {
        var warning = Color.Unspecified
        setContent {
            MaterialTheme {
                warning = ShogunTheme.status.of(AgentPhase.NEEDS_YOU.status).color
                AgentIndicator(model, compact = true, size = AgentIndicatorSize.LARGE)
            }
        }

        val pixels = onNodeWithContentDescription("Claude · needs you +1").captureToImage().toPixelMap()
        val colors = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }
        assertTrue(colors.any { it.isCloseTo(warning) }, "no pixel of the icon has the warning color")
    }

    @Test
    fun `a clickable indicator is a button that runs its action`() {
        listOf(false, true).forEach { compact ->
            runComposeUiTest {
                var clicks = 0
                setContent { MaterialTheme { AgentIndicator(model, compact = compact, onClick = { clicks++ }) } }

                onNodeWithContentDescription("Claude · needs you +1")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    .performClick()

                assertEquals(1, clicks, "compact = $compact")
            }
        }
    }

    @Test
    fun `an indicator without an action can't be clicked`() = runComposeUiTest {
        setContent { MaterialTheme { AgentIndicator(model) } }

        onNodeWithContentDescription("Claude · needs you +1")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    private fun Color.isCloseTo(other: Color) =
        alpha > 0.95f &&
            abs(red - other.red) < 0.02f &&
            abs(green - other.green) < 0.02f &&
            abs(blue - other.blue) < 0.02f
}
