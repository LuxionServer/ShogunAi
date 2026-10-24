package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.i18n.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ButtonLabelsUiTest {

    private val longLabel = "Forzar eliminación del worktree"

    private val buttons: List<Pair<String, @Composable (String) -> Unit>> = listOf(
        "primary" to { label -> PrimaryButton(onClick = {}) { Text(label) } },
        "secondary" to { label -> SecondaryButton(onClick = {}) { Text(label) } },
        "danger" to { label -> DangerButton(onClick = {}) { Text(label) } },
        "danger text" to { label -> DangerTextButton(onClick = {}) { Text(label) } },
        "neutral text" to { label -> NeutralTextButton(onClick = {}) { Text(label) } },
    )

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun `every shared button keeps a long label on one line in a narrow container`() = runComposeUiTest {
        buttons.forEach { (name, button) ->
            setContent {
                MaterialTheme {
                    Row(Modifier.width(80.dp)) { button(longLabel) }
                }
            }

            val layout = onNodeWithText(longLabel).textLayout()

            assertEquals(1, layout.lineCount, "$name button wrapped its label")
        }
    }

    @Test
    fun `dialog actions stay on one line in both languages`() = runComposeUiTest {
        listOf<Strings>(EnglishStrings, SpanishStrings).forEach { strings ->
            val labels = listOf(strings.cancel, strings.delete, strings.forceRemove, strings.cleanUp, strings.deleteBranchAnyway)
            labels.forEach { label ->
                setContent {
                    CompositionLocalProvider(LocalStrings provides strings) {
                        MaterialTheme {
                            Row(Modifier.width(120.dp)) {
                                DangerButton(onClick = {}) { Text(label) }
                            }
                        }
                    }
                }

                assertEquals(1, onNodeWithText(label).textLayout().lineCount, "\"$label\" wrapped")
            }
        }
    }

    @Test
    fun `every shared button shows a long label fully when it has room`() = runComposeUiTest {
        buttons.forEach { (name, button) ->
            setContent {
                MaterialTheme {
                    Row(Modifier.width(400.dp)) { button(longLabel) }
                }
            }

            val label = onNodeWithText(longLabel)

            assertEquals(1, label.textLayout().lineCount, "$name button wrapped its label")
            val button = label.onParent().getUnclippedBoundsInRoot()
            assertTrue(label.getUnclippedBoundsInRoot().width <= button.width, "$name button cut its label")
        }
    }
}
