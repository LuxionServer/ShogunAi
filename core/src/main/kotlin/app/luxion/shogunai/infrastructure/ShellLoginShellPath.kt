package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.LoginShellPath
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ShellLoginShellPath(
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
    private val shell: String? = System.getenv("SHELL"),
    private val processPath: String? = System.getenv("PATH"),
    private val home: String = System.getProperty("user.home"),
    private val timeout: Duration = 5.seconds,
) : LoginShellPath {

    private val resolved by lazy { resolve() }

    override fun folders(): List<String> = resolved

    private fun resolve(): List<String> {
        val processFolders = split(processPath)
        if (os == HostOs.WINDOWS) return processFolders
        val loginFolders = readLoginShell()
        if (loginFolders.isEmpty()) return (processFolders + ToolCatalog.userBinFolders(os, home)).distinct()
        return (loginFolders + processFolders).distinct()
    }

    private fun readLoginShell(): List<String> {
        val executable = shell?.takeIf { it.isNotBlank() } ?: return emptyList()
        val command = listOf(executable, "-l", "-i", "-c", "printf \"\\n$MARKER%s$MARKER\\n\" \"\$PATH\"")
        val output = try {
            runBlocking { executor.executeWithin(command, timeout) }.stdout
        } catch (_: CommandExecutionException) {
            return emptyList()
        }
        return split(Regex("$MARKER(.*?)$MARKER").find(output)?.groupValues?.get(1))
    }

    private fun split(path: String?): List<String> =
        path.orEmpty().split(if (os == HostOs.WINDOWS) ';' else ':').filter { it.isNotBlank() }

    private companion object {
        const val MARKER = "__SHOGUNAI_PATH__"
    }
}
