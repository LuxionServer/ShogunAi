package app.luxion.shogunai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.diagnostics.FakeDiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticKind
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ErrorNoticeDiagnosticsUiTest {

    @Test
    fun `showing an error records one entry and recomposing adds none`() = runComposeUiTest {
        val log = FakeDiagnosticsLog()
        val error = IllegalStateException("boom")
        var recompositions by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings, LocalDiagnosticsLog provides log) {
                MaterialTheme {
                    recompositions
                    ErrorNotice(error = error, onDismiss = null)
                }
            }
        }
        waitForIdle()
        recompositions++
        waitForIdle()
        Thread.sleep(200)

        assertEquals(1, log.entries.size)
        assertEquals(DiagnosticKind.SHOWN_ERROR, log.entries.single().kind)
    }
}
