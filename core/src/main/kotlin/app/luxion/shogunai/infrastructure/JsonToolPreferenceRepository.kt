package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ToolPreferenceRepository
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.TerminalId

class JsonToolPreferenceRepository(
    private val settingsFile: JsonSettingsFile,
) : ToolPreferenceRepository {

    override fun loadTerminal(): TerminalId? = settingsFile.read().terminal

    override fun saveTerminal(terminal: TerminalId?) {
        settingsFile.update { it.copy(terminal = terminal) }
    }

    override fun loadEditor(): EditorId? = settingsFile.read().editor

    override fun saveEditor(editor: EditorId?) {
        settingsFile.update { it.copy(editor = editor) }
    }

    override fun loadRunner(): RunnerId? = settingsFile.read().runner

    override fun saveRunner(runner: RunnerId?) {
        settingsFile.update { it.copy(runner = runner) }
    }
}
