package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.TerminalId

interface ToolPreferenceRepository {
    fun loadTerminal(): TerminalId?
    fun saveTerminal(terminal: TerminalId?)
    fun loadEditor(): EditorId?
    fun saveEditor(editor: EditorId?)
    fun loadRunner(): RunnerId?
    fun saveRunner(runner: RunnerId?)
}
