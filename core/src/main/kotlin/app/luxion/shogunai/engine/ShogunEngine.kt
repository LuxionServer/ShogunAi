package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.model.runSuspendCatching
import app.luxion.shogunai.domain.io.EnginePorts
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.usecase.AcquireDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.AcquirePhysicalDeviceUseCase
import app.luxion.shogunai.domain.usecase.AcquireWorktreeResourceUseCase
import app.luxion.shogunai.domain.usecase.CaptureHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CloseOrcaTerminalsUseCase
import app.luxion.shogunai.domain.usecase.ConfigureMultirepoUseCase
import app.luxion.shogunai.domain.usecase.CreateGroupUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DeleteUnusedSwiftBuildsUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.DesktopTurnLedger
import app.luxion.shogunai.domain.usecase.DetectMobileSecretsUseCase
import app.luxion.shogunai.domain.usecase.DetectGroupsUseCase
import app.luxion.shogunai.domain.usecase.DetectRepositoryStacksUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.HeadlessAppWorktrees
import app.luxion.shogunai.domain.usecase.ListComposeLeftoversUseCase
import app.luxion.shogunai.domain.usecase.ListComposeProjectsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.ManagePhysicalDevicesUseCase
import app.luxion.shogunai.domain.usecase.MeasureDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.MultirepoSharedEnvironment
import app.luxion.shogunai.domain.usecase.OrcaWorktreeReader
import app.luxion.shogunai.domain.usecase.PhysicalDeviceLedger
import app.luxion.shogunai.domain.usecase.PrepareCocoaPodsUseCase
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ReadAgentActivityUseCase
import app.luxion.shogunai.domain.usecase.ReadDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.ReadPhysicalDevicesUseCase
import app.luxion.shogunai.domain.usecase.ReadReleaseRecordUseCase
import app.luxion.shogunai.domain.usecase.ReadResourcePoolUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.ReleaseDesktopTurnUseCase
import app.luxion.shogunai.domain.usecase.ReleasePhysicalDeviceUseCase
import app.luxion.shogunai.domain.usecase.ReleaseWorktreeResourcesUseCase
import app.luxion.shogunai.domain.usecase.RemoveComposeLeftoversUseCase
import app.luxion.shogunai.domain.usecase.RenewWorktreeLeasesUseCase
import app.luxion.shogunai.domain.usecase.ResolveBaseBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveDerivedDataQuotaUseCase
import app.luxion.shogunai.domain.usecase.ResolveFlutterPackagesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMultirepoUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.RunComposeProjectUseCase
import app.luxion.shogunai.domain.usecase.ShowEmulatorInOrcaUseCase
import app.luxion.shogunai.domain.usecase.StartHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.StopMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.TakeDownComposeProjectsUseCase
import app.luxion.shogunai.domain.usecase.TrimDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.TrimGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.model.sameDirectory

class ShogunEngine(
    val ports: EnginePorts,
    val options: EngineOptions = EngineOptions(),
) {
    private val executor = ports.executor
    private val fileManager = ports.fileManager
    private val provisioningSource = ports.provisioningSource

    val isMac: Boolean = ports.derivedData.isSupported()

    val contributions = MachineProviderContributions(
        ports.appleSimulators,
        ::simulatorSettingsFor,
        gradleJvmArgsFor = { repository -> effectiveGradleJvmArgs(fileManager, ports.gradleUserHome, repository) },
        fileManager = fileManager,
        userHome = ports.userHome,
        isMac = isMac,
    )

    val resolveRepository = ResolveRepositoryUseCase(executor)
    val resolveStartPoint = ResolveStartPointUseCase(executor)
    val resolveBaseBranch = ResolveBaseBranchUseCase(executor)
    val readWorktreeStatus = ReadWorktreeStatusUseCase(executor, fileManager)
    val detectGroups = DetectGroupsUseCase(executor)
    val detectStacks = DetectRepositoryStacksUseCase(fileManager)
    val detectMobileSecrets = DetectMobileSecretsUseCase(executor, fileManager, provisioningSource)
    val checkGradleBuildCache = CheckGradleBuildCacheUseCase(fileManager, ports.gradleUserHome)
    val resolveMultirepo = ResolveMultirepoUseCase(ports.multirepos, executor, fileManager)
    val configureMultirepo = ConfigureMultirepoUseCase(ports.multirepos, resolveMultirepo, ports.projectRepository, executor)
    val sharedEnvironment = MultirepoSharedEnvironment(resolveMultirepo, executor)

    val resolveEnvironment: ResolveWorktreeEnvironmentUseCase = ResolveWorktreeEnvironmentUseCase(
        executor,
        provisioningSource,
        ports.environmentStateStore,
        ports.portProbe,
        contributions,
        options.environmentCache,
        ports.trust,
        ports.composeRegistry,
        sharedEnvironment,
        deviceVariables = { worktree -> physicalDeviceLedger.variablesFor(worktree) },
    )

    private suspend fun adminDirectoryOf(worktreePath: String): String = resolveEnvironment.directoriesOf(worktreePath).admin

    val readAgentActivity = ReadAgentActivityUseCase(ports.agentSessions)
    val resolveLimits = ResolveResourceLimitsUseCase(ports.resourceLimits, ports.machineCapacity)
    val listGradleDaemons = ListGradleDaemonsUseCase(ports.gradleDaemons, fileManager)
    val stopGradleDaemons = StopGradleDaemonsUseCase(ports.gradleDaemons, executor)
    val trimGradleDaemons = TrimGradleDaemonsUseCase(listGradleDaemons, stopGradleDaemons, resolveLimits)
    val listMavenDaemons = ListMavenDaemonsUseCase(ports.mavenDaemons, fileManager)
    val stopMavenDaemons = StopMavenDaemonsUseCase(ports.mavenDaemons, executor)

    val readPool = ReadResourcePoolUseCase(
        resolveEnvironment,
        ports.environmentStateStore,
        fileManager,
        ports.androidSdk,
        ports.appleSimulators,
        resolveLimits,
        listGradleDaemons,
        agentActivity = readAgentActivity::invoke,
        clock = options.clock,
    )
    val readReleaseRecord = ReadReleaseRecordUseCase(resolveEnvironment, ports.environmentStateStore)
    val renewLeases = RenewWorktreeLeasesUseCase(resolveEnvironment, ports.environmentStateStore, deviceLeases = ports.deviceLeases)
    val writeEnvironmentFile = WriteEnvironmentFileUseCase(executor, fileManager, provisioningSource)
    val acquireResource = AcquireWorktreeResourceUseCase(
        provisioningSource,
        resolveEnvironment,
        writeEnvironmentFile,
        ports.environmentStateStore,
        ports.portProbe,
        ports.appleSimulators,
        ports.androidSdk,
        readPool,
        { requested -> contributions.requesting(requested) },
        ports.deviceWaitQueue,
        ports.processLiveness,
        clock = options.clock,
        sleep = options.pauseWhileWaiting,
    )
    val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        writeEnvironmentFile,
        PrepareCocoaPodsUseCase(executor, fileManager, provisioningSource, isMacOs = isMac),
        ResolveFlutterPackagesUseCase(executor, fileManager, provisioningSource),
    )

    val startSimulator = StartWorktreeSimulatorUseCase(provisioningSource, ports.appleSimulators, acquireResource, resolveEnvironment)
    val stopSimulator = StopWorktreeSimulatorUseCase(provisioningSource, ports.appleSimulators, resolveEnvironment)
    val readSimulatorStates = ReadSimulatorStatesUseCase(provisioningSource, ports.appleSimulators, resolveEnvironment)
    val deleteSimulators = DeleteSimulatorsUseCase(provisioningSource, ports.appleSimulators, resolveEnvironment)

    val startEmulator = StartWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, acquireResource, ports.androidSdk)
    val stopEmulator = StopWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, ports.androidSdk)
    val readEmulatorStates = ReadEmulatorStatesUseCase(provisioningSource, resolveEnvironment, ports.androidSdk)
    val showEmulatorInOrca = ShowEmulatorInOrcaUseCase(ports.orca)

    val releaseResources = ReleaseWorktreeResourcesUseCase(
        provisioningSource,
        readEmulatorStates,
        stopEmulator,
        readSimulatorStates,
        stopSimulator,
        listGradleDaemons,
        stopGradleDaemons,
    )

    val headlessWorktrees = HeadlessAppWorktrees(provisioningSource, ::adminDirectoryOf, ports.headlessLauncher, ports.appWindows, fileManager)
    val startHeadlessApp = StartHeadlessAppUseCase(headlessWorktrees, ports.headlessLauncher, ports.appWindows, fileManager, options.pauseWhileStartingApp)
    val stopHeadlessApp = StopHeadlessAppUseCase(headlessWorktrees, ports.headlessLauncher)
    val readHeadlessApp = ReadHeadlessAppUseCase(headlessWorktrees, ports.appWindows)
    val captureHeadlessApp = CaptureHeadlessAppUseCase(headlessWorktrees, ports.appWindows)

    val desktopTurnLedger = DesktopTurnLedger(
        ports.desktopTurns,
        ports.environmentStateStore,
        ports.deviceWaitQueue,
        ports.processLiveness,
        ::adminDirectoryOf,
        clock = options.clock,
        sleep = options.pauseWhileWaiting,
    )
    val acquireDesktopTurn = AcquireDesktopTurnUseCase(desktopTurnLedger, resolveLimits::desktopTurnDuration)
    val releaseDesktopTurn = ReleaseDesktopTurnUseCase(desktopTurnLedger)
    val readDesktopTurn = ReadDesktopTurnUseCase(desktopTurnLedger)

    val physicalDeviceLedger: PhysicalDeviceLedger = PhysicalDeviceLedger(
        ports.physicalDevices,
        ports.deviceInventory,
        ports.deviceLeases,
        ports.environmentStateStore,
        ports.deviceWaitQueue,
        ports.processLiveness,
        ::adminDirectoryOf,
        resolveLimits::leaseDuration,
        agentActivity = readAgentActivity::invoke,
        clock = options.clock,
        sleep = options.pauseWhileWaiting,
    )
    val readPhysicalDevices = ReadPhysicalDevicesUseCase(physicalDeviceLedger)
    val acquirePhysicalDevice = AcquirePhysicalDeviceUseCase(physicalDeviceLedger)
    val releasePhysicalDevice = ReleasePhysicalDeviceUseCase(physicalDeviceLedger)
    val managePhysicalDevices = ManagePhysicalDevicesUseCase(physicalDeviceLedger)

    val orcaReader = OrcaWorktreeReader(ports.orca, ports.orcaWorkspaces, ports.userHome)
    val closeOrcaTerminals = CloseOrcaTerminalsUseCase(ports.orca)
    val detectRunningAgents = DetectRunningAgentsUseCase(
        executor,
        ports.herdr,
        ports.agentSessions,
        claudeSessions = ports.claudeSessions,
        orca = orcaReader,
    )

    val listDerivedData = ListDerivedDataUseCase(ports.derivedData, ports.derivedDataSizes, ports.packageCaches, ports.swiftBuilds)
    val listDerivedDataFolders = ListDerivedDataUseCase(ports.derivedData, ports.derivedDataSizes, swiftBuilds = ports.swiftBuilds)
    val measureDerivedData = MeasureDerivedDataUseCase(ports.derivedData, ports.derivedDataSizes, swiftBuilds = ports.swiftBuilds)
    val deleteOrphanedDerivedData = DeleteOrphanedDerivedDataUseCase(ports.derivedData)
    val resolveDerivedDataQuota = ResolveDerivedDataQuotaUseCase(ports.derivedData)
    val trimDerivedData = TrimDerivedDataUseCase(ports.derivedData, listDerivedData, ports.swiftBuilds, options.clock)
    val deleteUnusedSwiftBuilds = DeleteUnusedSwiftBuildsUseCase(ports.swiftBuilds, options.clock)

    val listComposeProjects = ListComposeProjectsUseCase(ports.composeProjects, ports.composeRegistry)
    val listComposeLeftovers = ListComposeLeftoversUseCase(ports.composeProjects, ports.composeRegistry)
    val removeComposeLeftovers = RemoveComposeLeftoversUseCase(ports.composeProjects, ports.composeRegistry)
    val takeDownComposeProjects = TakeDownComposeProjectsUseCase(ports.composeProjects)
    val runComposeProject = RunComposeProjectUseCase(ports.composeProjects, resolveEnvironment, ports.composeRegistry)

    fun createGroup(approval: RepositoryApproval): CreateGroupUseCase =
        CreateGroupUseCase(executor, fileManager, provisioningSource, prepareEnvironment, ports.trust, approval, ports.provisioningRecords)

    fun project(config: ProjectConfig): ProjectEngine = ProjectEngine(config, this)

    fun simulatorSettingsFor(repositoryPath: String): SimulatorSettings {
        val projects = runSuspendCatching { ports.projectRepository.list() }.getOrDefault(emptyList())
        val project = projects.firstOrNull { sameDirectory(it.config.baseRepositoryPath, repositoryPath) }
        return if (project == null) SimulatorSettings() else project.config.simulator
    }
}
