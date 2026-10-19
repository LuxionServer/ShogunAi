package app.luxion.shogunai

import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.ui.worktree.DialogPostCreateApproval
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.RunningAgent
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
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ProviderContributions
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.OpenWorktreeInHerdrUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.CheckProvisionedFilesUseCase
import app.luxion.shogunai.domain.usecase.ProvisionResult
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReleasedResources
import app.luxion.shogunai.domain.usecase.ReleaseWorktreeResourcesUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.WorktreeResourcePlan
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.ui.projectconfig.RepositoryInspection
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.WorktreeEnvironmentCache
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.infrastructure.AwtClipboardWriter
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.infrastructure.CliHerdrClient
import app.luxion.shogunai.infrastructure.HerdrConfigFile
import app.luxion.shogunai.infrastructure.JsonLanguagePreferenceRepository
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonRepositoryTrust
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonShellPreferenceRepository
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import app.luxion.shogunai.infrastructure.SharedDeviceStatesAndroidSdk
import app.luxion.shogunai.infrastructure.MacDerivedDataSource
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SimctlAppleSimulators
import app.luxion.shogunai.infrastructure.SocketPortProbe
import app.luxion.shogunai.infrastructure.defaultGradleUserHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer {
    val projectRepository: ProjectRepository = JsonProjectRepository()
    private val settingsFile = JsonSettingsFile()
    fun settingsProblem() = settingsFile.lastReadProblem

    fun acknowledgeSettingsProblem() = settingsFile.acknowledgeReadProblem()

    val themePreferenceRepository: ThemePreferenceRepository = JsonThemePreferenceRepository(settingsFile)
    val languagePreferenceRepository: LanguagePreferenceRepository = JsonLanguagePreferenceRepository(settingsFile)
    val shellPreferenceRepository: ShellPreferenceRepository = JsonShellPreferenceRepository(settingsFile)
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
    private val environmentCache = WorktreeEnvironmentCache()
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
        environmentCache,
    )

    private fun simulatorSettingsFor(repositoryPath: String): SimulatorSettings =
        runCatching { projectRepository.list() }.getOrDefault(emptyList())
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

        override fun isInitScriptInstalled(): Boolean = kmpTestWiring.isInitScriptInstalled()

        override fun uninstallInitScript(): Result<Unit> = runCatching { kmpTestWiring.uninstallInitScript() }

        override val snippet: String = FileKmpTestWiring.REPOSITORY_SNIPPET

        override val initScriptPath: String get() = kmpTestWiring.initScriptPath()
    }
    private val androidSdk = SharedDeviceStatesAndroidSdk(LocalAndroidSdk(shellCommandExecutor, fileManager))
    private val herdrConfig = HerdrConfigFile()
    private val herdrClient = CliHerdrClient(shellCommandExecutor)
    private val startEmulator = StartWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)
    private val stopEmulator = StopWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)
    private val readEmulatorStates = ReadEmulatorStatesUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)

    suspend fun loadAvds(repositoryPath: String): Result<List<String>> =
        runCatching { androidSdk.listAvds(androidSdk.locate(repositoryPath)) }

    suspend fun inspectRepository(folder: String): Result<RepositoryInspection> =
        ResolveRepositoryUseCase(shellCommandExecutor)(folder).map { topLevel ->
            val config = ProjectConfig(baseRepositoryPath = topLevel, worktreesRoot = "", secretFiles = emptyList())
            val mainBranch = ResolveMainBranchUseCase(config, shellCommandExecutor)().getOrNull()
            RepositoryInspection(topLevel, mainBranch?.name)
        }

    val detectRunningAgents = DetectRunningAgentsUseCase(shellCommandExecutor, herdrClient)

    private val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        WriteEnvironmentFileUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource),
    )

    val machineUseCases = MachineUseCases(
        listDaemons = { runCatching { gradleDaemonSource.list() } },
        stopDaemons = { pids -> StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor)(pids) },
        listDerivedData = { includeCompilationCache -> ListDerivedDataUseCase(derivedDataSource)(emptyList(), includeCompilationCache) },
        deleteOrphanedDerivedData = { DeleteOrphanedDerivedDataUseCase(derivedDataSource)() },
        readEmulatorStates = { config, worktrees -> readEmulatorStates(config, worktrees) },
        readRunningEmulators = { config ->
            runCatching { androidSdk.deviceStates(androidSdk.locate(config.baseRepositoryPath)) }
        },
        startEmulator = { config, worktreePath -> startEmulator(config, worktreePath) },
        stopEmulator = { config, worktreePath -> stopEmulator(config, worktreePath) },
        stopEmulatorSerials = { config, serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
        readSimulatorStates = { config, worktrees -> readSimulatorStates(config, worktrees) },
        startSimulator = { config, worktreePath -> startSimulator(config, worktreePath) },
        stopSimulator = { config, worktreePath -> stopSimulator(config, worktreePath) },
        readSimulatorOrphans = { config -> deleteSimulators.orphans(config) },
        deleteSimulators = { udids -> deleteSimulators(udids) },
    )

    val pathExists: (String) -> Boolean = fileManager::exists

    val repositoryTrust: RepositoryTrust = JsonRepositoryTrust()

    val postCreateApproval = DialogPostCreateApproval()

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor, fileManager),
        create = CreateWorktreeUseCase(
            config,
            shellCommandExecutor,
            fileManager,
            repositoryProvisioningSource,
            prepareEnvironment,
            repositoryTrust,
            postCreateApproval,
        ),
        createFromBranch = CreateWorktreeFromBranchUseCase(
            config,
            shellCommandExecutor,
            fileManager,
            repositoryProvisioningSource,
            prepareEnvironment,
            repositoryTrust,
            postCreateApproval,
        ),
        listBranches = ListBranchesUseCase(config, shellCommandExecutor),
        fetchRemote = FetchRemoteUseCase(config, shellCommandExecutor),
        resolveStartPoint = { ref -> ResolveStartPointUseCase(shellCommandExecutor)(config.baseRepositoryPath, ref) },
        remove = RemoveWorktreeUseCase(config, shellCommandExecutor, CloseHerdrWorkspacesUseCase(config, herdrClient)),
        copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboardWriter),
        repair = RepairWorktreesUseCase(config, shellCommandExecutor, fileManager),
        resolveMainBranch = ResolveMainBranchUseCase(config, shellCommandExecutor),
        readStatus = ReadWorktreeStatusUseCase(shellCommandExecutor),
        cleanUp = CleanUpWorktreesUseCase(config, shellCommandExecutor, CloseHerdrWorkspacesUseCase(config, herdrClient)),
        detectAgents = { worktrees -> detectRunningAgents(config, worktrees) },
        measureSize = MeasureWorktreeSizeUseCase(fileManager),
        readFingerprint = ReadGitMetadataFingerprintUseCase(config, fileManager),
        invalidateEnvironments = environmentCache::invalidate,
        readPorts = { worktree ->
            resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false).map { it.ports }
        },
        emulator = EmulatorUseCases(
            declared = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.emulator != null }
                        .getOrDefault(false)
                }
            },
            readStates = { worktrees -> readEmulatorStates(config, worktrees) },
            start = { worktreePath -> startEmulator(config, worktreePath) },
            stop = { worktreePath -> stopEmulator(config, worktreePath) },
            stopSerials = { serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
        ),
        simulator = SimulatorUseCases(
            declared = { withContext(Dispatchers.IO) { simulatorSetup.isAvailable(config.baseRepositoryPath) } },
            readStates = { worktrees -> readSimulatorStates(config, worktrees) },
            start = { worktreePath -> startSimulator(config, worktreePath) },
            stop = { worktreePath -> stopSimulator(config, worktreePath) },
            orphans = { deleteSimulators.orphans(config) },
            delete = { udids -> deleteSimulators(udids) },
        ),
        provisioning = ProvisioningUseCases(
            check = CheckProvisionedFilesUseCase(config, shellCommandExecutor, fileManager, repositoryProvisioningSource)::invoke,
            provision = ProvisionWorktreeUseCase(
                config,
                shellCommandExecutor,
                fileManager,
                repositoryProvisioningSource,
                prepareEnvironment,
                repositoryTrust,
                postCreateApproval,
            )::invoke,
        ),
        readEnvironment = { worktree -> resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false) },
        releaseResources = ReleaseWorktreeResourcesUseCase(
            repositoryProvisioningSource,
            readEmulatorStates,
            stopEmulator,
            readSimulatorStates,
            stopSimulator,
            ListGradleDaemonsUseCase(gradleDaemonSource, fileManager),
            StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor),
        ).let { releaser ->
            ReleaseResourcesUseCases(
                plan = { worktree, all -> releaser.plan(config, worktree, all) },
                release = { plan -> releaser.release(config, plan) },
            )
        },
        herdr = HerdrUseCases(
            worktreesRoot = { withContext(Dispatchers.IO) { runCatching { herdrConfig.worktreesRoot() }.getOrNull() } },
            open = OpenWorktreeInHerdrUseCase(config, herdrClient, resolveEnvironment)::invoke,
            openWorkspaces = CloseHerdrWorkspacesUseCase(config, herdrClient)::openWorkspaces,
            focusAgent = { paneId -> runCatching { herdrClient.focusAgent(paneId) } },
        ),
        gradle = GradleUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.gradle }.getOrNull()
                }
            },
            listDaemons = ListGradleDaemonsUseCase(gradleDaemonSource, fileManager),
            stopDaemons = StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor),
            isBuildCacheEnabled = {
                withContext(Dispatchers.IO) { CheckGradleBuildCacheUseCase(fileManager, gradleUserHome)(config.baseRepositoryPath) }
            },
        ),
        xcode = XcodeUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.xcode }.getOrNull()
                }
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
    val listBranches: ListBranchesUseCase,
    val fetchRemote: FetchRemoteUseCase,
    val resolveStartPoint: suspend (String) -> Result<String>,
    val remove: RemoveWorktreeUseCase,
    val copyLaunchCommand: CopyWorktreeLaunchCommandUseCase,
    val repair: RepairWorktreesUseCase,
    val resolveMainBranch: ResolveMainBranchUseCase,
    val readStatus: ReadWorktreeStatusUseCase,
    val cleanUp: CleanUpWorktreesUseCase,
    val detectAgents: suspend (List<Worktree>) -> Result<Map<String, List<RunningAgent>>>,
    val measureSize: MeasureWorktreeSizeUseCase,
    val readFingerprint: ReadGitMetadataFingerprintUseCase,
    val invalidateEnvironments: () -> Unit = {},
    val readPorts: suspend (Worktree) -> Result<Map<String, Int>>,
    val emulator: EmulatorUseCases,
    val simulator: SimulatorUseCases,
    val gradle: GradleUseCases,
    val xcode: XcodeUseCases,
    val herdr: HerdrUseCases = HerdrUseCases.NONE,
    val provisioning: ProvisioningUseCases = ProvisioningUseCases.NONE,
    val readEnvironment: suspend (Worktree) -> Result<WorktreeEnvironment> = { Result.success(WorktreeEnvironment()) },
    val releaseResources: ReleaseResourcesUseCases = ReleaseResourcesUseCases.NONE,
)

class ProvisioningUseCases(
    val check: suspend (String) -> Result<List<ProvisionDrift>>,
    val provision: suspend (String) -> Result<ProvisionResult>,
) {
    companion object {
        val NONE = ProvisioningUseCases(
            check = { Result.success(emptyList()) },
            provision = { Result.success(ProvisionResult(emptyList())) },
        )
    }
}

class ReleaseResourcesUseCases(
    val plan: suspend (Worktree, List<Worktree>) -> WorktreeResourcePlan,
    val release: suspend (WorktreeResourcePlan) -> ReleasedResources,
) {
    companion object {
        val NONE = ReleaseResourcesUseCases(
            plan = { _, _ -> WorktreeResourcePlan() },
            release = { ReleasedResources() },
        )
    }
}

class MachineUseCases(
    val listDaemons: suspend () -> Result<List<DaemonProcess>>,
    val stopDaemons: suspend (List<Long>?) -> Result<DaemonStopReport>,
    val listDerivedData: suspend (includeCompilationCache: Boolean) -> Result<DerivedDataListing>,
    val deleteOrphanedDerivedData: suspend () -> Result<DerivedDataDeletion>,
    val readEmulatorStates: suspend (ProjectConfig, List<Worktree>) -> Result<Map<String, WorktreeEmulator>>,
    val readRunningEmulators: suspend (ProjectConfig) -> Result<Map<String, EmulatorState>>,
    val startEmulator: suspend (ProjectConfig, String) -> Result<WorktreeEmulator>,
    val stopEmulator: suspend (ProjectConfig, String) -> Result<WorktreeEmulator?>,
    val stopEmulatorSerials: suspend (ProjectConfig, List<String>) -> Result<List<WorktreeEmulator>>,
    val readSimulatorStates: suspend (ProjectConfig, List<Worktree>) -> Result<Map<String, WorktreeSimulator>>,
    val startSimulator: suspend (ProjectConfig, String) -> Result<WorktreeSimulator>,
    val stopSimulator: suspend (ProjectConfig, String) -> Result<WorktreeSimulator?>,
    val readSimulatorOrphans: suspend (ProjectConfig) -> Result<List<SimulatorDevice>>,
    val deleteSimulators: suspend (List<String>) -> Result<List<String>>,
)

data class SimulatorUseCases(
    val declared: suspend () -> Boolean,
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

data class HerdrUseCases(
    val worktreesRoot: suspend () -> String?,
    val open: suspend (Worktree) -> Result<HerdrOpening>,
    val openWorkspaces: suspend (List<String>) -> Map<String, String> = { emptyMap() },
    val focusAgent: suspend (String) -> Result<Unit> = { Result.success(Unit) },
) {
    companion object {
        val NONE = HerdrUseCases(
            worktreesRoot = { null },
            open = { Result.failure(WorktreeError.HerdrNotInstalled) },
        )
    }
}

interface SimulatorSetup {
    fun isAvailable(repositoryPath: String): Boolean
    suspend fun runtimes(): Result<List<SimulatorRuntime>>
    fun isWired(repositoryPath: String): Boolean
    fun usesKotlinMultiplatform(repositoryPath: String): Boolean
    fun installInitScript(): Result<Unit>
    fun isInitScriptInstalled(): Boolean
    fun uninstallInitScript(): Result<Unit>
    val snippet: String
    val initScriptPath: String

    companion object {
        val NONE = object : SimulatorSetup {
            override fun isAvailable(repositoryPath: String) = false
            override suspend fun runtimes(): Result<List<SimulatorRuntime>> = Result.success(emptyList())
            override fun isWired(repositoryPath: String) = false
            override fun usesKotlinMultiplatform(repositoryPath: String) = false
            override fun installInitScript(): Result<Unit> = Result.success(Unit)
            override fun isInitScriptInstalled() = false
            override fun uninstallInitScript(): Result<Unit> = Result.success(Unit)
            override val snippet: String = ""
            override val initScriptPath: String = ""
        }
    }
}

data class EmulatorUseCases(
    val declared: suspend () -> Boolean,
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
    val provider: suspend () -> GradleProvider?,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: suspend () -> Boolean,
)

data class XcodeUseCases(
    val provider: suspend () -> XcodeProvider?,
    val supported: suspend () -> Boolean,
    val list: ListDerivedDataUseCase,
    val deleteOrphans: DeleteOrphanedDerivedDataUseCase,
)
