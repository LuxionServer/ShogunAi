package app.luxion.shogunai

import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.usecase.AddedRepository
import app.luxion.shogunai.domain.usecase.ConfigureMultirepoUseCase
import app.luxion.shogunai.domain.usecase.MeasureDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ResolveDerivedDataQuotaUseCase
import app.luxion.shogunai.domain.usecase.withWorkspaceAt
import app.luxion.shogunai.domain.usecase.CreateGroupUseCase
import app.luxion.shogunai.domain.usecase.DetectGroupsUseCase
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.OpenMultirepoAgentInHerdrUseCase
import app.luxion.shogunai.domain.usecase.ResolveMultirepoUseCase
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import app.luxion.shogunai.infrastructure.JsonDerivedDataSizeStore
import app.luxion.shogunai.infrastructure.JsonMultirepoSource
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
import app.luxion.shogunai.domain.usecase.AcquireWorktreeResourceUseCase
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.ReadAgentActivityUseCase
import app.luxion.shogunai.domain.usecase.ReadReleaseRecordUseCase
import app.luxion.shogunai.domain.usecase.ReadResourcePoolUseCase
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.domain.usecase.TrimDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.TrimGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.infrastructure.JsonResourceLimitsRepository
import app.luxion.shogunai.infrastructure.JvmMachineCapacity
import app.luxion.shogunai.ui.shell.ResourcePreferences
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.model.HostOs
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
import app.luxion.shogunai.infrastructure.FileAgentSessionStore
import app.luxion.shogunai.ui.shell.openInBrowser
import java.nio.file.Files
import java.nio.file.Path
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.ui.worktree.DialogRepositoryApproval
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
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
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.DetectMobileSecretsUseCase
import app.luxion.shogunai.domain.usecase.DetectRepositoryStacksUseCase
import app.luxion.shogunai.domain.usecase.ListComposeProjectsUseCase
import app.luxion.shogunai.domain.usecase.ListMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.TakeDownComposeProjectsUseCase
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.WorktreeComposeProject
import app.luxion.shogunai.infrastructure.DockerComposeProjectSource
import app.luxion.shogunai.infrastructure.ProcessMavenDaemonSource
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.usecase.DerivedDataMeasure
import app.luxion.shogunai.domain.usecase.DiagnosticReport
import app.luxion.shogunai.domain.usecase.Redactor
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
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.WorktreeResourcePlan
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ProjectRegistration
import app.luxion.shogunai.domain.usecase.RegisterProjectUseCase
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
import app.luxion.shogunai.infrastructure.NioDeviceWaitQueue
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.RotatingFileDiagnosticsLog
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.NioProvisioningRecordStore
import app.luxion.shogunai.infrastructure.ProcessHandleLiveness
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SimctlAppleSimulators
import app.luxion.shogunai.infrastructure.SocketPortProbe
import app.luxion.shogunai.infrastructure.defaultGradleUserHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AppContainer {
    val projectRepository: ProjectRepository = JsonProjectRepository()
    var loadedBranches: () -> Collection<String> = { emptyList() }
    private val redactor = Redactor(
        home = System.getProperty("user.home").orEmpty(),
        userName = System.getProperty("user.name").orEmpty(),
        projectNames = { runCatching { projectRepository.list().map { it.name } }.getOrDefault(emptyList()) },
        branchNames = { runCatching { loadedBranches() }.getOrDefault(emptyList()) },
    )
    val diagnosticsLog: DiagnosticsLog = RotatingFileDiagnosticsLog(redact = redactor::redact)
    val diagnosticReport = DiagnosticReport(BuildInfo.VERSION, diagnosticsLog)
    private val settingsFile = JsonSettingsFile()
    fun settingsProblem() = settingsFile.lastReadProblem

    fun acknowledgeSettingsProblem() = settingsFile.acknowledgeReadProblem()

    val themePreferenceRepository: ThemePreferenceRepository = JsonThemePreferenceRepository(settingsFile)
    val languagePreferenceRepository: LanguagePreferenceRepository = JsonLanguagePreferenceRepository(settingsFile)
    val shellPreferenceRepository: ShellPreferenceRepository = JsonShellPreferenceRepository(settingsFile)
    private val fileManager: FileManager = NioFileManager()
    private val shellCommandExecutor: ShellCommandExecutor = ProcessBuilderShellCommandExecutor()
    private val clipboardWriter: ClipboardWriter = AwtClipboardWriter()
    private val updatePreferences = JsonUpdatePreferenceRepository(settingsFile)
    val updates = UpdateState(
        check = CheckForUpdateUseCase(
            source = GitHubReleaseSource(BuildInfo.VERSION),
            preferences = updatePreferences,
            detectChannel = DetectInstallChannelUseCase(
                os = HostOs.current(),
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
    val repositoryProvisioningSource: RepositoryProvisioningSource = RepositoryProvisioningFiles()
    private val provisioningRecords: ProvisioningRecordStore = NioProvisioningRecordStore()
    private val gradleUserHome = defaultGradleUserHome()
    private val gradleDaemonSource = ProcessGradleDaemonSource(shellCommandExecutor, gradleUserHome)
    private val mavenDaemonSource = ProcessMavenDaemonSource(shellCommandExecutor)
    private val composeProjectSource = DockerComposeProjectSource(shellCommandExecutor)
    private val derivedDataSource = MacDerivedDataSource(
        shellCommandExecutor,
        fileManager,
        home = System.getProperty("user.home"),
    )
    private val derivedDataSizes = JsonDerivedDataSizeStore()
    private val measureDerivedDataSizes = MeasureDerivedDataUseCase(derivedDataSource, derivedDataSizes)
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
    val agentChoices = AgentChoices(settingsFile.read().worktreeAgents) { transform ->
        settingsFile.update { it.copy(worktreeAgents = transform(it.worktreeAgents)) }
    }
    private val commandLineTool = ExecutableLocator().let { locator ->
        CommandLineTool(
            os = HostOs.current(),
            appLauncher = System.getProperty("jpackage.app-path"),
            home = System.getProperty("user.home"),
            pathFolders = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator).filter { it.isNotBlank() },
            userBinFolders = locator.userBinFolders(),
            findShogun = { locator.find("shogun") },
            files = NioLauncherFiles(),
            userPath = PowerShellUserPath(shellCommandExecutor),
        )
    }
    val claudeHooks = ClaudeHooksState(
        hooks = ClaudeCodeHooks(fileManager),
        settingsPath = { ClaudeCodeHooks.userSettingsPath(System.getenv("CLAUDE_CONFIG_DIR"), System.getProperty("user.home")) },
        tool = commandLineTool,
        noticeDismissedStore = { settingsFile.read().claudeHooksNoticeDismissed },
        rememberNoticeDismissed = { settingsFile.update { it.copy(claudeHooksNoticeDismissed = true) } },
    )
    val tourLocation = TourLocation()
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
        HostOs.current(),
        ExecutableLocator(),
        RegistryUninstallLocations(shellCommandExecutor),
    )
    val tools = ToolsPreferences(
        JsonToolPreferenceRepository(settingsFile),
        LocalToolDetector(shellCommandExecutor, editorResolver = editorResolver),
    )
    private val terminalLaunchers = listOf(
        MacTerminalLauncher(TerminalId.TERMINAL, shellCommandExecutor),
        MacTerminalLauncher(TerminalId.ITERM2, shellCommandExecutor),
        WindowsTerminalLauncher(shellCommandExecutor),
        TmuxTerminalLauncher(shellCommandExecutor),
        ZellijTerminalLauncher(shellCommandExecutor),
    )
    private val editorLaunchers = EditorId.entries.map { CommandEditorLauncher(it, shellCommandExecutor, editorResolver = editorResolver) }
    private val folderLauncher = SystemFolderLauncher(shellCommandExecutor)
    private val claudeDesktopLauncher = MacClaudeDesktopLauncher(shellCommandExecutor)
    private val environmentCache = WorktreeEnvironmentCache()
    val repositoryTrust: RepositoryTrust = JsonRepositoryTrust()
    private val resolveEnvironment = resolverWith(contributions)
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
        repositoryTrust,
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
    private val resourceLimitsRepository: ResourceLimitsRepository = JsonResourceLimitsRepository(settingsFile)
    private val resolveResourceLimits = ResolveResourceLimitsUseCase(resourceLimitsRepository, JvmMachineCapacity())
    private val listGradleDaemons = ListGradleDaemonsUseCase(gradleDaemonSource, fileManager)
    private val stopGradleDaemons = StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor)
    private val agentSessionStore = FileAgentSessionStore()
    private val readPool = ReadResourcePoolUseCase(
        resolveEnvironment,
        environmentStateStore,
        fileManager,
        androidSdk,
        appleSimulators,
        resolveResourceLimits,
        listGradleDaemons,
        agentActivity = ReadAgentActivityUseCase(agentSessionStore)::invoke,
    )
    private val acquireResource = AcquireWorktreeResourceUseCase(
        repositoryProvisioningSource,
        resolveEnvironment,
        WriteEnvironmentFileUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource),
        environmentStateStore,
        portProbe,
        appleSimulators,
        androidSdk,
        readPool,
        { requested -> contributions.requesting(requested) },
        NioDeviceWaitQueue(),
        ProcessHandleLiveness(),
    )
    private val trimGradleDaemons = TrimGradleDaemonsUseCase(listGradleDaemons, stopGradleDaemons, resolveResourceLimits)
    val resources = ResourcePreferences(resourceLimitsRepository, resolveResourceLimits, showsSimulators = appleSimulators.isSupported(), showsDerivedDataQuota = derivedDataSource.isSupported())
    private val startSimulator = StartWorktreeSimulatorUseCase(repositoryProvisioningSource, appleSimulators, acquireResource, resolveEnvironment)
    private val startEmulator = StartWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, acquireResource, androidSdk)
    private val stopEmulator = StopWorktreeEmulatorUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)
    private val readReleaseRecord = ReadReleaseRecordUseCase(resolveEnvironment, environmentStateStore)
    private val readEmulatorStates = ReadEmulatorStatesUseCase(repositoryProvisioningSource, resolveEnvironment, androidSdk)

    fun detectStacks(repositoryPath: String) = DetectRepositoryStacksUseCase(fileManager)(repositoryPath)

    suspend fun detectMobileSecrets(config: ProjectConfig) =
        DetectMobileSecretsUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource)(config)

    fun listLocalFolder(path: String): DirectoryListing? = if (fileManager.isDirectory(path)) fileManager.listTree(path) else null

    suspend fun loadAvds(repositoryPath: String): Result<List<String>> =
        runCatching { androidSdk.listAvds(androidSdk.locate(repositoryPath)) }

    suspend fun inspectRepository(folder: String): Result<RepositoryInspection> =
        ResolveRepositoryUseCase(shellCommandExecutor)(folder).map { topLevel ->
            val config = ProjectConfig(baseRepositoryPath = topLevel, worktreesRoot = "", secretFiles = emptyList())
            val mainBranch = ResolveMainBranchUseCase(config, shellCommandExecutor)().getOrNull()
            RepositoryInspection(topLevel, mainBranch?.name)
        }

    val detectRunningAgents = DetectRunningAgentsUseCase(shellCommandExecutor, herdrClient, agentSessionStore)

    private val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        WriteEnvironmentFileUseCase(shellCommandExecutor, fileManager, repositoryProvisioningSource),
    )

    val machineUseCases = MachineUseCases(
        listDaemons = { runCatching { gradleDaemonSource.list() } },
        listMavenDaemons = { runCatching { mavenDaemonSource.list() } },
        listComposeProjects = { config, worktrees, root ->
            ListComposeProjectsUseCase(composeProjectSource)(config.baseRepositoryPath, worktrees, root)
        },
        takeDownComposeProjects = { names -> TakeDownComposeProjectsUseCase(composeProjectSource)(names) },
        stopDaemons = { pids -> StopGradleDaemonsUseCase(gradleDaemonSource, shellCommandExecutor)(pids) },
        listDerivedData = { ListDerivedDataUseCase(derivedDataSource)(emptyList(), includeCompilationCache = false, measure = DerivedDataMeasure.NONE) },
        measureDerivedData = { folders, remeasure -> measureDerivedDataSizes(folders, remeasure) },
        measureCompilationCache = { runCatching { derivedDataSource.compilationCacheBytes() } },
        deleteOrphanedDerivedData = { DeleteOrphanedDerivedDataUseCase(derivedDataSource)() },
        readEmulatorStates = { config, worktrees -> readEmulatorStates(config, worktrees) },
        readRunningEmulators = { config ->
            runCatching { androidSdk.deviceStates(androidSdk.locate(config.baseRepositoryPath)) }
        },
        startEmulator = { config, worktreePath -> startEmulator(config, worktreePath) },
        readPool = { kind, config, worktrees ->
            runCatching { readPool.of(kind, config.baseRepositoryPath, worktrees) }
        },
        trimGradleDaemons = { worktrees -> trimGradleDaemons(worktrees) },
        resourceSettings = { resources.settings },
        stopEmulator = { config, worktreePath -> stopEmulator(config, worktreePath) },
        stopEmulatorSerials = { config, serials -> runCatching { stopEmulator.stopSerials(config, serials) } },
        readSimulatorStates = { config, worktrees -> readSimulatorStates(config, worktrees) },
        startSimulator = { config, worktreePath -> startSimulator(config, worktreePath) },
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
                TrimDerivedDataUseCase(derivedDataSource, ListDerivedDataUseCase(derivedDataSource))(quotaBytes, worktrees, active, measured = listing).getOrThrow()
            }
        },
        resolveDerivedDataQuota = { settings, usage -> ResolveDerivedDataQuotaUseCase(derivedDataSource)(settings, usage) },
    )

    val pathExists: (String) -> Boolean = fileManager::exists

    val repositoryApproval = DialogRepositoryApproval()

    private val multirepoSource = JsonMultirepoSource()
    private val resolveMultirepo = ResolveMultirepoUseCase(multirepoSource, shellCommandExecutor, fileManager)
    private val configureMultirepo = ConfigureMultirepoUseCase(multirepoSource, resolveMultirepo, projectRepository, shellCommandExecutor)

    fun knownMultirepos(): List<Multirepo> = resolveMultirepo.known()

    fun registerWorkspaceProject(multirepo: Multirepo): Boolean {
        val workspace = multirepo.workspaceRepository ?: return false
        val registration = runBlocking {
            RegisterProjectUseCase(projectRepository, ResolveRepositoryUseCase(shellCommandExecutor), workspaceOf = listOf(multirepo)::withWorkspaceAt)(workspace.path)
        }
        return registration.getOrNull() is ProjectRegistration.Added
    }

    fun multirepoNameOfWorkspace(topLevel: String): String? = resolveMultirepo.approved().withWorkspaceAt(topLevel)?.name

    val multirepoActions = MultirepoActions(
        createGroup = { members, taskId, branchType, fetch, onRepository ->
            CreateGroupUseCase(
                shellCommandExecutor,
                fileManager,
                repositoryProvisioningSource,
                prepareEnvironment,
                repositoryTrust,
                repositoryApproval,
                provisioningRecords,
            )(members, taskId, branchType, fetch, onRepository)
        },
        openAgent = { multirepo -> OpenMultirepoAgentInHerdrUseCase(herdrClient)(multirepo) },
        trust = { multirepo -> configureMultirepo.trust(multirepo) },
    )

    val multirepoSetup: MultirepoSetup = object : MultirepoSetup {
        override suspend fun inspect(folder: String): FolderInspection = withContext(Dispatchers.IO) {
            val enclosing = ResolveRepositoryUseCase(shellCommandExecutor)(folder).getOrNull()
            if (enclosing != null && !sameFolder(enclosing, folder)) return@withContext FolderInspection(enclosing, emptyList())
            val subfolders = File(folder).listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
                .map { it.absolutePath }
            FolderInspection(null, subfolders.filter { isRepository(it) })
        }

        override suspend fun isRepository(path: String): Boolean = withContext(Dispatchers.IO) {
            ResolveRepositoryUseCase(shellCommandExecutor)(path).getOrNull()?.let { sameFolder(it, path) } == true
        }

        override suspend fun belongsToRepository(path: String): Boolean = withContext(Dispatchers.IO) {
            ResolveRepositoryUseCase(shellCommandExecutor)(path).isSuccess
        }

        override suspend fun detectGroups(members: List<MultirepoMember>): List<WorktreeGroup> = withContext(Dispatchers.IO) {
            DetectGroupsUseCase(shellCommandExecutor)(members).groups
        }

        override fun projects(): List<Project> = projectRepository.list()

        override fun read(folder: String): Multirepo? =
            runCatching { multirepoSource.read(folder) }.getOrNull()?.let { resolveMultirepo.build(folder, it) }

        override suspend fun init(folder: String): Result<Multirepo> = withContext(Dispatchers.IO) { configureMultirepo.init(folder, null) }

        override suspend fun trust(multirepo: Multirepo): Result<Multirepo> = withContext(Dispatchers.IO) { configureMultirepo.trust(multirepo) }

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

        override fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig) = configureMultirepo.setAgent(multirepo, agent)

        override fun setWorktreesRoot(multirepo: Multirepo, folder: String) = configureMultirepo.setWorktreesRoot(multirepo, folder)

        override fun delete(multirepo: Multirepo) = configureMultirepo.delete(multirepo)
    }

    fun worktreeUseCases(config: ProjectConfig): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, shellCommandExecutor, fileManager),
        create = CreateWorktreeUseCase(
            config,
            shellCommandExecutor,
            fileManager,
            repositoryProvisioningSource,
            prepareEnvironment,
            repositoryTrust,
            repositoryApproval,
            provisioningRecords,
        ),
        createFromBranch = CreateWorktreeFromBranchUseCase(
            config,
            shellCommandExecutor,
            fileManager,
            repositoryProvisioningSource,
            prepareEnvironment,
            repositoryTrust,
            repositoryApproval,
            provisioningRecords,
        ),
        listBranches = ListBranchesUseCase(config, shellCommandExecutor),
        fetchRemote = FetchRemoteUseCase(config, shellCommandExecutor),
        resolveStartPoint = { ref -> ResolveStartPointUseCase(shellCommandExecutor)(config.baseRepositoryPath, ref) },
        remove = RemoveWorktreeUseCase(config, shellCommandExecutor, CloseHerdrWorkspacesUseCase(config, herdrClient), fileManager),
        deleteBranch = DeleteBranchUseCase(config, shellCommandExecutor),
        copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboardWriter),
        agents = AgentUseCases(config, agentChoices),
        repair = RepairWorktreesUseCase(config, shellCommandExecutor, fileManager),
        resolveMainBranch = ResolveMainBranchUseCase(config, shellCommandExecutor),
        readStatus = ReadWorktreeStatusUseCase(shellCommandExecutor, fileManager),
        cleanUp = CleanUpWorktreesUseCase(config, shellCommandExecutor, CloseHerdrWorkspacesUseCase(config, herdrClient), composeProjectSource, fileManager),
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
            readRelease = { worktreePath -> readReleaseRecord(worktreePath, ResourceKind.EMULATOR) },
        ),
        simulator = SimulatorUseCases(
            declared = { withContext(Dispatchers.IO) { simulatorSetup.isAvailable(config.baseRepositoryPath) } },
            readStates = { worktrees -> readSimulatorStates(config, worktrees) },
            start = { worktreePath -> startSimulator(config, worktreePath) },
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
            check = CheckProvisionedFilesUseCase(
                config, shellCommandExecutor, fileManager, repositoryProvisioningSource, provisioningRecords,
            )::invoke,
            provision = ProvisionWorktreeUseCase(
                config,
                shellCommandExecutor,
                fileManager,
                repositoryProvisioningSource,
                prepareEnvironment,
                repositoryTrust,
                repositoryApproval,
                provisioningRecords,
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
        maven = MavenUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.maven }.getOrNull()
                }
            },
        ),
        compose = ComposeUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.dockerCompose }.getOrNull()
                }
            },
        ),
        xcode = XcodeUseCases(
            provider = {
                withContext(Dispatchers.IO) {
                    runCatching { repositoryProvisioningSource.read(config.baseRepositoryPath).providers.xcode }.getOrNull()
                }
            },
            supported = derivedDataSource::isSupported,
            list = ListDerivedDataUseCase(derivedDataSource, derivedDataSizes),
            deleteOrphans = DeleteOrphanedDerivedDataUseCase(derivedDataSource),
        ),
    )
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
    val remove: RemoveWorktreeUseCase,
    val deleteBranch: DeleteBranchUseCase,
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
    val maven: MavenUseCases = MavenUseCases.NONE,
    val compose: ComposeUseCases = ComposeUseCases.NONE,
    val herdr: HerdrUseCases = HerdrUseCases.NONE,
    val tools: ToolUseCases = ToolUseCases.NONE,
    val provisioning: ProvisioningUseCases = ProvisioningUseCases.NONE,
    val readEnvironment: suspend (Worktree) -> Result<WorktreeEnvironment> = { Result.success(WorktreeEnvironment()) },
    val releaseResources: ReleaseResourcesUseCases = ReleaseResourcesUseCases.NONE,
    val agents: AgentUseCases = AgentUseCases.NONE,

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
    val readPool: suspend (ResourceKind, ProjectConfig, List<Worktree>?) -> Result<PoolUsage>,
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
    val listComposeProjects: suspend (ProjectConfig, List<Worktree>, String?) -> Result<List<WorktreeComposeProject>> =
        { _, _, _ -> Result.success(emptyList()) },
    val takeDownComposeProjects: suspend (List<String>) -> ComposeDownReport = { ComposeDownReport() },
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

data class GradleUseCases(
    val provider: suspend () -> GradleProvider?,
    val listDaemons: ListGradleDaemonsUseCase,
    val stopDaemons: StopGradleDaemonsUseCase,
    val isBuildCacheEnabled: suspend () -> Boolean,
)

data class MavenUseCases(val provider: suspend () -> MavenProvider?) {
    companion object {
        val NONE = MavenUseCases(provider = { null })
    }
}

data class ComposeUseCases(val provider: suspend () -> DockerComposeProvider?) {
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
