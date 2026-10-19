package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextInputFocusUiTest {

    @Test
    fun `a focused text field marks text editing until it loses focus or leaves`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        setContent {
            MaterialTheme {
                if (shown) {
                    OutlinedTextField(value = "text", onValueChange = {}, modifier = Modifier.trackTextInputFocus())
                }
            }
        }
        assertFalse(TextInputFocus.isActive)

        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        assertTrue(TextInputFocus.isActive)

        shown = false
        waitForIdle()
        assertFalse(TextInputFocus.isActive)
    }
}
