package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class CopyWorktreeLaunchCommandUseCase(
    private val config: ProjectConfig,
    private val clipboard: ClipboardWriter,
) {
    suspend operator fun invoke(worktreePath: String): Result<Unit> = runCatching {
        val command = "cd '$worktreePath' && ${config.agentLaunchConfig.resolvedCommand()}"
        clipboard.write(command).getOrElse { throw WorktreeError.ClipboardWriteFailed(it) }
    }
}
