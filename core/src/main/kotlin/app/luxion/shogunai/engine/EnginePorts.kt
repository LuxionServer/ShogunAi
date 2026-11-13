package app.luxion.shogunai.engine

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

data class EnginePorts(
    val executor: ShellCommandExecutor,
    val fileManager: FileManager,
    val userHome: String,
    val projectRepository: ProjectRepository,
    val provisioningSource: RepositoryProvisioningSource,
    val provisioningRecords: ProvisioningRecordStore,
    val trust: RepositoryTrust,
    val environmentStateStore: EnvironmentStateStore,
    val portProbe: PortProbe,
    val androidSdk: AndroidSdk,
    val appleSimulators: AppleSimulators,
    val kmpTestWiring: KmpTestWiring,
    val resourceLimits: ResourceLimitsRepository,
    val machineCapacity: MachineCapacity,
    val gradleDaemons: GradleDaemonSource,
    val gradleUserHome: String,
    val mavenDaemons: MavenDaemonSource,
    val composeProjects: ComposeProjectSource,
    val composeRegistry: ComposeProjectRegistry,
    val derivedData: DerivedDataSource,
    val derivedDataSizes: DerivedDataSizeStore,
    val packageCaches: PackageCacheSource,
    val herdr: HerdrClient,
    val herdrConfig: HerdrConfigSource,
    val orca: OrcaClient,
    val orcaWorkspaces: OrcaWorkspaceStore,
    val gitHub: GitHubClient,
    val agentSessions: AgentSessionStore,
    val claudeSessions: ClaudeSessionRegistry,
    val multirepos: MultirepoSource,
    val deviceWaitQueue: DeviceWaitQueue,
    val processLiveness: ProcessLiveness,
    val headlessLauncher: HeadlessAppLauncher,
    val appWindows: AppWindowService,
    val desktopTurns: DesktopTurnStore,
    val physicalDevices: List<PhysicalDevices>,
    val deviceInventory: PhysicalDeviceInventoryStore,
    val deviceLeases: PhysicalDeviceLeaseStore,
)
