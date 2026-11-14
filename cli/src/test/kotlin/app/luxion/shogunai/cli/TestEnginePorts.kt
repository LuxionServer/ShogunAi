package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.FakeForegroundCommandRunner
import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.io.ForegroundCommandRunner
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GitHubClient
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.io.HerdrConfigSource
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.KmpTestWiring
import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.io.PackageCacheSource
import app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.claudeSessionsDirectory
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.io.EnginePorts
import app.luxion.shogunai.infrastructure.AdbPhysicalDevices
import app.luxion.shogunai.infrastructure.DevicectlPhysicalDevices
import app.luxion.shogunai.infrastructure.CliGitHubClient
import app.luxion.shogunai.infrastructure.CliHerdrClient
import app.luxion.shogunai.infrastructure.CliOrcaClient
import app.luxion.shogunai.infrastructure.DockerComposeProjectSource
import app.luxion.shogunai.infrastructure.ExecutableLocator
import app.luxion.shogunai.infrastructure.FileAgentSessionStore
import app.luxion.shogunai.infrastructure.FileClaudeSessionRegistry
import app.luxion.shogunai.infrastructure.GradleHeadlessAppLauncher
import app.luxion.shogunai.infrastructure.JsonComposeProjectRegistry
import app.luxion.shogunai.infrastructure.JsonMultirepoSource
import app.luxion.shogunai.infrastructure.JsonOrcaWorkspaceStore
import app.luxion.shogunai.infrastructure.MacAppWindowService
import app.luxion.shogunai.infrastructure.NioBuildTurnStore
import app.luxion.shogunai.infrastructure.NioDesktopTurnStore
import app.luxion.shogunai.infrastructure.NioDeviceWaitQueue
import app.luxion.shogunai.infrastructure.NioPhysicalDeviceInventoryStore
import app.luxion.shogunai.infrastructure.NioPhysicalDeviceLeaseStore
import app.luxion.shogunai.infrastructure.ProcessHandleLiveness
import app.luxion.shogunai.infrastructure.ProcessMavenDaemonSource
import app.luxion.shogunai.infrastructure.ShogunFiles

fun cliEnginePorts(
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    projectRepository: ProjectRepository,
    provisioningSource: RepositoryProvisioningSource,
    gradleDaemons: GradleDaemonSource,
    gradleUserHome: String,
    environmentStateStore: EnvironmentStateStore,
    portProbe: PortProbe,
    androidSdk: AndroidSdk,
    appleSimulators: AppleSimulators,
    kmpTestWiring: KmpTestWiring,
    resourceLimits: ResourceLimitsRepository,
    machineCapacity: MachineCapacity,
    derivedData: DerivedDataSource,
    herdrConfig: HerdrConfigSource,
    environment: HostEnvironment,
    files: ShogunFiles = ShogunFiles(environment.home),
    findExecutable: (String) -> String? = ExecutableLocator(environment)::find,
    herdr: HerdrClient = CliHerdrClient(executor, findExecutable),
    orca: OrcaClient = CliOrcaClient(executor, findExecutable),
    gitHub: GitHubClient = CliGitHubClient(executor, findExecutable),
    orcaWorkspaces: OrcaWorkspaceStore = JsonOrcaWorkspaceStore(files.orcaWorkspaces),
    trust: RepositoryTrust = RepositoryTrust.NONE,
    agentSessions: AgentSessionStore = FileAgentSessionStore(files.agentSessions),
    mavenDaemons: MavenDaemonSource = ProcessMavenDaemonSource(executor),
    composeProjects: ComposeProjectSource = DockerComposeProjectSource(executor),
    composeRegistry: ComposeProjectRegistry = JsonComposeProjectRegistry(files.composeProjects),
    multirepos: MultirepoSource = JsonMultirepoSource(files.multirepos),
    provisioningRecords: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
    derivedDataSizes: DerivedDataSizeStore = DerivedDataSizeStore.NONE,
    swiftBuilds: SwiftBuildSource = SwiftBuildSource.NONE,
    packageCaches: PackageCacheSource = PackageCacheSource.NONE,
    deviceWaitQueue: DeviceWaitQueue = NioDeviceWaitQueue(files.folder),
    processLiveness: ProcessLiveness = ProcessHandleLiveness(),
    headlessLauncher: HeadlessAppLauncher = GradleHeadlessAppLauncher(executor, files.headless.toString()),
    appWindows: AppWindowService = MacAppWindowService(executor, files.headless.toString()),
    desktopTurns: DesktopTurnStore = NioDesktopTurnStore(files.folder),
    buildTurns: BuildTurnStore = NioBuildTurnStore(files.folder),
    foregroundCommands: ForegroundCommandRunner = FakeForegroundCommandRunner(),
    workingDirectory: String = environment.home,
    physicalDevices: List<PhysicalDevices> = listOf(
        AdbPhysicalDevices(androidSdk) { workingDirectory },
        DevicectlPhysicalDevices(executor, if (environment.os == HostOs.MACOS) "Mac OS X" else environment.os.name),
    ),
    deviceInventory: PhysicalDeviceInventoryStore = NioPhysicalDeviceInventoryStore(files.folder),
    deviceLeases: PhysicalDeviceLeaseStore = NioPhysicalDeviceLeaseStore(files.folder),
    claudeSessions: ClaudeSessionRegistry = FileClaudeSessionRegistry(
        claudeSessionsDirectory(environment.variable("CLAUDE_CONFIG_DIR"), environment.home),
    ),
) = EnginePorts(
    executor = executor,
    fileManager = fileManager,
    userHome = environment.home,
    projectRepository = projectRepository,
    provisioningSource = provisioningSource,
    provisioningRecords = provisioningRecords,
    trust = trust,
    environmentStateStore = environmentStateStore,
    portProbe = portProbe,
    androidSdk = androidSdk,
    appleSimulators = appleSimulators,
    kmpTestWiring = kmpTestWiring,
    resourceLimits = resourceLimits,
    machineCapacity = machineCapacity,
    gradleDaemons = gradleDaemons,
    gradleUserHome = gradleUserHome,
    mavenDaemons = mavenDaemons,
    composeProjects = composeProjects,
    composeRegistry = composeRegistry,
    derivedData = derivedData,
    derivedDataSizes = derivedDataSizes,
    swiftBuilds = swiftBuilds,
    packageCaches = packageCaches,
    herdr = herdr,
    herdrConfig = herdrConfig,
    orca = orca,
    orcaWorkspaces = orcaWorkspaces,
    gitHub = gitHub,
    agentSessions = agentSessions,
    claudeSessions = claudeSessions,
    multirepos = multirepos,
    deviceWaitQueue = deviceWaitQueue,
    processLiveness = processLiveness,
    headlessLauncher = headlessLauncher,
    appWindows = appWindows,
    desktopTurns = desktopTurns,
    buildTurns = buildTurns,
    foregroundCommands = foregroundCommands,
    physicalDevices = physicalDevices,
    deviceInventory = deviceInventory,
    deviceLeases = deviceLeases,
)
