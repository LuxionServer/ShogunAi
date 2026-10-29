package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ToolDetector
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.TerminalId
import java.io.File

class LocalToolDetector(
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
    private val home: String = System.getProperty("user.home"),
    private val locator: ExecutableLocator = ExecutableLocator(os, home),
    private val editorResolver: EditorExecutableResolver = EditorExecutableResolver(os, locator),
    private val isDirectory: (String) -> Boolean = { File(it).isDirectory },
    private val isRunnerInstalled: (RunnerId) -> Boolean = { false },
) : ToolDetector {

    override suspend fun detect(): DetectedTools {
        val snapshot = editorResolver.snapshot()
        return DetectedTools(
            terminals = TerminalId.entries.filter { isTerminalAvailable(it) },
            editors = EditorId.entries.filter { isEditorAvailable(it, snapshot) },
            claudeDesktop = os == HostOs.MACOS && isMacAppInstalled(ToolCatalog.claudeDesktopApp),
            runners = RunnerId.entries.filterTo(mutableSetOf(), isRunnerInstalled),
        )
    }

    private suspend fun isTerminalAvailable(terminal: TerminalId): Boolean = when (terminal) {
        TerminalId.TERMINAL, TerminalId.ITERM2 ->
            os == HostOs.MACOS && isMacAppInstalled(ToolCatalog.terminalApps.getValue(terminal))
        TerminalId.WINDOWS_TERMINAL -> os == HostOs.WINDOWS && onPath(ToolCatalog.windowsTerminalExecutable())
        TerminalId.TMUX -> os != HostOs.WINDOWS && onPath("tmux") && hasSession(listOf("tmux", "list-sessions"))
        TerminalId.ZELLIJ -> os != HostOs.WINDOWS && onPath("zellij") && hasSession(listOf("zellij", "list-sessions", "--short"))
    }

    private suspend fun isEditorAvailable(editor: EditorId, snapshot: UninstallSnapshot): Boolean {
        if (os == HostOs.MACOS && isMacAppInstalled(ToolCatalog.editorApps.getValue(editor))) return true
        return editorResolver.find(editor, snapshot) != null
    }

    private fun onPath(executable: String): Boolean = locator.find(executable) != null

    private suspend fun isMacAppInstalled(app: MacApp): Boolean {
        if (app.bundleIds.any { bundleIdentified(it) }) return true
        val folders = ToolCatalog.applicationFolders.map { it.replaceFirst("~", home) }
        return folders.any { folder -> app.bundleNames.any { isDirectory("$folder/$it") } }
    }

    private suspend fun bundleIdentified(bundleId: String): Boolean = try {
        val result = executor.executeWithin(
            listOf("mdfind", "kMDItemCFBundleIdentifier == '$bundleId'"),
            CommandTimeouts.LISTING,
        )
        result.isSuccess && result.stdout.isNotBlank()
    } catch (_: CommandExecutionException) {
        false
    }

    private suspend fun hasSession(command: List<String>): Boolean = try {
        val result = executor.executeWithin(command, CommandTimeouts.LISTING)
        result.isSuccess && result.stdout.isNotBlank()
    } catch (_: CommandExecutionException) {
        false
    }
}
