package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.ui.theme.ShogunAiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ButtonsUiTest {

    private val buttons: List<Pair<String, @Composable (ButtonSize, Boolean, () -> Unit) -> Unit>> = listOf(
        "primary" to { size, enabled, onClick -> PrimaryButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
        "secondary" to { size, enabled, onClick -> SecondaryButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
        "ghost" to { size, enabled, onClick -> GhostButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
        "danger" to { size, enabled, onClick -> DangerButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
        "danger text" to { size, enabled, onClick -> DangerTextButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
        "neutral text" to { size, enabled, onClick -> NeutralTextButton(onClick = onClick, enabled = enabled, size = size) { Text(LABEL) } },
    )

    @Test
    fun `every button is 28 dp compact and 32 dp regular`() = runComposeUiTest {
        buttons.forEach { (_, button) ->
            listOf(ButtonSize.Compact to 28.dp, ButtonSize.Regular to 32.dp).forEach { (size, height) ->
                setContent { Themed(ThemeMode.LIGHT) { button(size, true) {} } }

                onNodeWithText(LABEL).assertHasClickAction().assertHeightIsEqualTo(height)
            }
        }
    }

    @Test
    fun `buttons are compact by default`() = runComposeUiTest {
        setContent { Themed(ThemeMode.DARK) { SecondaryButton(onClick = {}) { Text(LABEL) } } }

        onNodeWithText(LABEL).assertHeightIsEqualTo(ButtonSize.Compact.height)
    }

    @Test
    fun `an enabled button runs its action and a disabled one doesn't`() = runComposeUiTest {
        buttons.forEach { (name, button) ->
            listOf(true, false).forEach { enabled ->
                var clicks = 0
                setContent { Themed(ThemeMode.LIGHT) { button(ButtonSize.Compact, enabled) { clicks++ } } }

                val node = onNodeWithText(LABEL)
                if (!enabled) node.assertIsNotEnabled()
                node.performClick()

                assertEquals(if (enabled) 1 else 0, clicks, "$name enabled=$enabled")
            }
        }
    }

    @Composable
    private fun Themed(mode: ThemeMode, content: @Composable () -> Unit) {
        ShogunAiTheme(mode) { Box { content() } }
    }

    private companion object {
        const val LABEL = "Clean up"
    }
}
