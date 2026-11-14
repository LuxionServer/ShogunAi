package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.TerminalId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolsPreferencesTest {

    private fun preferences(detected: DetectedTools, editor: EditorId? = null) =
        ToolsPreferences(FakeToolPreferenceRepository(editor = editor), FakeToolDetector(detected)).also { runBlocking { it.refresh() } }

    @Test
    fun `before detecting, every runner counts as installed`() {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector())

        assertEquals(RunnerId.entries, tools.installedRunners)
    }

    @Test
    fun `the chosen editor is shown while it is installed`() {
        val tools = preferences(DetectedTools(editors = EditorId.entries), editor = EditorId.entries.last())

        assertEquals(EditorId.entries.last(), tools.editorShown)
    }

    @Test
    fun `a chosen editor that is no longer installed gives way to the first installed one`() {
        val tools = preferences(DetectedTools(editors = listOf(EditorId.entries.first())), editor = EditorId.entries.last())

        assertEquals(EditorId.entries.first(), tools.editorShown)
    }

    @Test
    fun `the preferences that remember nothing load nothing and detect no tool`() = runBlocking<Unit> {
        NoToolPreferences.saveTerminal(TerminalId.entries.first())
        NoToolPreferences.saveEditor(EditorId.entries.first())
        NoToolPreferences.saveRunner(RunnerId.ORCA)

        assertNull(NoToolPreferences.loadTerminal())
        assertNull(NoToolPreferences.loadEditor())
        assertNull(NoToolPreferences.loadRunner())
        assertEquals(DetectedTools(), NoToolDetector.detect())
    }
}
