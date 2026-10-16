package app.luxion.shogunai

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListLocalBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.infrastructure.AwtClipboardWriter
import app.luxion.shogunai.infrastructure.JsonLanguagePreferenceRepository
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.defaultGradleUserHome

class AppContainer {
    val projectRepository: ProjectRepository = JsonProjectRepository()
    private val settingsFile = JsonSettingsFile()
    val themePreferenceRepository: ThemePreferenceRepository = JsonThemePreferenceRepository(settingsFile)
    val languagePreferenceRepository: LanguagePreferenceRepository = JsonLanguagePreferenceRepository(settingsFile)
    private val fileManager: FileManager = NioFileManager()
    private val shellCommandExecutor: ShellCommandExecutor = ProcessBuilderShellCommandExecutor()
    private val clipboardWriter: ClipboardWriter = AwtClipboardWriter()
    val repositoryProvisioningSource: RepositoryProvisioningSource = RepositoryProvisioningFiles()
    private val gradleUserHome = defaultGradleUserHome()
    private val gradleDaemonSource = ProcessGradleDaemonSource(shellCommandExecutor, gradleUserHome)

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor),
        create = CreateWorktreeUseCase(config, shellCommandExecutor, fileManager, repositoryProvisioningSource),
        createFromBranch = CreateWorktreeFromBranchUseCase(config, shellCommandExecutor, fileManager, repositoryProvisioningSource),
        listLocalBranches = ListLocalBranchesUseCase(config, shellCommandExecutor),
        remove = RemoveWorktreeUseCase(config, shellCommandExecutor),
        copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboardWriter),
        repair = RepairWorktreesUseCase(config, shellCommandExecutor, fileManager),
        resolveMainBranch = ResolveMainBranchUseCase(config, shellCommandExecutor),
        readStatus = ReadWorktreeStatusUseCase(shellCommandExecutor),
        cleanUp = CleanUpWorktreesUseCase(config, shellCommandExecutor),
        detectAgents = DetectRunningAgentsUseCase(config, shellCommandExecutor),
        measureSize = MeasureWorktreeSizeUseCase(fileManager),
        readFingerprint = ReadGitMetadataFingerprintUseCase(config, fileManager),
        gradle = GradleUseCases(
            declaresProvider = {
                runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.gradle != null }
                    .getOrDefault(false)
            },
            listDaemons = ListGradleDaemonsUseCase(gradleDaemonSource, fileManager),
            stopDaemons = StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor),
            isBuildCacheEnabled = { CheckGradleBuildCacheUseCase(fileManager, gradleUserHome)(config.baseRepositoryPath) },
        ),
    )
}

data class WorktreeUseCases(
    val list: ListWorktreesUseCase,
    val create: CreateWorktreeUseCase,
    val createFromBranch: CreateWorktreeFromBranchUseCase,
    val listLocalBranches: ListLocalBranchesUseCase,
    val remove: RemoveWorktreeUseCase,
    val copyLaunchCommand: CopyWorktreeLaunchCommandUseCase,
    val repair: RepairWorktreesUseCase,
    val resolveMainBranch: ResolveMainBranchUseCase,
    val readStatus: ReadWorktreeStatusUseCase,
    val cleanUp: CleanUpWorktreesUseCase,
    val detectAgents: DetectRunningAgentsUseCase,
    val measureSize: MeasureWorktreeSizeUseCase,
    val readFingerprint: ReadGitMetadataFingerprintUseCase,
    val gradle: GradleUseCases,
)

data class GradleUseCases(
    val declaresProvider: () -> Boolean,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: () -> Boolean,
)
