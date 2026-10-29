package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.PackageCacheSource
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.io.HerdrConfigSource
import app.luxion.shogunai.domain.io.KmpTestWiring
import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.DesktopWait
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.ANDROID_SERIAL
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.AcquireDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.usecase.DesktopTurnLedger
import app.luxion.shogunai.domain.usecase.ReadDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.ReleaseDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.TrimDerivedDataUseCase
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.OrcaComputerUse
import app.luxion.shogunai.domain.model.OrcaEmulatorView
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.flag
import app.luxion.shogunai.domain.model.runnerInUse
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_UDID
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.PodsInstallReason
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.UnresolvedReason
import app.luxion.shogunai.domain.model.WorkflowContext
import app.luxion.shogunai.domain.model.WorkflowRules
import app.luxion.shogunai.domain.model.WorkflowTopic
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.AcquireWorktreeResourceUseCase
import app.luxion.shogunai.domain.usecase.BranchDeletion
import app.luxion.shogunai.domain.usecase.AgentInstructionsBlock
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.GuideBlockState
import app.luxion.shogunai.domain.usecase.GuideBlockStatus
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.domain.usecase.HooksPreview
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.DetectMobileSecretsUseCase
import app.luxion.shogunai.domain.usecase.DetectRepositoryStacksUseCase
import app.luxion.shogunai.domain.usecase.ListComposeLeftoversUseCase
import app.luxion.shogunai.domain.usecase.RemoveComposeLeftoversUseCase
import app.luxion.shogunai.domain.usecase.RunComposeProjectUseCase
import app.luxion.shogunai.domain.executor.OutputChannel
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.infrastructure.JsonComposeProjectRegistry
import app.luxion.shogunai.domain.usecase.ListComposeProjectsUseCase
import app.luxion.shogunai.domain.usecase.ListMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.TakeDownComposeProjectsUseCase
import app.luxion.shogunai.domain.usecase.totalMavenResidentMemory
import app.luxion.shogunai.domain.usecase.worktreeComposeProjectName
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ResolveBaseBranchUseCase
import app.luxion.shogunai.domain.usecase.ReadBaseBranchesUseCase
import app.luxion.shogunai.domain.usecase.RepositoryBases
import app.luxion.shogunai.domain.usecase.ResolveRecordedBasesUseCase
import app.luxion.shogunai.domain.usecase.SetBaseBranchUseCase
import app.luxion.shogunai.domain.usecase.DeleteMergedBranchUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DerivedDataMeasure
import app.luxion.shogunai.domain.usecase.ResolveDerivedDataQuotaUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.findWorktreeTarget
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.HerdrAgentLauncher
import app.luxion.shogunai.domain.usecase.isLiveClaudeProcess
import app.luxion.shogunai.domain.usecase.HerdrAgentRunner
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.usecase.PrepareCocoaPodsUseCase
import app.luxion.shogunai.domain.usecase.ResolveFlutterPackagesUseCase
import app.luxion.shogunai.domain.usecase.usesFvm
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ProviderContributions
import app.luxion.shogunai.domain.usecase.CheckProvisionedFilesUseCase
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadAgentActivityUseCase
import app.luxion.shogunai.domain.usecase.ReadReleaseRecordUseCase
import app.luxion.shogunai.domain.usecase.ReadResourcePoolUseCase
import app.luxion.shogunai.domain.usecase.RenewWorktreeLeasesUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.ReleaseWorktreeResourcesUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.GitFailureCause
import app.luxion.shogunai.domain.usecase.resolveWorktreeTarget
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.herdrDetail
import app.luxion.shogunai.domain.usecase.ClaudeSessionNaming
import app.luxion.shogunai.domain.usecase.CloseOrcaTerminalsUseCase
import app.luxion.shogunai.domain.usecase.orcaDetail
import app.luxion.shogunai.domain.usecase.OrcaAgentRunner
import app.luxion.shogunai.domain.usecase.OrcaWorktreeReader
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ShowEmulatorInOrcaUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.CaptureHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.HeadlessAppWorktrees
import app.luxion.shogunai.domain.usecase.ReadHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.StartHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.StopHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.TrimGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.domain.usecase.formatEnvironment
import app.luxion.shogunai.domain.usecase.idleDaemonsOfRemovedWorktrees
import app.luxion.shogunai.domain.usecase.idleMavenDaemonsOfRemovedWorktrees
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import app.luxion.shogunai.domain.usecase.owningWorktree
import app.luxion.shogunai.domain.usecase.classifyGitFailure
import app.luxion.shogunai.domain.usecase.hint
import app.luxion.shogunai.domain.usecase.selectCleanup
import app.luxion.shogunai.domain.usecase.totalResidentMemory
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.MavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonStopReport
import app.luxion.shogunai.domain.model.suggestProviders
import app.luxion.shogunai.domain.model.SuggestionReport
import app.luxion.shogunai.infrastructure.CliHerdrClient
import app.luxion.shogunai.infrastructure.CliOrcaClient
import app.luxion.shogunai.infrastructure.JsonOrcaWorkspaceStore
import app.luxion.shogunai.infrastructure.FileClaudeSessionRegistry
import app.luxion.shogunai.infrastructure.SystemProcessEnvironmentReader
import app.luxion.shogunai.infrastructure.DockerComposeProjectSource
import app.luxion.shogunai.infrastructure.NioDesktopTurnStore
import app.luxion.shogunai.infrastructure.ProcessMavenDaemonSource
import app.luxion.shogunai.infrastructure.FileAgentSessionStore
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.infrastructure.JsonMultirepoSource
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.usecase.ConfigureMultirepoUseCase
import app.luxion.shogunai.domain.usecase.CreateGroupUseCase
import app.luxion.shogunai.domain.usecase.DetectGroupsUseCase
import app.luxion.shogunai.domain.usecase.GroupContext
import app.luxion.shogunai.domain.usecase.GroupKeptReason
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupTarget
import app.luxion.shogunai.domain.usecase.GroupWorktreeState
import app.luxion.shogunai.domain.usecase.MultirepoContext
import app.luxion.shogunai.domain.usecase.MultirepoSharedEnvironment
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.io.AgentRunner
import app.luxion.shogunai.domain.io.ToolPreferenceRepository
import app.luxion.shogunai.infrastructure.JsonToolPreferenceRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.domain.usecase.ReadGroupsUseCase
import app.luxion.shogunai.domain.usecase.ResolveMultirepoUseCase
import app.luxion.shogunai.domain.usecase.UnknownMultirepoAlias
import app.luxion.shogunai.domain.usecase.WorktreesFolderDecision
import app.luxion.shogunai.domain.usecase.findGroup
import app.luxion.shogunai.domain.usecase.multirepoMembers
import app.luxion.shogunai.domain.usecase.selectLooseCleanup
import app.luxion.shogunai.domain.usecase.withWorkspaceAt
import app.luxion.shogunai.domain.usecase.selectGroupCleanup
import java.io.File
import app.luxion.shogunai.domain.io.AppInstanceLock
import app.luxion.shogunai.domain.io.AppLauncher
import app.luxion.shogunai.domain.io.AppRequestChannel
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.domain.model.toWaitText
import app.luxion.shogunai.domain.usecase.ProjectRegistration
import app.luxion.shogunai.domain.usecase.RegisterProjectUseCase
import app.luxion.shogunai.domain.usecase.UnregisterProjectUseCase
import app.luxion.shogunai.infrastructure.DesktopAppLauncher
import app.luxion.shogunai.infrastructure.GradleHeadlessAppLauncher
import app.luxion.shogunai.infrastructure.MacAppWindowService
import app.luxion.shogunai.infrastructure.FileAppInstanceLock
import app.luxion.shogunai.infrastructure.FileAppRequestChannel
import app.luxion.shogunai.infrastructure.NioDeviceWaitQueue
import app.luxion.shogunai.infrastructure.ProcessHandleLiveness
import java.io.FileNotFoundException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ShogunCli(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    private val daemonSource: GradleDaemonSource,
    private val gradleUserHome: String,
    private val environmentStateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val androidSdk: AndroidSdk,
    private val appleSimulators: AppleSimulators,
    private val kmpTestWiring: KmpTestWiring,
    private val resourceLimits: ResourceLimitsRepository,
    private val machineCapacity: MachineCapacity,
    private val derivedDataSource: DerivedDataSource,
    private val herdrConfig: HerdrConfigSource,
    private val workingDirectory: String,
    private val out: Appendable,
    private val err: Appendable,
    private val readInput: () -> String = { "" },
    private val canonicalPath: (String) -> String = { File(it).canonicalPath },
    private val now: () -> Instant = Instant::now,
    private val environmentVariable: (String) -> String? = System::getenv,
    private val userHome: String = System.getProperty("user.home"),
    private val herdr: HerdrClient = CliHerdrClient(executor, userHome = userHome),
    private val toolPreferences: ToolPreferenceRepository = JsonToolPreferenceRepository(JsonSettingsFile(Path.of(userHome, ".shogunai", "settings.json"))),
    private val herdrAgentStartTimeout: kotlin.time.Duration = AGENT_START_SECONDS.seconds,
    private val orca: OrcaClient = CliOrcaClient(executor, userHome = userHome),
    private val orcaWorkspaces: OrcaWorkspaceStore = JsonOrcaWorkspaceStore(Path.of(userHome, ".shogunai", "orca.json")),
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val readAnswer: () -> String? = { null },
    private val sessionStore: AgentSessionStore = FileAgentSessionStore(),
    private val agentProcessId: () -> Long? = ::claudeAncestorPid,
    private val mavenDaemonSource: MavenDaemonSource = ProcessMavenDaemonSource(executor),
    private val composeSource: ComposeProjectSource = DockerComposeProjectSource(executor),
    private val composeRegistry: ComposeProjectRegistry = JsonComposeProjectRegistry(Path.of(userHome, ".shogunai", "compose-projects.json")),
    private val multirepoSource: MultirepoSource = JsonMultirepoSource(Path.of(userHome, ".shogunai", "multirepos.json")),
    private val appRequests: AppRequestChannel = FileAppRequestChannel(Path.of(userHome, ".shogunai", "app-requests")),
    private val appLock: AppInstanceLock = FileAppInstanceLock(Path.of(userHome, ".shogunai", "app.lock")),
    private val appLauncher: AppLauncher = DesktopAppLauncher(),
    private val provisioningRecords: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
    private val derivedDataSizes: DerivedDataSizeStore = DerivedDataSizeStore.NONE,
    private val packageCacheSource: PackageCacheSource = PackageCacheSource.NONE,
    private val deviceWaitQueue: DeviceWaitQueue = NioDeviceWaitQueue(Path.of(userHome, ".shogunai")),
    private val processLiveness: ProcessLiveness = ProcessHandleLiveness(),
    private val deviceClock: () -> Instant = Instant::now,
    private val pauseWhileWaiting: suspend (Duration) -> Unit = { delay(it.toMillis()) },
    private val headlessLauncher: HeadlessAppLauncher = GradleHeadlessAppLauncher(executor, Path.of(userHome, ".shogunai", "headless").toString()),
    private val appWindows: AppWindowService = MacAppWindowService(executor, Path.of(userHome, ".shogunai", "headless").toString()),
    private val pauseWhileStartingApp: suspend (Long) -> Unit = { delay(it) },
    private val desktopTurnStore: DesktopTurnStore = NioDesktopTurnStore(Path.of(userHome, ".shogunai")),
    private val claudeSessions: ClaudeSessionRegistry = FileClaudeSessionRegistry(
        ClaudeCodeHooks.claudeSessionsDirectory(environmentVariable(CLAUDE_CONFIG_DIR), userHome),
    ),
    private val isClaudeProcessAlive: (Long) -> Boolean = ::isLiveClaudeProcess,
) {
    private val orcaReader = OrcaWorktreeReader(orca, orcaWorkspaces, userHome)

    private val terminalApproval = RepositoryApproval { repository, subject ->
        if (confirm("${subjectLines(repository, subject)}$APPROVAL_QUESTION")) ApprovalDecision.APPROVE else ApprovalDecision.SKIP
    }

    private val contributions = MachineProviderContributions(
        appleSimulators,
        ::simulatorSettingsFor,
        gradleJvmArgsFor = { repository -> effectiveGradleJvmArgs(fileManager, gradleUserHome, repository) },
        fileManager = fileManager,
        userHome = userHome,
        isMac = derivedDataSource.isSupported(),
    )
    private val listDerivedData = ListDerivedDataUseCase(derivedDataSource, derivedDataSizes, packageCacheSource)
    private val deleteOrphanedDerivedData = DeleteOrphanedDerivedDataUseCase(derivedDataSource)
    private val resolveEnvironment = resolverWith(contributions)
    private val resolveLimits = ResolveResourceLimitsUseCase(resourceLimits, machineCapacity)
    private val readPool = ReadResourcePoolUseCase(
        resolveEnvironment,
        environmentStateStore,
        fileManager,
        androidSdk,
        appleSimulators,
        resolveLimits,
        ListGradleDaemonsUseCase(daemonSource, fileManager),
        agentActivity = ReadAgentActivityUseCase(sessionStore)::invoke,
        clock = deviceClock,
    )
    private val readReleaseRecord = ReadReleaseRecordUseCase(resolveEnvironment, environmentStateStore)
    private val renewLeases = RenewWorktreeLeasesUseCase(resolveEnvironment, environmentStateStore)
    private val acquireResource = AcquireWorktreeResourceUseCase(
        provisioningSource,
        resolveEnvironment,
        WriteEnvironmentFileUseCase(executor, fileManager, provisioningSource),
        environmentStateStore,
        portProbe,
        appleSimulators,
        androidSdk,
        readPool,
        { requested -> contributions.requesting(requested) },
        deviceWaitQueue,
        processLiveness,
        clock = deviceClock,
        sleep = pauseWhileWaiting,
    )
    private val trimGradleDaemons = TrimGradleDaemonsUseCase(
        ListGradleDaemonsUseCase(daemonSource, fileManager),
        StopGradleDaemonsUseCase(daemonSource, executor),
        resolveLimits,
    )
    private val startSimulator = StartWorktreeSimulatorUseCase(provisioningSource, appleSimulators, acquireResource, resolveEnvironment)
    private val stopSimulator = StopWorktreeSimulatorUseCase(provisioningSource, appleSimulators, resolveEnvironment)
    private val readSimulatorStates = ReadSimulatorStatesUseCase(provisioningSource, appleSimulators, resolveEnvironment)
    private val deleteSimulators = DeleteSimulatorsUseCase(provisioningSource, appleSimulators, resolveEnvironment)

    private fun resolverWith(factory: ProviderContributions) =
        ResolveWorktreeEnvironmentUseCase(
            executor,
            provisioningSource,
            environmentStateStore,
            portProbe,
            factory,
            trust = trust,
            composeRegistry = composeRegistry,
            sharedEnvironment = { repository, worktree -> MultirepoSharedEnvironment(resolveMultirepo, executor).forWorktree(repository, worktree) },
        )

    private fun simulatorSettingsFor(repositoryPath: String): SimulatorSettings =
        projectRepository.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repositoryPath) }
            ?.config?.simulator ?: SimulatorSettings()
    private val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        WriteEnvironmentFileUseCase(executor, fileManager, provisioningSource),
        PrepareCocoaPodsUseCase(executor, fileManager, provisioningSource, isMacOs = derivedDataSource.isSupported()),
        ResolveFlutterPackagesUseCase(executor, fileManager, provisioningSource),
    )
    private val startEmulator = StartWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, acquireResource, androidSdk)
    private val stopEmulator = StopWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, androidSdk)
    private val readEmulatorStates = ReadEmulatorStatesUseCase(provisioningSource, resolveEnvironment, androidSdk)
    private val showEmulatorInOrca = ShowEmulatorInOrcaUseCase(orca)
    private val headlessWorktrees = HeadlessAppWorktrees(
        provisioningSource,
        { resolveEnvironment.directoriesOf(it).admin },
        headlessLauncher,
        appWindows,
        fileManager,
    )
    private val startHeadlessApp = StartHeadlessAppUseCase(headlessWorktrees, headlessLauncher, appWindows, fileManager, pauseWhileStartingApp)
    private val stopHeadlessApp = StopHeadlessAppUseCase(headlessWorktrees, headlessLauncher)
    private val readHeadlessApp = ReadHeadlessAppUseCase(headlessWorktrees, appWindows)
    private val captureHeadlessApp = CaptureHeadlessAppUseCase(headlessWorktrees, appWindows)
    private val desktopTurns = DesktopTurnLedger(
        desktopTurnStore,
        environmentStateStore,
        deviceWaitQueue,
        processLiveness,
        { resolveEnvironment.directoriesOf(it).admin },
        clock = deviceClock,
        sleep = pauseWhileWaiting,
    )
    private val acquireDesktopTurn = AcquireDesktopTurnUseCase(desktopTurns, resolveLimits::desktopTurnDuration)
    private val releaseDesktopTurn = ReleaseDesktopTurnUseCase(desktopTurns)
    private val readDesktopTurn = ReadDesktopTurnUseCase(desktopTurns)

    fun run(args: List<String>): Int = when (val parsed = parseArguments(args)) {
        is ParsedArguments.UsageError -> {
            err.line("shogun: ${parsed.message}")
            err.line("Run 'shogun help' for usage.")
            EXIT_USAGE
        }
        is ParsedArguments.Command -> try {
            runBlocking { cancelledOnShutdown { execute(parsed.command) } }
        } catch (_: CancellationException) {
            EXIT_INTERRUPTED
        }
    }

    private suspend fun <T> cancelledOnShutdown(block: suspend () -> T): T {
        val job = currentCoroutineContext().job
        val hook = Thread {
            job.cancel()
            runBlocking { job.join() }
        }
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            return block()
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }

    private suspend fun execute(command: CliCommand): Int = try {
        when (command) {
            CliCommand.Help -> out.append(CliHelp.USAGE).let { EXIT_OK }
            is CliCommand.CommandHelp -> out.append(CliHelp.forCommand(command.name).orEmpty()).let { EXIT_OK }
            CliCommand.Version -> out.line("shogun ${BuildInfo.VERSION}").let { EXIT_OK }
            is CliCommand.New -> new(command)
            is CliCommand.Multirepo -> multirepo(command)
            is CliCommand.Provision -> provision(command)
            is CliCommand.Trust -> trust(command)
            is CliCommand.Remove -> remove(command)
            is CliCommand.Repair -> repair(command)
            is CliCommand.Open -> open(command)
            is CliCommand.Status -> status(command)
            is CliCommand.Base -> base(command)
            is CliCommand.Clean -> clean(command)
            CliCommand.HookWorktreeCreate -> hookWorktreeCreate()
            CliCommand.HookWorktreeRemove -> hookWorktreeRemove()
            is CliCommand.Gradle -> gradle(command)
            is CliCommand.Stacks -> stacks(command)
            is CliCommand.Maven -> maven(command)
            is CliCommand.MavenStop -> mavenStop(command)
            is CliCommand.Compose -> compose(command)
            is CliCommand.ComposeDown -> composeDown(command)
            is CliCommand.ComposeRun -> composeRun(command)
            is CliCommand.ComposePrune -> composePrune(command)
            is CliCommand.Env -> env(command)
            is CliCommand.Emulator -> emulator(command)
            is CliCommand.Simulator -> simulator(command)
            is CliCommand.Headless -> headless(command)
            is CliCommand.Desktop -> desktop(command)
            is CliCommand.SimulatorGradleInit -> simulatorGradleInit(command)
            CliCommand.HookSessionStart -> hookSessionStart()
            CliCommand.HookHerdr -> hookHerdr()
            is CliCommand.HookOrca -> hookOrca(command.action)
            CliCommand.HookAgentState -> hookAgentState()
            CliCommand.HookDesktopTurn -> hookDesktopTurn()
            is CliCommand.Hooks -> hooks(command)
            is CliCommand.Guide -> guide(command)
            is CliCommand.GuideBlock -> guideBlock(command)
            is CliCommand.GradleStop -> gradleStop(command)
            is CliCommand.Pool -> pool(command)
            is CliCommand.Project -> project(command)
            is CliCommand.App -> app(command)
            is CliCommand.Xcode -> xcode(command)
            is CliCommand.XcodeClean -> xcodeClean(command)
            is CliCommand.XcodeTrim -> xcodeTrim(command)
        }
    } catch (error: WorktreeError) {
        err.line("shogun: ${error.message}")
        if (error is WorktreeError.GitCommandFailed) classifyGitFailure(error.errorOutput).hint()?.let { err.line("hint: $it") }
        EXIT_FAILURE
    } catch (error: CommandExecutionException) {
        err.line("shogun: ${error.message}")
        EXIT_FAILURE
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        err.line("shogun: ${error.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: error::class.simpleName}")
        EXIT_FAILURE
    }

    private suspend fun new(command: CliCommand.New): Int {
        val context = multirepoContext()
        if (command.repos != null || context?.atFolder == true) {
            if (context == null) {
                err.line(
                    if (ignoredDeclarationNoted) {
                        "shogun: --repos needs a multirepo approved on this machine"
                    } else {
                        "shogun: --repos needs a multirepo; create one with 'shogun multirepo init'"
                    },
                )
                return EXIT_USAGE
            }
            return newGroup(context, command)
        }
        val config = configFor(workingDirectory, worktreesRoot = command.worktreesRoot?.let(::absolute))
        if (config.worktreesRoot.isBlank()) {
            err.line("shogun: this repository has no ShogunAi project, so there's no worktrees folder.")
            err.line("Pass --worktrees-root <dir>, or add the repository as a project in the desktop app.")
            return EXIT_USAGE
        }
        if (command.fetch) FetchRemoteUseCase(config, executor)().getOrThrow()
        val approval = if (command.json) RepositoryApproval.SKIP else terminalApproval
        val created = if (command.branch != null) {
            CreateWorktreeFromBranchUseCase(
                config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval, provisioningRecords,
            )(command.branch)
        } else {
            val startPoint = resolveStartPoint(workingDirectory, command.from)
            CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval, provisioningRecords)(
                command.taskId.orEmpty(),
                command.branchType,
                startPoint,
                resolveBaseBranch(workingDirectory, command.from),
            )
        }
        val failure = created.exceptionOrNull()
        if (failure is WorktreeError.PostCreateCommandFailed) {
            if (command.json) {
                printJson(newJson(failure.worktreePath, branchOf(config, failure.worktreePath), emptyList(), emptyList(), failure))
            } else {
                out.line(failure.worktreePath)
            }
            err.line("shogun: ${failure.message}")
            return EXIT_FAILURE
        }
        val worktree = created.getOrThrow()
        if (command.json) {
            printJson(newJson(worktree.path, worktree.branch, worktree.skippedPostCreateCommands, worktree.skippedVariables, null, worktree.skippedLinks, worktree.pods, worktree.flutter))
            return EXIT_OK
        }
        out.line(worktree.path)
        noteSkippedLinks(worktree.skippedLinks)
        podsLines(worktree.pods).forEach { err.line("shogun: $it") }
        worktree.flutter?.let { err.line("shogun: ${flutterPubGetLine(it)}") }
        noteSkipped(config.baseRepositoryPath, worktree.skippedPostCreateCommands, worktree.skippedVariables)
        return EXIT_OK
    }

    private val resolveMultirepo = ResolveMultirepoUseCase(multirepoSource, executor, fileManager)

    private var ignoredDeclarationNoted = false

    private suspend fun multirepoContext(): MultirepoContext? {
        val context = resolveMultirepo(workingDirectory).getOrThrow()
        if (context == null && !ignoredDeclarationNoted) {
            runCatching { resolveMultirepo.nearestDeclaration(workingDirectory) }.getOrNull()?.takeIf { !it.approved }?.let { ignored ->
                ignoredDeclarationNoted = true
                err.line("shogun: ignoring ${printable(ignored.declarationPath)}: it isn't approved on this machine; review it with 'shogun multirepo trust'")
            }
        }
        return context
    }

    private fun printable(text: String): String =
        text.replace(Regex("[\\p{Cc}\\p{Cf}\\u2028\\u2029]")) { "\\u%04x".format(it.value.single().code) }

    private fun members(context: MultirepoContext): List<MultirepoMember> =
        multirepoMembers(context.multirepo, projectRepository.list())

    private val readGroups = ReadGroupsUseCase(
        DetectGroupsUseCase(executor),
        resolveBases = { member ->
            RepositoryBases(ResolveMainBranchUseCase(member.config, executor)().getOrNull(), ResolveRecordedBasesUseCase(member.config, executor)())
        },
        readStatus = { member, mainBranch, base -> ReadWorktreeStatusUseCase(executor, fileManager)(member.worktree, mainBranch, base) },
        detectAgents = { searches -> DetectRunningAgentsUseCase(executor, herdr, sessionStore, claudeSessions = claudeSessions, orca = orcaReader)(searches) },
    )

    private suspend fun multirepo(command: CliCommand.Multirepo): Int {
        val configure = ConfigureMultirepoUseCase(multirepoSource, resolveMultirepo, projectRepository, executor)
        val action = command.action
        if (action is MultirepoAction.Init) return initMultirepo(configure, action, command.json)
        if (action is MultirepoAction.Trust) return trustMultirepo(configure, action, command.json)
        val context = resolveMultirepo(workingDirectory).getOrThrow() ?: run {
            val nearest = resolveMultirepo.nearestDeclaration(workingDirectory) ?: throw WorktreeError.MultirepoNotFound(workingDirectory)
            if (action != MultirepoAction.Show && action != MultirepoAction.EnvList) throw WorktreeError.MultirepoNeedsReview(nearest.declarationPath)
            resolveMultirepo.validate(nearest)
            MultirepoContext(nearest, workingDirectory, null)
        }
        val multirepo = context.multirepo
        val updated = try {
            when (action) {
                is MultirepoAction.Init, MultirepoAction.Show, is MultirepoAction.Trust -> multirepo
                is MultirepoAction.Name -> configure.setName(multirepo, action.name).getOrThrow()
                is MultirepoAction.AddRepo -> {
                    val decision = worktreesFolderDecision(action.useWorktreesRoot, command.json)
                    val added = configure.addRepository(multirepo, action.alias, absolute(action.path), decision).getOrThrow()
                    if (command.json) {
                        printJson(addedRepositoryJson(multirepo, added))
                    } else {
                        out.line("added ${added.alias} (${added.path})" + if (added.registered) ", registered as a project" else "")
                        out.line("worktrees of ${added.alias} go to ${added.project.config.worktreesRoot}")
                    }
                    return EXIT_OK
                }
                is MultirepoAction.RemoveRepo -> configure.removeRepository(multirepo, action.alias).getOrThrow()
                is MultirepoAction.Defaults -> configure.setDefaults(multirepo, action.aliases).getOrThrow()
                is MultirepoAction.Agent -> configure.setAgent(multirepo, AgentLaunchConfig(action.name, action.command, action.useHeadroom)).getOrThrow()
                is MultirepoAction.WorkspaceSet -> configure.setWorkspace(multirepo, action.alias, action.default).getOrThrow()
                MultirepoAction.WorkspaceClear -> configure.clearWorkspace(multirepo).getOrThrow()
                MultirepoAction.WorkspaceDetect -> {
                    val detected = configure.detectWorkspace(multirepo).getOrThrow()
                    if (!command.json) workspaceDetectionLines(multirepo, detected).forEach { out.line(it) }
                    detected
                }
                MultirepoAction.EnvList -> return printSharedVariables(multirepo, command.json)
                is MultirepoAction.EnvSet ->
                    return printSharedVariables(configure.setSharedVariable(multirepo, action.alias, action.name, action.template).getOrThrow(), command.json)
                is MultirepoAction.EnvUnset ->
                    return printSharedVariables(configure.unsetSharedVariable(multirepo, action.alias, action.name).getOrThrow(), command.json)
                is MultirepoAction.WorktreesRoot -> configure.setWorktreesRoot(multirepo, absolute(action.folder)).getOrThrow()
                MultirepoAction.Delete -> {
                    configure.delete(multirepo).getOrThrow()
                    if (command.json) printJson(buildJsonObjectOf("deleted" to multirepo.declarationPath)) else out.line("deleted ${multirepo.declarationPath}")
                    return EXIT_OK
                }
            }
        } catch (error: UnknownMultirepoAlias) {
            err.line("shogun: ${error.message}")
            return EXIT_USAGE
        }
        val configs = members(context.copy(multirepo = updated)).associate { member ->
            member.alias to projectRepository.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, member.config.baseRepositoryPath) }?.config
        }
        val bases = if (action == MultirepoAction.Show) {
            members(context.copy(multirepo = updated)).associate { it.alias to ResolveMainBranchUseCase(it.config, executor)().getOrNull()?.name }
        } else {
            emptyMap()
        }
        if (command.json) {
            printJson(multirepoJson(updated, configs, bases))
        } else {
            out.line("${updated.name}  ${updated.folder}")
            out.line(if (updated.approved) "approved on this machine" else "not approved on this machine; review it with 'shogun multirepo trust'")
            out.line("worktrees-root  ${updated.worktreesRoot}")
            updated.repositories.forEach { repository ->
                val config = configs[repository.alias]
                val details = listOfNotNull(
                    repository.path,
                    config?.worktreesRoot?.ifBlank { null }?.let { "worktrees=$it" } ?: "no-project",
                    bases[repository.alias]?.let { "base=$it" },
                    "default".takeIf { repository.alias in updated.defaultRepos },
                    "workspace".takeIf { repository == updated.workspaceRepository },
                    "detected".takeIf { repository == updated.workspaceRepository && updated.workspaceDetected },
                )
                out.line("  ${repository.alias}  ${details.joinToString("  ")}")
            }
            sharedVariableLines(updated).forEach { out.line(it) }
            out.line("agent  ${updated.agent.displayName} (${updated.agent.resolvedCommand()})")
            if (updated.ignoresDeclaredAgent) out.line(IGNORED_DECLARED_AGENT)
            if (updated.workspaceDetected) out.line("The folder is a Git repository, detected as the workspace repository; declare it with `shogun multirepo workspace set` or turn it off with `shogun multirepo workspace clear`.")
        }
        return EXIT_OK
    }

    private suspend fun initMultirepo(configure: ConfigureMultirepoUseCase, action: MultirepoAction.Init, json: Boolean): Int {
        multirepoSource.forgetMissing()
        if (multirepoSource.read(workingDirectory) == null) {
            val multirepo = configure.init(workingDirectory, action.worktreesRoot?.let(::absolute), action.name).getOrThrow()
            if (json) printJson(multirepoJson(multirepo, emptyMap(), emptyMap())) else out.line(multirepo.declarationPath)
            return EXIT_OK
        }
        listOfNotNull("--worktrees-root".takeIf { action.worktreesRoot != null }, "--name".takeIf { action.name != null }).firstOrNull()?.let {
            err.line("shogun: $it only applies to a new multirepo; this folder already has one, use 'shogun multirepo worktrees-root' or 'shogun multirepo name'")
            return EXIT_USAGE
        }
        val reviewed = resolveMultirepo.build(workingDirectory, multirepoSource.read(workingDirectory) ?: throw WorktreeError.MultirepoNotFound(workingDirectory))
        resolveMultirepo.validate(reviewed)
        if (!reviewed.approved) approvalRefusal(reviewed, action.yes, json)?.let { return it }
        val registered = configure.register(reviewed, worktreesFolderDecision(action.useWorktreesRoot, json)).getOrThrow()
        val multirepo = registered.multirepo
        if (json) {
            printJson(registeredMultirepoJson(registered))
        } else {
            out.line(
                if (registered.trustedNow) "approved and registered the existing multirepo ${printable(multirepo.name)} (${printable(multirepo.declarationPath)})"
                else "the multirepo ${printable(multirepo.name)} is already registered (${printable(multirepo.declarationPath)})",
            )
            registered.registeredProjects.forEach { out.line("registered ${it.name} as a project; its worktrees go to ${it.config.worktreesRoot}") }
        }
        return EXIT_OK
    }

    private fun worktreesFolderDecision(useWorktreesRoot: Boolean?, json: Boolean) = WorktreesFolderDecision { project, current, proposed ->
        useWorktreesRoot ?: (!json && confirm(
            "Keep the new worktrees of $project in $proposed" + (current?.let { " instead of $it" } ?: "") + "? [y/N] ",
        ))
    }

    private fun approvalRefusal(multirepo: Multirepo, yes: Boolean, json: Boolean): Int? {
        if (yes) return null
        if (json) {
            err.line("shogun: ${printable(multirepo.declarationPath)} isn't approved on this machine; pass --yes to approve it")
            return EXIT_FAILURE
        }
        multirepoReview(multirepo).forEach { out.line(it) }
        err.append("Approve this multirepo? ShogunAi will create branches and worktrees in these repositories and run their approved post-creation commands. [y/N] ")
        val answer = readAnswer() ?: run {
            err.line("")
            err.line("shogun: can't ask without a terminal; pass --yes to approve this multirepo")
            return EXIT_FAILURE
        }
        return if (answer.trim().lowercase() in setOf("y", "yes")) null else EXIT_OK
    }

    private suspend fun trustMultirepo(configure: ConfigureMultirepoUseCase, action: MultirepoAction.Trust, json: Boolean): Int {
        val directory = action.folder?.let(::absolute) ?: workingDirectory
        val multirepo = resolveMultirepo.nearestDeclaration(directory) ?: throw WorktreeError.MultirepoNotFound(directory)
        if (action.revoke) {
            configure.revoke(multirepo).getOrThrow()
            if (json) {
                printJson(multirepoTrustJson(multirepo, approved = false, changed = multirepo.approved))
            } else {
                out.line("forgot ${printable(multirepo.declarationPath)}; the declaration file is kept")
            }
            return EXIT_OK
        }
        resolveMultirepo.validate(multirepo)
        if (json) {
            val changed = action.yes && !multirepo.approved
            if (changed) configure.trust(multirepo).getOrThrow()
            printJson(multirepoTrustJson(multirepo, approved = multirepo.approved || changed, changed = changed))
            return EXIT_OK
        }
        multirepoReview(multirepo).forEach { out.line(it) }
        out.line(if (multirepo.approved) "approved on this machine" else "not approved on this machine")
        if (multirepo.approved) return EXIT_OK
        if (!action.yes) {
            err.append("Approve this multirepo? ShogunAi will create branches and worktrees in these repositories and run their approved post-creation commands. [y/N] ")
            val answer = readAnswer() ?: run {
                err.line("")
                err.line("shogun: can't ask without a terminal; pass --yes to approve this multirepo")
                return EXIT_FAILURE
            }
            if (answer.trim().lowercase() !in setOf("y", "yes")) return EXIT_OK
        }
        configure.trust(multirepo).getOrThrow()
        out.line("approved")
        return EXIT_OK
    }

    private fun multirepoReview(multirepo: Multirepo): List<String> {
        fun located(path: String) = printable(path) + if (multirepo.isOutsideParent(path)) "  (outside ${printable(multirepo.parentFolder)})" else ""
        return buildList {
            add("Multirepo declared by ${printable(multirepo.declarationPath)}:")
            multirepo.declaredName?.let { add("  name  ${printable(it)}") }
            multirepo.repositories.forEach { repository ->
                val workspace = if (repository == multirepo.workspaceRepository) "  (workspace repository)" else ""
                add("  ${repository.alias}  ${located(repository.path)}$workspace")
            }
            if (multirepo.repositories.isEmpty()) add("  no repositories")
            add("worktrees-root  ${located(multirepo.worktreesRoot)}")
            addAll(sharedVariableLines(multirepo))
            if (multirepo.ignoresDeclaredAgent) add(IGNORED_DECLARED_AGENT)
        }
    }

    private fun printSharedVariables(multirepo: Multirepo, json: Boolean): Int {
        when {
            json -> printJson(sharedVariablesListJson(multirepo))
            multirepo.sharedTemplates.isEmpty() -> out.line("${multirepo.name} shares no variables")
            else -> sharedVariableLines(multirepo).forEach { out.line(it) }
        }
        return EXIT_OK
    }

    private fun workspaceDetectionLines(before: Multirepo, after: Multirepo): List<String> {
        val alias = after.workspaceRepository?.alias ?: return emptyList()
        if (before.declaration.workspace == null) return listOf("the workspace repository is already detected as '$alias'")
        val defaults = if (alias in after.defaultRepos) "" else "; it isn't a default repository"
        return listOf("the workspace repository is detected again as '$alias'$defaults")
    }

    private fun sharedVariableLines(multirepo: Multirepo): List<String> {
        val templates = multirepo.sharedTemplates
        if (templates.isEmpty()) return emptyList()
        return listOf("shared variables") + templates.groupBy { it.first }.flatMap { (alias, variables) ->
            listOf("  $alias") + variables.map { (_, name, template) -> "    ${printable(name)}=${printable(template)}" }
        }
    }

    private fun buildJsonObjectOf(vararg entries: Pair<String, String>): JsonObject =
        JsonObject(entries.associate { (key, value) -> key to JsonPrimitive(value) })

    private suspend fun newGroup(context: MultirepoContext, command: CliCommand.New): Int {
        listOfNotNull(
            "--branch".takeIf { command.branch != null },
            "--from".takeIf { command.from != null },
            "--worktrees-root".takeIf { command.worktreesRoot != null },
        ).firstOrNull()?.let {
            err.line("shogun: $it doesn't apply to a group")
            return EXIT_USAGE
        }
        val multirepo = context.multirepo
        val aliases = command.repos ?: multirepo.defaultRepos
        if (aliases.isEmpty()) {
            err.line("shogun: ${multirepo.name} has no default repositories; pass --repos <alias>[,<alias>...]")
            return EXIT_USAGE
        }
        aliases.firstOrNull { it !in multirepo.aliases }?.let {
            err.line("shogun: ${UnknownMultirepoAlias(it, multirepo.aliases).message}")
            return EXIT_USAGE
        }
        val members = members(context).let { all -> aliases.distinct().mapNotNull { alias -> all.firstOrNull { it.alias == alias } } }
        val approval = if (command.json) RepositoryApproval.SKIP else terminalApproval
        val creation = CreateGroupUseCase(executor, fileManager, provisioningSource, prepareEnvironment, trust, approval, provisioningRecords)(
            members,
            command.taskId.orEmpty(),
            command.branchType,
            fetch = command.fetch,
        ).getOrThrow()
        if (command.json) {
            printJson(groupCreationJson(creation))
        } else {
            creation.repositories.forEach { result ->
                when (result.outcome) {
                    GroupRepositoryOutcome.CREATED -> {
                        out.line(result.path.orEmpty())
                        err.line("${result.alias}: created ${result.path} on ${creation.branch}")
                        members.firstOrNull { it.alias == result.alias }?.let {
                            noteSkipped(it.config.baseRepositoryPath, result.skippedPostCreateCommands, result.skippedVariables)
                        }
                    }
                    GroupRepositoryOutcome.EXISTING -> err.line("${result.alias}: already in the group at ${result.path}")
                    GroupRepositoryOutcome.FAILED -> err.line(
                        "shogun: ${result.alias}: couldn't create its worktree" + (result.path?.let { " ($it kept)" } ?: "") + ": " +
                            failureReason(result.error ?: IllegalStateException("unknown failure")),
                    )
                }
            }
        }
        return if (creation.failed) EXIT_FAILURE else EXIT_OK
    }

    private suspend fun groupStatus(context: MultirepoContext, json: Boolean): Int {
        val state = readGroups(members(context))
        if (json) {
            printJson(groupStatusJson(context.multirepo, state))
            return EXIT_OK
        }
        state.worktrees.failures.forEach { (alias, error) -> err.line("shogun: couldn't list the worktrees of $alias: ${failureReason(error)}") }
        if (state.groups.isEmpty()) out.line("No groups.")
        state.groups.forEach { group ->
            out.line(group.group.branch)
            group.worktrees.forEach { out.line("  ${groupWorktreeLine(it, showBranch = false)}") }
        }
        if (state.ungrouped.isNotEmpty()) {
            out.line("ungrouped")
            state.ungrouped.forEach { out.line("  ${groupWorktreeLine(it, showBranch = true)}") }
        }
        return EXIT_OK
    }

    private fun groupWorktreeLine(state: GroupWorktreeState, showBranch: Boolean): String {
        val worktree = state.member.worktree
        val status = state.status?.getOrNull()
        val details = buildList {
            add(state.member.alias)
            add(worktree.path)
            if (showBranch) add(worktree.branch ?: "(detached)")
            state.agents.takeIf { it.isNotEmpty() }?.let { agents -> add("agents=${agents.joinToString(",") { agentLabel(it) }}") }
            when {
                worktree.isPrunable -> add("folder-missing")
                status == null -> add(state.status?.exceptionOrNull()?.let { "status-unavailable (${failureReason(it)})" } ?: "status-unavailable")
                else -> {
                    add("changes=${status.uncommittedChanges}")
                    status.base?.let { add("ahead=${it.ahead} behind=${it.behind}" + (status.baseName?.let { base -> " of $base" } ?: "")) }
                    if (status.isMerged) add("merged")
                    if (status.isNotStarted) add("not-started")
                }
            }
        }
        return details.joinToString("  ")
    }

    private suspend fun openInMultirepo(context: MultirepoContext, command: CliCommand.Open): Int {
        val multirepo = context.multirepo
        val target = command.target
        val runner = runnerFor(command)
        if (target == null) {
            val opening = runner.openMultirepoAgent(multirepo).getOrThrow()
            if (command.json) printJson(multirepoAgentOpenJson(multirepo, opening)) else out.line(openingLine(multirepo.folder, opening))
            return EXIT_OK
        }
        if (command.agent != null) {
            err.line("shogun: each worktree of a group opens with its own project's agent, so --agent doesn't apply")
            return EXIT_USAGE
        }
        val worktrees = DetectGroupsUseCase(executor)(members(context))
        val group = when (val found = findGroup(worktrees, target)) {
            is GroupTarget.Found -> found.group
            is GroupTarget.Ambiguous -> {
                err.line("shogun: '$target' matches more than one group: ${found.branches.joinToString()}")
                return EXIT_USAGE
            }
            GroupTarget.Missing -> {
                err.line("shogun: no group matches '$target' in ${multirepo.name}")
                return EXIT_FAILURE
            }
        }
        val openings = runner.openGroup(group).getOrThrow()
        if (command.json) {
            printJson(groupOpenJson(group.branch, runner.id, openings))
        } else {
            openings.forEach { opening ->
                opening.result
                    .onSuccess { out.line(openingLine(opening.path, it)) }
                    .onFailure { err.line("shogun: couldn't open ${opening.alias} (${opening.path}) in ${runner.id.displayName}: ${failureReason(it)}") }
            }
        }
        return if (openings.all { it.result.isSuccess }) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun cleanGroups(context: MultirepoContext, command: CliCommand.Clean, say: (String) -> Unit): Int {
        val state = readGroups(members(context))
        val selection = selectGroupCleanup(state.groups).let { it.copy(loose = selectLooseCleanup(state.ungrouped)) }
        selection.kept.forEach { kept ->
            val reason = when (kept.reason) {
                GroupKeptReason.NOT_MERGED -> "${kept.alias} isn't merged"
                GroupKeptReason.UNCOMMITTED_CHANGES -> "${kept.alias} has uncommitted changes"
                GroupKeptReason.STATUS_UNAVAILABLE -> "the status of ${kept.alias} is unavailable"
                GroupKeptReason.AGENT_RUNNING -> "${kept.agents.joinToString(", ") { it.name }} is running in ${kept.alias}"
            }
            say("kept ${kept.group.branch} ($reason)")
        }
        if (selection.isEmpty) {
            if (command.json) printJson(groupCleanJson(selection, dryRun = !command.confirmed)) else out.line("Nothing to clean up.")
            return EXIT_OK
        }
        if (!command.confirmed) {
            if (command.json) {
                printJson(groupCleanJson(selection, dryRun = true))
                return EXIT_OK
            }
            selection.candidates.forEach { group ->
                out.line("would remove the group ${group.group.branch}")
                group.worktrees.forEach { out.line("  would remove ${it.member.alias} ${it.member.worktree.path}") }
            }
            selection.loose.forEach { out.line("would remove ${it.member.alias} ${it.member.worktree.path}") }
            val flags = listOfNotNull(
                "--delete-branches".takeIf { command.deleteBranches },
                "--stop-gradle-daemons".takeIf { command.stopGradleDaemons },
                "--stop-maven-daemons".takeIf { command.stopMavenDaemons },
                "--stop-emulators".takeIf { command.stopEmulators },
                "--delete-simulators".takeIf { command.deleteSimulators },
                "--delete-derived-data".takeIf { command.deleteDerivedData },
                "--down-compose-projects".takeIf { command.downComposeProjects },
                "--yes",
            )
            out.line("Run 'shogun clean ${flags.joinToString(" ")}' to remove ${if (selection.loose.isEmpty()) "the groups" else "them"}.")
            return EXIT_OK
        }
        var exitCode = EXIT_OK
        selection.removableByAlias.forEach { (_, worktrees) ->
            val config = worktrees.first().member.config
            val all = ListWorktreesUseCase(config, executor)().getOrThrow()
            val paths = worktrees.map { it.member.worktree.path }.toSet()
            val candidates = all.filter { it.path in paths }
            val plan = CleanJson(dryRun = false, candidates = candidates.map { CleanCandidateJson(it.path, it.branch, it.isPrunable) })
            val code = cleanCandidates(config, all, ResolveMainBranchUseCase(config, executor)().getOrNull(), candidates, plan, command.copy(json = false), say)
            if (code != EXIT_OK) exitCode = code
        }
        if (command.json) {
            val paths = selection.removablePaths
            printJson(
                groupCleanJson(
                    selection,
                    dryRun = false,
                    removed = paths.filterNot { fileManager.exists(it) },
                    failures = paths.filter { fileManager.exists(it) }.map { it to "not removed" },
                ),
            )
        }
        return exitCode
    }

    private suspend fun groupContextLines(context: MultirepoContext, worktreePath: String): List<String> {
        val worktrees = DetectGroupsUseCase(executor)(members(context))
        val group = worktrees.groupOf(worktreePath) ?: return emptyList()
        return GroupContext(fileManager).groupLines(context.multirepo, group, worktreePath)
    }

    private suspend fun branchOf(config: ProjectConfig, path: String): String? =
        ListWorktreesUseCase(config, executor)().getOrNull()?.firstOrNull { sameDirectory(it.path, path) }?.branch

    private fun printJson(json: JsonObject) = out.line(json.compact())

    private fun saying(json: Boolean): (String) -> Unit = if (json) { _ -> } else { text -> out.line(text) }

    private fun podsLines(pods: PodsPreparation): List<String> =
        pods.cloned.map { "${podsFolder(it)} (cloned from the base)" } +
            pods.installNeeded.map { "${podsFolder(it.folder)}: pod install needed (${podsReason(it.reason)})" } +
            listOfNotNull(pods.error?.let { "couldn't prepare the pods: $it" })

    private fun podsFolder(folder: String): String = if (folder == ".") "Pods" else "$folder/Pods"

    private fun podsReason(reason: PodsInstallReason): String = when (reason) {
        PodsInstallReason.BASE_HAS_NO_PODS -> "the base has no pods"
        PodsInstallReason.BASE_PODS_DIFFER -> "the base's pods differ from Podfile.lock"
        PodsInstallReason.CLONE_FAILED -> "the base's pods couldn't be cloned"
    }

    private fun noteSkippedLinks(skipped: List<String>, prefix: String = "shogun") {
        skipped.forEach { err.line("$prefix: skipped the symbolic link $it in a local folder") }
    }

    private fun noteSkipped(
        repository: String,
        commands: List<List<String>>,
        variables: List<String>,
        prefix: String = "shogun",
    ) {
        if (commands.isNotEmpty()) {
            val count = if (commands.size == 1) "1 post-creation command" else "${commands.size} post-creation commands"
            err.line("$prefix: skipped $count not approved on this machine; run 'shogun trust' in $repository to review them")
        }
        if (variables.isNotEmpty()) {
            err.line(
                "$prefix: skipped environment variables not approved on this machine (${variables.joinToString(", ")}); " +
                    "run 'shogun trust' in $repository to review them",
            )
        }
    }

    private fun subjectLines(repository: String, subject: RepositoryApprovalSubject): String = buildString {
        if (subject.commands.isNotEmpty()) {
            append("Post-creation commands declared by $repository:\n")
            subject.commands.forEach { append("  ${shellLine(it)}\n") }
        }
        if (subject.variables.isNotEmpty()) {
            append("Environment variables declared by $repository:\n")
            subject.variables.forEach { (name, template) -> append("  $name = $template\n") }
        }
        subject.envFile?.let { append("Environment file: $it\n") }
    }

    private fun shellLine(command: List<String>): String = ShellQuoting.posixCommand(command)

    private fun confirm(question: String): Boolean {
        err.append(question)
        val answer = readAnswer() ?: return false.also { err.line("") }
        return answer.trim().lowercase() in setOf("y", "yes")
    }

    private suspend fun trust(command: CliCommand.Trust): Int {
        val repository = configFor(absolute(command.path ?: workingDirectory)).baseRepositoryPath
        if (command.json) return trustAsJson(command, repository)
        if (command.revoke) {
            trust.revoke(repository)
            out.line("revoked the approval of $repository")
            return EXIT_OK
        }
        val subject = provisioningSource.read(repository).approvalSubject
        if (subject.isEmpty) {
            out.line("$repository declares no post-creation commands, environment variables nor environment file.")
            return EXIT_OK
        }
        val approved = trust.isApproved(repository, subject)
        out.append(subjectLines(repository, subject))
        out.line(if (approved) "approved on this machine" else "not approved on this machine")
        if (approved) return EXIT_OK
        if (command.yes || confirm(APPROVAL_QUESTION)) {
            trust.approve(repository, subject)
            out.line("approved")
        }
        return EXIT_OK
    }

    private fun trustAsJson(command: CliCommand.Trust, repository: String): Int {
        val declared = runCatching { provisioningSource.read(repository).approvalSubject }
        val subject = if (command.revoke) declared.getOrDefault(RepositoryApprovalSubject()) else declared.getOrThrow()
        val wasApproved = trust.isApproved(repository, subject)
        val changed = when {
            command.revoke -> (!subject.isEmpty && wasApproved).also { trust.revoke(repository) }
            command.yes && !wasApproved -> true.also { trust.approve(repository, subject) }
            else -> false
        }
        val approved = if (command.revoke) subject.isEmpty else wasApproved || changed
        printJson(trustJson(repository, subject, approved, changed))
        return EXIT_OK
    }

    private suspend fun resolveStartPoint(directory: String, from: String?): String? {
        val resolve = ResolveStartPointUseCase(executor)
        return if (from != null) {
            resolve(directory, from).getOrThrow()
        } else {
            resolve(directory, "HEAD").getOrElse { throw WorktreeError.RepositoryHasNoCommits }
        }
    }

    private suspend fun resolveBaseBranch(directory: String, from: String?): String? =
        ResolveBaseBranchUseCase(executor)(directory, from ?: "HEAD")

    private suspend fun provision(command: CliCommand.Provision): Int {
        val target = absolute(command.path ?: workingDirectory)
        return when {
            command.check -> checkProvisionedFiles(target, command.json)
            command.json -> provisionWorktreeAt(target, RepositoryApproval.SKIP, json = true)
            else -> provisionWorktreeAt(target, terminalApproval)
        }
    }

    private suspend fun checkProvisionedFiles(target: String, json: Boolean): Int {
        val config = configFor(target)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { sameDirectory(it.path, target) }
            ?: throw WorktreeError.NotAWorktree(target)
        val drift = CheckProvisionedFilesUseCase(config, executor, fileManager, provisioningSource, provisioningRecords)(worktree.path).getOrThrow()
        if (json) printJson(provisionCheckJson(worktree.path, drift)) else drift.forEach { out.line("${it.path}: ${driftLabel(it.state)}") }
        return if (drift.isEmpty()) EXIT_OK else EXIT_DRIFT
    }

    private fun driftLabel(state: ProvisionDriftState): String = when (state) {
        ProvisionDriftState.MISSING -> "missing"
        ProvisionDriftState.DIFFERS -> "differs"
        ProvisionDriftState.NOT_LINKED -> "not linked"
        ProvisionDriftState.DELETED_IN_BASE -> "deleted in base"
    }

    private fun provisionedLabel(file: ProvisionedFile): String = when (file.mode) {
        ProvisionMode.COPIED -> file.path
        ProvisionMode.LINKED -> "${file.path} (linked)"
        ProvisionMode.COPIED_INSTEAD_OF_LINKING -> "${file.path} (copied: symbolic links aren't available)"
    }

    private suspend fun provisionWorktreeAt(
        target: String,
        approval: RepositoryApproval,
        onProvisioned: suspend (Worktree, ProjectConfig) -> Unit = { _, _ -> },
        json: Boolean = false,
        configDirectory: String = target,
    ): Int {
        val config = configFor(configDirectory)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { sameDirectory(it.path, target) }
            ?: throw WorktreeError.NotAWorktree(target)
        val provisioned = ProvisionWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval, provisioningRecords)(worktree.path)
        val failure = provisioned.exceptionOrNull()
        if (json && failure is WorktreeError.PostCreateCommandFailed) {
            printJson(provisionJson(worktree.path, emptyList(), emptyList(), emptyList(), failure))
            err.line("shogun: ${failure.message}")
            return EXIT_FAILURE
        }
        val result = provisioned.getOrThrow()
        if (json) {
            printJson(
                provisionJson(
                    worktree.path, result.files, result.skippedPostCreateCommands, result.skippedVariables, null,
                    result.removed, result.skippedLinks, result.pods, result.flutter,
                ),
            )
            return EXIT_OK
        }
        result.files.forEach { out.line(provisionedLabel(it)) }
        result.removed.forEach { out.line("$it (removed: deleted from the base folder)") }
        result.skippedLinks.forEach { out.line("$it (skipped: symbolic link)") }
        podsLines(result.pods).forEach { out.line(it) }
        result.flutter?.let { out.line(flutterPubGetLine(it)) }
        noteSkipped(config.baseRepositoryPath, result.skippedPostCreateCommands, result.skippedVariables)
        onProvisioned(worktree, config)
        return EXIT_OK
    }

    private suspend fun status(command: CliCommand.Status): Int {
        multirepoContext()?.takeIf { it.atFolder }?.let { return groupStatus(it, command.json) }
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor, fileManager)
        val herdrWorktreesRoot = herdrConfig.worktreesRoot()
        val agents = DetectRunningAgentsUseCase(executor, herdr, sessionStore, claudeSessions = claudeSessions, orca = orcaReader)(config, worktrees.filterNot { it.isPrunable }).getOrNull().orEmpty()
        val desktop = readDesktopTurn().getOrElse { DesktopTurnState(holder = null, waiting = emptyList()) }
        val bases = ReadBaseBranchesUseCase(config, executor)().getOrElse { emptyMap() }
        val resolvedBases = ResolveRecordedBasesUseCase(config, executor)()
        val entries = worktrees.map { worktree ->
            val read = if (worktree.isPrunable) null else readStatus(worktree, mainBranch, worktree.branch?.let(resolvedBases::get) ?: mainBranch)
            WorktreeEntry(
                worktree,
                read?.getOrNull(),
                classifyOrigin(worktree, config, herdrWorktreesRoot, orcaReader.origins()),
                read?.exceptionOrNull()?.let(::failureReason),
                agents[worktree.path].orEmpty(),
                desktop,
                worktree.branch?.let(bases::get),
            )
        }
        if (command.json) {
            printJson(statusJson(config.baseRepositoryPath, mainBranch?.name, entries))
        } else {
            entries.forEach { out.line(statusLine(it)) }
        }
        return EXIT_OK
    }

    private suspend fun base(command: CliCommand.Base): Int {
        val (config, worktree) = if (command.target == null) {
            configAndWorktreeContaining(workingDirectory)
        } else {
            val config = configFor(workingDirectory)
            config to findWorktreeTarget(command.target, ListWorktreesUseCase(config, executor)().getOrThrow(), config, ::canonical)
        }
        val branch = worktree.branch ?: run {
            if (command.json) printJson(baseJson(worktree.path, null, null))
            err.line("shogun: ${worktree.path} has no branch (detached HEAD), so it has no base")
            return EXIT_FAILURE
        }
        val base = if (command.set != null) {
            SetBaseBranchUseCase(config, executor)(branch, command.set).getOrThrow()
        } else {
            ReadBaseBranchesUseCase(config, executor)().getOrThrow()[branch]
        }
        if (command.json) printJson(baseJson(worktree.path, branch, base))
        if (base == null) {
            err.line("shogun: no base is recorded for $branch; set it with 'shogun base --set <branch>'")
            return EXIT_FAILURE
        }
        if (!command.json) out.line(base)
        return EXIT_OK
    }

    private val releaseResources = ReleaseWorktreeResourcesUseCase(
        provisioningSource,
        readEmulatorStates,
        stopEmulator,
        readSimulatorStates,
        stopSimulator,
        ListGradleDaemonsUseCase(daemonSource, fileManager),
        StopGradleDaemonsUseCase(daemonSource, executor),
    )

    private suspend fun remove(command: CliCommand.Remove): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val worktree = resolveWorktreeTarget(command.target, worktrees, config, ::canonical)
        if (!command.force && !worktree.isPrunable) {
            val agents = DetectRunningAgentsUseCase(executor, sessionStore = sessionStore)(config, listOf(worktree))
                .onFailure { err.line("shogun: couldn't check for running agents, so none were taken into account: ${it.message}") }
                .getOrNull()?.get(worktree.path).orEmpty()
            if (agents.isNotEmpty()) {
                err.line("shogun: ${agents.joinToString { it.name }} is running in ${worktree.path}; use --force to remove it anyway")
                return EXIT_FAILURE
            }
        }
        val plan = releaseResources.plan(config, worktree, worktrees)
        val removal = RemoveWorktreeUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr), fileManager, stopHeadlessApp, releaseDesktopTurn, CloseOrcaTerminalsUseCase(orca))(
            worktree.path,
            branchToDelete = worktree.branch?.takeIf { command.deleteBranch || command.forceDeleteBranch },
            force = command.force,
            forceBranchDeletion = command.forceDeleteBranch,
        )
            .getOrElse { failure ->
                if (failure is WorktreeError.GitCommandFailed && classifyGitFailure(failure.errorOutput) == GitFailureCause.LocalChanges) {
                    err.line("shogun: ${worktree.path} has uncommitted or untracked changes; use --force to remove it anyway")
                    return EXIT_FAILURE
                }
                throw failure
            }
        val say = saying(command.json)
        say("removed ${worktree.path}")
        removal.herdrFailure?.let { err.line(herdrCloseFailure(worktree.path, it)) }
        removal.orcaFailure?.let { err.line(orcaCloseFailure(worktree.path, it)) }
        removal.headlessAppFailure?.let { err.line(headlessStopFailure(worktree.path, it)) }
        val released = releaseResources.release(config, plan)
        released.stoppedEmulators.forEach { say("stopped emulator $it") }
        released.stoppedSimulators.forEach { say("stopped simulator $it") }
        released.stoppedDaemons.forEach { say("stopped Gradle daemon $it") }
        released.failures.forEach { err.line("shogun: $it") }
        val branch = worktree.branch
        val branchDeleted = branch?.takeIf { removal.branch == BranchDeletion.Deleted }
        val branchKeptReason = branch?.let { removal.branch?.keptReason() }
        if (branch != null && removal.branch != null) {
            say(if (branchDeleted != null) "deleted branch $branch" else "kept branch $branch ($branchKeptReason)")
        }
        if (command.json) {
            printJson(
                removeJson(
                    worktree.path,
                    worktree.branch,
                    branchDeleted,
                    branchKeptReason,
                    released.stoppedEmulators,
                    released.stoppedSimulators,
                    released.stoppedDaemons,
                    released.failures,
                ),
            )
        }
        return if (released.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun open(command: CliCommand.Open): Int {
        multirepoContext()?.takeIf { it.atFolder }?.let { context ->
            return openInMultirepo(context, command)
        }
        val target = command.target ?: run {
            err.line("shogun: 'open' needs a task id, path or branch")
            return EXIT_USAGE
        }
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val worktree = findWorktreeTarget(target, worktrees, config, ::canonical)
        val agent = if (command.agent == null) {
            config.agentLaunchConfig
        } else {
            config.agents.firstOrNull { it.displayName.equals(command.agent, ignoreCase = true) } ?: run {
                err.line("shogun: no agent named '${command.agent}'; this project's agents are ${config.agents.joinToString { it.displayName }}")
                return EXIT_USAGE
            }
        }
        val opening = runnerFor(command).openWorktree(config, worktree, agent).getOrThrow()
        if (command.json) printJson(openJson(worktree.path, worktree.branch, opening)) else out.line(openingLine(worktree.path, opening))
        return EXIT_OK
    }

    private fun agentRunner(id: RunnerId): AgentRunner = when (id) {
        RunnerId.HERDR -> HerdrAgentRunner(herdr, resolveEnvironment, herdrLauncher())
        RunnerId.ORCA -> OrcaAgentRunner(
            orca,
            resolveEnvironment,
            claudeSessionNaming(herdrAgentStartTimeout),
            runningEmulator = readEmulatorStates::runningSerial,
        )
    }

    private fun runnerFor(command: CliCommand.Open): AgentRunner = agentRunner(command.runner ?: runnerInUse())

    private fun runnerInUse(): RunnerId =
        runnerInUse(runCatching { toolPreferences.loadRunner() }.getOrNull()) { agentRunner(it).isInstalled() }

    private fun claudeSessionNaming(timeout: kotlin.time.Duration) =
        ClaudeSessionNaming(claudeSessions, isClaudeProcessAlive, timeout, processEnvironment = SystemProcessEnvironmentReader(executor))

    private fun herdrLauncher() =
        HerdrAgentLauncher(herdr, herdrAgentStartTimeout, registry = claudeSessions, isClaudeProcessAlive = isClaudeProcessAlive)

    private fun openingLine(path: String, opening: RunnerOpening): String {
        val agent = opening.agent
        val handle = opening.handle
        val line = when (opening.runner) {
            RunnerId.HERDR -> when (opening.outcome) {
                RunnerOutcome.OPENED -> "opened $path in Herdr with $agent (pane $handle)"
                RunnerOutcome.STARTED_AGENT -> "started $agent in $path's open Herdr workspace (pane $handle)"
                RunnerOutcome.FOCUSED -> "focused $path in Herdr (already open, pane $handle)"
            }
            RunnerId.ORCA -> when (opening.outcome) {
                RunnerOutcome.OPENED, RunnerOutcome.STARTED_AGENT -> "opened $path in Orca with $agent (terminal $handle)"
                RunnerOutcome.FOCUSED -> "focused $path's running agent in Orca (terminal $handle)"
            }
        }
        val named = opening.sessionName?.let { "$line as session $it" } ?: line
        return opening.emulator?.let { "$named, with $it in its emulator view" } ?: named
    }

    private suspend fun repair(command: CliCommand.Repair): Int {
        val projectConfig = configFor(workingDirectory)
        if (projectConfig.worktreesRoot.isBlank()) {
            err.line("shogun: this repository has no ShogunAi project, so there's no worktrees folder to repair into.")
            err.line("Add the repository as a project in the desktop app first.")
            return EXIT_USAGE
        }
        val relinked = RepairWorktreesUseCase(projectConfig, executor, fileManager)().getOrThrow()
        when {
            command.json -> printJson(repairJson(relinked))
            relinked.isEmpty() -> out.line("Nothing to repair.")
            else -> relinked.forEach { out.line("repaired $it") }
        }
        return EXIT_OK
    }

    private fun canonical(path: String): String = runCatching { canonicalPath(absolute(path)) }.getOrDefault(path)

    private suspend fun clean(command: CliCommand.Clean): Int {
        val say = saying(command.json)
        multirepoContext()?.takeIf { it.atFolder }?.let { return cleanGroups(it, command, say) }
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor, fileManager)
        val bases = ResolveRecordedBasesUseCase(config, executor)()
        val statusReads = worktrees
            .filterNot { it.isMain || it.isPrunable || it.isLocked }
            .associateWith { worktree -> readStatus(worktree, mainBranch, worktree.branch?.let(bases::get) ?: mainBranch) }
        val statuses = statusReads.mapNotNull { (worktree, read) -> read.getOrNull()?.let { worktree.path to it } }.toMap()
        val agents = DetectRunningAgentsUseCase(executor, herdr, sessionStore, orca = orcaReader)(config, worktrees.filterNot { it.isMain || it.isPrunable })
            .onFailure { err.line("shogun: couldn't check for running agents, so none were taken into account: ${it.message}") }
            .getOrNull()
        val selection = selectCleanup(worktrees, statuses, agents)
        val candidates = selection.candidates
        selection.keptForRunningAgent.forEach { (worktree, running) ->
            say("kept ${worktree.path} (${running.joinToString { it.name }} is running)")
        }
        val skipped = statusReads.mapNotNull { (worktree, read) ->
            read.exceptionOrNull()?.let { CleanSkippedJson(worktree.path, failureReason(it)) }
        }
        skipped.forEach { say("skipped ${it.path} (status unavailable: ${it.reason})") }
        val plan = CleanJson(
            dryRun = !command.confirmed,
            candidates = candidates.map { CleanCandidateJson(it.path, it.branch, it.isPrunable) },
            keptForAgents = selection.keptForRunningAgent.map { (worktree, running) ->
                CleanKeptJson(worktree.path, running.map { it.name })
            },
            skipped = skipped,
        )
        return cleanCandidates(config, worktrees, mainBranch, candidates, plan, command, say)
    }

    private suspend fun cleanCandidates(
        config: ProjectConfig,
        worktrees: List<Worktree>,
        mainBranch: MainBranch?,
        candidates: List<Worktree>,
        plan: CleanJson,
        command: CliCommand.Clean,
        say: (String) -> Unit,
    ): Int {
        val simulators = if (command.deleteSimulators) simulatorsBeforeCleanup(config, candidates) else SimulatorCleanup()
        val derivedDataOrphans = if (command.deleteDerivedData && derivedDataSource.isSupported() && xcodeProvider(config.baseRepositoryPath) != null) {
            listDerivedData(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ORPHANS)
                .onFailure { err.line("shogun: couldn't list DerivedData: ${it.message}") }
                .getOrNull()?.orphans.orEmpty()
        } else {
            emptyList()
        }
        if (candidates.isEmpty() && simulators.orphans.isEmpty() && derivedDataOrphans.isEmpty()) {
            if (command.json) printJson(cleanJson(plan)) else out.line("Nothing to clean up.")
            return EXIT_OK
        }
        val daemons = if (command.stopGradleDaemons) daemonsBeforeCleanup(worktrees) else emptyList()
        val mavenDaemons = if (command.stopMavenDaemons) mavenDaemonsBeforeCleanup(config, worktrees) else emptyList()
        val emulators = if (command.stopEmulators) emulatorsBeforeCleanup(config, candidates) else emptyMap()
        val closeHerdrWorkspaces = CloseHerdrWorkspacesUseCase(config, herdr)
        val closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca)
        val downCompose = command.downComposeProjects && declaresCompose(config.baseRepositoryPath)
        if (!command.confirmed) {
            if (command.json) {
                printJson(cleanJson(plan))
                return EXIT_OK
            }
            val herdrWorkspaces = closeHerdrWorkspaces.openWorkspaces(candidates.map { it.path })
            val orcaTerminals = closeOrcaTerminals.withTerminals(candidates.filterNot { it.isPrunable }.map { it.path })
            candidates.forEach {
                out.line("would remove ${it.path}${if (it.isPrunable) " (folder missing)" else ""}")
                if (it.path in herdrWorkspaces) out.line("would close Herdr workspace of ${it.path}")
                if (it.path in orcaTerminals) out.line("would close Orca terminals of ${it.path}")
            }
            idleDaemonsOfRemovedWorktrees(daemons, worktrees, candidates.map { it.path })
                .forEach { out.line("would stop Gradle daemon $it") }
            idleMavenDaemonsOfRemovedWorktrees(mavenDaemons, worktrees, candidates.map { it.path })
                .forEach { out.line("would stop Maven daemon $it") }
            emulators.values.forEach { out.line("would stop emulator $it") }
            simulators.byWorktree.values.forEach { out.line("would delete simulator ${it.name} (${it.udid})") }
            simulators.orphans.forEach { out.line("would delete orphaned simulator ${it.name} (${it.udid})") }
            derivedDataOrphans.forEach { out.line("would delete orphaned DerivedData ${it.path}") }
            if (downCompose) composeProjectsOf(config, candidates).forEach { out.line("would take down Compose project $it") }
            out.line(dryRunHint(command, candidates.size, simulators.orphans.isNotEmpty(), derivedDataOrphans.isNotEmpty()))
            return EXIT_OK
        }
        val report = CleanUpWorktreesUseCase(
            config, executor, closeHerdrWorkspaces, composeSource, fileManager, composeRegistry, stopHeadlessApp, releaseDesktopTurn, closeOrcaTerminals,
        )(
            candidates,
            mainBranch,
            command.deleteBranches,
            downComposeProjects = downCompose,
            removeComposeVolumes = downCompose && command.removeComposeVolumes,
        ).getOrThrow()
        report.removed.forEach { say("removed $it") }
        report.keptBranches.forEach { say("kept branch $it (not merged)") }
        report.failures.forEach { err.line("shogun: couldn't remove ${it.path}: ${it.error.message}") }
        report.herdrFailures.forEach { err.line(herdrCloseFailure(it.path, it.error)) }
        report.orcaFailures.forEach { err.line(orcaCloseFailure(it.path, it.error)) }
        report.headlessAppFailures.forEach { err.line(headlessStopFailure(it.path, it.error)) }
        report.composeDown?.takenDown?.forEach { say("took down Compose project $it") }
        report.composeDown?.failures?.forEach { err.line("shogun: couldn't take down Compose project ${it.name}: ${it.detail}") }
        report.composeUnavailable?.let { err.line("shogun: Docker isn't available ($it), so the Compose projects were kept") }
        report.composeResources?.let { printLeftoversReport(it, say) }
        val deletedBranches = if (command.deleteBranches) {
            candidates.filter { it.path in report.removed }.mapNotNull { it.branch }.filterNot { it in report.keptBranches }
        } else {
            emptyList()
        }
        val pids = idleDaemonsOfRemovedWorktrees(daemons, worktrees, report.removed)
        val stoppedDaemons = if (pids.isNotEmpty()) stopDaemonsAfterCleanup(pids, say) else emptyList()
        val mavenPids = idleMavenDaemonsOfRemovedWorktrees(mavenDaemons, worktrees, report.removed)
        val stoppedMavenDaemons = if (mavenPids.isNotEmpty()) stopMavenDaemonsAfterCleanup(mavenPids, say) else emptyList()
        val removedEmulators = report.removed.mapNotNull { emulators[it] }
        val stoppedEmulators = if (removedEmulators.isNotEmpty()) stopEmulatorsAfterCleanup(config, removedEmulators, say) else emptyList()
        var deletedSimulators = emptyList<String>()
        if (command.deleteSimulators) {
            val orphansNow = deleteSimulators.orphans(config).getOrDefault(simulators.orphans)
            val removedSimulators = report.removed.mapNotNull { simulators.byWorktree[it]?.udid } + orphansNow.map { it.udid }
            if (removedSimulators.isNotEmpty()) deletedSimulators = deleteSimulatorsAfterCleanup(removedSimulators.distinct(), say)
        }
        var derivedDataFailed = false
        val derivedDataDeleted = mutableListOf<String>()
        val derivedDataFailures = mutableListOf<CleanFailureJson>()
        if (command.deleteDerivedData && derivedDataSource.isSupported() && xcodeProvider(config.baseRepositoryPath) != null) {
            deleteOrphanedDerivedData()
                .onSuccess { deletion ->
                    deletion.deleted.forEach {
                        derivedDataDeleted += it.path
                        say("deleted DerivedData ${it.path}")
                    }
                    deletion.failures.forEach {
                        derivedDataFailed = true
                        derivedDataFailures += CleanFailureJson(it.path, it.detail)
                        err.line("shogun: couldn't delete DerivedData ${it.path}: ${it.detail}")
                    }
                }
                .onFailure {
                    derivedDataFailed = true
                    err.line("shogun: couldn't delete DerivedData: ${it.message}")
                }
        }
        if (command.json) {
            printJson(
                cleanJson(
                    plan.copy(
                        removed = report.removed,
                        deletedBranches = deletedBranches,
                        failures = report.failures.map { CleanFailureJson(it.path, it.error.message.orEmpty()) } + derivedDataFailures,
                        gradleDaemonsStopped = stoppedDaemons,
                        mavenDaemonsStopped = stoppedMavenDaemons,
                        emulatorsStopped = stoppedEmulators,
                        simulatorsDeleted = deletedSimulators,
                        derivedDataDeleted = derivedDataDeleted,
                        composeProjectsDown = report.composeDown?.takenDown,
                        composeResources = report.composeResources,
                    ),
                ),
            )
        }
        return if (report.failures.isEmpty() && !derivedDataFailed) EXIT_OK else EXIT_FAILURE
    }

    private fun dryRunHint(command: CliCommand.Clean, worktrees: Int, simulatorOrphans: Boolean, derivedDataOrphans: Boolean): String {
        val flags = listOfNotNull(
            "--delete-branches".takeIf { command.deleteBranches },
            "--stop-gradle-daemons".takeIf { command.stopGradleDaemons },
            "--stop-maven-daemons".takeIf { command.stopMavenDaemons },
            "--stop-emulators".takeIf { command.stopEmulators },
            "--delete-simulators".takeIf { command.deleteSimulators },
            "--delete-derived-data".takeIf { command.deleteDerivedData },
            "--down-compose-projects".takeIf { command.downComposeProjects },
            "--compose-volumes".takeIf { command.removeComposeVolumes },
            "--yes",
        )
        val what = listOfNotNull(
            when (worktrees) {
                0 -> null
                1 -> "the worktree"
                else -> "the worktrees"
            },
            "the orphaned simulators".takeIf { simulatorOrphans },
            "the orphaned DerivedData".takeIf { derivedDataOrphans },
        )
        val listed = if (what.size > 1) what.dropLast(1).joinToString(", ") + " and " + what.last() else what.single()
        return "Run 'shogun clean ${flags.joinToString(" ")}' to remove $listed."
    }

    private fun failureReason(error: Throwable): String {
        val text = (error as? WorktreeError.GitCommandFailed)?.errorOutput?.takeIf { it.isNotBlank() } ?: error.message.orEmpty()
        return text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: error::class.simpleName.orEmpty()
    }

    private data class SimulatorCleanup(
        val byWorktree: Map<String, WorktreeSimulator> = emptyMap(),
        val orphans: List<SimulatorDevice> = emptyList(),
    )

    private suspend fun simulatorsBeforeCleanup(config: ProjectConfig, candidates: List<Worktree>): SimulatorCleanup =
        SimulatorCleanup(
            byWorktree = readSimulatorStates(config, candidates)
                .onFailure { err.line("shogun: couldn't read simulators: ${it.message}") }
                .getOrDefault(emptyMap()),
            orphans = deleteSimulators.orphans(config)
                .onFailure { err.line("shogun: couldn't look for orphaned simulators: ${it.message}") }
                .getOrDefault(emptyList()),
        )

    private suspend fun deleteSimulatorsAfterCleanup(udids: List<String>, say: (String) -> Unit): List<String> =
        deleteSimulators(udids)
            .onSuccess { deleted -> deleted.forEach { say("deleted simulator $it") } }
            .onFailure { err.line("shogun: couldn't delete simulators: ${it.message}") }
            .getOrDefault(emptyList())

    private suspend fun simulator(command: CliCommand.Simulator): Int {
        val (config, worktree) = configAndWorktreeContaining(absolute(command.path ?: workingDirectory))
        var acquisition: ResourceAcquisition? = null
        val simulator = when (command.action) {
            SimulatorAction.START -> {
                val started = startSimulator(
                    config,
                    worktree.path,
                    SimulatorSettings(command.device, command.runtime),
                    command.wait?.let(::deviceWait),
                ).getOrThrow()
                acquisition = started.acquisition
                started.device
            }
            SimulatorAction.STOP -> stopSimulator(config, worktree.path).getOrThrow()
            SimulatorAction.STATUS -> {
                if (provisioningSource.read(config.baseRepositoryPath).providers.iosSimulator == null) {
                    throw WorktreeError.IosSimulatorProviderNotDeclared
                }
                if (!appleSimulators.isSupported()) throw WorktreeError.IosSimulatorsNotSupported
                readSimulatorStates(config, listOf(worktree)).getOrThrow()[worktree.path]
            }
        }
        acquisition?.releases?.forEach { err.line("released the simulator of ${releasedName(it)} to make room") }
        reportWaited(acquisition)
        val released = if (simulator == null) readReleaseRecord(worktree.path, ResourceKind.IOS_SIMULATOR) else null
        if (command.json) {
            printJson(simulatorJson(worktree.path, simulator, acquisition, released))
            return EXIT_OK
        }
        if (simulator == null) {
            out.line("not acquired" + releaseExplanation(released))
        } else {
            out.line("${simulator.udid} ${simulator.state.name.lowercase()}  ${simulator.name}")
            if (acquisition?.acquiredNow == true) out.line("export $IOS_SIMULATOR_UDID=${simulator.udid}")
        }
        if (command.action == SimulatorAction.STATUS) printKmpWiring(config)
        return EXIT_OK
    }

    private fun releasedName(release: PoolRelease): String = release.holderName.ifEmpty { "another worktree" }

    private fun printKmpWiring(config: ProjectConfig) {
        if (!kmpTestWiring.usesKotlinMultiplatform(config.baseRepositoryPath)) return
        if (kmpTestWiring.isWiredInRepository(config.baseRepositoryPath) || kmpTestWiring.isInitScriptInstalled()) {
            out.line("KMP simulator tests use this worktree's simulator.")
            return
        }
        out.line("KMP simulator tests don't use this worktree's simulator yet. Add this to the module's build.gradle.kts:")
        out.line(FileKmpTestWiring.REPOSITORY_SNIPPET)
        out.line("or run 'shogun simulator gradle-init install' to do it for every project on this machine.")
    }

    private fun simulatorGradleInit(command: CliCommand.SimulatorGradleInit): Int {
        val path = kmpTestWiring.initScriptPath()
        if (!command.install) {
            kmpTestWiring.uninstallInitScript()
            out.line("Removed $path")
            return EXIT_OK
        }
        out.line("This writes $path, which makes the iOS simulator tests of every Gradle project on this machine")
        out.line("run on the simulator in IOS_SIMULATOR_UDID when that variable is set:")
        out.line(FileKmpTestWiring.INIT_SCRIPT.trimEnd())
        if (!command.confirmed) {
            out.append("Install it? [y/N] ")
            val answer = readInput().trim().lowercase()
            if (answer != "y" && answer != "yes") {
                out.line("Nothing installed.")
                return EXIT_OK
            }
        }
        kmpTestWiring.installInitScript()
        out.line("Installed $path")
        return EXIT_OK
    }

    private suspend fun emulatorsBeforeCleanup(config: ProjectConfig, candidates: List<Worktree>): Map<String, String> =
        readEmulatorStates(config, candidates)
            .onFailure { err.line("shogun: couldn't read emulators: ${it.message}") }
            .getOrDefault(emptyMap())
            .filterValues { it.state != EmulatorState.STOPPED }
            .mapValues { it.value.serial }

    private suspend fun stopEmulatorsAfterCleanup(config: ProjectConfig, serials: List<String>, say: (String) -> Unit): List<String> =
        runCatching { stopEmulator.stopSerials(config, serials) }
            .onSuccess { stopped -> stopped.forEach { say("stopped emulator ${it.serial}") } }
            .onFailure { err.line("shogun: couldn't stop emulators: ${it.message}") }
            .getOrDefault(emptyList())
            .map { it.serial }

    private suspend fun emulator(command: CliCommand.Emulator): Int {
        val target = absolute(command.path ?: workingDirectory)
        val (config, worktree) = configAndWorktreeContaining(target)
        var acquisition: ResourceAcquisition? = null
        var orcaView: OrcaEmulatorView? = null
        val emulator = when (command.action) {
            EmulatorAction.START -> {
                val started = startEmulator(
                    config,
                    worktree.path,
                    command.avd,
                    command.noWindow.takeIf { it },
                    command.wait?.let(::deviceWait),
                ).getOrThrow()
                acquisition = started.acquisition
                orcaView = showEmulatorInOrca(worktree.path, started.device.serial)
                started.device
            }
            EmulatorAction.STOP -> stopEmulator(config, worktree.path).getOrThrow()
            EmulatorAction.STATUS -> {
                if (provisioningSource.read(config.baseRepositoryPath).providers.emulator == null) {
                    throw WorktreeError.EmulatorProviderNotDeclared
                }
                readEmulatorStates(config, listOf(worktree)).getOrThrow()[worktree.path]
            }
        }
        acquisition?.releases?.forEach { err.line("released the emulator of ${releasedName(it)} to make room") }
        reportWaited(acquisition)
        if (emulator != null) reportOrcaView(emulator.serial, orcaView)
        val released = if (emulator == null) readReleaseRecord(worktree.path, ResourceKind.EMULATOR) else null
        if (command.json) {
            printJson(emulatorJson(worktree.path, emulator, acquisition, released, orcaView))
            return EXIT_OK
        }
        if (emulator == null) {
            out.line("not acquired" + releaseExplanation(released))
        } else {
            out.line("${emulator.serial} ${emulator.state.name.lowercase()}")
            if (command.action == EmulatorAction.START) out.line("export $ANDROID_SERIAL=${emulator.serial}")
        }
        return EXIT_OK
    }

    private fun reportOrcaView(serial: String, view: OrcaEmulatorView?) {
        when (view) {
            OrcaEmulatorView.Shown -> err.line("shown $serial in Orca's emulator view")
            is OrcaEmulatorView.Failed -> err.line("shogun: couldn't show the emulator in Orca: ${view.message}")
            OrcaEmulatorView.NotOpen, null -> Unit
        }
    }

    private fun deviceWait(timeout: Duration): DeviceWait {
        var lastLine: String? = null
        return DeviceWait(timeout) { progress ->
            val line = waitProgressLine(progress)
            if (line != lastLine) err.line(line)
            lastLine = line
        }
    }

    private fun reportWaited(acquisition: ResourceAcquisition?) {
        val seconds = acquisition?.waitedSeconds?.takeIf { it > 0 } ?: return
        err.line("waited ${Duration.ofSeconds(seconds).toWaitText()}")
    }

    private fun releaseExplanation(record: ReleaseRecord?): String =
        record?.let { " (released for ${it.forWorktree} at ${Instant.ofEpochMilli(it.releasedAt)})" }.orEmpty()

    private suspend fun pool(command: CliCommand.Pool): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        if (command.action == PoolAction.TRIM) return poolTrim(command, worktrees)
        val usages = listOfNotNull(
            readPool.emulators(config.baseRepositoryPath),
            readPool.simulators(config.baseRepositoryPath).takeIf { appleSimulators.isSupported() },
            readPool.gradleDaemons(worktrees),
        )
        val waiting = usages.associate { usage ->
            usage.kind to if (usage.kind == ResourceKind.GRADLE_DAEMON) emptyList() else acquireResource.waiting(usage.kind)
        }
        if (command.json) {
            printJson(poolJson(usages) { waiting[it].orEmpty() })
            return EXIT_OK
        }
        usages.forEach { usage ->
            val limit = usage.effectiveLimit?.toString() ?: "no limit"
            out.line("${usage.kind.poolLabel} ${usage.inUse}/$limit${if (usage.isOverLimit) "  over the limit" else ""}")
            usage.holders.forEach { holder -> out.line("  ${holderLine(holder)}") }
            waiting[usage.kind].orEmpty().forEach { ticket ->
                out.line("  waiting: ${ticket.displayName} since ${Instant.ofEpochMilli(ticket.since).toLocalClockTime()}")
            }
        }
        return EXIT_OK
    }

    private suspend fun poolTrim(command: CliCommand.Pool, worktrees: List<Worktree>): Int {
        val trim = trimGradleDaemons(worktrees).getOrThrow()
        if (command.json) {
            printJson(poolTrimJson(trim))
            return EXIT_OK
        }
        printStopReport(DaemonStopReport(stopped = trim.stopped, kept = trim.kept), saying(false))
        val limit = trim.limit?.toString() ?: "no limit"
        out.line("gradle daemons ${trim.remaining}/$limit")
        return EXIT_OK
    }

    private fun holderLine(holder: PoolHolder): String {
        val state = holder.state.name.lowercase()
        return if (holder.kind == ResourceKind.GRADLE_DAEMON) {
            "pid ${holder.reference} ${holder.displayName.ifEmpty { "-" }} $state"
        } else {
            "${holder.displayName} $state"
        }
    }

    private val ResourceKind.poolLabel: String
        get() = when (this) {
            ResourceKind.EMULATOR -> "emulators"
            ResourceKind.IOS_SIMULATOR -> "simulators"
            ResourceKind.GRADLE_DAEMON -> "gradle daemons"
        }

    private suspend fun daemonsBeforeCleanup(worktrees: List<Worktree>): List<GradleDaemon> =
        ListGradleDaemonsUseCase(daemonSource, fileManager)(worktrees)
            .onFailure { err.line("shogun: couldn't list Gradle daemons: ${it.message}") }
            .getOrDefault(emptyList())

    private suspend fun stopDaemonsAfterCleanup(pids: List<Long>, say: (String) -> Unit): List<Long> =
        StopGradleDaemonsUseCase(daemonSource, executor)(pids)
            .onSuccess { report ->
                printStopReport(report, say)
                report.failures.forEach { err.line("shogun: couldn't stop Gradle daemon ${it.pid}: ${it.detail}") }
            }
            .onFailure { err.line("shogun: couldn't stop Gradle daemons: ${it.message}") }
            .getOrNull()?.stopped?.map { it.pid }.orEmpty()

    private suspend fun mavenDaemonsBeforeCleanup(config: ProjectConfig, worktrees: List<Worktree>): List<MavenDaemon> =
        ListMavenDaemonsUseCase(mavenDaemonSource, fileManager)(worktrees, worktreesRootOf(config, worktrees))
            .onFailure { err.line("shogun: couldn't list Maven daemons: ${it.message}") }
            .getOrDefault(emptyList())

    private suspend fun stopMavenDaemonsAfterCleanup(pids: List<Long>, say: (String) -> Unit): List<Long> =
        StopMavenDaemonsUseCase(mavenDaemonSource, executor)(pids)
            .onSuccess { report ->
                printMavenStopReport(report, say)
                report.failures.forEach { err.line("shogun: couldn't stop Maven daemon ${it.pid}: ${it.detail}") }
            }
            .onFailure { err.line("shogun: couldn't stop Maven daemons: ${it.message}") }
            .getOrNull()?.stopped?.map { it.pid }.orEmpty()

    private suspend fun env(command: CliCommand.Env): Int {
        val target = absolute(command.path ?: workingDirectory)
        val (repository, worktree) = worktreeContaining(target)
        val environment = resolveEnvironment(repository, worktree.path).getOrThrow()
        out.append(formatEnvironment(environment, command.format).takeIf { !environment.isEmpty }.orEmpty())
        if (environment.awaitingApproval.isNotEmpty()) {
            err.line(
                "shogun: environment variables awaiting approval on this machine (${environment.awaitingApproval.joinToString(", ")}); " +
                    "run 'shogun trust' in $repository to review them",
            )
        }
        environment.unresolved.forEach { err.line("shogun: shared variable ${it.name} couldn't be resolved: ${unresolvedReason(it.reason)}") }
        return EXIT_OK
    }

    private fun unresolvedReason(reason: UnresolvedReason): String = when (reason) {
        is UnresolvedReason.RepositoryNotApproved ->
            "the environment of '${reason.alias}' isn't approved on this machine; run 'shogun trust' in ${reason.repositoryPath} to review it"
        is UnresolvedReason.PortNotDeclared -> "'${reason.alias}' doesn't declare the port '${reason.key}' (\${port:${reason.key}} in its .shogunai.json)"
        is UnresolvedReason.PortNotReserved -> "'${reason.alias}' hasn't reserved the port '${reason.key}' yet"
        is UnresolvedReason.WorktreeUnreadable -> "the worktree of '${reason.alias}' can't be read: ${reason.detail}"
        is UnresolvedReason.InvalidDeclaration -> "${reason.declarationPath}: ${reason.detail}"
    }

    private suspend fun hookSessionStart(): Int {
        val input = hookInput("session-start") ?: return EXIT_USAGE
        val directory = input.string("cwd") ?: workingDirectory
        val multirepo = runCatching { resolveMultirepo(directory).getOrThrow() }.getOrNull()
        if (multirepo?.atFolder == true) {
            GroupContext(fileManager).multirepoLines(multirepo.multirepo, runnerInUse()).forEach { out.line(it) }
            if (multirepo.repositoryPath == null) return EXIT_OK
        }
        if (ResolveRepositoryUseCase(executor)(directory).isFailure) return EXIT_OK
        val (repository, worktree) = worktreeContaining(directory)
        if (!isRegisteredProject(repository)) {
            WorkflowRules.sessionStartLines(unregisteredWorkflowContext(worktree)).forEach { out.line(it) }
            return EXIT_OK
        }
        val environment = resolveEnvironment(repository, worktree.path).getOrThrow()
        val context = workflowContext(repository, worktree)
        if (!environment.isEmpty) {
            environmentVariable(CLAUDE_ENV_FILE)?.takeIf { it.isNotBlank() }?.let { path ->
                appendToEnvironmentFile(path, formatEnvironment(environment, EnvironmentFormat.SH))
            }
            val listed = environment.variables.joinToString(", ") { (name, _) -> name }
            val compose = if (context.composeProvider) ". $COMPOSE_SESSION_HINT" else ""
            out.line("ShogunAi environment of worktree ${folderName(worktree.path)}: $listed$compose")
        }
        if (environment.awaitingApproval.isNotEmpty()) {
            out.line(
                "Environment variables awaiting the user's approval on this machine: ${environment.awaitingApproval.joinToString(", ")}. " +
                    "Ask the user to review them with 'shogun trust'; don't approve them yourself.",
            )
        }
        environment.unresolved.forEach { out.line("Shared variable ${it.name} of the multirepo isn't available: ${unresolvedReason(it.reason)}.") }
        WorkflowRules.sessionStartLines(context).forEach { out.line(it) }
        multirepo?.takeIf { !it.atFolder && it.alias != null }?.let { found ->
            runCatching { groupContextLines(found, worktree.path) }.getOrDefault(emptyList()).forEach { out.line(it) }
        }
        return EXIT_OK
    }

    private fun isRegisteredProject(repository: String): Boolean =
        projectRepository.list().any { sameDirectory(it.config.baseRepositoryPath, repository) }

    private suspend fun unregisteredWorkflowContext(worktree: Worktree): WorkflowContext {
        val runner = runnerInUse()
        return WorkflowContext(
            inMainCheckout = worktree.isMain,
            runnerAvailable = runnerAvailable(runner),
            emulatorProvider = false,
            simulatorProvider = false,
            desktopAppProvider = declaresHeadlessApp(worktree.path),
            runner = runner,
        )
    }

    private suspend fun workflowContext(repository: String, worktree: Worktree): WorkflowContext {
        val providers = provisioningSource.read(repository).providers
        val runner = runnerInUse()
        return WorkflowContext(
            inMainCheckout = worktree.isMain,
            runnerAvailable = runnerAvailable(runner),
            emulatorProvider = providers.emulator != null,
            simulatorProvider = providers.iosSimulator != null && appleSimulators.isSupported(),
            composeProvider = providers.dockerCompose != null,
            desktopAppProvider = declaresHeadlessApp(worktree.path),
            runner = runner,
            flutterProvider = providers.flutter != null,
            flutterUsesFvm = usesFvm(fileManager, worktree.path),
        )
    }

    private fun declaresHeadlessApp(worktreePath: String): Boolean =
        appWindows.isSupported && runCatching { provisioningSource.read(worktreePath).providers.desktopApp }.getOrNull() != null

    private suspend fun runnerAvailable(runner: RunnerId): Boolean = when (runner) {
        RunnerId.HERDR -> herdrAvailable()
        RunnerId.ORCA -> orca.isInstalled()
    }

    private suspend fun herdrAvailable(): Boolean = try {
        withTimeout(HERDR_PROBE_MILLIS) { herdr.listAgents() }
        true
    } catch (error: TimeoutCancellationException) {
        false
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        false
    }

    private suspend fun guide(command: CliCommand.Guide): Int {
        val topic = command.topic?.let(WorkflowTopic::parse)
        when {
            command.migrate -> migrateText(command.file?.let(::absolute)).forEach { out.line(it) }
            command.plain -> plainGuideText(WorkflowRules.forRunner(runnerInUse()).filter { topic == null || it.topic == topic }).forEach { out.line(it) }
            else -> {
                if (ResolveRepositoryUseCase(executor)(workingDirectory).isFailure) {
                    err.line("shogun: No Git repository found here, so the rules that apply can't be chosen.")
                    err.line("Run 'shogun guide --plain' to print every rule.")
                    return EXIT_FAILURE
                }
                val (repository, worktree) = worktreeContaining(workingDirectory)
                guideText(WorkflowRules.rulesFor(workflowContext(repository, worktree), topic)).forEach { out.line(it) }
            }
        }
        return EXIT_OK
    }

    private val instructionsBlock = AgentInstructionsBlock(fileManager)

    private suspend fun guideBlock(command: CliCommand.GuideBlock): Int {
        if (command.action == GuideAction.STATUS) return guideStatus(command)
        val (_, path) = guideTarget(command)
        val install = command.action == GuideAction.INSTALL
        val current = instructionsBlock.status(path)
        val warnings = guideDuplicateWarnings(listOf(current)).takeIf { install }.orEmpty()
        val upToDate = if (install) current.state == GuideBlockState.INSTALLED else !current.hasBlock
        if (upToDate) {
            if (command.json) printJson(guideResultJson(current, applied = false, warnings = warnings)) else {
                out.line(if (install) "The ShogunAi guide block is already installed in $path" else "There is no ShogunAi guide block in $path")
                warnings.forEach { err.line("shogun: warning: $it") }
            }
            return EXIT_OK
        }
        if (!command.json) {
            out.line(if (install) "Will write this block to $path:" else "Will remove the ShogunAi guide block from $path")
            if (install) out.line(AgentInstructionsBlock.current())
        }
        val confirmed = command.yes || (!command.json && confirm(if (install) "Write it? [y/N] " else "Remove it? [y/N] "))
        val result = if (!confirmed) current else if (install) instructionsBlock.install(path) else instructionsBlock.uninstall(path)
        when {
            command.json -> printJson(guideResultJson(result, applied = confirmed, warnings = warnings))
            confirmed -> {
                out.line(if (install) "Installed in $path" else "Removed from $path")
                warnings.forEach { err.line("shogun: warning: $it") }
            }
            else -> out.line("Nothing written.")
        }
        return EXIT_OK
    }

    private suspend fun guideTarget(command: CliCommand.GuideBlock): Pair<String, String> = when {
        command.agent != null -> command.agent to guideAgentPath(command.agent)
        command.project -> "project" to projectInstructionsPath()
        else -> "file" to absolute(command.file.orEmpty())
    }

    private fun guideAgentPath(agent: String): String = when (agent) {
        "codex" -> (environmentVariable(CODEX_HOME)?.takeIf { it.isNotBlank() } ?: "$userHome/.codex").trimEnd('/') + "/AGENTS.md"
        else -> "$userHome/.gemini/GEMINI.md"
    }

    private suspend fun projectInstructionsPath(): String =
        ResolveRepositoryUseCase(executor)(workingDirectory).getOrThrow().trimEnd('/') + "/AGENTS.md"

    private fun guideDuplicateWarnings(statuses: List<GuideBlockStatus>): List<String> =
        statuses.filter { it.duplicatesOutside }.map {
            "${it.path} mentions 'shogun new', 'shogun open' or 'shogun clean' outside the block, so it may repeat the guide; " +
                "run 'shogun guide --migrate --file ${it.path}' to review it"
        }

    private suspend fun guideStatus(command: CliCommand.GuideBlock): Int {
        val targets = buildList {
            GUIDE_AGENTS.forEach { add(it to guideAgentPath(it)) }
            ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()?.let { add("project" to it.trimEnd('/') + "/AGENTS.md") }
            command.file?.let { add("file" to absolute(it)) }
        }
        val statuses = targets.map { (label, path) -> label to instructionsBlock.status(path) }
        val warnings = guideDuplicateWarnings(statuses.map { it.second }) + hookAndBlockWarnings(statuses)
        if (command.json) {
            printJson(guideStatusJson(statuses, warnings))
            return EXIT_OK
        }
        statuses.forEach { (label, status) -> out.line("$label: ${status.state.name.lowercase()}  ${status.path}") }
        warnings.forEach { err.line("shogun: warning: $it") }
        return EXIT_OK
    }

    private suspend fun hookAndBlockWarnings(statuses: List<Pair<String, GuideBlockStatus>>): List<String> {
        val readByClaude = statuses.filter { (label, status) -> label in setOf("project", "file") && status.hasBlock }
        if (readByClaude.isEmpty() || !sessionStartHookInstalled()) return emptyList()
        return readByClaude.map { (_, status) ->
            "Claude Code gets the rules twice: its SessionStart hook is installed and ${status.path} also has the block"
        }
    }

    private suspend fun sessionStartHookInstalled(): Boolean {
        val paths = listOfNotNull(
            userHooksSettingsPath(),
            ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()?.let(ClaudeCodeHooks::projectSettingsPath),
        )
        return paths.any { claudeHooks.status(it).getOrNull()?.state == HooksInstallState.INSTALLED }
    }

    private suspend fun worktreeContaining(directory: String): Pair<String, Worktree> {
        val (config, worktree) = configAndWorktreeContaining(directory)
        return config.baseRepositoryPath to worktree
    }

    private suspend fun configAndWorktreeContaining(directory: String): Pair<ProjectConfig, Worktree> {
        val config = configFor(directory)
        val canonicalDirectory = runCatching { canonicalPath(directory) }.getOrDefault(directory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val canonical = worktrees.associateBy { runCatching { canonicalPath(it.path) }.getOrDefault(it.path) }
        val owner = owningWorktree(canonicalDirectory, canonical.map { (path, worktree) -> worktree.copy(path = path) })
            ?: throw WorktreeError.NotAWorktree(directory)
        return config to canonical.getValue(owner.path)
    }

    private suspend fun gradle(command: CliCommand.Gradle): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val worktrees = repository?.let { ListWorktreesUseCase(configFor(workingDirectory), executor)().getOrNull() }
        val daemons = ListGradleDaemonsUseCase(daemonSource, fileManager)(worktrees).getOrThrow()
        if (command.json) {
            printJson(gradleJson(daemons, attributed = worktrees != null))
            return EXIT_OK
        }
        if (daemons.isEmpty()) {
            out.line("No Gradle or Kotlin daemons running.")
        } else {
            daemons.forEach { out.line(daemonLine(it, attributed = worktrees != null)) }
            out.line("total  memory=${formatMemory(totalResidentMemory(daemons))}  daemons=${daemons.size}")
        }
        if (repository != null && declaresGradle(repository) &&
            !CheckGradleBuildCacheUseCase(fileManager, gradleUserHome)(repository)
        ) {
            out.line(BUILD_CACHE_NOTICE)
        }
        return EXIT_OK
    }

    private suspend fun project(command: CliCommand.Project): Int {
        val resolveRepository = ResolveRepositoryUseCase(executor)
        val directory = command.path?.let(::absolute) ?: workingDirectory
        when (command.action) {
            ProjectAction.ADD -> {
                val registration = RegisterProjectUseCase(projectRepository, resolveRepository, workspaceOf = resolveMultirepo.known()::withWorkspaceAt)(directory).getOrThrow()
                val project = registration.project
                val added = registration is ProjectRegistration.Added
                if (command.json) {
                    printJson(projectJson(if (added) "added" else "alreadyRegistered", project))
                } else if (added) {
                    out.line("Added project ${project.name} (${project.config.baseRepositoryPath})")
                } else {
                    out.line("Project ${project.name} is already registered (${project.config.baseRepositoryPath})")
                }
            }
            ProjectAction.LIST -> {
                val projects = projectRepository.list()
                if (command.json) {
                    printJson(projectListJson(projects))
                } else if (projects.isEmpty()) {
                    out.line("No projects registered.")
                } else {
                    projects.forEach { out.line("${it.name}  ${it.config.baseRepositoryPath}") }
                }
            }
            ProjectAction.REMOVE -> {
                val project = UnregisterProjectUseCase(projectRepository, resolveRepository)(directory).getOrThrow()
                if (command.json) printJson(projectJson("removed", project)) else out.line("Removed project ${project.name} (${project.config.baseRepositoryPath})")
            }
        }
        return EXIT_OK
    }

    private suspend fun app(command: CliCommand.App): Int {
        val resolveRepository = ResolveRepositoryUseCase(executor)
        val repository = if (command.path != null) {
            resolveRepository(absolute(command.path)).getOrThrow()
        } else {
            resolveRepository(workingDirectory).getOrNull()
        }
        if (repository == null && command.settings) {
            err.line("shogun: --settings needs a repository; run it inside one or pass a path")
            return EXIT_USAGE
        }
        val running = appLock.isHeldByAnyInstance()
        if (!running && !appLauncher.isAvailable()) throw WorktreeError.AppNotAvailable
        val createdAt = now().toEpochMilli()
        val multirepoFolder = repository?.let { resolveMultirepo.known().withWorkspaceAt(it) }
        val request = if (repository == null || multirepoFolder != null) {
            AppRequest.openApp(createdAt)
        } else {
            val registration = RegisterProjectUseCase(projectRepository, resolveRepository)(repository).getOrThrow()
            if (registration is ProjectRegistration.Added) out.line("Added project ${registration.project.name} (${repository})")
            AppRequest.openProject(createdAt, registration.project.id, command.settings)
        }
        appRequests.send(request)
        if (!running) appLauncher.launch()
        out.line(
            when {
                repository == null -> "Opening ShogunAi"
                multirepoFolder != null -> "Opening ShogunAi; ${multirepoFolder.name} is a multirepo, its workspace repository belongs to it"
                command.settings -> "Opening the settings of ${repository.substringAfterLast('/')} in ShogunAi"
                else -> "Opening ${repository.substringAfterLast('/')} in ShogunAi"
            },
        )
        return EXIT_OK
    }

    private suspend fun stacks(command: CliCommand.Stacks): Int {
        val config = configFor(workingDirectory)
        val repository = config.baseRepositoryPath
        val detected = DetectRepositoryStacksUseCase(fileManager)(repository)
        val declared = provisioningSource.read(repository).providers
        val report = suggestProviders(detected, declared, isMac = derivedDataSource.isSupported())
        val secrets = DetectMobileSecretsUseCase(executor, fileManager, provisioningSource)(config)
        if (command.json) {
            printJson(stacksJson(detected, report, secrets))
            return EXIT_OK
        }
        if (detected.isEmpty() && secrets.isEmpty) {
            out.line("No known stack detected.")
            return EXIT_OK
        }
        detected.forEach { stack ->
            val providers = (report.suggestions + report.declared).filter { it.stack == stack.stack }.joinToString("  ") {
                "${it.provider.id}=${if (it in report.declared) "declared" else "suggested"}"
            }
            out.line(listOf(stackLabel(stack.stack), "evidence=${stack.evidence.joinToString(",")}", providers).filter { it.isNotEmpty() }.joinToString("  "))
        }
        secrets.secrets.forEach { out.line("Secret  ${it.path}  kind=${it.kind.id}  link=${it.linkPattern}") }
        secrets.warnings.forEach {
            out.line("Warning  ${it.file}: ${it.key}=${it.value} points outside the repository and won't resolve from a worktree")
        }
        return EXIT_OK
    }

    private fun stackLabel(stack: app.luxion.shogunai.domain.model.Stack): String = when (stack) {
        app.luxion.shogunai.domain.model.Stack.GRADLE -> "Gradle"
        app.luxion.shogunai.domain.model.Stack.ANDROID -> "Android"
        app.luxion.shogunai.domain.model.Stack.XCODE -> "Xcode"
        app.luxion.shogunai.domain.model.Stack.MAVEN -> "Maven"
        app.luxion.shogunai.domain.model.Stack.DOCKER_COMPOSE -> "Docker Compose"
        app.luxion.shogunai.domain.model.Stack.FLUTTER -> "Flutter"
    }

    private suspend fun maven(command: CliCommand.Maven): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val config = repository?.let { configFor(workingDirectory) }
        val worktrees = config?.let { ListWorktreesUseCase(it, executor)().getOrNull() }
        val daemons = ListMavenDaemonsUseCase(mavenDaemonSource, fileManager)(worktrees, worktreesRootOf(config, worktrees)).getOrThrow()
        if (command.json) {
            printJson(mavenJson(daemons, attributed = worktrees != null))
            return EXIT_OK
        }
        if (daemons.isEmpty()) {
            out.line("No Maven daemons running.")
            return EXIT_OK
        }
        daemons.forEach { out.line(mavenDaemonLine(it, attributed = worktrees != null)) }
        out.line("total  memory=${formatMemory(totalMavenResidentMemory(daemons))}  daemons=${daemons.size}")
        return EXIT_OK
    }

    private fun mavenDaemonLine(daemon: MavenDaemon, attributed: Boolean): String {
        val details = buildList {
            add("memory=${formatMemory(daemon.process.residentMemoryBytes)}")
            add(stateDetail(daemon.process.state))
            if (attributed) {
                when (val owner = daemon.owner) {
                    is DaemonOwner.Worktree -> add("worktree=${owner.path}")
                    DaemonOwner.RemovedWorktree -> add("worktree=removed")
                    DaemonOwner.OtherProject -> add("worktree=other-project")
                    null -> add("worktree=unknown")
                }
            }
        }
        return (listOf(daemon.pid.toString()) + details).joinToString("  ")
    }

    private fun stateDetail(state: DaemonState): String = when (state) {
        DaemonState.Busy -> "state=busy"
        is DaemonState.Idle -> "state=idle  idle-for=${formatDuration(Duration.between(state.since, now()))}"
        DaemonState.Unknown, DaemonState.Untracked -> "state=unknown"
    }

    private suspend fun mavenStop(command: CliCommand.MavenStop): Int {
        val report = StopMavenDaemonsUseCase(mavenDaemonSource, executor)(command.pids.ifEmpty { null }).getOrThrow()
        if (command.json) {
            printJson(mavenStopJson(report))
        } else {
            if (command.pids.isEmpty() && report.stopped.isEmpty() && report.kept.isEmpty() && report.failures.isEmpty()) {
                out.line("No Maven daemons running.")
            }
            printMavenStopReport(report, saying(false))
        }
        report.notDaemons.forEach { err.line("shogun: $it isn't a Maven daemon") }
        report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
        return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun printMavenStopReport(report: MavenDaemonStopReport, say: (String) -> Unit) {
        report.stopped.forEach { say("stopped ${it.pid}  Maven") }
        report.kept.forEach { say("kept ${it.pid}  Maven  (${it.reason.label})") }
    }

    private fun worktreesRootOf(config: ProjectConfig?, worktrees: List<Worktree>?): String? =
        config?.worktreesRoot?.takeIf { it.isNotBlank() }
            ?: worktrees?.firstOrNull { !it.isMain }?.path?.replace('\\', '/')?.trimEnd('/')?.substringBeforeLast('/', "")?.ifEmpty { null }

    private fun declaresCompose(repository: String): Boolean =
        runCatching { provisioningSource.read(repository).providers.dockerCompose != null }.getOrDefault(false)

    private suspend fun composeProjectsOf(config: ProjectConfig, worktrees: List<Worktree>): List<String> {
        val names = worktrees.map { worktreeComposeProjectName(config.baseRepositoryPath, it.path) }.toSet()
        return runCatching { composeSource.list() }.getOrDefault(emptyList()).map { it.name }.filter { it in names }
    }

    private suspend fun requireComposeProvider(): ProjectConfig? {
        val config = configFor(workingDirectory)
        if (declaresCompose(config.baseRepositoryPath)) return config
        err.line("shogun: this repository doesn't declare the docker-compose provider in .shogunai.json")
        return null
    }

    private suspend fun compose(command: CliCommand.Compose): Int {
        val config = requireComposeProvider() ?: return EXIT_FAILURE
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val projects = ListComposeProjectsUseCase(composeSource, composeRegistry)(config.baseRepositoryPath, worktrees, worktreesRootOf(config, worktrees)).getOrThrow()
        val leftovers = ListComposeLeftoversUseCase(composeSource, composeRegistry)(config.baseRepositoryPath, worktrees).getOrThrow()
        if (command.json) {
            printJson(composeJson(projects, leftovers))
            return EXIT_OK
        }
        if (projects.isEmpty()) out.line("No Compose projects for the worktrees.")
        projects.forEach {
            val worktree = when (val owner = it.owner) {
                is ComposeOwner.Worktree -> owner.path
                ComposeOwner.RemovedWorktree -> "removed"
            }
            out.line("${it.project.name}  status=${it.project.status.ifEmpty { "unknown" }}  worktree=$worktree")
        }
        leftovers.forEach { out.line("leftover ${it.kind.id} ${it.name}  project=${it.project}") }
        if (leftovers.isNotEmpty()) {
            val prune = if (leftovers.any { it.kind == ComposeResourceKind.VOLUME }) "shogun compose prune --volumes" else "shogun compose prune"
            out.line("Run '$prune' to see what it removes, and add --yes to remove it.")
        }
        return EXIT_OK
    }

    private suspend fun composeRun(command: CliCommand.ComposeRun): Int {
        val config = requireComposeProvider() ?: return EXIT_FAILURE
        val worktree = if (command.target != null) {
            findWorktreeTarget(command.target, ListWorktreesUseCase(config, executor)().getOrThrow(), config, ::canonical)
        } else {
            configAndWorktreeContaining(workingDirectory).second
        }
        val action = if (command.onlyBuild) ComposeAction.Build(command.extraArguments) else ComposeAction.Up(command.build, command.extraArguments)
        val verb = if (command.onlyBuild) "built" else "started"
        val result = RunComposeProjectUseCase(composeSource, resolveEnvironment, composeRegistry)(config.baseRepositoryPath, worktree.path, action) { line ->
            if (command.json || line.channel == OutputChannel.ERROR) err.line(line.text) else out.line(line.text)
        }
        val project = result.getOrNull()
        val error = result.exceptionOrNull()
        if (command.json) printJson(composeRunJson(worktree.path, project, error?.message))
        if (error != null) {
            err.line("shogun: ${error.message}")
            return EXIT_FAILURE
        }
        if (!command.json) out.line("$verb Compose project $project for ${worktree.path}")
        return EXIT_OK
    }

    private suspend fun composePrune(command: CliCommand.ComposePrune): Int {
        val config = requireComposeProvider() ?: return EXIT_FAILURE
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val leftovers = ListComposeLeftoversUseCase(composeSource, composeRegistry)(config.baseRepositoryPath, worktrees).getOrThrow()
        if (!command.confirmed) {
            val (volumes, others) = leftovers.partition { it.kind == ComposeResourceKind.VOLUME }
            val plan = ComposeLeftoversReport(
                removed = others + if (command.volumes) volumes else emptyList(),
                kept = if (command.volumes) emptyList() else volumes,
            )
            if (command.json) {
                printJson(composeLeftoversJson(plan, dryRun = true))
                return EXIT_OK
            }
            if (leftovers.isEmpty()) {
                out.line("Nothing to remove.")
                return EXIT_OK
            }
            plan.removed.forEach { out.line("would remove ${it.kind.id} ${it.name}  project=${it.project}") }
            plan.kept.forEach { out.line("would keep ${it.kind.id} ${it.name}  project=${it.project} (add --volumes to remove it)") }
            out.line(if (plan.removed.isEmpty()) "Run 'shogun compose prune --volumes --yes' to remove the volumes." else "Run again with --yes to remove them.")
            return EXIT_OK
        }
        val report = RemoveComposeLeftoversUseCase(composeSource, composeRegistry)(leftovers, command.volumes, config.baseRepositoryPath)
        if (command.json) {
            printJson(composeLeftoversJson(report, dryRun = false))
        } else if (leftovers.isEmpty()) {
            out.line("Nothing to remove.")
        } else {
            printLeftoversReport(report, saying(false))
        }
        report.failures.takeIf { command.json }?.forEach { err.line("shogun: couldn't remove ${it.resource.kind.id} ${it.resource.name}: ${it.detail}") }
        return if (report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun printLeftoversReport(report: ComposeLeftoversReport, say: (String) -> Unit) {
        report.removed.forEach { say("removed ${it.kind.id} ${it.name}") }
        report.kept.forEach { say("kept ${it.kind.id} ${it.name}") }
        report.failures.forEach { err.line("shogun: couldn't remove ${it.resource.kind.id} ${it.resource.name}: ${it.detail}") }
    }

    private suspend fun composeDown(command: CliCommand.ComposeDown): Int {
        val config = requireComposeProvider() ?: return EXIT_FAILURE
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val names = if (command.target != null) {
            val worktree = findWorktreeTarget(command.target, worktrees, config, ::canonical)
            listOf(worktreeComposeProjectName(config.baseRepositoryPath, worktree.path))
        } else {
            ListComposeProjectsUseCase(composeSource, composeRegistry)(config.baseRepositoryPath, worktrees, worktreesRootOf(config, worktrees))
                .getOrThrow()
                .filter { it.owner == ComposeOwner.RemovedWorktree }
                .map { it.project.name }
        }
        if (names.isEmpty()) {
            if (command.json) printJson(composeDownJson(app.luxion.shogunai.domain.model.ComposeDownReport())) else out.line("No Compose projects to take down.")
            return EXIT_OK
        }
        val report = TakeDownComposeProjectsUseCase(composeSource)(names, command.volumes)
        if (command.json) {
            printJson(composeDownJson(report))
        } else {
            report.takenDown.forEach { out.line("took down $it${if (command.volumes) " (volumes removed)" else " (volumes kept)"}") }
        }
        report.failures.forEach { err.line("shogun: couldn't take down ${it.name}: ${it.detail}") }
        return if (report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun gradleStop(command: CliCommand.GradleStop): Int {
        val report = StopGradleDaemonsUseCase(daemonSource, executor)(command.pids.ifEmpty { null }).getOrThrow()
        if (command.json) {
            printJson(gradleStopJson(report))
            report.notDaemons.forEach { err.line("shogun: $it isn't a Gradle or Kotlin daemon") }
            report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
            return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
        }
        if (command.pids.isEmpty() && report.stopped.isEmpty() && report.kept.isEmpty() && report.failures.isEmpty()) {
            out.line("No Gradle or Kotlin daemons running.")
        }
        printStopReport(report, saying(false))
        report.notDaemons.forEach { err.line("shogun: $it isn't a Gradle or Kotlin daemon") }
        report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
        return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun printStopReport(report: DaemonStopReport, say: (String) -> Unit) {
        report.stopped.forEach { say("stopped ${it.pid}  ${kindLabel(it)}") }
        report.kept.forEach { say("kept ${it.pid}  ${it.kind.label}  (${it.reason.label})") }
    }

    private suspend fun xcode(command: CliCommand.Xcode): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val provider = repository?.let { xcodeProvider(it) }
        if (repository == null || provider == null || !derivedDataSource.isSupported()) {
            if (command.json) printJson(xcodeJson(active = false, listing = null)) else out.line("The Xcode provider isn't active.")
            return EXIT_OK
        }
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val listing = listDerivedData(worktrees, includeCompilationCache = provider.compilationCache, measure = DerivedDataMeasure.ALL).getOrThrow()
        if (command.json) {
            printJson(xcodeJson(active = true, listing = listing))
            return EXIT_OK
        }
        listing.ofWorktrees().forEach { item ->
            val worktree = (item.owner as DerivedDataOwner.Worktree).path
            val packages = item.folder.packagesBytes?.takeIf { it > 0 }?.let { "  (packages ${formatMemory(it)})" }.orEmpty()
            out.line("$worktree  ${formatMemory(item.folder.sizeBytes ?: 0L)}$packages")
        }
        val orphans = listing.orphans
        out.line("orphans: ${orphans.size} (${formatMemory(orphans.sumOf { it.sizeBytes ?: 0L })})")
        val unavailableCount = listing.folders.count { it.owner == DerivedDataOwner.UnavailableVolume }
        if (unavailableCount > 0) {
            out.line("folders on unavailable volumes: $unavailableCount")
        }
        if (provider.compilationCache) {
            out.line("compilation cache: ${formatMemory(listing.compilationCacheBytes ?: 0L)}")
        }
        listing.packageCaches.swiftPmBytes?.let { out.line("SwiftPM cache: ${formatMemory(it)}") }
        listing.packageCaches.cocoaPodsBytes?.let { out.line("CocoaPods cache: ${formatMemory(it)}") }
        return EXIT_OK
    }

    private suspend fun xcodeClean(command: CliCommand.XcodeClean): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val provider = repository?.let { xcodeProvider(it) }
        if (repository == null || provider == null || !derivedDataSource.isSupported()) {
            if (command.json) printJson(xcodeCleanJson(active = false, deletion = null)) else out.line("The Xcode provider isn't active.")
            return EXIT_OK
        }
        val deletion = deleteOrphanedDerivedData().getOrThrow()
        if (command.json) {
            printJson(xcodeCleanJson(active = true, deletion = deletion))
            deletion.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
            return if (deletion.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
        }
        if (deletion.deleted.isEmpty() && deletion.failures.isEmpty()) {
            out.line("Nothing to delete.")
            return EXIT_OK
        }
        deletion.deleted.forEach { out.line("deleted ${it.path}") }
        if (deletion.deleted.isNotEmpty()) {
            out.line("freed ${formatMemory(deletion.bytesFreed)}")
        }
        deletion.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
        return if (deletion.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun xcodeTrim(command: CliCommand.XcodeTrim): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val provider = repository?.let { xcodeProvider(it) }
        if (repository == null || provider == null || !derivedDataSource.isSupported()) {
            if (command.json) printJson(xcodeTrimJson(active = false, trim = null)) else out.line("The Xcode provider isn't active.")
            return EXIT_OK
        }
        val settings = runCatching { resourceLimits.load() }.getOrDefault(ResourceSettings())
        if (settings.quotaMode == DerivedDataQuotaMode.NONE) return noDerivedDataQuota(command, DerivedDataQuota(DerivedDataQuotaMode.NONE, null))
        val configs = (listOf(configFor(workingDirectory)) + projectRepository.list().map { it.config })
            .distinctBy { it.baseRepositoryPath.trimEnd('/') }
        val searches = configs.mapNotNull { config ->
            ListWorktreesUseCase(config, executor)().getOrNull()?.filterNot { it.isPrunable }?.let { AgentSearch(config.agents.map { agent -> agent.agentCommand }, it) }
        }
        val worktrees = searches.flatMap { it.worktrees }.distinctBy { it.path.trimEnd('/') }
        val listing = listDerivedData(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()
        val quota = ResolveDerivedDataQuotaUseCase(derivedDataSource)(settings, listing.folders.sumOf { it.folder.sizeBytes ?: 0L })
        val quotaBytes = quota.bytes ?: return noDerivedDataQuota(command, quota)
        val agents = DetectRunningAgentsUseCase(executor, herdr, sessionStore, orca = orcaReader)(searches).getOrNull().orEmpty()
        val active = agents.filterValues { running -> running.any { it.state == AgentState.WORKING || it.state == AgentState.BLOCKED } }.keys
        val trim = TrimDerivedDataUseCase(derivedDataSource, listDerivedData, now)(quotaBytes, worktrees, active, command.dryRun, listing, quota.mode).getOrThrow()
        if (command.json) {
            printJson(xcodeTrimJson(active = true, trim = trim))
            trim.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
            return if (trim.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
        }
        val releasedLabel = if (trim.dryRun) "would release" else "released"
        trim.released.forEach { out.line("$releasedLabel ${it.folder.path}  ${formatMemory(it.folder.sizeBytes ?: 0L)}") }
        if (trim.released.isNotEmpty()) {
            out.line("${if (trim.dryRun) "would free" else "freed"} ${formatMemory(trim.bytesFreed)}")
        }
        out.line("usage ${formatMemory(trim.usageAfter)} of ${formatMemory(quotaBytes)}" + if (quota.mode == DerivedDataQuotaMode.AUTOMATIC) " (automatic)" else "")
        if (trim.isExceeded) {
            val inUse = trim.inUse.joinToString(", ") { (it.owner as DerivedDataOwner.Worktree).path }
            out.line("quota still exceeded" + if (inUse.isNotEmpty()) "; worktrees in use: $inUse" else "")
        }
        trim.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
        return if (trim.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun noDerivedDataQuota(command: CliCommand.XcodeTrim, quota: DerivedDataQuota): Int {
        if (command.json) {
            printJson(xcodeTrimJson(active = true, trim = DerivedDataTrim(null, 0L, 0L, dryRun = command.dryRun, quotaMode = quota.mode)))
        } else if (quota.isUnknown) {
            out.line("The automatic DerivedData quota couldn't be computed: the free disk space is unknown.")
        } else {
            out.line("No DerivedData quota is set; set one in Preferences > Resources.")
        }
        return EXIT_OK
    }

    private fun xcodeProvider(repository: String): XcodeProvider? =
        runCatching { provisioningSource.read(repository).providers.xcode }.getOrNull()

    private fun declaresGradle(repository: String): Boolean =
        runCatching { provisioningSource.read(repository).providers.gradle != null }.getOrDefault(false)

    private fun daemonLine(daemon: GradleDaemon, attributed: Boolean): String {
        val process = daemon.process
        val details = buildList {
            add(kindLabel(process))
            add("memory=${formatMemory(process.residentMemoryBytes)}")
            when (val state = process.state) {
                DaemonState.Busy -> add("state=busy")
                is DaemonState.Idle -> add("state=idle  idle-for=${formatDuration(Duration.between(state.since, now()))}")
                DaemonState.Unknown -> add("state=unknown")
                DaemonState.Untracked -> Unit
            }
            if (attributed) {
                when (val owner = daemon.owner) {
                    is DaemonOwner.Worktree -> add("worktree=${owner.path}")
                    DaemonOwner.RemovedWorktree -> add("worktree=removed")
                    DaemonOwner.OtherProject -> add("worktree=other-project")
                    null -> Unit
                }
            }
        }
        return (listOf(process.pid.toString()) + details).joinToString("  ")
    }

    private fun kindLabel(process: DaemonProcess): String =
        listOfNotNull(process.kind.label, process.gradleVersion).joinToString(" ")

    private suspend fun hookWorktreeCreate(): Int {
        val input = hookInput("worktree-create") ?: return EXIT_USAGE
        val name = input.string("name") ?: return hookUsageError("worktree-create", "the input has no \"name\"")
        val directory = input.string("cwd") ?: workingDirectory
        val projectConfig = configFor(directory)
        val config = if (projectConfig.worktreesRoot.isBlank()) {
            projectConfig.copy(worktreesRoot = "${projectConfig.baseRepositoryPath.trimEnd('/')}/$CLAUDE_CODE_WORKTREES")
        } else {
            projectConfig
        }
        val target = config.worktreePathFor(normalizeTaskId(name))
        val existing = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, target) }
        val path = existing?.path ?: run {
            val created = CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust, records = provisioningRecords)(
                name,
                BranchType.FEATURE,
                resolveStartPoint(directory, null),
                resolveBaseBranch(directory, null),
            )
            when (val failure = created.exceptionOrNull()) {
                null -> created.getOrThrow().also {
                    noteSkippedLinks(it.skippedLinks, "shogun: hook worktree-create")
                    podsLines(it.pods).forEach { line -> err.line("shogun: hook worktree-create: $line") }
                    it.flutter?.let { pubGet -> err.line("shogun: hook worktree-create: ${flutterPubGetLine(pubGet)}") }
                    noteSkipped(config.baseRepositoryPath, it.skippedPostCreateCommands, it.skippedVariables, "shogun: hook worktree-create")
                }.path
                is WorktreeError.PostCreateCommandFailed -> {
                    err.line("shogun: hook worktree-create: ${failure.message}")
                    failure.worktreePath
                }
                else -> throw failure
            }
        }
        out.line(path)
        return EXIT_OK
    }

    private suspend fun hookWorktreeRemove(): Int {
        val input = hookInput("worktree-remove") ?: return EXIT_USAGE
        val path = input.string("worktree_path")
            ?: return hookUsageError("worktree-remove", "the input has no \"worktree_path\"")
        val directory = if (fileManager.exists(path)) path else input.string("cwd") ?: workingDirectory
        val config = configFor(directory)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, path) }
            ?: throw WorktreeError.NotAWorktree(path)
        val removal = RemoveWorktreeUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr), fileManager, stopHeadlessApp, releaseDesktopTurn, CloseOrcaTerminalsUseCase(orca))(
            worktree.path,
            branchToDelete = worktree.branch,
            force = true,
        ).getOrThrow()
        removal.herdrFailure?.let { err.line(herdrCloseFailure(worktree.path, it)) }
        removal.orcaFailure?.let { err.line(orcaCloseFailure(worktree.path, it)) }
        removal.headlessAppFailure?.let { err.line(headlessStopFailure(worktree.path, it)) }
        reportKeptBranch(worktree.branch, removal.branch)
        return EXIT_OK
    }

    private fun headlessStopFailure(path: String, error: WorktreeError) =
        "shogun: couldn't stop the desktop app of $path: ${error.message}"

    private suspend fun headless(command: CliCommand.Headless): Int {
        val worktree = worktreeTopLevel(absolute(command.path ?: workingDirectory))
        when (command.action) {
            HeadlessAction.START -> {
                val started = startHeadlessApp(worktree, command.visible, command.fresh).getOrThrow()
                started.missingPermission?.let {
                    err.line("shogun: the app's window is visible because macOS hasn't granted ${it.displayName} to the application that runs shogun; grant it in System Settings › ${it.settingsPane} to hide it")
                }
                if (command.json) printJson(headlessStartJson(worktree, started)) else out.line(headlessStartLine(started))
            }
            HeadlessAction.STOP -> {
                val stopped = stopHeadlessApp(worktree).getOrThrow()
                if (command.json) printJson(headlessStopJson(worktree, stopped)) else out.line(if (stopped.wasRunning) "stopped ${stopped.pid}" else "stopped")
            }
            HeadlessAction.STATUS -> {
                val status = readHeadlessApp(worktree).getOrThrow()
                if (command.json) printJson(headlessStatusJson(worktree, status)) else headlessStatusLines(status).forEach { out.line(it) }
            }
            HeadlessAction.CAPTURE -> {
                val path = captureHeadlessApp(worktree, command.output?.let(::absolute)).getOrThrow()
                if (command.json) printJson(headlessCaptureJson(worktree, path)) else out.line(path)
            }
        }
        return EXIT_OK
    }

    private suspend fun desktop(command: CliCommand.Desktop): Int {
        when (command.action) {
            DesktopAction.ACQUIRE -> {
                val worktree = worktreeTopLevel(absolute(command.path ?: workingDirectory))
                val acquisition = acquireDesktopTurn(worktree, command.wait?.let(::desktopWait)).getOrThrow()
                acquisition.waitedSeconds?.takeIf { it > 0 }?.let { err.line("waited ${Duration.ofSeconds(it).toWaitText()}") }
                if (command.json) printJson(desktopAcquisitionJson(acquisition)) else out.line(desktopAcquisitionLine(acquisition))
            }
            DesktopAction.RELEASE -> {
                val worktree = worktreeTopLevel(absolute(command.path ?: workingDirectory))
                val release = releaseDesktopTurn(worktree).getOrThrow()
                val state = readDesktopTurn().getOrThrow()
                if (command.json) printJson(desktopReleaseJson(release, state)) else out.line(desktopReleaseLine(release))
            }
            DesktopAction.STATUS -> {
                val state = readDesktopTurn().getOrThrow()
                if (command.json) printJson(desktopStateJson(state)) else desktopStateLines(state).forEach { out.line(it) }
            }
        }
        return EXIT_OK
    }

    private fun desktopWait(timeout: Duration): DesktopWait {
        var lastLine: String? = null
        return DesktopWait(timeout) { progress ->
            val line = desktopWaitProgressLine(progress)
            if (line != lastLine) err.line(line)
            lastLine = line
        }
    }

    private suspend fun worktreeTopLevel(directory: String): String {
        val result = executor.execute(listOf("git", "rev-parse", "--show-toplevel"), directory)
        val topLevel = result.stdout.trim()
        if (!result.isSuccess || topLevel.isEmpty()) throw WorktreeError.RepositoryNotFound(directory)
        return topLevel
    }

    private fun herdrCloseFailure(path: String, error: WorktreeError) =
        "shogun: couldn't close the Herdr workspace of $path: ${error.herdrDetail()}"

    private fun orcaCloseFailure(path: String, error: WorktreeError) =
        "shogun: couldn't close the Orca terminals of $path: ${error.orcaDetail()}"

    private suspend fun hookAgentState(): Int {
        val input = hookInput("agent-state") ?: return EXIT_USAGE
        val sessionId = input.string("session_id") ?: return EXIT_OK
        val event = input.string("hook_event_name") ?: return EXIT_OK
        val state = if (event == SESSION_END) null else agentStateOf(event, input.string("notification_type")) ?: return EXIT_OK
        try {
            val directory = input.string("cwd") ?: workingDirectory
            val folder = unversionedMultirepoFolder(directory)
            if (folder == null && ResolveRepositoryUseCase(executor)(directory).isFailure) return EXIT_OK
            val sessionPath = folder ?: worktreeContaining(directory).second.path
            if (state == null) {
                sessionStore.remove(sessionPath, sessionId)
            } else {
                sessionStore.write(
                    sessionPath,
                    AgentSessionRecord(sessionId, "claude", state, now().toEpochMilli(), input.string("transcript_path"), agentProcessId()),
                )
                if (folder == null && state == AgentState.WORKING) renewLeases(sessionPath)
            }
            if (folder == null && event in DESKTOP_RELEASING_EVENTS) releaseDesktopTurn.ifHeldBy(sessionPath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return EXIT_OK
        }
        return EXIT_OK
    }

    private suspend fun hookDesktopTurn(): Int {
        val input = hookInput("desktop-turn") ?: return EXIT_USAGE
        if (input.string("tool_name") != BASH_TOOL) return EXIT_OK
        val command = (input["tool_input"] as? JsonObject)?.string("command") ?: return EXIT_OK
        if (!OrcaComputerUse.drivesDesktop(command)) return EXIT_OK
        try {
            val directory = input.string("cwd") ?: workingDirectory
            if (ResolveRepositoryUseCase(executor)(directory).isFailure) return EXIT_OK
            val worktree = worktreeTopLevel(directory)
            val taken = acquireDesktopTurn(worktree).exceptionOrNull() as? WorktreeError.DesktopTurnTaken ?: return EXIT_OK
            printJson(desktopTurnDenial(taken))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return EXIT_OK
        }
        return EXIT_OK
    }

    private fun desktopTurnDenial(taken: WorktreeError.DesktopTurnTaken): JsonObject = buildJsonObject {
        put(
            "hookSpecificOutput",
            buildJsonObject {
                put("hookEventName", "PreToolUse")
                put("permissionDecision", "deny")
                put(
                    "permissionDecisionReason",
                    "${taken.summary}. Take your turn first with `shogun desktop acquire --wait`, run in the background " +
                        "since it can wait up to 30 minutes, then run this command again.",
                )
            },
        )
    }

    private suspend fun unversionedMultirepoFolder(directory: String): String? {
        if (ResolveRepositoryUseCase(executor)(directory).isSuccess) return null
        return resolveMultirepo.known().firstOrNull { sameDirectory(it.folder, directory) }?.folder
    }

    private fun agentStateOf(event: String, notificationType: String?): AgentState? = when (event) {
        "UserPromptSubmit" -> AgentState.WORKING
        "Stop" -> AgentState.DONE
        "Notification" -> when (notificationType) {
            "idle_prompt" -> AgentState.DONE
            "auth_success" -> null
            else -> AgentState.BLOCKED
        }
        else -> null
    }

    private val claudeHooks = ClaudeCodeHooks(fileManager)

    private suspend fun hooks(command: CliCommand.Hooks): Int {
        if (command.action == HooksAction.STATUS) return hooksStatus(command)
        val path = hooksSettingsPath(command.project)
        val install = command.action == HooksAction.INSTALL
        val preview = (if (install) claudeHooks.previewInstall(path) else claudeHooks.previewUninstall(path)).getOrThrow()
        if (preview.isEmpty) {
            if (command.json) printJson(hooksResultJson(path, preview, applied = false)) else out.line(hooksUpToDate(install, path))
            return EXIT_OK
        }
        if (!command.json) printHooksPreview(path, preview)
        val confirmed = command.yes || (!command.json && confirm(if (install) "Install them? [y/N] " else "Remove them? [y/N] "))
        if (confirmed) (if (install) claudeHooks.install(path) else claudeHooks.uninstall(path)).getOrThrow()
        when {
            command.json -> printJson(hooksResultJson(path, preview, applied = confirmed))
            confirmed -> out.line(if (install) "Installed in $path" else "Removed from $path")
            else -> out.line("Nothing written.")
        }
        return EXIT_OK
    }

    private fun hooksUpToDate(install: Boolean, path: String) =
        if (install) "ShogunAi's hooks are already installed in $path" else "There are no ShogunAi hooks in $path"

    private fun printHooksPreview(path: String, preview: HooksPreview) {
        out.line("Changes to $path:")
        preview.added.forEach { out.line("  + ${it.event}: ${it.command}") }
        preview.replaced.forEach { out.line("  ~ ${it.event}: ${it.from.joinToString()} -> ${it.to}") }
        preview.removed.forEach { out.line("  - ${it.event}: ${it.command}") }
    }

    private suspend fun hooksSettingsPath(project: Boolean): String =
        if (project) {
            ClaudeCodeHooks.projectSettingsPath(ResolveRepositoryUseCase(executor)(workingDirectory).getOrThrow())
        } else {
            userHooksSettingsPath()
        }

    private fun userHooksSettingsPath() =
        ClaudeCodeHooks.userSettingsPath(environmentVariable(CLAUDE_CONFIG_DIR), userHome)

    private suspend fun hooksStatus(command: CliCommand.Hooks): Int {
        val scopes = buildList {
            add("user" to userHooksSettingsPath())
            ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
                ?.let { add("project" to ClaudeCodeHooks.projectSettingsPath(it)) }
        }
        val statuses = scopes.map { (scope, path) -> scope to claudeHooks.status(path).getOrThrow() }
        if (command.json) {
            printJson(hooksStatusJson(statuses))
            return EXIT_OK
        }
        statuses.forEach { (scope, status) ->
            val detail = if (status.state == HooksInstallState.OUTDATED) " (${status.events.joinToString()})" else ""
            out.line("$scope: ${status.state.name.lowercase()}$detail  ${status.path}")
        }
        return EXIT_OK
    }

    private suspend fun hookHerdr(): Int {
        val event = environmentVariable(HERDR_PLUGIN_EVENT)?.takeIf { it.isNotBlank() }
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT isn't set; run it from Herdr's plugin")
        if (event != HERDR_WORKTREE_CREATED && event != HERDR_WORKTREE_REMOVED) return EXIT_OK
        val json = environmentVariable(HERDR_PLUGIN_EVENT_JSON)
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT_JSON isn't set")
        val envelope = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT_JSON isn't JSON")
        val data = envelope["data"] as? JsonObject
        val removed = data?.get("worktree") as? JsonObject
        val path = removed?.string("path")
            ?: return hookUsageError("herdr", "the event has no \"data.worktree.path\"")
        if (event == HERDR_WORKTREE_CREATED) return provisionWorktreeAt(path, RepositoryApproval.SKIP, ::flagOutsideWorktreesRoot)
        val repoRoot = ((data.get("workspace") as? JsonObject)?.get("worktree") as? JsonObject)?.string("repo_root")
        return deleteMergedBranchOfRemovedWorktree(path, removed.string("branch"), repoRoot)
    }

    private suspend fun hookOrca(action: OrcaHookAction): Int {
        val path = environmentVariable(ORCA_WORKTREE_PATH)?.takeIf { it.isNotBlank() }
            ?: return hookUsageError("orca", "$ORCA_WORKTREE_PATH isn't set; run it from the scripts of Orca's orca.yaml")
        val repository = repositoryOfOrcaWorktree(path)
        return when (action) {
            OrcaHookAction.SETUP -> provisionWorktreeAt(path, RepositoryApproval.SKIP, ::noteOutsideWorktreesRoot, configDirectory = repository)
            OrcaHookAction.ARCHIVE -> releaseOrcaWorktree(path, repository)
        }
    }

    private suspend fun repositoryOfOrcaWorktree(path: String): String {
        if (ResolveRepositoryUseCase(executor)(path).isSuccess) return path
        return environmentVariable(ORCA_ROOT_PATH)?.takeIf { it.isNotBlank() } ?: path
    }

    private suspend fun releaseOrcaWorktree(path: String, repository: String): Int {
        try {
            val config = configFor(repository)
            val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
            val worktree = worktrees.firstOrNull { sameDirectory(it.path, path) } ?: throw WorktreeError.NotAWorktree(path)
            stopHeadlessApp.beforeRemoving(worktree.path)?.let { err.line(headlessStopFailure(worktree.path, it)) }
            releaseDesktopTurn.onWorktreeRemoved(worktree.path)
            val released = releaseResources.release(config, releaseResources.plan(config, worktree, worktrees))
            released.stoppedEmulators.forEach { out.line("stopped emulator $it") }
            released.stoppedSimulators.forEach { out.line("stopped simulator $it") }
            released.stoppedDaemons.forEach { out.line("stopped Gradle daemon $it") }
            released.failures.forEach { err.line("shogun: hook orca: $it") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            err.line("shogun: hook orca: couldn't free what ShogunAi holds for $path: ${error.message}")
        }
        return EXIT_OK
    }

    private fun noteOutsideWorktreesRoot(worktree: Worktree, config: ProjectConfig) {
        if (config.worktreesRoot.isBlank() || classifyOrigin(worktree, config) == WorktreeOrigin.SHOGUNAI) return
        out.line(
            "note: ${folderName(worktree.path)} is outside ShogunAi's worktrees folder ${displayPath(config.worktreesRoot)}; " +
                "create new worktrees from ShogunAi so they land there",
        )
    }

    private suspend fun deleteMergedBranchOfRemovedWorktree(path: String, branch: String?, repoRoot: String?): Int {
        val projects = projectRepository.list()
        val project = if (repoRoot != null) {
            projects.firstOrNull { sameDirectory(it.config.baseRepositoryPath, repoRoot) }
        } else {
            projects.firstOrNull { it.config.worktreesRoot.isNotBlank() && classifyOrigin(Worktree(path = path, branch = null), it.config) == WorktreeOrigin.SHOGUNAI }
                ?: run {
                    err.line("shogun: hook herdr: couldn't find the repository of $path, so its branch was left alone")
                    return EXIT_OK
                }
        }
        if (project == null || branch == null) return EXIT_OK
        reportKeptBranch(branch, DeleteMergedBranchUseCase(project.config, executor).outcome(branch).getOrThrow())
        return EXIT_OK
    }

    private fun reportKeptBranch(branch: String?, deletion: BranchDeletion?) {
        val reason = deletion?.keptBecause() ?: return
        err.line("shogun: kept branch $branch because $reason")
    }

    private suspend fun flagOutsideWorktreesRoot(worktree: Worktree, config: ProjectConfig) {
        if (config.worktreesRoot.isBlank() || classifyOrigin(worktree, config) == WorktreeOrigin.SHOGUNAI) return
        val body = "${folderName(worktree.path)} is outside ${displayPath(config.worktreesRoot)}; create worktrees from ShogunAi"
        val herdr = environmentVariable(HERDR_BIN_PATH)?.takeIf { it.isNotBlank() } ?: "herdr"
        val failure = try {
            val result = executor.execute(listOf(herdr, "notification", "show", "ShogunAi", "--body", body))
            if (result.isSuccess) null else result.stderr.trim().ifEmpty { "exit code ${result.exitCode}" }
        } catch (error: CommandExecutionException) {
            error.message
        }
        failure?.let { err.line("shogun: hook herdr: couldn't show Herdr's notification: $it") }
    }

    private fun displayPath(path: String): String =
        if (path == userHome || path.startsWith("$userHome/")) "~" + path.removePrefix(userHome) else path

    private fun hookInput(event: String): JsonObject? {
        val text = readInput()
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        if (parsed == null) hookUsageError(event, "expected the hook's JSON on standard input")
        return parsed
    }

    private fun hookUsageError(event: String, message: String): Int {
        err.line("shogun: hook $event: $message")
        return EXIT_USAGE
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private suspend fun configFor(directory: String, worktreesRoot: String? = null): ProjectConfig {
        val repository = ResolveRepositoryUseCase(executor)(directory).getOrThrow()
        val project = projectRepository.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        val config = project?.config ?: ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "", secretFiles = emptyList())
        return if (worktreesRoot != null) config.copy(worktreesRoot = worktreesRoot) else config
    }

    private fun statusLine(entry: WorktreeEntry): String {
        val (worktree, status, origin) = entry
        val details = buildList {
            add(worktree.branch ?: "(detached)")
            if (worktree.isMain) add("main")
            origin?.let { add("origin=${it.label}") }
            entry.base?.let { add("base=$it") }
            if (worktree.isLocked) add("locked")
            entry.agents.takeIf { it.isNotEmpty() }?.let { agents -> add("agents=${agents.joinToString(",") { agentLabel(it) }}") }
            desktopTurnStatusNote(entry.desktop, worktree.path)?.let(::add)
            when {
                worktree.isPrunable -> add("folder-missing")
                status == null -> add(entry.statusProblem?.let { "status-unavailable ($it)" } ?: "status-unavailable")
                else -> {
                    add("changes=${status.uncommittedChanges}")
                    status.upstream?.let { add("ahead=${it.ahead} behind=${it.behind}") }
                    status.base?.let { add("base-ahead=${it.ahead} base-behind=${it.behind}" + (status.baseName?.let { base -> " of $base" } ?: "")) }
                    if (status.isMerged) add("merged")
                    if (status.isNotStarted) add("not-started")
                }
            }
        }
        return (listOf(worktree.path) + details).joinToString("  ")
    }

    private fun appendToEnvironmentFile(path: String, content: String) {
        try {
            File(path).appendText(content)
        } catch (error: FileNotFoundException) {
            throw FileNotFoundException(error.message.orEmpty().replaceFirst(File(path).path, path))
        }
    }

    private fun folderName(path: String): String =
        path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    private fun agentLabel(agent: RunningAgent): String {
        val details = listOfNotNull(agent.state?.name?.lowercase()) + agent.sessions.map { it.name }
        return if (details.isEmpty()) agent.name else "${agent.name}(${details.joinToString(", ")})"
    }

    private fun absolute(path: String): String =
        if (path.startsWith('/') || File(path).isAbsolute) path else File(workingDirectory, path).toPath().normalize().toString().replace('\\', '/')

    private fun sameDirectory(first: String, second: String): Boolean =
        runCatching { canonicalPath(first) == canonicalPath(second) }.getOrDefault(first == second)

    private fun Appendable.line(text: String) {
        append(text).append('\n')
    }

    private val DaemonKind.label: String
        get() = when (this) {
            DaemonKind.GRADLE -> "gradle"
            DaemonKind.KOTLIN -> "kotlin"
        }

    private val DaemonKeptReason.label: String
        get() = when (this) {
            DaemonKeptReason.BUSY -> "busy"
            DaemonKeptReason.STATE_UNKNOWN -> "state unknown"
            DaemonKeptReason.BUILD_RUNNING -> "a Gradle build is running"
        }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_FAILURE = 1
        const val EXIT_USAGE = 2
        const val EXIT_DRIFT = 3
        const val EXIT_INTERRUPTED = 130
        private const val CLAUDE_CODE_WORKTREES = ".claude/worktrees"
        private const val CLAUDE_ENV_FILE = "CLAUDE_ENV_FILE"
        private const val COMPOSE_SESSION_HINT = "Start its Docker Compose services with `shogun compose up` (`--build` to rebuild)."
        private const val APPROVAL_QUESTION = "Approve them on this machine? [y/N] "
        private const val CLAUDE_CONFIG_DIR = "CLAUDE_CONFIG_DIR"
        private const val CODEX_HOME = "CODEX_HOME"
        private const val HERDR_PROBE_MILLIS = 2_000L
        private const val IGNORED_DECLARED_AGENT =
            "The declaration's agent is ignored; set the multirepo agent on this machine with 'shogun multirepo agent'."
        private const val SESSION_END = "SessionEnd"
        private const val BASH_TOOL = "Bash"
        private val DESKTOP_RELEASING_EVENTS = setOf("Stop", SESSION_END)
        private const val HERDR_PLUGIN_EVENT = "HERDR_PLUGIN_EVENT"
        private const val HERDR_PLUGIN_EVENT_JSON = "HERDR_PLUGIN_EVENT_JSON"
        private const val HERDR_WORKTREE_CREATED = "worktree.created"
        private const val HERDR_WORKTREE_REMOVED = "worktree.removed"
        private const val HERDR_BIN_PATH = "HERDR_BIN_PATH"
        private const val ORCA_WORKTREE_PATH = "ORCA_WORKTREE_PATH"
        private const val ORCA_ROOT_PATH = "ORCA_ROOT_PATH"

        const val BUILD_CACHE_NOTICE =
            "Gradle's build cache is off: add org.gradle.caching=true to gradle.properties so a new worktree " +
                "reuses what other worktrees already built."

        fun formatMemory(bytes: Long): String {
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
        }

        fun formatDuration(duration: Duration): String {
            val minutes = duration.toMinutes().coerceAtLeast(0)
            return when {
                minutes < 60 -> "${minutes}m"
                minutes < 24 * 60 -> "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}m"
                else -> "${minutes / (24 * 60)}d"
            }
        }


    }
}

internal fun claudeAncestorPid(): Long? =
    generateSequence(ProcessHandle.current().parent().orElse(null)) { it.parent().orElse(null) }
        .firstOrNull { it.info().command().orElse("").substringAfterLast('/') == "claude" }
        ?.pid()
