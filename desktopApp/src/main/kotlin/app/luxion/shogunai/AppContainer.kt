package app.luxion.shogunai

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
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
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.infrastructure.AwtClipboardWriter
import app.luxion.shogunai.infrastructure.JsonLanguagePreferenceRepository
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SocketPortProbe
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
    private val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(
        shellCommandExecutor,
        repositoryProvisioningSource,
        NioEnvironmentStateStore(),
        SocketPortProbe(),
    )
    private val androidSdk = LocalAndroidSdk(shellCommandExecutor, fileManager)
    private val startEmulator = StartWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)
    private val stopEmulator = StopWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)
    private val readEmulatorStates = ReadEmulatorStatesUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)

    suspend fun loadAvds(repositoryPath: String): Result<List<String>> =
        runCatching { androidSdk.listAvds(androidSdk.locate(repositoryPath)) }

    private val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        WriteEnvironmentFileUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource),
    )

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor),
        create = CreateWorktreeUseCase(config, shellCommandExecutor, fileManager, repositoryProvisioningSource, prepareEnvironment),
        createFromBranch = CreateWorktreeFromBranchUseCase(
            config,
            shellCommandExecutor,
            fileManager,
            repositoryProvisioningSource,
            prepareEnvironment,
        ),
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
        readPorts = { worktree ->
            resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false).map { it.ports }
        },
        emulator = EmulatorUseCases(
            declared = {
                runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.emulator != null }
                    .getOrDefault(false)
            },
            readStates = { worktrees -> readEmulatorStates(config, worktrees) },
            start = { worktreePath -> startEmulator(config, worktreePath) },
            stop = { worktreePath -> stopEmulator(config, worktreePath) },
            stopSerials = { serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
        ),
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
    val readPorts: suspend (Worktree) -> Result<Map<String, Int>>,
    val emulator: EmulatorUseCases,
    val gradle: GradleUseCases,
)

data class EmulatorUseCases(
    val declared: () -> Boolean,
    val readStates: suspend (List<Worktree>) -> Result<Map<String, WorktreeEmulator>>,
    val start: suspend (String) -> Result<WorktreeEmulator>,
    val stop: suspend (String) -> Result<WorktreeEmulator?>,
    val stopSerials: suspend (List<String>) -> Result<List<WorktreeEmulator>>,
) {
    companion object {
        val NONE = EmulatorUseCases(
            declared = { false },
            readStates = { Result.success(emptyMap()) },
            start = { Result.failure(WorktreeError.EmulatorProviderNotDeclared) },
            stop = { Result.success(null) },
            stopSerials = { Result.success(emptyList()) },
        )
    }
}

data class GradleUseCases(
    val declaresProvider: () -> Boolean,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: () -> Boolean,
)
