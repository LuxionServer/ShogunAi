package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InlineConfirmUiTest {

    @Test
    fun `the button says copied for a moment and then returns to its label`() = runComposeUiTest {
        var copies = 0
        setContent {
            MaterialTheme { InlineConfirmButton(label = "Copy snippet", onClick = { copies++ }) }
        }

        onNodeWithText("Copy snippet").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("Copied").assertIsDisplayed()

        mainClock.advanceTimeBy(InlineConfirmMillis)
        onNodeWithText("Copy snippet").assertIsDisplayed()
        assertEquals(1, copies)
    }
}
