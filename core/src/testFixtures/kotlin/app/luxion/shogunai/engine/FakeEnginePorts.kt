package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeComposeProjectRegistry
import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitHubClient
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeMavenDaemonSource
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.FakePhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GitHubClient
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.io.HerdrConfigSource
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

fun fakeEnginePorts(
    executor: ShellCommandExecutor = FakeShellCommandExecutor(),
    fileManager: FileManager = FakeFileManager(),
    userHome: String = "/home/dev",
    projectRepository: ProjectRepository = FakeProjectRepository(),
    provisioningSource: RepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
    provisioningRecords: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
    trust: RepositoryTrust = RepositoryTrust.NONE,
    environmentStateStore: EnvironmentStateStore = FakeEnvironmentStateStore(),
    portProbe: PortProbe = FakePortProbe(),
    androidSdk: AndroidSdk = FakeAndroidSdk(),
    appleSimulators: AppleSimulators = FakeAppleSimulators(),
    kmpTestWiring: KmpTestWiring = FakeKmpTestWiring(),
    resourceLimits: ResourceLimitsRepository = FakeResourceLimitsRepository(),
    machineCapacity: MachineCapacity = FakeMachineCapacity(),
    gradleDaemons: GradleDaemonSource = FakeGradleDaemonSource(),
    gradleUserHome: String = "$userHome/.gradle",
    mavenDaemons: MavenDaemonSource = FakeMavenDaemonSource(),
    composeProjects: ComposeProjectSource = FakeComposeProjectSource(),
    composeRegistry: ComposeProjectRegistry = FakeComposeProjectRegistry(),
    derivedData: DerivedDataSource = FakeDerivedDataSource(),
    derivedDataSizes: DerivedDataSizeStore = DerivedDataSizeStore.NONE,
    packageCaches: PackageCacheSource = PackageCacheSource.NONE,
    herdr: HerdrClient = FakeHerdrClient(),
    herdrConfig: HerdrConfigSource = FakeHerdrConfigSource(),
    orca: OrcaClient = FakeOrcaClient(),
    orcaWorkspaces: OrcaWorkspaceStore = OrcaWorkspaceStore.NONE,
    gitHub: GitHubClient = FakeGitHubClient(),
    agentSessions: AgentSessionStore = FakeAgentSessionStore(),
    claudeSessions: ClaudeSessionRegistry = FakeClaudeSessionRegistry(),
    multirepos: MultirepoSource = FakeMultirepoSource(),
    deviceWaitQueue: DeviceWaitQueue = FakeDeviceWaitQueue(),
    processLiveness: ProcessLiveness = FakeProcessLiveness(),
    headlessLauncher: HeadlessAppLauncher = FakeHeadlessAppLauncher(FakeFileManager(), "$userHome/headless.pid"),
    appWindows: AppWindowService = FakeAppWindowService(),
    desktopTurns: DesktopTurnStore = FakeDesktopTurnStore(),
    physicalDevices: List<PhysicalDevices> = emptyList(),
    deviceInventory: PhysicalDeviceInventoryStore = FakePhysicalDeviceInventoryStore(),
    deviceLeases: PhysicalDeviceLeaseStore = FakePhysicalDeviceLeaseStore(),
): EnginePorts = EnginePorts(
    executor = executor,
    fileManager = fileManager,
    userHome = userHome,
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
    physicalDevices = physicalDevices,
    deviceInventory = deviceInventory,
    deviceLeases = deviceLeases,
)
