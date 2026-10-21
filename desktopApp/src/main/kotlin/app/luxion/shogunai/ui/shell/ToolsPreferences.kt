package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.ToolDetector
import app.luxion.shogunai.domain.io.ToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToolsPreferences(
    private val repository: ToolPreferenceRepository,
    private val detector: ToolDetector,
) {
    var detected by mutableStateOf<DetectedTools?>(null)
        private set
    var chosenTerminal by mutableStateOf(repository.loadTerminal())
        private set
    var chosenEditor by mutableStateOf(repository.loadEditor())
        private set

    val availableTerminals: List<TerminalId> get() = detected?.terminals.orEmpty()
    val availableEditors: List<EditorId> get() = detected?.editors.orEmpty()

    val claudeDesktopAvailable: Boolean get() = detected?.claudeDesktop == true

    val terminalInUse: TerminalId? get() = chosenTerminal?.takeIf { it in availableTerminals }

    val terminalShown: TerminalId? get() = terminalInUse ?: availableTerminals.firstOrNull()

    val editorShown: EditorId? get() = chosenEditor?.takeIf { it in availableEditors } ?: availableEditors.firstOrNull()

    fun editorFor(override: EditorId?): EditorId? = override?.takeIf { it in availableEditors } ?: editorShown

    suspend fun refresh() {
        detected = withContext(Dispatchers.IO) { detector.detect() }
    }

    fun chooseTerminal(terminal: TerminalId) {
        chosenTerminal = terminal
        repository.saveTerminal(terminal)
    }

    fun chooseEditor(editor: EditorId) {
        chosenEditor = editor
        repository.saveEditor(editor)
    }

    companion object {
        val NONE = ToolsPreferences(
            repository = object : ToolPreferenceRepository {
                override fun loadTerminal(): TerminalId? = null
                override fun saveTerminal(terminal: TerminalId?) = Unit
                override fun loadEditor(): EditorId? = null
                override fun saveEditor(editor: EditorId?) = Unit
            },
            detector = object : ToolDetector {
                override suspend fun detect() = DetectedTools()
            },
        )
    }
}
