package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ClaudeDesktopLauncher
import app.luxion.shogunai.domain.io.EditorLauncher
import app.luxion.shogunai.domain.io.FolderLauncher
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.WorktreeError
import java.net.URLEncoder

class CommandEditorLauncher(
    override val editor: EditorId,
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
    private val locator: ExecutableLocator = ExecutableLocator(os),
    private val xcodeWorkspaceFinder: XcodeWorkspaceFinder = FileSystemXcodeWorkspaceFinder(),
) : EditorLauncher {

    override suspend fun open(path: String): Result<Unit> = runCatching {
        val target = if (editor == EditorId.XCODE) xcodeWorkspaceFinder.find(path) else path
        val failure = if (os == HostOs.MACOS) openBundle(target) else null
        if (os == HostOs.MACOS && failure == null) return@runCatching
        try {
            openExecutable(target)
        } catch (executableFailure: ToolCommandFailed) {
            throw WorktreeError.EditorLaunchFailed(editor, failure?.detail ?: executableFailure.detail)
        }
    }

    private suspend fun openBundle(path: String): ToolCommandFailed? {
        var last: ToolCommandFailed? = null
        for (bundleId in ToolCatalog.editorApps.getValue(editor).bundleIds) {
            try {
                executor.runTool(listOf("open", "-b", bundleId, path))
                return null
            } catch (failure: ToolCommandFailed) {
                last = failure
            }
        }
        return last
    }

    private suspend fun openExecutable(path: String) {
        val name = ToolCatalog.editorExecutable(editor, os)
            ?: throw ToolCommandFailed(null, "${editor.displayName} can't be opened on this system")
        val executable = locator.find(name, includeToolbox = true)
            ?: throw ToolCommandFailed(null, "$name wasn't found")
        val command = if (os == HostOs.LINUX) listOf("sh", "-c", DETACHED, executable, path) else listOf(executable, path)
        executor.runTool(command)
    }

    private companion object {
        const val DETACHED = "nohup \"\$0\" \"\$@\" >/dev/null 2>&1 &"
    }
}

class SystemFolderLauncher(
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
) : FolderLauncher {

    override suspend fun open(path: String): Result<Unit> = runCatching {
        try {
            executor.runTool(command(path))
        } catch (failure: ToolCommandFailed) {
            val explorerReportsStatusOne = os == HostOs.WINDOWS && failure.result?.exitCode == 1
            if (!explorerReportsStatusOne) throw WorktreeError.FolderOpenFailed(path, failure.detail)
        }
        Unit
    }

    private fun command(path: String): List<String> = when (os) {
        HostOs.MACOS -> listOf("open", path)
        HostOs.WINDOWS -> listOf("explorer.exe", path)
        HostOs.LINUX -> listOf("xdg-open", path)
    }
}

class MacClaudeDesktopLauncher(
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
) : ClaudeDesktopLauncher {

    override suspend fun open(path: String): Result<Unit> = runCatching {
        if (os != HostOs.MACOS) throw WorktreeError.ClaudeDesktopLaunchFailed("it can't be opened on this system")
        try {
            executor.runTool(listOf("open", link(path)))
        } catch (failure: ToolCommandFailed) {
            throw WorktreeError.ClaudeDesktopLaunchFailed(failure.detail)
        }
        Unit
    }

    companion object {
        const val NEW_SESSION_LINK = "claude://code/new"
        const val SOURCE = "shogunai"

        fun link(path: String): String {
            val folder = URLEncoder.encode(path, Charsets.UTF_8).replace("+", "%20")
            return "$NEW_SESSION_LINK?folder=$folder&source=$SOURCE"
        }
    }
}
