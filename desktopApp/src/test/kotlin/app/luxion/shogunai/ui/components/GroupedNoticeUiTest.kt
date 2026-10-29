package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupedNoticeUiTest {

    @Test
    fun `one banner shows the summary, the count and folded details`() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme {
                GroupedNotice(
                    kind = NoticeKind.INFO,
                    summary = "4 idle Gradle daemons were stopped",
                    details = listOf("First report", "Second report", "Third report"),
                    onDismiss = { dismissed++ },
                )
            }
        }

        onNodeWithText("4 idle Gradle daemons were stopped").assertIsDisplayed()
        onNodeWithText("Second report").assertDoesNotExist()

        onNodeWithText("Details (3)").performClick()
        onNodeWithText("Second report").assertIsDisplayed()

        onNodeWithContentDescription("Close message").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a single notice has no details`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                GroupedNotice(kind = NoticeKind.INFO, summary = "Only one", details = listOf("Only one"), onDismiss = {})
            }
        }

        onNodeWithText("Details", substring = true).assertDoesNotExist()
    }
}
