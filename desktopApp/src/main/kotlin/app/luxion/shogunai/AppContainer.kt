package app.luxion.shogunai

import app.luxion.shogunai.domain.io.AgentRunner
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.usecase.HerdrAgentRunner
import app.luxion.shogunai.engine.EngineOptions
import app.luxion.shogunai.engine.ShogunEngine
import app.luxion.shogunai.infrastructure.hostEnginePorts
import app.luxion.shogunai.domain.usecase.OrcaAgentRunner
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.KnownMultirepos
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.usecase.AddedRepository
import app.luxion.shogunai.domain.usecase.withWorkspaceAt
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.RegisteredMultirepo
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import app.luxion.shogunai.ui.components.ShownErrorLog
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.machine.PhysicalDeviceActions
import app.luxion.shogunai.ui.machine.PhysicalDevicesWatch
import app.luxion.shogunai.ui.multirepo.FolderInspection
import app.luxion.shogunai.ui.multirepo.MultirepoActions
import app.luxion.shogunai.ui.multirepo.MultirepoSetup
import app.luxion.shogunai.ui.multirepo.sameFolder
import java.io.File
import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.executor.ClipboardWriter
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.infrastructure.PmsetPowerSource
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.shell.ResourcePreferences
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.usecase.CheckForUpdateUseCase
import app.luxion.shogunai.domain.usecase.DetectInstallChannelUseCase
import app.luxion.shogunai.infrastructure.GitHubReleaseSource
import app.luxion.shogunai.infrastructure.JsonUpdatePreferenceRepository
import app.luxion.shogunai.ui.shell.ClaudeHooksState
import app.luxion.shogunai.ui.shell.TourActions
import app.luxion.shogunai.ui.shell.TourState
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CreateTourUseCase
import app.luxion.shogunai.domain.usecase.EndTourUseCase
import app.luxion.shogunai.domain.usecase.TourLocation
import app.luxion.shogunai.ui.shell.UpdateState
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.infrastructure.ExecutableLocator
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.infrastructure.NioLauncherFiles
import app.luxion.shogunai.infrastructure.PowerShellUserPath
import app.luxion.shogunai.infrastructure.SystemProcessEnvironmentReader
import app.luxion.shogunai.ui.shell.openInBrowser
import java.nio.file.Files
import java.nio.file.Path
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.ui.worktree.DialogRepositoryApproval
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.usecase.OpenWorktreeInTerminalUseCase
import app.luxion.shogunai.infrastructure.CommandEditorLauncher
import app.luxion.shogunai.infrastructure.EditorExecutableResolver
import app.luxion.shogunai.infrastructure.RegistryUninstallLocations
import app.luxion.shogunai.infrastructure.JsonToolPreferenceRepository
import app.luxion.shogunai.infrastructure.LocalToolDetector
import app.luxion.shogunai.infrastructure.MacTerminalLauncher
import app.luxion.shogunai.infrastructure.MacClaudeDesktopLauncher
import app.luxion.shogunai.infrastructure.SystemFolderLauncher
import app.luxion.shogunai.infrastructure.TmuxTerminalLauncher
import app.luxion.shogunai.infrastructure.WindowsTerminalLauncher
import app.luxion.shogunai.infrastructure.ZellijTerminalLauncher
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.WorktreeAgents
import app.luxion.shogunai.ui.shell.AgentChoices
import app.luxion.shogunai.ui.shell.ToolsPreferences
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppStop
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ListLocalFileCandidatesUseCase
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.MavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenDaemonStopReport
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.WorktreeComposeProject
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.WorktreeComposeState
import app.luxion.shogunai.domain.usecase.COMPOSE_PROJECT_NAME
import app.luxion.shogunai.domain.usecase.worktreeComposeProjectName
import app.luxion.shogunai.domain.usecase.ClaudeSessionNaming
import app.luxion.shogunai.domain.usecase.OpenWorktreeInOrcaUseCase
import app.luxion.shogunai.domain.usecase.OrcaOrigins
import app.luxion.shogunai.domain.usecase.OrcaSyncItem
import app.luxion.shogunai.domain.usecase.OrcaWorktreeSync
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.usecase.DerivedDataMeasure
import app.luxion.shogunai.domain.usecase.CheckInstallationUseCase
import app.luxion.shogunai.domain.usecase.CheckMultireposUseCase
import app.luxion.shogunai.domain.usecase.CheckProviderToolsUseCase
import app.luxion.shogunai.ui.shell.InstallationCheckState
import app.luxion.shogunai.domain.usecase.DiagnosticReport
import app.luxion.shogunai.domain.usecase.Redactor
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.OpenWorktreeInHerdrUseCase
import app.luxion.shogunai.domain.usecase.HerdrAgentLauncher
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.ProvisionResult
import app.luxion.shogunai.domain.usecase.ReleasedResources
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.WorktreeResourcePlan
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ProjectRegistration
import app.luxion.shogunai.domain.usecase.RegisterProjectUseCase
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.projectconfig.RepositoryInspection
import app.luxion.shogunai.domain.usecase.WorktreeEnvironmentCache
import app.luxion.shogunai.domain.usecase.ResolveRefreshCadenceUseCase
import app.luxion.shogunai.domain.io.PowerSource
import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.infrastructure.AwtClipboardWriter
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.domain.usecase.HerdrPluginHealth
import app.luxion.shogunai.ui.shell.HerdrPluginSettings
import app.luxion.shogunai.infrastructure.JsonLanguagePreferenceRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonShellPreferenceRepository
import app.luxion.shogunai.infrastructure.JsonThemePreferenceRepository
import app.luxion.shogunai.infrastructure.RotatingFileDiagnosticsLog
import app.luxion.shogunai.infrastructure.ShogunFiles
import app.luxion.shogunai.infrastructure.SystemHostEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AppContainer(private val hostEnvironment: HostEnvironment = SystemHostEnvironment.forDesktopApp()) {
    init {
        Thread({ hostEnvironment.path }, "host-environment-path").apply { isDaemon = true }.start()
    }

    val files = ShogunFiles(hostEnvironment.home)
    private val settingsFile = JsonSettingsFile(files.settings)
    private val environmentCache = WorktreeEnvironmentCache()
    private val engine = ShogunEngine(
        hostEnginePorts(hostEnvironment, files, settingsFile, sdkRepository = { hostEnvironment.home }, shareDeviceStates = true),
        EngineOptions(environmentCache = environmentCache),
    )
    private val ports = engine.ports
    val projectRepository: ProjectRepository = ports.projectRepository

    fun projectCatalogModifiedAt(): Long? = fileManager.lastModifiedMillis(files.projects.toString())
    var loadedBranches: () -> Collection<String> = { emptyList() }
    private val redactor = Redactor(
        home = hostEnvironment.home,
        userName = System.getProperty("user.name").orEmpty(),
        projectNames = { runCatching { projectRepository.list().map { it.name } }.getOrDefault(emptyList()) },
        branchNames = { runCatching { loadedBranches() }.getOrDefault(emptyList()) },
    )
    val diagnosticsLog: DiagnosticsLog = RotatingFileDiagnosticsLog(files.logs, redact = redactor::redact)
    val diagnosticReport = DiagnosticReport(BuildInfo.VERSION, diagnosticsLog, installation = { checkInstallation() }, redact = redactor::redact)
    val shownErrorLog = ShownErrorLog(diagnosticsLog)
    fun settingsProblem() = settingsFile.lastReadProblem

    fun acknowledgeSettingsProblem() = settingsFile.acknowledgeReadProblem()

    val themePreferenceRepository: ThemePreferenceRepository = JsonThemePreferenceRepository(settingsFile)
    val languagePreferenceRepository: LanguagePreferenceRepository = JsonLanguagePreferenceRepository(settingsFile)
    val shellPreferenceRepository: ShellPreferenceRepository = JsonShellPreferenceRepository(settingsFile)
    private val fileManager: FileManager = ports.fileManager
    private val shellCommandExecutor: ShellCommandExecutor = ports.executor
    private val clipboardWriter: ClipboardWriter = AwtClipboardWriter()
    private val updatePreferences = JsonUpdatePreferenceRepository(settingsFile)
    val updates = UpdateState(
        check = CheckForUpdateUseCase(
            source = GitHubReleaseSource(BuildInfo.VERSION),
            preferences = updatePreferences,
            detectChannel = DetectInstallChannelUseCase(
                os = hostEnvironment.os,
                appLocation = System.getProperty("java.home").orEmpty(),
                pathExists = { Files.exists(Path.of(it)) },
                executor = shellCommandExecutor,
            ),
            runningVersion = BuildInfo.VERSION,
            architecture = HostArchitecture.current(),
        )::invoke,
        preferences = updatePreferences,
        clipboard = clipboardWriter,
        openUrl = ::openInBrowser,
    )
    val repositoryProvisioningSource: RepositoryProvisioningSource = ports.provisioningSource
    private val gradleUserHome = ports.gradleUserHome
    private val gradleDaemonSource = ports.gradleDaemons
    private val composeProjectSource = ports.composeProjects

    private suspend fun composeProjectNameOf(config: ProjectConfig, worktreePath: String): String =
        resolveEnvironment(config.baseRepositoryPath, worktreePath, reserve = false).getOrNull()
            ?.variables?.lastOrNull { it.first == COMPOSE_PROJECT_NAME }?.second
            ?: worktreeComposeProjectName(config.baseRepositoryPath, worktreePath)

    private suspend fun readComposeStates(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, WorktreeComposeState>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val statuses = composeProjectSource.list().associate { it.name to it.status }
                worktrees.associate { worktree ->
                    val project = composeProjectNameOf(config, worktree.path)
                    worktree.path to WorktreeComposeState(project, statuses[project])
                }
            }
        }
    private val packageCacheSource = ports.packageCaches
    private val derivedDataSource = ports.derivedData
    private val appleSimulators = ports.appleSimulators
    private val kmpTestWiring = ports.kmpTestWiring
    val agentChoices = AgentChoices(settingsFile.read().worktreeAgents) { transform ->
        settingsFile.update { it.copy(worktreeAgents = transform(it.worktreeAgents)) }
    }
    private val locator = ExecutableLocator(hostEnvironment)
    private val commandLineTool = CommandLineTool(
        os = hostEnvironment.os,
        appLauncher = System.getProperty("jpackage.app-path"),
        home = hostEnvironment.home,
        pathFolders = { hostEnvironment.path.folders },
        userBinFolders = locator.userBinFolders(),
        findShogun = { locator.find("shogun") },
        files = NioLauncherFiles(),
        userPath = PowerShellUserPath(shellCommandExecutor),
    )
    val claudeHooks = ClaudeHooksState(
        hooks = ClaudeCodeHooks(fileManager),
        settingsPath = { ClaudeCodeHooks.userSettingsPath(hostEnvironment.variable("CLAUDE_CONFIG_DIR"), hostEnvironment.home) },
        tool = commandLineTool,
        noticeDismissedStore = { settingsFile.read().claudeHooksNoticeDismissed },
        rememberNoticeDismissed = { settingsFile.update { it.copy(claudeHooksNoticeDismissed = true) } },
    )
    val tourLocation = TourLocation(hostEnvironment.home)
    val tour = TourState(
        TourActions(
            create = { CreateTourUseCase(shellCommandExecutor, fileManager, projectRepository, tourLocation)(languagePreferenceRepository.load()) },
            end = { EndTourUseCase(fileManager, projectRepository, tourLocation)() },
            existingProject = { runCatching { projectRepository.list().firstOrNull(tourLocation::isTour) }.getOrNull() },
            leftoverFolder = { fileManager.exists(tourLocation.root) },
            toolAvailable = {
                claudeHooks.refresh()
                claudeHooks.toolStatus?.state == CommandLineToolState.AVAILABLE
            },
            offered = { settingsFile.read().tourOffered },
            rememberOffered = { settingsFile.update { it.copy(tourOffered = true) } },
        ),
    )
    private val editorResolver = EditorExecutableResolver(
        hostEnvironment.os,
        locator,
        RegistryUninstallLocations(shellCommandExecutor),
    )
    val tools = ToolsPreferences(
        JsonToolPreferenceRepository(settingsFile),
        LocalToolDetector(shellCommandExecutor, hostEnvironment.os, hostEnvironment.home, locator, editorResolver = editorResolver, isRunnerInstalled = ::isRunnerInstalled),
    )
    private val terminalLaunchers = listOf(
        MacTerminalLauncher(TerminalId.TERMINAL, shellCommandExecutor),
        MacTerminalLauncher(TerminalId.ITERM2, shellCommandExecutor),
        WindowsTerminalLauncher(shellCommandExecutor),
        TmuxTerminalLauncher(shellCommandExecutor),
        ZellijTerminalLauncher(shellCommandExecutor),
    )
    private val editorLaunchers = EditorId.entries.map { CommandEditorLauncher(it, shellCommandExecutor, hostEnvironment.os, locator, editorResolver = editorResolver) }
    private val folderLauncher = SystemFolderLauncher(shellCommandExecutor)
    private val claudeDesktopLauncher = MacClaudeDesktopLauncher(shellCommandExecutor)
    val repositoryTrust: RepositoryTrust = ports.trust
    private val resolveEnvironment = engine.resolveEnvironment
    private val stopSimulator = engine.stopSimulator
    private val readSimulatorStates = engine.readSimulatorStates
    private val deleteSimulators = engine.deleteSimulators

    private suspend fun startEmulatorShownInOrca(config: ProjectConfig, worktreePath: String): Result<DeviceStart<WorktreeEmulator>> =
        startEmulator(config, worktreePath).onSuccess {
            showEmulatorInOrca(worktreePath, it.device.serial) { readSimulatorStates.runningUdid(config, worktreePath) != null }
        }

    private suspend fun startSimulatorShownInOrca(config: ProjectConfig, worktreePath: String): Result<DeviceStart<WorktreeSimulator>> =
        startSimulator(config, worktreePath).onSuccess {
            showEmulatorInOrca(worktreePath, it.device.udid) { readEmulatorStates.runningSerial(config, worktreePath) != null }
        }

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
    private val androidSdk = ports.androidSdk
    private val herdrConfig = ports.herdrConfig
    private val herdrClient = ports.herdr
    private val orcaClient = ports.orca
    val checkInstallation by lazy {
        CheckInstallationUseCase(
            executor = shellCommandExecutor,
            findExecutable = locator::find,
            path = { hostEnvironment.path },
            shell = hostEnvironment.shell,
            commandLineTool = commandLineTool::status,
            hooks = ClaudeCodeHooks(fileManager),
            hooksSettingsPath = ClaudeCodeHooks.userSettingsPath(hostEnvironment.variable("CLAUDE_CONFIG_DIR"), hostEnvironment.home),
            herdrLocation = herdrClient::location,
            orcaLocation = orcaClient::location,
            projects = projectRepository,
            fileManager = fileManager,
            provisioningSource = repositoryProvisioningSource,
            providerTools = CheckProviderToolsUseCase(shellCommandExecutor, locator::find, fileManager, androidSdk, appleSimulators, hostEnvironment::variable, hostEnvironment.os, hostEnvironment.home),
            multirepos = CheckMultireposUseCase(multirepoSource, resolveMultirepo, engine.detectGroups, projectRepository),
        )
    }
    val installationCheck = InstallationCheckState { withContext(Dispatchers.IO) { checkInstallation() } }
    private val orcaReader = engine.orcaReader
    private val orcaSync = OrcaWorktreeSync(orcaClient, ports.orcaWorkspaces, ports.gitHub)

    private fun isRunnerInstalled(runner: RunnerId): Boolean = when (runner) {
        RunnerId.HERDR -> herdrClient.isInstalled()
        RunnerId.ORCA -> orcaClient.isInstalled()
    }
    private val resourceLimitsRepository: ResourceLimitsRepository = ports.resourceLimits
    private val resolveResourceLimits = engine.resolveLimits
    private val resolveRefreshCadence = ResolveRefreshCadenceUseCase(
        resourceLimitsRepository,
        ports.machineCapacity,
        if (isMacOs) PmsetPowerSource(shellCommandExecutor) else PowerSource.UNKNOWN,
    )

    suspend fun refreshCadence(): RefreshCadence = withContext(Dispatchers.IO) { resolveRefreshCadence() }
    private val claudeSessions = ports.claudeSessions
    private val herdrLauncher = HerdrAgentLauncher(herdrClient, registry = claudeSessions)
    val herdrPlugin = HerdrPluginSettings(HerdrPluginHealth(herdrClient))
    private val processEnvironment = SystemProcessEnvironmentReader(shellCommandExecutor)

    private fun orcaSessionNaming() = ClaudeSessionNaming(claudeSessions, processEnvironment = processEnvironment)

    private val agentRunners: Map<RunnerId, AgentRunner> = listOf(
        HerdrAgentRunner(herdrClient, resolveEnvironment, herdrLauncher),
        OrcaAgentRunner(
            orcaClient,
            resolveEnvironment,
            orcaSessionNaming(),
            runningEmulator = { config, path -> readEmulatorStates.runningSerial(config, path) },
            runningSimulator = { config, path -> readSimulatorStates.runningUdid(config, path) },
            heldPhysicalDevice = { path -> physicalDeviceLedger.connectedAndroidSerial(path) },
        ),
    ).associateBy { it.id }
    private val readPool = engine.readPool
    private val trimGradleDaemons = engine.trimGradleDaemons
    val resources = ResourcePreferences(resourceLimitsRepository, resolveResourceLimits, showsSimulators = appleSimulators.isSupported(), showsDerivedDataQuota = derivedDataSource.isSupported())
    private val startSimulator = engine.startSimulator
    private val startEmulator = engine.startEmulator
    private val showEmulatorInOrca = engine.showEmulatorInOrca
    private val stopEmulator = engine.stopEmulator
    private val headlessAppWindows = ports.appWindows
    private val stopHeadlessApp = engine.stopHeadlessApp
    private val readHeadlessApp = engine.readHeadlessApp
    private val releaseDesktopTurn = engine.releaseDesktopTurn
    private val readDesktopTurn = engine.readDesktopTurn
    val desktopTurn = DesktopTurnWatch(
        read = { withContext(Dispatchers.IO) { readDesktopTurn() } },
        forceRelease = { withContext(Dispatchers.IO) { releaseDesktopTurn.force() } },
    )
    private val physicalDeviceLedger = engine.physicalDeviceLedger
    private val readPhysicalDevices = engine.readPhysicalDevices
    private val releasePhysicalDevice = engine.releasePhysicalDevice
    private val managePhysicalDevices = engine.managePhysicalDevices
    var declaresMobileProviders: () -> Boolean = { false }
    val physicalDevices = PhysicalDevicesWatch(
        read = { withContext(Dispatchers.IO) { readPhysicalDevices() } },
        actions = PhysicalDeviceActions(
            release = { id -> withContext(Dispatchers.IO) { releasePhysicalDevice.force(id) } },
            lend = { id -> withContext(Dispatchers.IO) { managePhysicalDevices.lend(id) } },
            withdraw = { id -> withContext(Dispatchers.IO) { managePhysicalDevices.withdraw(id) } },
            rename = { id, alias -> withContext(Dispatchers.IO) { managePhysicalDevices.rename(id, alias) } },
            forget = { id -> withContext(Dispatchers.IO) { managePhysicalDevices.forget(id) } },
        ),
        shouldRead = { declaresMobileProviders() || withContext(Dispatchers.IO) { readPhysicalDevices.hasRecorded() } },
    )
    private val readReleaseRecord = engine.readReleaseRecord
    private val readEmulatorStates = engine.readEmulatorStates

    fun detectStacks(repositoryPath: String) = engine.detectStacks(repositoryPath)

    suspend fun detectMobileSecrets(config: ProjectConfig) =
        engine.detectMobileSecrets(config)

    suspend fun listLocalFileCandidates(config: ProjectConfig) =
        ListLocalFileCandidatesUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource)(config)

    fun listLocalFolder(path: String): DirectoryListing? = if (fileManager.isDirectory(path)) fileManager.listTree(path) else null

    suspend fun loadAvds(repositoryPath: String): Result<List<String>> =
        runCatching { androidSdk.listAvds(androidSdk.locate(repositoryPath)) }

    suspend fun inspectRepository(folder: String): Result<RepositoryInspection> =
        engine.resolveRepository(folder).map { topLevel ->
            val config = ProjectConfig(baseRepositoryPath = topLevel, worktreesRoot = "", secretFiles = emptyList())
            val mainBranch = engine.project(config).resolveMainBranch().getOrNull()
            RepositoryInspection(topLevel, mainBranch?.name)
        }

    val detectRunningAgents = engine.detectRunningAgents

    suspend fun syncOrca(items: List<OrcaSyncItem>) {
        val rows = orcaReader.lastRows() ?: return
        withContext(Dispatchers.IO) { orcaSync.sync(items, rows, prune = true) }
    }


    val machineUseCases = MachineUseCases(
        listDaemons = { runCatching { gradleDaemonSource.list() } },
        listMavenDaemons = { runCatching { ports.mavenDaemons.list() } },
        stopMavenDaemons = { pids -> engine.stopMavenDaemons(pids) },
        listComposeProjects = { config, worktrees, root -> engine.listComposeProjects(config.baseRepositoryPath, worktrees, root) },
        listComposeLeftovers = { config, worktrees -> engine.listComposeLeftovers(config.baseRepositoryPath, worktrees) },
        removeComposeLeftovers = { config, resources, removeVolumes ->
            engine.removeComposeLeftovers(resources, removeVolumes, config.baseRepositoryPath)
        },
        takeDownComposeProjects = { names -> engine.takeDownComposeProjects(names) },
        stopDaemons = { pids -> engine.stopGradleDaemons(pids) },
        listDerivedData = { engine.listDerivedDataFolders(emptyList(), includeCompilationCache = false, measure = DerivedDataMeasure.NONE) },
        measureDerivedData = { folders, remeasure -> engine.measureDerivedData(folders, remeasure) },
        measureCompilationCache = { runCatching { derivedDataSource.compilationCacheBytes() } },
        measurePackageCaches = { runCatching { packageCacheSource.sizes() } },
        deleteOrphanedDerivedData = { engine.deleteOrphanedDerivedData() },
        readEmulatorStates = { config, worktrees -> readEmulatorStates(config, worktrees) },
        readRunningEmulators = { config ->
            runCatching { androidSdk.deviceStates(androidSdk.locate(config.baseRepositoryPath)) }
        },
        startEmulator = { config, worktreePath -> startEmulatorShownInOrca(config, worktreePath) },
        readPool = { kind, config, worktrees, listedGradleDaemons ->
            runCatching { readPool.of(kind, config.baseRepositoryPath, worktrees, listedGradleDaemons) }
        },
        trimGradleDaemons = { worktrees -> trimGradleDaemons(worktrees) },
        resourceSettings = { resources.settings },
        stopEmulator = { config, worktreePath -> stopEmulator(config, worktreePath) },
        stopEmulatorSerials = { config, serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
        readSimulatorStates = { config, worktrees -> readSimulatorStates(config, worktrees) },
        startSimulator = { config, worktreePath -> startSimulatorShownInOrca(config, worktreePath) },
        stopSimulator = { config, worktreePath -> stopSimulator(config, worktreePath) },
        readSimulatorOrphans = { config -> deleteSimulators.orphans(config) },
        deleteSimulators = { udids -> deleteSimulators(udids) },
        trimDerivedData = { quotaBytes, declaring, listing ->
            runCatching {
                val searches = declaring.map { machineProject ->
                    AgentSearch(
                        machineProject.project.config.agents.map { it.agentCommand },
                        machineProject.worktrees.orEmpty().filterNot { it.isPrunable },
                    )
                }
                val worktrees = searches.flatMap { it.worktrees }
                val agents = detectRunningAgents(searches).getOrNull().orEmpty()
                val active = agents.filterValues { running -> running.any { it.state == AgentState.WORKING || it.state == AgentState.BLOCKED } }.keys
                engine.trimDerivedData(quotaBytes, worktrees, active, measured = listing).getOrThrow()
            }
        },
        resolveDerivedDataQuota = { settings, usage -> engine.resolveDerivedDataQuota(settings, usage) },
    )

    val pathExists: (String) -> Boolean = fileManager::exists

    val repositoryApproval = DialogRepositoryApproval()

    private val multirepoSource = ports.multirepos
    private val resolveMultirepo = engine.resolveMultirepo
    private val configureMultirepo = engine.configureMultirepo

    fun knownMultirepos(): KnownMultirepos = resolveMultirepo.listing()

    fun registerWorkspaceProject(multirepo: Multirepo): Boolean {
        val workspace = multirepo.workspaceRepository ?: return false
        val registration = runBlocking {
            RegisterProjectUseCase(projectRepository, engine.resolveRepository, workspaceOf = listOf(multirepo)::withWorkspaceAt)(workspace.path)
        }
        return registration.getOrNull() is ProjectRegistration.Added
    }

    fun projectConfigViewModel(existingProject: Project?): ProjectConfigViewModel = ProjectConfigViewModel(
        projectRepository,
        repositoryProvisioningSource,
        existingProject,
        ::loadAvds,
        simulatorSetup,
        ::inspectRepository,
        isMacOs,
        repositoryTrust,
        tools,
        ::detectStacks,
        ::multirepoNameOfWorkspace,
        ::detectMobileSecrets,
        ::listLocalFolder,
        ::listLocalFileCandidates,
    )

    fun multirepoNameOfWorkspace(topLevel: String): String? = resolveMultirepo.approved().withWorkspaceAt(topLevel)?.name

    val multirepoActions = MultirepoActions(
        createGroup = { members, taskId, branchType, fetch, onRepository ->
            engine.createGroup(repositoryApproval)(members, taskId, branchType, fetch, onRepository)
        },
        openAgent = { runner, multirepo -> agentRunners.getValue(runner).openMultirepoAgent(multirepo) },
        trust = { multirepo -> configureMultirepo.trust(multirepo) },
        openGroup = { runner, group -> agentRunners.getValue(runner).openGroup(group) },
        installedRunners = agentRunners.values.filter { it.isInstalled() }.map { it.id }.toSet(),
    )

    fun forgetMultirepo(folder: String) {
        runCatching { multirepoSource.forget(folder) }
    }

    val multirepoSetup: MultirepoSetup = object : MultirepoSetup {
        override suspend fun inspect(folder: String): FolderInspection = withContext(Dispatchers.IO) {
            val enclosing = engine.resolveRepository(folder).getOrNull()
            if (enclosing != null && !sameFolder(enclosing, folder)) return@withContext FolderInspection(enclosing, emptyList())
            val subfolders = File(folder).listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
                .map { it.absolutePath }
            FolderInspection(null, subfolders.filter { isRepository(it) })
        }

        override suspend fun isRepository(path: String): Boolean = withContext(Dispatchers.IO) {
            engine.resolveRepository(path).getOrNull()?.let { sameFolder(it, path) } == true
        }

        override suspend fun belongsToRepository(path: String): Boolean = withContext(Dispatchers.IO) {
            engine.resolveRepository(path).isSuccess
        }

        override suspend fun detectGroups(members: List<MultirepoMember>): List<WorktreeGroup> = withContext(Dispatchers.IO) {
            engine.detectGroups(members).groups
        }

        override fun projects(): List<Project> = projectRepository.list()

        override fun read(folder: String): Multirepo? =
            runCatching { multirepoSource.read(folder) }.getOrNull()?.let { resolveMultirepo.build(folder, it) }

        override suspend fun init(folder: String): Result<Multirepo> = withContext(Dispatchers.IO) { configureMultirepo.init(folder, null) }

        override suspend fun trust(multirepo: Multirepo): Result<Multirepo> = withContext(Dispatchers.IO) { configureMultirepo.trust(multirepo) }

        override suspend fun register(reviewed: Multirepo): Result<RegisteredMultirepo> = withContext(Dispatchers.IO) {
            configureMultirepo.register(reviewed) { _, _, _ -> true }
        }

        override fun setName(multirepo: Multirepo, name: String?) = configureMultirepo.setName(multirepo, name)

        override suspend fun addRepository(multirepo: Multirepo, alias: String, path: String, useProposedFolder: Boolean): Result<AddedRepository> =
            withContext(Dispatchers.IO) {
                configureMultirepo.addRepository(multirepo, alias, path) { _, _, _ -> useProposedFolder }
            }

        override fun removeRepository(multirepo: Multirepo, alias: String) = configureMultirepo.removeRepository(multirepo, alias)

        override fun renameRepository(multirepo: Multirepo, alias: String, newAlias: String) =
            configureMultirepo.renameRepository(multirepo, alias, newAlias)

        override fun setDefaults(multirepo: Multirepo, aliases: List<String>) = configureMultirepo.setDefaults(multirepo, aliases)

        override fun setWorkspace(multirepo: Multirepo, alias: String, default: Boolean) = configureMultirepo.setWorkspace(multirepo, alias, default)

        override fun clearWorkspace(multirepo: Multirepo) = configureMultirepo.clearWorkspace(multirepo)

        override fun detectWorkspace(multirepo: Multirepo) = configureMultirepo.detectWorkspace(multirepo)

        override fun setSharedVariables(multirepo: Multirepo, byAlias: Map<String, Map<String, String>>) =
            configureMultirepo.setSharedVariables(multirepo, byAlias)

        override fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig) = configureMultirepo.setAgent(multirepo, agent)

        override fun setWorktreesRoot(multirepo: Multirepo, folder: String) = configureMultirepo.setWorktreesRoot(multirepo, folder)

        override fun delete(multirepo: Multirepo) = configureMultirepo.delete(multirepo)
    }

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = engine.project(config).let { project -> WorktreeUseCases(
        home = hostEnvironment.home,
        list = project.listWorktrees,
        create = project.create(repositoryApproval),
        createFromBranch = project.createFromBranch(repositoryApproval),
        listBranches = project.listBranches,
        fetchRemote = project.fetchRemote,
        resolveStartPoint = { ref -> engine.resolveStartPoint(config.baseRepositoryPath, ref) },
        resolveBaseBranch = { ref -> engine.resolveBaseBranch(config.baseRepositoryPath, ref) },
        remove = project.remove,
        deleteBranch = project.deleteBranch,
        copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboardWriter),
        agents = AgentUseCases(config, agentChoices),
        repair = project.repair,
        resolveMainBranch = project.resolveMainBranch,
        resolveRecordedBases = { project.resolveRecordedBases() },
        readStatus = engine.readWorktreeStatus,
        cleanUp = project.cleanUp,
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
            start = { worktreePath -> startEmulatorShownInOrca(config, worktreePath) },
            stop = { worktreePath -> stopEmulator(config, worktreePath) },
            stopSerials = { serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
            readRelease = { worktreePath -> readReleaseRecord(worktreePath, ResourceKind.EMULATOR) },
        ),
        headlessApp = HeadlessAppUseCases(
            declared = {
                withContext(Dispatchers.IO) {
                    headlessAppWindows.isSupported &&
                        runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.desktopApp != null }.getOrDefault(false)
                }
            },
            read = { worktreePath -> withContext(Dispatchers.IO) { readHeadlessApp.process(worktreePath) } },
            stop = { worktreePath -> stopHeadlessApp(worktreePath) },
        ),
        simulator = SimulatorUseCases(
            declared = { withContext(Dispatchers.IO) { simulatorSetup.isAvailable(config.baseRepositoryPath) } },
            readStates = { worktrees -> readSimulatorStates(config, worktrees) },
            start = { worktreePath -> startSimulatorShownInOrca(config, worktreePath) },
            stop = { worktreePath -> stopSimulator(config, worktreePath) },
            orphans = { deleteSimulators.orphans(config) },
            delete = { udids -> deleteSimulators(udids) },
            readRelease = { worktreePath -> readReleaseRecord(worktreePath, ResourceKind.IOS_SIMULATOR) },
        ),
        tools = ToolUseCases(
            preferences = tools,
            editorOverride = config.editorOverride,
            openInTerminal = OpenWorktreeInTerminalUseCase(config, terminalLaunchers, resolveEnvironment)::invoke,
            openInEditor = { path, editor -> editorLaunchers.first { it.editor == editor }.open(path) },
            openFolder = folderLauncher::open,
            openInClaudeDesktop = claudeDesktopLauncher::open,
        ),
        provisioning = ProvisioningUseCases(
            check = project.checkProvisioned::invoke,
            provision = project.provision(repositoryApproval)::invoke,
        ),
        readEnvironment = { worktree -> resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false) },
        releaseResources = engine.releaseResources.let { releaser ->
            ReleaseResourcesUseCases(
                plan = { worktree, all -> releaser.plan(config, worktree, all) },
                release = { plan -> releaser.release(config, plan) },
            )
        },

        herdr = HerdrUseCases(
            worktreesRoot = { withContext(Dispatchers.IO) { runCatching { herdrConfig.worktreesRoot() }.getOrNull() } },
            open = OpenWorktreeInHerdrUseCase(config, herdrClient, resolveEnvironment, herdrLauncher)::invoke,
            openWorkspaces = project.closeHerdrWorkspaces::openWorkspaces,
            focusAgent = { paneId -> runCatching { herdrClient.focusAgent(paneId) } },
        ),
        orca = OrcaUseCases(
            open = OpenWorktreeInOrcaUseCase(
                config,
                orcaClient,
                resolveEnvironment,
                orcaSessionNaming(),
                runningEmulator = { path -> readEmulatorStates.runningSerial(config, path) },
                runningSimulator = { path -> readSimulatorStates.runningUdid(config, path) },
            )::invoke,
            withTerminals = { paths -> withContext(Dispatchers.IO) { engine.closeOrcaTerminals.withTerminals(paths) } },
            origins = orcaReader::origins,
            ports = orcaReader::ports,
        ),
        gradle = GradleUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.gradle }.getOrNull()
                }
            },
            listDaemons = engine.listGradleDaemons,
            stopDaemons = engine.stopGradleDaemons,
            isBuildCacheEnabled = {
                withContext(Dispatchers.IO) { engine.checkGradleBuildCache(config.baseRepositoryPath) }
            },
        ),
        maven = MavenUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.maven }.getOrNull()
                }
            },
            listDaemons = { worktrees -> engine.listMavenDaemons(worktrees, config.worktreesRoot.ifBlank { null }) },
            stopDaemons = { pids -> engine.stopMavenDaemons(pids) },
        ),
        compose = ComposeUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.dockerCompose }.getOrNull()
                }
            },
            readStates = { worktrees -> readComposeStates(config, worktrees) },
            run = { worktreePath, action ->
                engine.runComposeProject(config.baseRepositoryPath, worktreePath, action)
            },
            down = { worktreePath -> composeProjectSource.down(composeProjectNameOf(config, worktreePath), removeVolumes = false) },
        ),
        xcode = XcodeUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.xcode }.getOrNull()
                }
            },
            supported = derivedDataSource::isSupported,
            list = engine.listDerivedDataFolders,
            deleteOrphans = engine.deleteOrphanedDerivedData,
        ),
    ) }
}

class AgentUseCases(
    val config: ProjectConfig,
    val choices: AgentChoices,
) {
    val options: List<AgentLaunchConfig> get() = config.agents
    val hasChoice: Boolean get() = options.size > 1

    fun agentFor(worktreePath: String): AgentLaunchConfig =
        WorktreeAgents.agentFor(config, choices.chosen, worktreePath)

    fun choose(worktreePath: String, agent: AgentLaunchConfig) {
        choices.update { WorktreeAgents.choose(config, it, worktreePath, agent) }
    }

    fun prune(existingWorktreePaths: Set<String>) {
        choices.update { WorktreeAgents.prune(config, it, existingWorktreePaths) }
    }

    companion object {
        val NONE = AgentUseCases(ProjectConfig("", "", emptyList()), AgentChoices())
    }
}

data class WorktreeUseCases(
    val list: ListWorktreesUseCase,
    val create: CreateWorktreeUseCase,
    val createFromBranch: CreateWorktreeFromBranchUseCase,
    val listBranches: ListBranchesUseCase,
    val fetchRemote: FetchRemoteUseCase,
    val resolveStartPoint: suspend (String) -> Result<String>,
    val resolveBaseBranch: suspend (String) -> String? = { null },
    val remove: RemoveWorktreeUseCase,
    val deleteBranch: DeleteBranchUseCase,
    val copyLaunchCommand: CopyWorktreeLaunchCommandUseCase,
    val repair: RepairWorktreesUseCase,
    val resolveMainBranch: ResolveMainBranchUseCase,
    val resolveRecordedBases: suspend () -> Map<String, MainBranch> = { emptyMap() },
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
    val maven: MavenUseCases = MavenUseCases.NONE,
    val compose: ComposeUseCases = ComposeUseCases.NONE,
    val herdr: HerdrUseCases = HerdrUseCases.NONE,
    val orca: OrcaUseCases = OrcaUseCases.NONE,
    val tools: ToolUseCases = ToolUseCases.NONE,
    val provisioning: ProvisioningUseCases = ProvisioningUseCases.NONE,
    val readEnvironment: suspend (Worktree) -> Result<WorktreeEnvironment> = { Result.success(WorktreeEnvironment()) },
    val releaseResources: ReleaseResourcesUseCases = ReleaseResourcesUseCases.NONE,
    val agents: AgentUseCases = AgentUseCases.NONE,
    val headlessApp: HeadlessAppUseCases = HeadlessAppUseCases.NONE,
    val home: String = "",
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
    val listDerivedData: suspend () -> Result<DerivedDataListing>,
    val measureDerivedData: (List<DerivedDataFolder>, Boolean) -> Flow<MeasuredSize>,
    val measureCompilationCache: suspend () -> Result<Long?>,
    val deleteOrphanedDerivedData: suspend () -> Result<DerivedDataDeletion>,
    val readEmulatorStates: suspend (ProjectConfig, List<Worktree>) -> Result<Map<String, WorktreeEmulator>>,
    val readRunningEmulators: suspend (ProjectConfig) -> Result<Map<String, EmulatorState>>,
    val startEmulator: suspend (ProjectConfig, String) -> Result<DeviceStart<WorktreeEmulator>>,
    val readPool: suspend (ResourceKind, ProjectConfig, List<Worktree>?, List<DaemonProcess>?) -> Result<PoolUsage>,
    val trimGradleDaemons: suspend (List<Worktree>?) -> Result<GradleTrim>,
    val resourceSettings: () -> ResourceSettings,
    val stopEmulator: suspend (ProjectConfig, String) -> Result<WorktreeEmulator?>,
    val stopEmulatorSerials: suspend (ProjectConfig, List<String>) -> Result<List<WorktreeEmulator>>,
    val readSimulatorStates: suspend (ProjectConfig, List<Worktree>) -> Result<Map<String, WorktreeSimulator>>,
    val startSimulator: suspend (ProjectConfig, String) -> Result<DeviceStart<WorktreeSimulator>>,
    val stopSimulator: suspend (ProjectConfig, String) -> Result<WorktreeSimulator?>,
    val readSimulatorOrphans: suspend (ProjectConfig) -> Result<List<SimulatorDevice>>,
    val deleteSimulators: suspend (List<String>) -> Result<List<String>>,
    val trimDerivedData: suspend (Long, List<MachineProject>, DerivedDataListing) -> Result<DerivedDataTrim> =
        { quotaBytes, _, _ -> Result.success(DerivedDataTrim(quotaBytes, 0L, 0L)) },
    val resolveDerivedDataQuota: suspend (ResourceSettings, Long) -> DerivedDataQuota =
        { settings, _ -> DerivedDataQuota(settings.quotaMode, settings.fixedDerivedDataQuotaBytes.takeIf { settings.quotaMode == DerivedDataQuotaMode.FIXED }) },
    val listMavenDaemons: suspend () -> Result<List<MavenDaemonProcess>> = { Result.success(emptyList()) },
    val stopMavenDaemons: suspend (List<Long>?) -> Result<MavenDaemonStopReport> = { Result.success(MavenDaemonStopReport()) },
    val listComposeProjects: suspend (ProjectConfig, List<Worktree>, String?) -> Result<List<WorktreeComposeProject>> =
        { _, _, _ -> Result.success(emptyList()) },
    val takeDownComposeProjects: suspend (List<String>) -> ComposeDownReport = { ComposeDownReport() },
    val listComposeLeftovers: suspend (ProjectConfig, List<Worktree>) -> Result<List<ComposeResource>> = { _, _ -> Result.success(emptyList()) },
    val removeComposeLeftovers: suspend (ProjectConfig, List<ComposeResource>, Boolean) -> ComposeLeftoversReport =
        { _, _, _ -> ComposeLeftoversReport() },
    val measurePackageCaches: suspend () -> Result<PackageCacheSizes> = { Result.success(PackageCacheSizes()) },
)

data class SimulatorUseCases(
    val declared: suspend () -> Boolean,
    val readStates: suspend (List<Worktree>) -> Result<Map<String, WorktreeSimulator>>,
    val start: suspend (String) -> Result<DeviceStart<WorktreeSimulator>>,
    val stop: suspend (String) -> Result<WorktreeSimulator?>,
    val orphans: suspend () -> Result<List<SimulatorDevice>>,
    val delete: suspend (List<String>) -> Result<List<String>>,
    val readRelease: suspend (String) -> ReleaseRecord? = { null },
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

data class ToolUseCases(
    val preferences: ToolsPreferences,
    val editorOverride: EditorId?,
    val openInTerminal: suspend (path: String, terminal: TerminalId, agent: AgentLaunchConfig) -> Result<Unit>,
    val openInEditor: suspend (path: String, editor: EditorId) -> Result<Unit>,
    val openFolder: suspend (path: String) -> Result<Unit>,
    val openInClaudeDesktop: suspend (path: String) -> Result<Unit> =
        { Result.failure(WorktreeError.ClaudeDesktopLaunchFailed("no launcher")) },
) {
    companion object {
        val NONE = ToolUseCases(
            preferences = ToolsPreferences.NONE,
            editorOverride = null,
            openInTerminal = { _, terminal, _ -> Result.failure(WorktreeError.TerminalUnsupported(terminal, "no launcher")) },
            openInEditor = { _, editor -> Result.failure(WorktreeError.EditorLaunchFailed(editor, "no launcher")) },
            openFolder = { path -> Result.failure(WorktreeError.FolderOpenFailed(path, "no launcher")) },
        )
    }
}

data class HerdrUseCases(
    val worktreesRoot: suspend () -> String?,
    val open: suspend (Worktree, AgentLaunchConfig) -> Result<HerdrOpening>,
    val openWorkspaces: suspend (List<String>) -> Map<String, String> = { emptyMap() },
    val focusAgent: suspend (String) -> Result<Unit> = { Result.success(Unit) },
) {
    companion object {
        val NONE = HerdrUseCases(
            worktreesRoot = { null },
            open = { _, _ -> Result.failure(WorktreeError.HerdrNotInstalled) },
        )
    }
}

data class OrcaUseCases(
    val open: suspend (Worktree, AgentLaunchConfig) -> Result<OrcaOpening>,
    val withTerminals: suspend (List<String>) -> Set<String> = { emptySet() },
    val origins: () -> OrcaOrigins? = { null },
    val ports: (List<Worktree>) -> Map<String, List<ObservedPort>>? = { null },
) {
    companion object {
        val NONE = OrcaUseCases(open = { _, _ -> Result.failure(WorktreeError.OrcaNotInstalled) })
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
    val start: suspend (String) -> Result<DeviceStart<WorktreeEmulator>>,
    val stop: suspend (String) -> Result<WorktreeEmulator?>,
    val stopSerials: suspend (List<String>) -> Result<List<WorktreeEmulator>>,
    val readRelease: suspend (String) -> ReleaseRecord? = { null },
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

data class HeadlessAppUseCases(
    val declared: suspend () -> Boolean,
    val read: suspend (String) -> Result<HeadlessAppProcess?>,
    val stop: suspend (String) -> Result<HeadlessAppStop>,
) {
    companion object {
        val NONE = HeadlessAppUseCases(
            declared = { false },
            read = { Result.success(null) },
            stop = { Result.success(HeadlessAppStop(wasRunning = false, pid = null)) },
        )
    }
}

data class GradleUseCases(
    val provider: suspend () -> GradleProvider?,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: suspend () -> Boolean,
)

data class MavenUseCases(
    val provider: suspend () -> MavenProvider?,
    val listDaemons: suspend (List<Worktree>) -> Result<List<MavenDaemon>> = { Result.success(emptyList()) },
    val stopDaemons: suspend (List<Long>) -> Result<MavenDaemonStopReport> = { Result.success(MavenDaemonStopReport()) },
) {
    companion object {
        val NONE = MavenUseCases(provider = { null })
    }
}

data class ComposeUseCases(
    val provider: suspend () -> DockerComposeProvider?,
    val readStates: suspend (List<Worktree>) -> Result<Map<String, WorktreeComposeState>> = { Result.success(emptyMap()) },
    val run: suspend (String, ComposeAction) -> Result<String> = { _, _ -> Result.failure(WorktreeError.DockerUnavailable(null)) },
    val down: suspend (String) -> Result<Unit> = { Result.success(Unit) },
) {
    companion object {
        val NONE = ComposeUseCases(provider = { null })
    }
}

data class XcodeUseCases(
    val provider: suspend () -> XcodeProvider?,
    val supported: suspend () -> Boolean,
    val list: ListDerivedDataUseCase,
    val deleteOrphans: DeleteOrphanedDerivedDataUseCase,
)
