package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.EditorLauncher
import app.luxion.shogunai.domain.io.FolderLauncher
import app.luxion.shogunai.domain.io.TerminalLauncher
import app.luxion.shogunai.domain.io.ToolDetector
import app.luxion.shogunai.domain.io.ToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch

class FakeTerminalLauncher(
    override val terminal: TerminalId,
    var failure: Throwable? = null,
) : TerminalLauncher {

    val launches = mutableListOf<TerminalLaunch>()

    override suspend fun open(launch: TerminalLaunch): Result<Unit> {
        launches += launch
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}

class FakeEditorLauncher(
    override val editor: EditorId,
    var failure: Throwable? = null,
) : EditorLauncher {

    val opened = mutableListOf<String>()

    override suspend fun open(path: String): Result<Unit> {
        opened += path
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}

class FakeFolderLauncher(var failure: Throwable? = null) : FolderLauncher {

    val opened = mutableListOf<String>()

    override suspend fun open(path: String): Result<Unit> {
        opened += path
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}

class FakeToolDetector(var tools: DetectedTools = DetectedTools()) : ToolDetector {

    override suspend fun detect(): DetectedTools = tools
}

class FakeToolPreferenceRepository(
    var terminal: TerminalId? = null,
    var editor: EditorId? = null,
) : ToolPreferenceRepository {

    override fun loadTerminal(): TerminalId? = terminal

    override fun saveTerminal(terminal: TerminalId?) {
        this.terminal = terminal
    }

    override fun loadEditor(): EditorId? = editor

    override fun saveEditor(editor: EditorId?) {
        this.editor = editor
    }
}
