package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MarkdownTextUiTest {

    @Test
    fun `clicking a link opens its URL`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            MaterialTheme {
                MarkdownText("## Fixes\n\n- Read [the guide](https://example.test/guide) first", onOpenUrl = { opened += it })
            }
        }

        onNodeWithText("Fixes").assertIsDisplayed()
        onNodeWithText("Read the guide first").performFirstLinkClick()

        assertEquals(listOf("https://example.test/guide"), opened)
    }
}
