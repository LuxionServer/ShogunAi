package app.luxion.shogunai

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor

/**
 * Manual dependency wiring for the app. Holds the stateless singletons and
 * builds the worktree use cases per active project, since they are scoped to
 * a [ProjectConfig] rather than the app as a whole.
 */
class AppContainer {
    val projectRepository: ProjectRepository = JsonProjectRepository()
    private val fileManager: FileManager = NioFileManager()
    private val shellCommandExecutor: ShellCommandExecutor = ProcessBuilderShellCommandExecutor()

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor),
        create = CreateWorktreeUseCase(config, shellCommandExecutor, fileManager),
        remove = RemoveWorktreeUseCase(config, shellCommandExecutor),
    )
}

data class WorktreeUseCases(
    val list: ListWorktreesUseCase,
    val create: CreateWorktreeUseCase,
    val remove: RemoveWorktreeUseCase,
)
