package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch

interface TerminalLauncher {
    val terminal: TerminalId

    suspend fun open(launch: TerminalLaunch): Result<Unit>
}

interface EditorLauncher {
    val editor: EditorId

    suspend fun open(path: String): Result<Unit>
}

interface FolderLauncher {
    suspend fun open(path: String): Result<Unit>
}

interface ClaudeDesktopLauncher {
    suspend fun open(path: String): Result<Unit>
}

interface ToolDetector {
    suspend fun detect(): DetectedTools
}
