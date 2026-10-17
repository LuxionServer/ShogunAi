package app.luxion.shogunai

import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListLocalBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ProviderContributions
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.infrastructure.AwtClipboardWriter
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.infrastructure.JsonLanguagePreferenceRepository
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import app.luxion.shogunai.infrastructure.MacDerivedDataSource
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SimctlAppleSimulators
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
    private val derivedDataSource = MacDerivedDataSource(
        shellCommandExecutor,
        fileManager,
        home = System.getProperty("user.home"),
    )
    private val environmentStateStore = NioEnvironmentStateStore()
    private val portProbe = SocketPortProbe()
    private val appleSimulators = SimctlAppleSimulators(shellCommandExecutor)
    private val kmpTestWiring = FileKmpTestWiring(gradleUserHome)
    private val contributions = MachineProviderContributions(
        appleSimulators,
        ::simulatorSettingsFor,
        gradleJvmArgsFor = { repository -> effectiveGradleJvmArgs(fileManager, gradleUserHome, repository) },
        fileManager = fileManager,
        userHome = System.getProperty("user.home"),
        isMac = derivedDataSource.isSupported(),
    )
    private val resolveEnvironment = resolverWith(contributions)
    private val startSimulator = StartWorktreeSimulatorUseCase(repositoryProvisioningSource, appleSimulators, { repository, worktree, requested ->
        resolverWith(contributions.requesting(requested))(repository, worktree)
    })
    private val stopSimulator = StopWorktreeSimulatorUseCase(repositoryProvisioningSource, appleSimulators, resolveEnvironment)
    private val readSimulatorStates = ReadSimulatorStatesUseCase(repositoryProvisioningSource, appleSimulators, resolveEnvironment)
    private val deleteSimulators = DeleteSimulatorsUseCase(repositoryProvisioningSource, appleSimulators, resolveEnvironment)

    private fun resolverWith(factory: ProviderContributions) = ResolveWorktreeEnvironmentUseCase(
        shellCommandExecutor,
        repositoryProvisioningSource,
        environmentStateStore,
        portProbe,
        factory,
    )

    private fun simulatorSettingsFor(repositoryPath: String): SimulatorSettings =
        projectRepository.list()
            .firstOrNull { it.config.baseRepositoryPath.trimEnd('/') == repositoryPath.trimEnd('/') }
            ?.config?.simulator ?: SimulatorSettings()

    val simulatorSetup = object : SimulatorSetup {
        override fun isAvailable(repositoryPath: String): Boolean = appleSimulators.isSupported() &&
            runCatching { repositoryProvisioningSource.read(repositoryPath).providers.iosSimulator != null }.getOrDefault(false)

        override suspend fun runtimes(): Result<List<SimulatorRuntime>> = runCatching { appleSimulators.runtimes() }

        override fun isWired(repositoryPath: String): Boolean =
            kmpTestWiring.isWiredInRepository(repositoryPath) || kmpTestWiring.isInitScriptInstalled()

        override fun usesKotlinMultiplatform(repositoryPath: String): Boolean =
            kmpTestWiring.usesKotlinMultiplatform(repositoryPath)

        override fun installInitScript(): Result<Unit> = runCatching { kmpTestWiring.installInitScript() }

        override val snippet: String = FileKmpTestWiring.REPOSITORY_SNIPPET

        override val initScriptPath: String get() = kmpTestWiring.initScriptPath()
    }
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
        simulator = SimulatorUseCases(
            declared = { simulatorSetup.isAvailable(config.baseRepositoryPath) },
            readStates = { worktrees -> readSimulatorStates(config, worktrees) },
            start = { worktreePath -> startSimulator(config, worktreePath) },
            stop = { worktreePath -> stopSimulator(config, worktreePath) },
            orphans = { deleteSimulators.orphans(config) },
            delete = { udids -> deleteSimulators(udids) },
        ),
        gradle = GradleUseCases(
            provider = {
                runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.gradle }.getOrNull()
            },
            listDaemons = ListGradleDaemonsUseCase(gradleDaemonSource, fileManager),
            stopDaemons = StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor),
            isBuildCacheEnabled = { CheckGradleBuildCacheUseCase(fileManager, gradleUserHome)(config.baseRepositoryPath) },
        ),
        xcode = XcodeUseCases(
            provider = {
                runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.xcode }.getOrNull()
            },
            supported = derivedDataSource::isSupported,
            list = ListDerivedDataUseCase(derivedDataSource),
            deleteOrphans = DeleteOrphanedDerivedDataUseCase(derivedDataSource),
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
    val simulator: SimulatorUseCases,
    val gradle: GradleUseCases,
    val xcode: XcodeUseCases,
)

data class SimulatorUseCases(
    val declared: () -> Boolean,
    val readStates: suspend (List<Worktree>) -> Result<Map<String, WorktreeSimulator>>,
    val start: suspend (String) -> Result<WorktreeSimulator>,
    val stop: suspend (String) -> Result<WorktreeSimulator?>,
    val orphans: suspend () -> Result<List<SimulatorDevice>>,
    val delete: suspend (List<String>) -> Result<List<String>>,
) {
    companion object {
        val NONE = SimulatorUseCases(
            declared = { false },
            readStates = { Result.success(emptyMap()) },
            start = { Result.failure(WorktreeError.IosSimulatorProviderNotDeclared) },
            stop = { Result.success(null) },
            orphans = { Result.success(emptyList()) },
            delete = { Result.success(emptyList()) },
        )
    }
}

interface SimulatorSetup {
    fun isAvailable(repositoryPath: String): Boolean
    suspend fun runtimes(): Result<List<SimulatorRuntime>>
    fun isWired(repositoryPath: String): Boolean
    fun usesKotlinMultiplatform(repositoryPath: String): Boolean
    fun installInitScript(): Result<Unit>
    val snippet: String
    val initScriptPath: String

    companion object {
        val NONE = object : SimulatorSetup {
            override fun isAvailable(repositoryPath: String) = false
            override suspend fun runtimes(): Result<List<SimulatorRuntime>> = Result.success(emptyList())
            override fun isWired(repositoryPath: String) = false
            override fun usesKotlinMultiplatform(repositoryPath: String) = false
            override fun installInitScript(): Result<Unit> = Result.success(Unit)
            override val snippet: String = ""
            override val initScriptPath: String = ""
        }
    }
}

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
    val provider: () -> GradleProvider?,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: () -> Boolean,
)

data class XcodeUseCases(
    val provider: () -> XcodeProvider?,
    val supported: () -> Boolean,
    val list: ListDerivedDataUseCase,
    val deleteOrphans: DeleteOrphanedDerivedDataUseCase,
)
