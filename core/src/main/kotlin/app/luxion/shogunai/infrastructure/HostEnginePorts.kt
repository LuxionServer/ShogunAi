package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.claudeSessionsDirectory
import app.luxion.shogunai.domain.io.EnginePorts

fun hostEnginePorts(
    environment: HostEnvironment,
    files: ShogunFiles,
    settings: JsonSettingsFile,
    sdkRepository: () -> String,
    shareDeviceStates: Boolean,
): EnginePorts {
    val executor = ProcessBuilderShellCommandExecutor(path = { environment.path.folders })
    val fileManager = NioFileManager()
    val home = environment.home
    val locator = ExecutableLocator(environment)
    val findExecutable: (String) -> String? = { locator.find(it) }
    val gradleUserHome = defaultGradleUserHome(environment)
    val localSdk = LocalAndroidSdk(executor, fileManager, environment::variable, home, logDirectory = files.logs)
    val androidSdk: AndroidSdk = if (shareDeviceStates) SharedDeviceStatesAndroidSdk(localSdk) else localSdk
    return EnginePorts(
        executor = executor,
        fileManager = fileManager,
        userHome = home,
        projectRepository = JsonProjectRepository(files.projects),
        provisioningSource = RepositoryProvisioningFiles(),
        provisioningRecords = NioProvisioningRecordStore(),
        trust = JsonRepositoryTrust(files.trust),
        environmentStateStore = NioEnvironmentStateStore(files.folder),
        portProbe = SocketPortProbe(),
        androidSdk = androidSdk,
        appleSimulators = SimctlAppleSimulators(executor),
        kmpTestWiring = FileKmpTestWiring(gradleUserHome),
        resourceLimits = JsonResourceLimitsRepository(settings),
        machineCapacity = JvmMachineCapacity(),
        gradleDaemons = ProcessGradleDaemonSource(executor, gradleUserHome),
        gradleUserHome = gradleUserHome,
        mavenDaemons = ProcessMavenDaemonSource(executor),
        composeProjects = DockerComposeProjectSource(executor),
        composeRegistry = JsonComposeProjectRegistry(files.composeProjects),
        derivedData = MacDerivedDataSource(executor, fileManager, home),
        derivedDataSizes = JsonDerivedDataSizeStore(files.derivedDataSizes),
        swiftBuilds = FileSwiftBuildSource(fileManager),
        packageCaches = MacPackageCacheSource(fileManager, home),
        herdr = CliHerdrClient(executor, findExecutable),
        herdrConfig = HerdrConfigFile(environment::variable, home),
        orca = CliOrcaClient(executor, findExecutable),
        orcaWorkspaces = JsonOrcaWorkspaceStore(files.orcaWorkspaces),
        gitHub = CliGitHubClient(executor, findExecutable),
        agentSessions = FileAgentSessionStore(files.agentSessions),
        claudeSessions = FileClaudeSessionRegistry(claudeSessionsDirectory(environment.variable(CLAUDE_CONFIG_DIR), home)),
        multirepos = JsonMultirepoSource(files.multirepos),
        deviceWaitQueue = NioDeviceWaitQueue(files.folder),
        processLiveness = ProcessHandleLiveness(),
        headlessLauncher = GradleHeadlessAppLauncher(executor, files.headless.toString()),
        appWindows = MacAppWindowService(executor, files.headless.toString()),
        desktopTurns = NioDesktopTurnStore(files.folder),
        physicalDevices = listOf(
            AdbPhysicalDevices(androidSdk, sdkRepository),
            DevicectlPhysicalDevices(executor, devicectlOsName(environment.os)),
        ),
        deviceInventory = NioPhysicalDeviceInventoryStore(files.folder),
        deviceLeases = NioPhysicalDeviceLeaseStore(files.folder),
        poolLeases = NioCustomPoolLeaseStore(files.folder),
    )
}

internal fun devicectlOsName(os: HostOs): String = if (os == HostOs.MACOS) "Mac OS X" else os.name

private const val CLAUDE_CONFIG_DIR = "CLAUDE_CONFIG_DIR"
