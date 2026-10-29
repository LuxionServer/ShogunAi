package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.TerminalLauncher
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EnvironmentCommand
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch
import app.luxion.shogunai.domain.model.WorktreeError

class OpenWorktreeInTerminalUseCase(
    private val config: ProjectConfig,
    private val launchers: List<TerminalLauncher>,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend operator fun invoke(
        worktreePath: String,
        terminal: TerminalId,
        agent: AgentLaunchConfig = config.agentLaunchConfig,
    ): Result<Unit> = runCatching {
        val launcher = launchers.firstOrNull { it.terminal == terminal }
            ?: throw WorktreeError.TerminalUnsupported(terminal, "it isn't available on this system")
        val variables = resolveEnvironment(config.baseRepositoryPath, worktreePath).getOrThrow().variables
        val agentCommand = agent.resolvedCommand()
        val line = if (terminal.usesPowerShell) {
            powershellLine(worktreePath, variables, agentCommand)
        } else {
            posixLine(worktreePath, variables, agentCommand)
        }
        launcher.open(TerminalLaunch(worktreePath, variables, agentCommand, line)).getOrThrow()
    }

    private fun posixLine(path: String, variables: List<Pair<String, String>>, agentCommand: String): String {
        val exports = variables.map { (name, value) -> "export $name=${ShellQuoting.posix(value)}" }
        return (listOf("cd ${ShellQuoting.posix(path)}") + exports + agentCommand).joinToString(" && ")
    }

    private fun powershellLine(path: String, variables: List<Pair<String, String>>, agentCommand: String): String {
        val assignments = EnvironmentCommand.powershellAssignments(variables)
        return (listOf("Set-Location -LiteralPath ${ShellQuoting.powershell(path)}") + assignments + agentCommand)
            .joinToString("; ")
    }
}
