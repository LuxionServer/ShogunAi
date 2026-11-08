package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.HostPath
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SystemHostEnvironment(
    override val os: HostOs,
    private val variables: Map<String, String>,
    override val home: String,
    private val loginShell: ShellCommandExecutor?,
    private val timeout: Duration = 5.seconds,
    override val userBinFolders: List<String> = ToolCatalog.userBinFolders(os, home),
) : HostEnvironment {

    override val shell: String? = variable("SHELL")?.takeIf { it.isNotBlank() }

    override val path: HostPath by lazy { resolve() }

    override fun variable(name: String): String? =
        variables[name] ?: variables.entries.firstOrNull { os == HostOs.WINDOWS && it.key.equals(name, ignoreCase = true) }?.value

    private fun resolve(): HostPath {
        val processFolders = split(variable("PATH"))
        if (os == HostOs.WINDOWS) return HostPath(processFolders, HostPathSource.PROCESS)
        val withUsualFolders = (processFolders + userBinFolders).distinct()
        val executor = loginShell ?: return HostPath(withUsualFolders, HostPathSource.PROCESS)
        val loginFolders = readLoginShell(executor)
        if (loginFolders.isEmpty()) return HostPath(withUsualFolders, HostPathSource.FALLBACK)
        return HostPath((loginFolders + processFolders).distinct(), HostPathSource.LOGIN_SHELL)
    }

    private fun readLoginShell(executor: ShellCommandExecutor): List<String> {
        val executable = shell ?: return emptyList()
        val command = listOf(executable, "-l", "-i", "-c", "printf \"\\n$MARKER%s$MARKER\\n\" \"\$PATH\"")
        val output = try {
            runBlocking { executor.executeWithin(command, timeout) }.stdout
        } catch (_: CommandExecutionException) {
            return emptyList()
        }
        return split(Regex("$MARKER(.*?)$MARKER").find(output)?.let { it.groupValues[1] })
    }

    private fun split(path: String?): List<String> =
        path.orEmpty().split(if (os == HostOs.WINDOWS) ';' else ':').filter { it.isNotBlank() }

    companion object {
        private const val MARKER = "__SHOGUNAI_PATH__"

        fun forCommandLine(): SystemHostEnvironment =
            SystemHostEnvironment(HostOs.current(), System.getenv(), System.getProperty("user.home"), loginShell = null)

        fun forDesktopApp(): SystemHostEnvironment =
            SystemHostEnvironment(HostOs.current(), System.getenv(), System.getProperty("user.home"), ProcessBuilderShellCommandExecutor(path = null))
    }
}
