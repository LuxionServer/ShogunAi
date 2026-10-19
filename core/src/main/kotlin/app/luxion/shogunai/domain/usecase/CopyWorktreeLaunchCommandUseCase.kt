package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.WorktreeError

class CopyWorktreeLaunchCommandUseCase(
    private val config: ProjectConfig,
    private val clipboard: ClipboardWriter,
    private val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
) {
    suspend operator fun invoke(worktreePath: String): Result<Unit> = runCatching {
        val directory = if (windows) ShellQuoting.windows(worktreePath) else ShellQuoting.posix(worktreePath)
        val command = "cd $directory && ${config.agentLaunchConfig.resolvedCommand()}"
        clipboard.write(command).getOrElse { throw WorktreeError.ClipboardWriteFailed(it) }
    }
}
