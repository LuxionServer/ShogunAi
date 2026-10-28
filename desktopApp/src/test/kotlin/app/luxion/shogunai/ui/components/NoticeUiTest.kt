package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NoticeUiTest {

    @Test
    fun `the icon is a button only when the notice asks for it`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                Column {
                    Notice(NoticeKind.INFO, "With an action", onDismiss = null, onIconClick = { clicks++ }, iconDescription = "More about it")
                    Notice(NoticeKind.INFO, "Without an action", onDismiss = null)
                }
            }
        }

        onNodeWithContentDescription("More about it").assertHasClickAction().performClick()

        assertEquals(1, clicks)
        onNodeWithText("Without an action").assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
