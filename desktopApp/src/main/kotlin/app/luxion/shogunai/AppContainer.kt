package app.luxion.shogunai

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.executor.TerminalEmulatorDetector
import app.luxion.shogunai.domain.executor.TerminalLauncher
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.OpenWorktreeTerminalUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessTerminalLauncher
import app.luxion.shogunai.infrastructure.SystemTerminalEmulatorDetector

/**
 * Manual dependency wiring for the app. Holds the stateless singletons and
 * builds the worktree use cases per active project, since they are scoped to
 * a [ProjectConfig] rather than the app as a whole.
 */
class AppContainer {
    val projectRepository: ProjectRepository = JsonProjectRepository()
    val themePreferenceRepository: ThemePreferenceRepository = JsonThemePreferenceRepository()
    private val fileManager: FileManager = NioFileManager()
    private val shellCommandExecutor: ShellCommandExecutor = ProcessBuilderShellCommandExecutor()
    private val terminalLauncher: TerminalLauncher = ProcessTerminalLauncher()
    private val terminalEmulatorDetector: TerminalEmulatorDetector =
        SystemTerminalEmulatorDetector(fileManager, shellCommandExecutor)

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor),
        create = CreateWorktreeUseCase(config, shellCommandExecutor, fileManager),
        remove = RemoveWorktreeUseCase(config, shellCommandExecutor),
        openTerminal = OpenWorktreeTerminalUseCase(config, terminalLauncher, terminalEmulatorDetector),
    )
}

data class WorktreeUseCases(
    val list: ListWorktreesUseCase,
    val create: CreateWorktreeUseCase,
    val remove: RemoveWorktreeUseCase,
    val openTerminal: OpenWorktreeTerminalUseCase,
)
