package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class DeviceLabelTextUiTest {

    private fun SemanticsNodeInteraction.fontFamily(): FontFamily? {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.fontFamily
    }

    @Test
    fun `a mode is written in the interface font and the device type in the data font`() = runComposeUiTest {
        var dataFamily: FontFamily? = null
        setContent {
            MaterialTheme {
                dataFamily = ShogunTheme.typography.dataSmall.fontFamily
                DeviceLabelText(DeviceLabel(data = "iPhone 16", note = "Not created"))
            }
        }

        assertEquals(dataFamily, onNodeWithText("iPhone 16").fontFamily())
        assertNotEquals(dataFamily, onNodeWithText("Not created").fontFamily())
    }

    @Test
    fun `automatic is not written in the data font`() = runComposeUiTest {
        var dataFamily: FontFamily? = null
        setContent {
            MaterialTheme {
                dataFamily = ShogunTheme.typography.dataSmall.fontFamily
                DeviceLabelText(DeviceLabel(data = null, note = "Automatic"))
            }
        }

        assertNotEquals(dataFamily, onNodeWithText("Automatic").fontFamily())
    }
}
