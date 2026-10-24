package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ToolsSectionUiTest {

    private fun preferences(detected: DetectedTools, repository: FakeToolPreferenceRepository = FakeToolPreferenceRepository()) =
        ToolsPreferences(repository, FakeToolDetector(detected)).also { runBlocking { it.refresh() } }

    @Test
    fun `choosing a terminal and an IDE saves them`() = runComposeUiTest {
        val repository = FakeToolPreferenceRepository()
        val tools = preferences(
            DetectedTools(
                terminals = listOf(TerminalId.TERMINAL, TerminalId.ITERM2),
                editors = listOf(EditorId.VS_CODE, EditorId.ANDROID_STUDIO),
            ),
            repository,
        )
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ToolsSection(tools) } }
            }
        }

        onNode(hasText(TerminalId.TERMINAL.displayName) and hasClickAction()).performClick()
        onNodeWithText(TerminalId.ITERM2.displayName).performClick()
        onNode(hasText(EditorId.VS_CODE.displayName) and hasClickAction()).performClick()
        onNodeWithText(EditorId.ANDROID_STUDIO.displayName).performClick()

        assertEquals(TerminalId.ITERM2, repository.terminal)
        assertEquals(EditorId.ANDROID_STUDIO, repository.editor)
    }

    @Test
    fun `says so when no terminal or IDE was found`() = runComposeUiTest {
        val tools = preferences(DetectedTools())
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { ToolsSection(tools) } }
            }
        }

        onNodeWithText("No supported terminal found").assertIsDisplayed()
        onNodeWithText("No supported IDE found").assertIsDisplayed()
    }
}
