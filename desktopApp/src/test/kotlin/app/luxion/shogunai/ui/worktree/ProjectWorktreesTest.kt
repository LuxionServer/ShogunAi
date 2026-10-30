package app.luxion.shogunai.ui.worktree

import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.actions.paletteEntries
import app.luxion.shogunai.ui.worktree.inspector.environmentAsDotenv
import app.luxion.shogunai.ui.worktree.inspector.portsLabel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.getOrNull
import app.luxion.shogunai.HeadlessAppUseCases
import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.HeadlessAppStop
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.domain.usecase.HeadlessAppWorktrees
import app.luxion.shogunai.domain.usecase.StopHeadlessAppUseCase
import app.luxion.shogunai.domain.usecase.worktreeComposeProjectName
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.ComposeUseCases
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.WorktreeComposeState
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.ToolUseCases
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.shell.ToolsPreferences
import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.AgentUseCases
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.OrcaUseCases
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.ui.shell.AgentChoices
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.ProvisioningUseCases
import app.luxion.shogunai.ReleaseResourcesUseCases
import app.luxion.shogunai.domain.usecase.ReleasedResources
import app.luxion.shogunai.domain.usecase.WorktreeResourcePlan
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.usecase.ProvisionResult
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.withoutLocalTwins
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.CloseOrcaTerminalsUseCase
import app.luxion.shogunai.domain.usecase.OrcaOrigins
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.ResolveBaseBranchUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRecordedBasesUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Project
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.luxion.shogunai.domain.FakeMavenDaemonSource
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.usecase.ListMavenDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopMavenDaemonsUseCase
import app.luxion.shogunai.MavenUseCases

class ProjectWorktreesTest {

    private val dispatcher = StandardTestDispatcher()

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )
    private val worktreePath = "/home/dev/projects/TASK-123"
    private val worktree = Worktree(path = worktreePath, branch = "feature/TASK-123")

    private val listCommand = listOf("git", "worktree", "list", "--porcelain")
    private val removeCommand = listOf("git", "worktree", "remove", worktreePath)
    private val forceRemoveCommand = listOf("git", "worktree", "remove", "--force", worktreePath)
    private val repairCommandPrefix = listOf("git", "worktree", "repair")
    private val forEachRefCommand = listOf("git", "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes/origin/")

    private val porcelainOutput = """
        worktree $worktreePath
        HEAD abc123
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loadedProjectWorktrees(useCases: WorktreeUseCases): ProjectWorktrees =
        ProjectWorktrees(useCases, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), ioDispatcher = Dispatchers.Unconfined)
            .also { it.refresh() }

    private fun projectWorktrees(useCases: WorktreeUseCases, onUsed: (String) -> Unit = {}): ProjectWorktrees =
        ProjectWorktrees(useCases, CoroutineScope(SupervisorJob() + dispatcher), ioDispatcher = dispatcher, onUsed = onUsed)
            .also { it.refresh() }

    private val project = Project(id = "main-repo", name = "main-repo", config = config)

    private fun board(
        useCases: WorktreeUseCases,
        project: Project = this.project,
        rememberFetchBeforeCreate: (String, Boolean) -> Unit = { _, _ -> },
    ): WorktreeBoard = WorktreeBoard(
        useCasesFor = { useCases },
        detectAgents = { searches -> useCases.detectAgents(searches.flatMap { it.worktrees }) },
        preferences = FakeShellPreferenceRepository(),
        ioDispatcher = dispatcher,
        rememberFetchBeforeCreate = rememberFetchBeforeCreate,
    ).also {
        it.syncCatalog(listOf(project))
        it.show(null)
    }

    private val WorktreeBoard.project: ProjectWorktrees
        get() = holder(this@ProjectWorktreesTest.project.id)!!

    private fun WorktreeBoard.selectPath(path: String) = select(WorktreeRef(this@ProjectWorktreesTest.project.id, path))

    private fun useCases(responder: (List<String>) -> CommandResult): WorktreeUseCases =
        useCasesWith(FakeShellCommandExecutor(responder))

    private fun useCasesWithFiles(fileManager: FakeFileManager, responder: (List<String>) -> CommandResult): WorktreeUseCases =
        useCasesWith(FakeShellCommandExecutor(responder), fileManager)

    private fun useCasesWith(
        executor: ShellCommandExecutor,
        fileManager: FakeFileManager = FakeFileManager(),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        createFiles: FakeFileManager = FakeFileManager(),
        trust: RepositoryTrust = RepositoryTrust.NONE,
        approval: RepositoryApproval = RepositoryApproval.SKIP,
    ): WorktreeUseCases {
        return WorktreeUseCases(
            list = ListWorktreesUseCase(config, executor),
            create = CreateWorktreeUseCase(config, executor, createFiles, provisioning, trust = trust, approval = approval),
            createFromBranch = CreateWorktreeFromBranchUseCase(
                config,
                executor,
                FakeFileManager(
                    existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) },
                ),
                FakeRepositoryProvisioningSource(),
            ),
            listBranches = ListBranchesUseCase(config, executor),
            fetchRemote = FetchRemoteUseCase(config, executor),
            resolveStartPoint = { ref -> ResolveStartPointUseCase(executor)(config.baseRepositoryPath, ref) },
            resolveBaseBranch = { ref -> ResolveBaseBranchUseCase(executor)(config.baseRepositoryPath, ref) },
            remove = RemoveWorktreeUseCase(config, executor),
            deleteBranch = DeleteBranchUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false),
            repair = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath))),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            resolveRecordedBases = { ResolveRecordedBasesUseCase(config, executor)() },
            readStatus = ReadWorktreeStatusUseCase(executor, FakeFileManager()),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = { worktrees -> DetectRunningAgentsUseCase(executor)(config, worktrees) },
            measureSize = MeasureWorktreeSizeUseCase(fileManager, dispatcher),
            readFingerprint = ReadGitMetadataFingerprintUseCase(config, fileManager, dispatcher),
            readPorts = { worktree -> Result.success(portsByWorktree[worktree.path].orEmpty()) },
            emulator = EmulatorUseCases.NONE,
            simulator = SimulatorUseCases.NONE,
            gradle = gradleUseCases(executor),
            xcode = xcodeUseCases(FakeDerivedDataSource()),
        )
    }

    private var portsByWorktree: Map<String, Map<String, Int>> = emptyMap()
    private val clipboard = FakeClipboardWriter()

    private fun xcodeUseCases(source: DerivedDataSource, provider: XcodeProvider? = null) = XcodeUseCases(
        provider = { provider },
        supported = { source.isSupported() },
        list = ListDerivedDataUseCase(source),
        deleteOrphans = DeleteOrphanedDerivedDataUseCase(source),
    )

    private fun xcodeRepository(
        responder: (List<String>) -> CommandResult,
        source: DerivedDataSource = FakeDerivedDataSource(),
        provider: XcodeProvider = XcodeProvider(),
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        return useCasesWith(executor).copy(xcode = xcodeUseCases(source, provider))
    }

    private class CountingDerivedDataSource(val source: FakeDerivedDataSource) : DerivedDataSource by source {
        var listings = 0
        override suspend fun folders(): List<DerivedDataFolder> {
            listings++
            return source.folders()
        }
    }

    private class ThrowingDerivedDataSource(private val error: IOException) : DerivedDataSource {
        override fun isSupported(): Boolean = true
        override suspend fun folders(): List<DerivedDataFolder> = throw error
        override suspend fun compilationCacheBytes(): Long? = null
        override suspend fun freeBytes(): Long? = null
        override fun projectExists(workspacePath: String): Boolean = false
        override fun isOnUnavailableVolume(workspacePath: String): Boolean = false
        override suspend fun size(folder: DerivedDataFolder): Long = 0L
        override suspend fun packagesSize(folder: DerivedDataFolder): Long = 0L
        override suspend fun delete(folder: DerivedDataFolder) {}
    }

    private fun worktreeProject() = "$worktreePath/iosApp/App.xcodeproj"

    private fun gradleUseCases(
        executor: ShellCommandExecutor,
        source: FakeGradleDaemonSource = FakeGradleDaemonSource(),
        provider: GradleProvider? = null,
        buildCacheEnabled: Boolean = true,
    ) = GradleUseCases(
        provider = { provider },
        listDaemons = ListGradleDaemonsUseCase(source, FakeFileManager(existing = listOf(config.baseRepositoryPath, worktreePath))),
        stopDaemons = StopGradleDaemonsUseCase(source, executor),
        isBuildCacheEnabled = { buildCacheEnabled },
    )

    private fun gradleRepository(
        responder: (List<String>) -> CommandResult,
        source: FakeGradleDaemonSource,
        buildCacheEnabled: Boolean = true,
        provider: GradleProvider = GradleProvider(),
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        return useCasesWith(executor).copy(
            gradle = gradleUseCases(executor, source, provider = provider, buildCacheEnabled = buildCacheEnabled),
        )
    }

    private fun mavenRepository(
        responder: (List<String>) -> CommandResult,
        source: FakeMavenDaemonSource,
        declared: Boolean = true,
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        val fileManager = FakeFileManager(existing = listOf(config.baseRepositoryPath, worktreePath))
        return useCasesWith(executor).copy(
            maven = MavenUseCases(
                provider = { MavenProvider().takeIf { declared } },
                listDaemons = { worktrees -> ListMavenDaemonsUseCase(source, fileManager)(worktrees) },
                stopDaemons = { pids -> StopMavenDaemonsUseCase(source, executor)(pids) },
            ),
        )
    }

    private fun mavenDaemon(pid: Long, state: DaemonState, workingDirectory: String) =
        MavenDaemonProcess(pid, 100, workingDirectory, state)

    private fun existingBase() =
        FakeFileManager(existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) })

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))

    private fun listingResponder(executed: MutableList<List<String>> = mutableListOf()): (List<String>) -> CommandResult = { command ->
        executed += command
        if (command == listCommand) success(command, porcelainOutput) else success(command)
    }

    @Test
    fun `reloading the worktree list keeps the resolved environments`() = runTest(dispatcher) {
        var invalidations = 0
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(invalidateEnvironments = { invalidations++ }))

        holder.refresh()

        assertEquals(0, invalidations)
    }

    @Test
    fun `createFromBranch adds the worktree on success`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val branchWorktreePath = "/home/dev/projects/hotfix-security-patch"
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "show-ref" -> success(command)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.createFromBranch(branch)
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertEquals(WorktreeSuccess.Created(branchWorktreePath), viewModel.success)
        assertTrue(viewModel.worktrees.any { it.path == branchWorktreePath && it.branch == branch })
    }

    @Test
    fun `createFromBranch sets error when the branch doesn't exist`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "show-ref" -> failure(command, exitCode = 1)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.createFromBranch(branch)
        advanceUntilIdle()

        assertEquals(branch, assertIs<WorktreeError.BranchNotFound>(viewModel.createError).branch)
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.none { it.branch == branch })
    }

    @Test
    fun `opening the create dialog resets the form fields`() = runTest(dispatcher) {
        val board = board(useCases(listingResponder()))
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")
        board.selectedBranch = "hotfix/security-patch"
        board.dismissCreateDialog()

        board.openCreateDialog()

        assertTrue(board.isCreateDialogOpen)
        assertEquals("", board.taskId)
        assertNull(board.selectedBranch)
        assertNull(board.project.createError)
    }

    @Test
    fun `a failed creation keeps the dialog open with the task id and its error`() = runTest(dispatcher) {
        val board = board(useCases(listingResponder()))
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(board.isCreateDialogOpen)
        assertEquals("TASK-456", board.taskId)
        assertIs<WorktreeError.BaseRepositoryNotFound>(board.project.createError)
        assertNull(board.project.error)
        assertNull(board.project.success)
    }

    @Test
    fun `a failing post-creation command leaves the dialog unable to create again`() = runTest(dispatcher) {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                config.baseRepositoryPath to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"))),
            ),
        )
        val executor = FakeShellCommandExecutor { command ->
            when {
                command == listCommand -> success(command, porcelainOutput)
                command.first() == "npm" -> failure(command, exitCode = 1, stderr = "ENOTFOUND")
                else -> success(command)
            }
        }
        val baseFiles = FakeFileManager(existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) })
        val trust = FakeRepositoryTrust(mapOf(config.baseRepositoryPath to RepositoryApprovalSubject(listOf(listOf("npm", "install")))))
        val board = board(useCasesWith(executor, provisioning = provisioning, createFiles = baseFiles, trust = trust))
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        advanceUntilIdle()

        val error = assertIs<WorktreeError.PostCreateCommandFailed>(board.project.createError)
        assertEquals(error.worktreePath, board.project.createdWithErrorPath)
        assertTrue(board.isCreateDialogOpen)
        assertFalse(board.canCreate)
    }

    private fun approvalScenario(
        approval: DialogRepositoryApproval,
        trust: FakeRepositoryTrust,
        declared: RepositoryProvisioning = RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"))),
    ): Pair<WorktreeBoard, FakeShellCommandExecutor> {
        val provisioning = FakeRepositoryProvisioningSource(byRepository = mapOf(config.baseRepositoryPath to declared))
        val executor = FakeShellCommandExecutor { command ->
            if (command == listCommand) success(command, porcelainOutput) else success(command)
        }
        val baseFiles = FakeFileManager(existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) })
        val useCases = useCasesWith(executor, provisioning = provisioning, createFiles = baseFiles, trust = trust, approval = approval)
        val board = WorktreeBoard(
            useCasesFor = { useCases },
            detectAgents = { searches -> useCases.detectAgents(searches.flatMap { it.worktrees }) },
            preferences = FakeShellPreferenceRepository(),
            approval = approval,
            ioDispatcher = dispatcher,
        ).also {
            it.syncCatalog(listOf(project))
            it.show(null)
        }
        return board to executor
    }

    @Test
    fun `creation waits for the approval of unapproved commands and runs and remembers them on run`() = runTest(dispatcher) {
        val approval = DialogRepositoryApproval()
        val trust = FakeRepositoryTrust()
        val (board, executor) = approvalScenario(approval, trust)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        advanceUntilIdle()

        assertEquals(listOf(listOf("npm", "install")), board.approval.pending?.subject?.commands)
        assertTrue(board.project.isCreating)
        assertFalse(executor.executedCommands.any { it.first() == "npm" })

        board.approval.resolve(ApprovalDecision.APPROVE)
        advanceUntilIdle()

        assertNull(board.approval.pending)
        assertTrue(executor.executedCommands.any { it == listOf("npm", "install") })
        assertTrue(trust.isApproved(config.baseRepositoryPath, RepositoryApprovalSubject(listOf(listOf("npm", "install")))))
        assertEquals(WorktreeSuccess.Created("${config.worktreesRoot}/TASK-456", commandsSkipped = false), board.project.success)
    }

    @Test
    fun `skipping the approval creates the worktree without running the commands and says so`() = runTest(dispatcher) {
        val approval = DialogRepositoryApproval()
        val trust = FakeRepositoryTrust()
        val (board, executor) = approvalScenario(approval, trust)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")
        board.confirmCreate()
        advanceUntilIdle()

        board.approval.resolve(ApprovalDecision.SKIP)
        advanceUntilIdle()

        assertFalse(executor.executedCommands.any { it.first() == "npm" })
        assertFalse(trust.isApproved(config.baseRepositoryPath, RepositoryApprovalSubject(listOf(listOf("npm", "install")))))
        assertEquals(WorktreeSuccess.Created("${config.worktreesRoot}/TASK-456", commandsSkipped = true), board.project.success)
    }

    @Test
    fun `the approval shows declared variables and the environment file and skipping them says so`() = runTest(dispatcher) {
        val approval = DialogRepositoryApproval()
        val declared = RepositoryProvisioning(
            environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")), ".env.shogunai"),
        )
        val (board, _) = approvalScenario(approval, FakeRepositoryTrust(), declared)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")
        board.confirmCreate()
        advanceUntilIdle()

        val subject = board.approval.pending?.subject
        assertEquals(listOf("PORT" to "\${port}"), subject?.variables)
        assertEquals(".env.shogunai", subject?.envFile)

        board.approval.resolve(ApprovalDecision.SKIP)
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.Created("${config.worktreesRoot}/TASK-456", commandsSkipped = true), board.project.success)
    }

    @Test
    fun `approved commands are not asked again`() = runTest(dispatcher) {
        val approval = DialogRepositoryApproval()
        val trust = FakeRepositoryTrust(mapOf(config.baseRepositoryPath to RepositoryApprovalSubject(listOf(listOf("npm", "install")))))
        val (board, executor) = approvalScenario(approval, trust)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        advanceUntilIdle()

        assertNull(board.approval.pending)
        assertTrue(executor.executedCommands.any { it == listOf("npm", "install") })
    }

    @Test
    fun `a successful creation closes the dialog and remembers the new worktree`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val board = board(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command == forEachRefCommand -> success(command, stdout = "refs/heads/$branch\n")
                    command.getOrNull(1) == "show-ref" -> success(command)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateCreateMode(CreateMode.EXISTING_BRANCH)
        advanceUntilIdle()
        board.selectedBranch = branch

        board.confirmCreate()
        advanceUntilIdle()

        assertFalse(board.isCreateDialogOpen)
        assertEquals("/home/dev/projects/hotfix-security-patch", board.project.lastCreatedWorktree?.path)
        assertEquals(WorktreeSuccess.Created("/home/dev/projects/hotfix-security-patch"), board.project.success)
    }

    @Test
    fun `dismissing the create dialog is ignored while creating`() = runTest(dispatcher) {
        val board = board(useCases(listingResponder()))
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        board.dismissCreateDialog()

        assertTrue(board.project.isCreating)
        assertTrue(board.isCreateDialogOpen)
        advanceUntilIdle()
    }

    @Test
    fun `confirmCreate does nothing with an invalid task id`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val board = board(useCases(listingResponder(executed)))
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("bad..id")

        board.confirmCreate()
        advanceUntilIdle()

        assertFalse(board.project.isCreating)
        assertNull(board.project.createError)
        assertTrue(executed.none { it.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `copying from the creation message copies the new worktree's launch command`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "show-ref" -> success(command)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.createFromBranch(branch)
        advanceUntilIdle()

        viewModel.copyLastCreatedLaunchCommand()
        advanceUntilIdle()

        assertEquals(1, clipboard.writtenText.size)
        assertTrue(clipboard.writtenText.single().startsWith("cd '/home/dev/projects/hotfix-security-patch' && "))
    }

    @Test
    fun `dismissError clears the error message`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command.take(3) == repairCommandPrefix -> failure(command, exitCode = 128, stderr = "permission denied")
                    command == listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.repairAfterMove(previousWorktreesRoot = "/home/dev/old-projects")
        advanceUntilIdle()
        assertIs<WorktreeError>(viewModel.error)

        viewModel.dismissError()

        assertNull(viewModel.error)
    }

    private fun unmergedBranchResponder(executed: MutableList<List<String>> = mutableListOf()): (List<String>) -> CommandResult = { command ->
        executed += command
        when {
            command == listCommand -> success(command, porcelainOutput)
            command.getOrNull(1) == "symbolic-ref" -> success(command, stdout = "origin/main\n")
            command.take(2) == listOf("git", "merge-base") -> failure(command, exitCode = 1, stderr = "")
            else -> success(command)
        }
    }

    @Test
    fun `removal that keeps an unmerged branch still closes the dialog and keeps a notice`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(useCases(unmergedBranchResponder(executed)))
        advanceUntilIdle()

        viewModel.remove(worktree, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertNull(viewModel.removeError)
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertEquals(ProjectWorktrees.KeptBranchNotice("feature/TASK-123"), viewModel.keptBranchNotice)
        assertTrue(executed.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `confirming the forced deletion deletes the kept branch`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(useCases(unmergedBranchResponder(executed)))
        advanceUntilIdle()
        viewModel.remove(worktree, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()

        viewModel.requestForcedBranchDeletion()
        assertTrue(viewModel.isConfirmingForcedBranchDeletion)
        viewModel.confirmForcedBranchDeletion()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("git", "branch", "-D", "--", "feature/TASK-123")))
        assertNull(viewModel.keptBranchNotice)
        assertFalse(viewModel.isConfirmingForcedBranchDeletion)
        assertEquals(WorktreeSuccess.BranchDeleted("feature/TASK-123"), viewModel.success)
    }

    @Test
    fun `cancelling the forced deletion keeps the branch and the notice`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(useCases(unmergedBranchResponder(executed)))
        advanceUntilIdle()
        viewModel.remove(worktree, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()

        viewModel.requestForcedBranchDeletion()
        viewModel.cancelForcedBranchDeletion()
        advanceUntilIdle()

        assertFalse(viewModel.isConfirmingForcedBranchDeletion)
        assertNotNull(viewModel.keptBranchNotice)
        assertTrue(executed.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `dismissing the notice leaves the branch`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(useCases(unmergedBranchResponder(executed)))
        advanceUntilIdle()
        viewModel.remove(worktree, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()

        viewModel.dismissKeptBranchNotice()

        assertNull(viewModel.keptBranchNotice)
        assertTrue(executed.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `sets pending removal in force state when remove fails suggesting force`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(
                        command,
                        stderr = "fatal: '$worktreePath' contains modified or untracked files, use --force to delete it",
                    )
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        assertEquals(worktree, viewModel.worktreePendingRemoval)
        assertTrue(viewModel.pendingRemovalRequiresForce)
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `a locked worktree offers the force retry with its lock reason and removes it forcing twice`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val doublyForced = listOf("git", "worktree", "remove", "--force", "--force", worktreePath)
        val viewModel = projectWorktrees(
            useCases { command ->
                executed += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand, forceRemoveCommand -> failure(
                        command,
                        exitCode = 128,
                        stderr = "fatal: cannot remove a locked working tree, lock reason: orca-create-preparation:v1\n" +
                            "use 'remove -f -f' to override or unlock first",
                    )
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        assertTrue(viewModel.pendingRemovalRequiresForce)
        assertEquals("orca-create-preparation:v1", viewModel.pendingRemovalLock?.reason)
        assertNull(viewModel.removeError)

        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertTrue(doublyForced in executed)
        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
    }

    @Test
    fun `a failure suggesting force carries no lock`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "use --force to delete it")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        assertTrue(viewModel.pendingRemovalRequiresForce)
        assertNull(viewModel.pendingRemovalLock)
    }

    @Test
    fun `a removal failing for a reason unrelated to force stays in the dialog with its error`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "fatal: '$worktreePath' is not a working tree")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.requestRemoval(worktree)

        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertEquals(worktree, viewModel.worktreePendingRemoval)
        val error = assertIs<WorktreeError.GitCommandFailed>(viewModel.removeError)
        assertTrue(error.errorOutput.contains("is not a working tree"))
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `dismissing the removal dialog is ignored while removing and clears the error afterwards`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = projectWorktrees(
            useCasesWith(
                object : ShellCommandExecutor {
                    override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                        if (command != removeCommand) return listingResponder()(command)
                        gate.await()
                        return failure(command, stderr = "fatal: locked")
                    }
                },
            ),
        )
        advanceUntilIdle()
        viewModel.requestRemoval(worktree)
        viewModel.confirmPendingRemoval()
        runCurrent()

        viewModel.dismissPendingRemoval()

        assertTrue(viewModel.isRemoving)
        assertEquals(worktree, viewModel.worktreePendingRemoval)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.removeError != null)
        viewModel.dismissPendingRemoval()
        assertNull(viewModel.worktreePendingRemoval)
        assertNull(viewModel.removeError)
    }

    @Test
    fun `a refresh during a slow creation is skipped and the created worktree stays once`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val created = "/home/dev/projects/TASK-456"
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCasesWith(
                object : ShellCommandExecutor {
                    override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                        executed += command
                        if (command.take(3) == listOf("git", "worktree", "add")) gate.await()
                        return if (command == listCommand) success(command, porcelainOutput) else success(command)
                    }
                },
                createFiles = existingBase(),
            ),
        )
        advanceUntilIdle()
        val listingsBefore = executed.count { it == listCommand }

        viewModel.create("TASK-456", BranchType.FEATURE)
        runCurrent()
        viewModel.refresh()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listingsBefore, executed.count { it == listCommand })
        assertEquals(1, viewModel.worktrees.count { it.path == created })
    }

    @Test
    fun `a created worktree already listed by a refresh is not duplicated`() = runTest(dispatcher) {
        val created = "/home/dev/projects/TASK-456"
        val listing = """
            $porcelainOutput

            worktree $created
            HEAD abc123
            branch refs/heads/feature/TASK-456
        """.trimIndent()
        val viewModel = projectWorktrees(
            useCasesWith(
                FakeShellCommandExecutor { command -> if (command == listCommand) success(command, listing) else success(command) },
                createFiles = existingBase(),
            ),
        )
        advanceUntilIdle()

        viewModel.create("TASK-456", BranchType.FEATURE)
        advanceUntilIdle()

        assertEquals(1, viewModel.worktrees.count { it.path == created })
    }

    @Test
    fun `a failure listing branches is kept apart from the view error and retried`() = runTest(dispatcher) {
        var failing = true
        val viewModel = projectWorktrees(useCases { command ->
            if (command.getOrNull(1) == "for-each-ref" && failing) failure(command, stderr = "fatal: not a git repository") else listingResponder()(command)
        })
        advanceUntilIdle()

        viewModel.loadBranches()
        advanceUntilIdle()

        assertTrue(viewModel.branchesError != null)
        assertNull(viewModel.error)
        failing = false
        viewModel.loadBranches()
        advanceUntilIdle()
        assertNull(viewModel.branchesError)
    }

    @Test
    fun `confirmPendingRemoval retries with force and clears pending state on success`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "use --force to delete it")
                    forceRemoveCommand -> success(command)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
    }

    @Test
    fun `confirmPendingRemoval keeps the branch when the checkbox is unchecked`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCases { command ->
                executedCommands += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.requestRemoval(worktree)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertTrue(executedCommands.none { it == listOf("git", "branch", "-D", "--", worktree.branch) })
    }

    @Test
    fun `confirmPendingRemoval deletes the branch when the checkbox is checked`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCases { command ->
                executedCommands += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    else -> if (command.getOrNull(1) == "symbolic-ref") success(command, stdout = "origin/main\n") else success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.requestRemoval(worktree)
        viewModel.setPendingRemovalDeleteBranch(true)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-D", "--", worktree.branch) })
    }

    @Test
    fun `force-eligible failure preserves the chosen deleteBranch value`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCases { command ->
                executedCommands += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "use --force to delete it")
                    forceRemoveCommand -> success(command)
                    else -> if (command.getOrNull(1) == "symbolic-ref") success(command, stdout = "origin/main\n") else success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.requestRemoval(worktree)
        viewModel.setPendingRemovalDeleteBranch(true)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertTrue(viewModel.pendingRemovalRequiresForce)
        assertTrue(viewModel.pendingRemovalDeleteBranch)

        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-D", "--", worktree.branch) })
    }

    @Test
    fun `refresh re-invokes ListWorktreesUseCase and replaces worktrees with the new result`() = runTest(dispatcher) {
        var listCallCount = 0
        val otherWorktreePath = "/home/dev/projects/TASK-456"
        val otherPorcelainOutput = """
            worktree $otherWorktreePath
            HEAD def456
            branch refs/heads/feature/TASK-456
        """.trimIndent()
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> {
                        listCallCount++
                        if (listCallCount == 1) success(command, porcelainOutput) else success(command, otherPorcelainOutput)
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, listCallCount)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertTrue(viewModel.worktrees.any { it.path == otherWorktreePath })
    }

    @Test
    fun `a failed list is kept as the project's load failure`() = runTest(dispatcher) {
        var listCallCount = 0
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> {
                        listCallCount++
                        if (listCallCount == 1) success(command, porcelainOutput) else failure(command, stderr = "not a git repository")
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        assertEquals(ProjectLoadState.Loaded, viewModel.loadState)

        viewModel.refresh()
        advanceUntilIdle()

        val failed = assertIs<ProjectLoadState.Failed>(viewModel.loadState)
        val error = assertIs<WorktreeError.GitCommandFailed>(failed.error)
        assertTrue(error.errorOutput.contains("not a git repository"))
        assertNull(viewModel.error)
    }

    @Test
    fun `refresh also reloads local branches when create mode is existing branch`() = runTest(dispatcher) {
        var forEachRefCallCount = 0
        val board = board(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> {
                        forEachRefCallCount++
                        success(command, stdout = "refs/heads/feature/TASK-123\nrefs/heads/hotfix/security-patch\n")
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateCreateMode(CreateMode.EXISTING_BRANCH)
        advanceUntilIdle()
        assertEquals(1, forEachRefCallCount)

        board.refreshShown()
        advanceUntilIdle()

        assertEquals(2, forEachRefCallCount)
        assertEquals(
            listOf(
                BranchOption("feature/TASK-123", isCheckedOut = true),
                BranchOption("hotfix/security-patch", isCheckedOut = false),
            ),
            board.project.branches,
        )
    }

    @Test
    fun `refresh reloads branches while the create dialog is open in new branch mode`() = runTest(dispatcher) {
        var forEachRefCallCount = 0
        val board = board(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> {
                        forEachRefCallCount++
                        success(command, stdout = "refs/heads/hotfix/security-patch\n")
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        board.openCreateDialog()
        assertEquals(CreateMode.NEW_BRANCH, board.createMode)
        advanceUntilIdle()
        assertEquals(1, forEachRefCallCount)

        board.refreshShown()
        advanceUntilIdle()

        assertEquals(2, forEachRefCallCount)
    }

    private val addPrefix = listOf("git", "worktree", "add", "--no-track", "/home/dev/projects/TASK-456", "-b", "feature/TASK-456")

    private fun startPointScenario(
        remoteBranches: String = "",
        fetchResult: (List<String>) -> CommandResult = { success(it) },
        hasOrigin: Boolean = true,
        mainBranch: String? = "develop",
        projectConfig: ProjectConfig = config,
    ): Pair<WorktreeBoard, FakeShellCommandExecutor> {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command == listCommand -> success(command, "worktree ${config.baseRepositoryPath}\nHEAD abc123\nbranch refs/heads/develop\n\n$porcelainOutput")
                command.take(2) == listOf("git", "show-ref") ->
                    if (command.last().startsWith("refs/remotes/origin/")) success(command) else failure(command)
                command == forEachRefCommand ->
                    success(command, stdout = "refs/heads/develop\nrefs/heads/feature/TASK-123\n$remoteBranches")
                command.take(2) == listOf("git", "symbolic-ref") ->
                    mainBranch?.let { success(command, stdout = "origin/$it\n") } ?: failure(command)
                command.take(2) == listOf("git", "rev-parse") && command.last() == "refs/remotes/origin/${mainBranch ?: "develop"}" -> success(command)
                command.take(2) == listOf("git", "rev-parse") && command.last() == "origin/develop^{commit}" ->
                    success(command, stdout = "fetched1\n")
                command.take(2) == listOf("git", "rev-parse") && command.last() == "develop^{commit}" ->
                    success(command, stdout = "local11\n")
                "--symbolic-full-name" in command && command.last() == "origin/develop" -> success(command, stdout = "refs/remotes/origin/develop\n")
                "--symbolic-full-name" in command && command.last() == "HEAD" -> success(command, stdout = "refs/heads/develop\n")
                command.take(2) == listOf("git", "rev-parse") -> failure(command)
                command.take(3) == listOf("git", "remote", "get-url") ->
                    if (hasOrigin) success(command, stdout = "git@example.com:team/repo.git") else failure(command, exitCode = 2)
                command.take(2) == listOf("git", "fetch") -> fetchResult(command)
                else -> success(command)
            }
        }
        val useCases = useCasesWith(executor, createFiles = existingBase())
        return board(useCases, project.copy(config = projectConfig)) to executor
    }

    @Test
    fun `the new branch starts at the repository's current branch by default, as shogun new does`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario(mainBranch = "main")
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")

        assertEquals(StartOption.Current, board.effectiveStartFrom)
        assertEquals(listOf(StartOption.Current, StartOption.Branch("origin/main")), board.startOptions.take(2))

        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(executor.executed("git", "worktree", "add", "/home/dev/projects/TASK-456", "-b", "feature/TASK-456"), executor.executedCommands.toString())
        assertTrue(executor.executed("git", "config", "branch.feature/TASK-456.gh-merge-base", "develop"), executor.executedCommands.toString())
    }

    @Test
    fun `choosing the main branch starts the new branch at its commit`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario()
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = board.startOptions[1]
        assertEquals(StartOption.Branch("origin/develop"), board.effectiveStartFrom)

        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(executor.executed(*(addPrefix + "fetched1").toTypedArray()), executor.executedCommands.toString())
        assertTrue(executor.executed("git", "config", "branch.feature/TASK-456.gh-merge-base", "develop"), executor.executedCommands.toString())
    }

    @Test
    fun `starting from the repository's current branch adds no start point`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario()
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = StartOption.Current

        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(executor.executed("git", "worktree", "add", "/home/dev/projects/TASK-456", "-b", "feature/TASK-456"), executor.executedCommands.toString())
        assertTrue(executor.executed("git", "rev-parse", "--verify", "--quiet", "--symbolic-full-name", "HEAD"))
        assertTrue(executor.executed("git", "config", "branch.feature/TASK-456.gh-merge-base", "develop"), executor.executedCommands.toString())
    }

    @Test
    fun `without a main branch the current branch is the default`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario(mainBranch = null)
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()

        assertEquals(StartOption.Current, board.effectiveStartFrom)
        assertEquals(StartOption.Current, board.startOptions.first())
        assertEquals("develop", board.currentBranchName)
    }

    @Test
    fun `a start point that disappeared creates nothing and reports it`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario()
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = StartOption.Branch("origin/deleted")

        board.confirmCreate()
        advanceUntilIdle()

        val error = assertIs<WorktreeError.StartPointNotFound>(board.project.createError)
        assertEquals("origin/deleted", error.ref)
        assertTrue(board.isCreateDialogOpen)
        assertFalse(executor.executedCommands.any { it.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `fetching first runs before the start point is resolved`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario()
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = StartOption.Branch("origin/develop")
        board.fetchFirst = true

        board.confirmCreate()
        advanceUntilIdle()

        val commands = executor.executedCommands
        val fetch = commands.indexOf(listOf("git", "fetch", "--prune", "origin"))
        val resolve = commands.indexOf(listOf("git", "rev-parse", "--verify", "--quiet", "origin/develop^{commit}"))
        assertTrue(fetch in 0 until resolve, commands.toString())
        assertTrue(executor.executed(*(addPrefix + "fetched1").toTypedArray()))
    }

    @Test
    fun `a failed fetch creates nothing until the user retries or skips it`() = runTest(dispatcher) {
        var fetchSucceeds = false
        val (board, executor) = startPointScenario(
            fetchResult = { if (fetchSucceeds) success(it) else failure(it, exitCode = 128, stderr = "Could not resolve host") },
        )
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = StartOption.Branch("origin/develop")
        board.fetchFirst = true

        board.confirmCreate()
        advanceUntilIdle()

        val fetchError = assertIs<WorktreeError.GitCommandFailed>(board.project.fetchError)
        assertEquals("Could not resolve host", fetchError.errorOutput)
        assertTrue(board.isCreateDialogOpen)
        assertFalse(board.project.isCreating)
        assertFalse(executor.executedCommands.any { it.take(3) == listOf("git", "worktree", "add") })

        fetchSucceeds = true
        board.confirmCreate()
        advanceUntilIdle()

        assertNull(board.project.fetchError)
        assertFalse(board.isCreateDialogOpen)
        assertTrue(executor.executed(*(addPrefix + "fetched1").toTypedArray()))
    }

    @Test
    fun `creating without fetching skips a failing fetch`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario(fetchResult = { failure(it, exitCode = 128) })
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")
        board.startFrom = StartOption.Branch("origin/develop")
        board.fetchFirst = true
        board.confirmCreate()
        advanceUntilIdle()

        board.confirmCreate(skipFetch = true)
        advanceUntilIdle()

        assertNull(board.project.fetchError)
        assertFalse(board.isCreateDialogOpen)
        assertEquals(1, executor.executedCommands.count { it.take(2) == listOf("git", "fetch") })
        assertTrue(executor.executed(*(addPrefix + "local11").toTypedArray()).not())
        assertTrue(executor.executed(*(addPrefix + "fetched1").toTypedArray()))
    }

    @Test
    fun `the fetch option starts from the project's remembered value and is remembered when it changes`() = runTest(dispatcher) {
        val remembered = mutableListOf<Pair<String, Boolean>>()
        val executor = FakeShellCommandExecutor { command ->
            if (command == listCommand) success(command, porcelainOutput) else success(command)
        }
        val board = board(
            useCasesWith(executor, createFiles = existingBase()),
            rememberFetchBeforeCreate = { id, fetch -> remembered += id to fetch },
        )
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        assertFalse(board.fetchFirst)
        board.updateTaskId("TASK-456")
        board.fetchFirst = true

        board.confirmCreate()
        advanceUntilIdle()

        assertEquals(listOf(project.id to true), remembered)
    }

    @Test
    fun `the dialog opens with the fetch option on when the project remembers it`() = runTest(dispatcher) {
        val (board, _) = startPointScenario(projectConfig = config.copy(fetchBeforeCreate = true))
        advanceUntilIdle()

        board.openCreateDialog()

        assertTrue(board.fetchFirst)
    }

    @Test
    fun `a repository without origin offers no fetch`() = runTest(dispatcher) {
        val (board, _) = startPointScenario(hasOrigin = false)
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()

        assertFalse(board.canFetchFirst)
    }

    @Test
    fun `remote only branches are listed once and create a local branch tracking them`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario(
            remoteBranches = "refs/remotes/origin/HEAD\nrefs/remotes/origin/develop\nrefs/remotes/origin/feature/TASK-9\n",
        )
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateCreateMode(CreateMode.EXISTING_BRANCH)
        advanceUntilIdle()

        val listed = board.project.branches.withoutLocalTwins().filter { it.isRemote }
        assertEquals(listOf("origin/feature/TASK-9"), listed.map { it.name })

        board.selectedBranch = "origin/feature/TASK-9"
        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(
            executor.executed(
                "git", "worktree", "add", "--track", "-b", "feature/TASK-9", "/home/dev/projects/TASK-9", "origin/feature/TASK-9",
            ),
            executor.executedCommands.toString(),
        )
    }

    @Test
    fun `updateTaskId normalizes whitespace to a single dash and trims`() = runTest(dispatcher) {
        val board = board(useCases { command -> success(command) })
        advanceUntilIdle()

        board.updateTaskId("  TASK   123  ")

        assertEquals("TASK-123", board.taskId)
    }

    @Test
    fun `loadBranches populates localBranches flagging already checked out branches`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> success(command, stdout = "refs/heads/feature/TASK-123\nrefs/heads/hotfix/security-patch\n")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.loadBranches()
        advanceUntilIdle()

        assertEquals(
            listOf(
                BranchOption("feature/TASK-123", isCheckedOut = true),
                BranchOption("hotfix/security-patch", isCheckedOut = false),
            ),
            viewModel.branches,
        )
        assertEquals(false, viewModel.isLoadingBranches)
    }

    @Test
    fun `dismissPendingRemoval clears pending state without executing another command`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCases { command ->
                executedCommands += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "use --force to delete it")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()
        val commandCountBeforeDismiss = executedCommands.size

        viewModel.dismissPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertEquals(commandCountBeforeDismiss, executedCommands.size)
    }

    @Test
    fun `dismissPendingRemoval cancels before any command runs`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(
            useCases { command ->
                executedCommands += command
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        val commandCountBeforeRequest = executedCommands.size

        viewModel.requestRemoval(worktree)
        viewModel.dismissPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertEquals(commandCountBeforeRequest, executedCommands.size)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `repairAfterMove keeps the repair failure visible after reloading the list`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command.take(3) == repairCommandPrefix -> failure(command, exitCode = 128, stderr = "fatal: not a git repository")
                    command == listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )

        viewModel.repairAfterMove(previousWorktreesRoot = "/home/dev/old-projects")
        advanceUntilIdle()

        val error = assertIs<WorktreeError.GitCommandFailed>(viewModel.error)
        assertEquals(128, error.exitCode)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
        assertNull(viewModel.success)
        assertEquals(false, viewModel.isLoading)
    }

    @Test
    fun `repairAfterMove reports success and lists the worktrees`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )

        viewModel.repairAfterMove(previousWorktreesRoot = "/home/dev/old-projects")
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.RepairedAfterMove, viewModel.success)
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `isSelectedBranchAvailable is true only for a loaded branch without a worktree`() = runTest(dispatcher) {
        val board = board(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> success(command, stdout = "refs/heads/develop\nrefs/heads/feature/TASK-123\n")
                    else -> success(command)
                }
            },
        )
        board.project.loadBranches()
        advanceUntilIdle()

        board.selectedBranch = "develop"
        assertTrue(board.project.isBranchAvailable(board.selectedBranch))

        board.selectedBranch = "feature/TASK-123"
        assertFalse(board.project.isBranchAvailable(board.selectedBranch))

        board.selectedBranch = "missing"
        assertFalse(board.project.isBranchAvailable(board.selectedBranch))

        board.selectedBranch = null
        assertFalse(board.project.isBranchAvailable(board.selectedBranch))
    }

    private val statusCommand = listOf("git", "--no-optional-locks", "status", "--porcelain")

    @Test
    fun `statuses are read for every listed worktree`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    statusCommand -> success(command, stdout = " M build.gradle.kts\n?? notes.md\n")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        val state = assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath])
        assertEquals(2, state.status.uncommittedChanges)
    }

    @Test
    fun `the list is shown while statuses are still loading`() = runTest(dispatcher) {
        val statusGate = CompletableDeferred<Unit>()
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                if (command == statusCommand) statusGate.await()
                return if (command == listCommand) success(command, porcelainOutput) else success(command)
            }
        }
        val viewModel = projectWorktrees(useCasesWith(executor))
        advanceUntilIdle()

        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
        assertEquals(WorktreeStatusState.Loading, viewModel.statuses[worktreePath])

        statusGate.complete(Unit)
        advanceUntilIdle()

        assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath])
    }

    @Test
    fun `a worktree whose status can't be read is unavailable without failing the list`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    statusCommand -> failure(command, exitCode = 128, stderr = "fatal: cannot change to directory")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        assertEquals(WorktreeStatusState.Unavailable, viewModel.statuses[worktreePath])
        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `refresh reads the statuses again`() = runTest(dispatcher) {
        var statusReads = 0
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    statusCommand -> {
                        statusReads++
                        success(command)
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, statusReads)
    }

    @Test
    fun `a reload keeps the previous statuses until they are read again and new worktrees start loading`() = runTest(dispatcher) {
        val otherPath = "/home/dev/projects/TASK-456"
        var porcelain = porcelainOutput
        var statusGate: CompletableDeferred<Unit>? = null
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                if (command == statusCommand) statusGate?.await()
                return when (command) {
                    listCommand -> success(command, porcelain)
                    statusCommand -> success(command, stdout = " M build.gradle.kts\n")
                    else -> success(command)
                }
            }
        }
        val viewModel = projectWorktrees(useCasesWith(executor))
        advanceUntilIdle()
        val previous = assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath])

        statusGate = CompletableDeferred()
        porcelain = "$porcelainOutput\n\nworktree $otherPath\nHEAD def456\nbranch refs/heads/feature/TASK-456"
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(previous, viewModel.statuses[worktreePath])
        assertEquals(WorktreeStatusState.Loading, viewModel.statuses[otherPath])

        statusGate.complete(Unit)
        advanceUntilIdle()

        assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[otherPath])
    }

    @Test
    fun `a reload drops the statuses and sizes of worktrees no longer listed`() = runTest(dispatcher) {
        val otherPath = "/home/dev/projects/TASK-456"
        val fileManager = FakeFileManager(
            existing = listOf(worktreePath, otherPath),
            sizes = mapOf(worktreePath to 1000L, otherPath to 500L),
        )
        var porcelain = "$porcelainOutput\n\nworktree $otherPath\nHEAD def456\nbranch refs/heads/feature/TASK-456"
        val viewModel = projectWorktrees(
            useCasesWithFiles(fileManager) { command ->
                if (command == listCommand) success(command, porcelain) else success(command)
            },
        )
        advanceUntilIdle()

        porcelain = porcelainOutput
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(setOf(worktreePath), viewModel.statuses.keys)
        assertEquals(mapOf(worktreePath to 1000L), viewModel.sizes)
    }

    @Test
    fun `refreshStatuses reads the statuses again without listing the worktrees`() = runTest(dispatcher) {
        var listCalls = 0
        var statusOutput = ""
        var statusGate: CompletableDeferred<Unit>? = null
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                if (command == statusCommand) statusGate?.await()
                return when (command) {
                    listCommand -> {
                        listCalls++
                        success(command, porcelainOutput)
                    }
                    statusCommand -> success(command, stdout = statusOutput)
                    else -> success(command)
                }
            }
        }
        val viewModel = projectWorktrees(useCasesWith(executor))
        advanceUntilIdle()

        statusGate = CompletableDeferred()
        statusOutput = " M build.gradle.kts\n"
        viewModel.refreshStatuses()
        advanceUntilIdle()

        assertEquals(0, assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath]).status.uncommittedChanges)

        statusGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, listCalls)
        assertEquals(1, assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath]).status.uncommittedChanges)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `a created worktree gets its status without reloading the list`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        var listCalls = 0
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command == listCommand -> {
                        listCalls++
                        success(command, porcelainOutput)
                    }
                    command.getOrNull(1) == "show-ref" -> success(command)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.createFromBranch(branch)
        advanceUntilIdle()

        assertEquals(1, listCalls)
        assertIs<WorktreeStatusState.Loaded>(viewModel.statuses["/home/dev/projects/hotfix-security-patch"])
    }

    private val mergedPorcelain = """
        worktree ${config.baseRepositoryPath}
        HEAD aaa000
        branch refs/heads/main

        worktree $worktreePath
        HEAD abc123
        branch refs/heads/feature/TASK-123

        worktree /home/dev/projects/TASK-456
        HEAD def456
        branch refs/heads/feature/TASK-456
        locked agent session
    """.trimIndent()

    private fun mergedResponder(
        executed: MutableList<List<String>> = mutableListOf(),
        removeResult: (List<String>) -> CommandResult = { success(it) },
    ): (List<String>) -> CommandResult = { command ->
        executed += command
        when {
            command == listCommand -> success(command, mergedPorcelain)
            command.getOrNull(1) == "symbolic-ref" -> success(command, stdout = "origin/main\n")
            command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD") -> success(command, stdout = "2\t0\n")
            command.getOrNull(1) == "rev-list" -> failure(command, stderr = "no upstream")
            command.take(3) == listOf("git", "worktree", "remove") -> removeResult(command)
            else -> success(command)
        }
    }

    @Test
    fun `merged clean unlocked worktrees are cleanup candidates`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(mergedResponder()))
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), viewModel.cleanupCandidates.map { it.path })
    }

    private fun agentRunningResponder(executed: MutableList<List<String>> = mutableListOf()): (List<String>) -> CommandResult {
        val base = mergedResponder(executed)
        return { command ->
            when (command.first()) {
                "lsof" -> success(command, stdout = "p42\nfcwd\nn$worktreePath\n")
                "ps" -> success(command, stdout = "42 /usr/local/bin/claude\n")
                else -> base(command)
            }
        }
    }

    @Test
    fun `a merged worktree with a running agent is kept out of the cleanup and says which agent`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(agentRunningResponder()))
        advanceUntilIdle()

        assertTrue(viewModel.cleanupCandidates.isEmpty())
        assertEquals(
            listOf(worktreePath to listOf(RunningAgent("claude"))),
            viewModel.cleanupKeptForRunningAgent.map { (worktree, agents) -> worktree.path to agents },
        )
    }

    @Test
    fun `cleaning up never removes a worktree where an agent is running`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = projectWorktrees(useCases(agentRunningResponder(executed)))
        advanceUntilIdle()

        viewModel.cleanUp(CleanupOptions())
        advanceUntilIdle()

        assertTrue(executed.none { it.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `cancelling the cleanup dialog runs nothing`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val board = board(useCases(mergedResponder(executed)))
        advanceUntilIdle()
        val before = executed.size

        board.openCleanup()
        assertTrue(board.isCleanupDialogOpen)
        assertFalse(board.cleanupDeleteBranches)
        board.dismissCleanup()
        advanceUntilIdle()

        assertFalse(board.isCleanupDialogOpen)
        assertEquals(before, executed.size)
    }

    @Test
    fun `confirming the cleanup removes candidates, reports and reloads`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val board = board(useCases(mergedResponder(executed)))
        advanceUntilIdle()
        val listsBefore = executed.count { it == listCommand }

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("git", "worktree", "remove", worktreePath)))
        assertTrue(executed.none { it == listOf("git", "worktree", "remove", "/home/dev/projects/TASK-456") })
        assertTrue(executed.none { it.take(3) == listOf("git", "branch", "-D") })
        assertEquals(WorktreeSuccess.CleanedUp(1), board.cleanupSuccess)
        assertNull(board.project.cleanupIssues)
        assertFalse(board.isCleanupDialogOpen)
        assertEquals(listsBefore + 1, executed.count { it == listCommand })
    }

    @Test
    fun `confirming with branch deletion deletes the merged branch`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val board = board(useCases(mergedResponder(executed)))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupDeleteBranches = true
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("git", "branch", "-D", "--", "feature/TASK-123")))
    }

    @Test
    fun `a failed removal is kept as a cleanup issue`() = runTest(dispatcher) {
        val board = board(
            useCases(mergedResponder(removeResult = { failure(it, exitCode = 128, stderr = "use --force to delete it") })),
        )
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(0), board.cleanupSuccess)
        assertEquals(listOf(worktreePath), board.project.cleanupIssues?.failures?.map { it.path })

        board.project.dismissCleanupIssues()
        assertNull(board.project.cleanupIssues)
    }

    @Test
    fun `running agents are detected for the listed worktrees`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when (command.first()) {
                    "lsof" -> success(command, stdout = "p42\nc2.1.301\nfcwd\nn$worktreePath/src\n")
                    "ps" -> success(command, stdout = "42 /usr/local/bin/claude\n")
                    else -> if (command == listCommand) success(command, porcelainOutput) else success(command)
                }
            },
        )
        advanceUntilIdle()

        assertEquals(mapOf(worktreePath to listOf(RunningAgent("claude"))), viewModel.agents)
    }

    @Test
    fun `agents are unknown when processes can't be listed`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command.first() == "ps" -> failure(command, exitCode = 127, stderr = "ps: not found")
                    command == listCommand -> success(command, porcelainOutput)
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        assertNull(viewModel.agents)
        assertNull(viewModel.error)
    }

    @Test
    fun `sizes are measured for every worktree and added up, and measured again on a manual reload`() = runTest(dispatcher) {
        val otherPath = "/home/dev/projects/TASK-456"
        val fileManager = FakeFileManager(
            existing = listOf(worktreePath, otherPath),
            sizes = mapOf(worktreePath to 1000L, otherPath to 500L),
        )
        val porcelain = "$porcelainOutput\n\nworktree $otherPath\nHEAD def456\nbranch refs/heads/feature/TASK-456"
        val viewModel = projectWorktrees(
            useCasesWithFiles(fileManager) { command ->
                if (command == listCommand) success(command, porcelain) else success(command)
            },
        )
        advanceUntilIdle()

        assertEquals(mapOf(worktreePath to 1000L, otherPath to 500L), viewModel.sizes)
        assertEquals(1500L, viewModel.totalSize)

        viewModel.refresh(remeasureSizes = true)
        assertEquals(mapOf(worktreePath to 1000L, otherPath to 500L), viewModel.sizes)
        advanceUntilIdle()

        assertEquals(4, fileManager.sizeRequests.size)
        assertEquals(1500L, viewModel.totalSize)
    }

    @Test
    fun `an automatic reload measures only worktrees without a size`() = runTest(dispatcher) {
        val otherPath = "/home/dev/projects/TASK-456"
        val fileManager = FakeFileManager(
            existing = listOf(worktreePath, otherPath),
            sizes = mapOf(worktreePath to 1000L, otherPath to 500L),
        )
        var porcelain = porcelainOutput
        val viewModel = projectWorktrees(
            useCasesWithFiles(fileManager) { command ->
                if (command == listCommand) success(command, porcelain) else success(command)
            },
        )
        advanceUntilIdle()

        porcelain = "$porcelainOutput\n\nworktree $otherPath\nHEAD def456\nbranch refs/heads/feature/TASK-456"
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(worktreePath, otherPath), fileManager.sizeRequests.map { it.first })
        assertEquals(mapOf(worktreePath to 1000L, otherPath to 500L), viewModel.sizes)
    }

    private val gitDir = "${config.baseRepositoryPath}/.git"

    private fun watchedFiles() = FakeFileManager(directories = setOf(gitDir)).apply {
        stamps["$gitDir/HEAD"] = 1
        stamps["$gitDir/refs/heads/main"] = 1
    }

    private class Counters {
        var lists = 0
        var processListings = 0
        var statusReads = 0
    }

    private fun countingResponder(counters: Counters): (List<String>) -> CommandResult = { command ->
        when {
            command == listCommand -> {
                counters.lists++
                success(command, porcelainOutput)
            }
            command.first() == "ps" -> {
                counters.processListings++
                success(command)
            }
            "status" in command -> {
                counters.statusReads++
                success(command)
            }
            else -> success(command)
        }
    }

    private fun TestScope.tick() {
        advanceTimeBy(3.seconds)
        runCurrent()
    }

    @Test
    fun `a change in the git metadata reloads the list on the next check`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        board.startWatching()

        tick()
        assertEquals(1, counters.lists)

        files.stamps["$gitDir/refs/heads/main"] = 2
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        board.stopWatching()

        assertEquals(2, counters.lists)
    }

    @Test
    fun `a change limited to index files reads the statuses again without reloading the list`() = runTest(dispatcher) {
        val files = watchedFiles().apply {
            stamps["$gitDir/index"] = 1
            stamps["$gitDir/worktrees/TASK-123/index"] = 1
        }
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        board.startWatching()
        tick()
        val statusReadsBefore = counters.statusReads

        files.stamps["$gitDir/worktrees/TASK-123/index"] = 2
        files.stamps["$gitDir/worktrees/TASK-123/index.lock"] = 2
        files.stamps["$gitDir/index"] = 2
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        board.stopWatching()

        assertEquals(1, counters.lists)
        assertTrue(counters.statusReads > statusReadsBefore)
    }

    @Test
    fun `an index change together with a ref change reloads the list`() = runTest(dispatcher) {
        val files = watchedFiles().apply { stamps["$gitDir/worktrees/TASK-123/index"] = 1 }
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        board.startWatching()
        tick()

        files.stamps["$gitDir/worktrees/TASK-123/index"] = 2
        files.stamps["$gitDir/refs/heads/main"] = 2
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        board.stopWatching()

        assertEquals(2, counters.lists)
    }

    @Test
    fun `without changes only agents are detected again`() = runTest(dispatcher) {
        val counters = Counters()
        val board = board(useCasesWithFiles(watchedFiles(), countingResponder(counters)))
        advanceUntilIdle()
        val listingsAfterLoad = counters.processListings
        board.startWatching()

        tick()
        tick()
        board.stopWatching()

        assertEquals(1, counters.lists)
        assertEquals(listingsAfterLoad + 2, counters.processListings)
    }

    @Test
    fun `no checks run after stopWatching`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        val listingsAfterLoad = counters.processListings
        board.startWatching()
        board.stopWatching()

        files.stamps["$gitDir/HEAD"] = 2
        tick()
        tick()

        assertEquals(1, counters.lists)
        assertEquals(listingsAfterLoad, counters.processListings)
    }

    @Test
    fun `a reload by the app itself doesn't trigger a second one`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        board.startWatching()

        files.stamps["$gitDir/refs/heads/main"] = 2
        board.project.refresh()
        runCurrent()
        tick()
        tick()
        board.stopWatching()

        assertEquals(2, counters.lists)
    }

    @Test
    fun `checks are skipped while an action is running`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val removeGate = CompletableDeferred<Unit>()
        val responder = countingResponder(counters)
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                if (command == removeCommand) removeGate.await()
                return responder(command)
            }
        }
        val board = board(useCasesWith(executor, files))
        advanceUntilIdle()
        val listingsAfterLoad = counters.processListings
        board.startWatching()

        board.project.remove(worktree, branchToDelete = null)
        runCurrent()
        files.stamps["$gitDir/HEAD"] = 2
        tick()
        tick()

        assertTrue(board.project.isRemoving)
        assertEquals(1, counters.lists)
        assertEquals(listingsAfterLoad, counters.processListings)

        removeGate.complete(Unit)
        board.stopWatching()
        runCurrent()
    }

    @Test
    fun `the resolved main branch is exposed`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "symbolic-ref" -> success(command, stdout = "origin/main\n")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        assertEquals(MainBranch("main", "origin/main"), viewModel.mainBranch)
    }

    @Test
    fun `repositories without the gradle provider don't list daemons`() = runTest(dispatcher) {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle)))
        val executor = FakeShellCommandExecutor(listingResponder())
        val viewModel = projectWorktrees(useCasesWith(executor).copy(gradle = gradleUseCases(executor, source)))
        advanceUntilIdle()

        assertFalse(viewModel.isGradleRepository)
        assertEquals(0, source.listings)
    }

    @Test
    fun `gradle repositories expose the build cache state without listing daemons`() = runTest(dispatcher) {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val viewModel = projectWorktrees(gradleRepository(listingResponder(), source, buildCacheEnabled = false))
        advanceUntilIdle()

        assertTrue(viewModel.isGradleRepository)
        assertFalse(viewModel.isGradleBuildCacheEnabled)
        assertEquals(setOf(MachineResource.GRADLE_DAEMONS), viewModel.declaredResources)
        assertEquals(0, source.listings)
    }

    @Test
    fun `gradle settings are exposed only when declared`() = runTest(dispatcher) {
        val settings = GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30)
        val declared = projectWorktrees(gradleRepository(listingResponder(), FakeGradleDaemonSource(), provider = settings))
        val bare = projectWorktrees(gradleRepository(listingResponder(), FakeGradleDaemonSource()))
        advanceUntilIdle()

        assertEquals(settings, declared.gradleSettings)
        assertTrue(bare.isGradleRepository)
        assertNull(bare.gradleSettings)
    }

    @Test
    fun `cleanup stops the idle daemons of the removed worktrees`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idle, lastBuildDirectory = worktreePath),
                gradleDaemon(11, idle, lastBuildDirectory = config.baseRepositoryPath),
            ),
        )
        val board = board(gradleRepository(mergedResponder(executed), source))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.cleanupStopGradleDaemons)
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("kill", "10")))
        assertFalse(executed.contains(listOf("kill", "11")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, stoppedDaemons = 1), board.cleanupSuccess)
    }

    private fun composeRepository(
        responder: (List<String>) -> CommandResult,
        source: FakeComposeProjectSource,
        declared: Boolean = true,
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        return useCasesWith(executor).copy(
            compose = ComposeUseCases(provider = { DockerComposeProvider().takeIf { declared } }),
            cleanUp = CleanUpWorktreesUseCase(config, executor, composeProjects = source),
        )
    }

    private val worktreeComposeName get() = worktreeComposeProjectName(config.baseRepositoryPath, worktreePath)

    private fun composeSection(
        status: () -> String? = { null },
        run: suspend (String, ComposeAction) -> Result<String> = { _, _ -> Result.success(worktreeComposeName) },
        unavailable: Boolean = false,
        declared: Boolean = true,
    ) = ComposeUseCases(
        provider = { DockerComposeProvider().takeIf { declared } },
        readStates = { worktrees ->
            if (unavailable) {
                Result.failure(WorktreeError.DockerUnavailable("daemon down"))
            } else {
                Result.success(worktrees.associate { it.path to WorktreeComposeState(worktreeComposeProjectName(config.baseRepositoryPath, it.path), status()) })
            }
        },
        run = run,
    )

    @Test
    fun `the compose section shows the project not started before any start`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = composeSection()))
        advanceUntilIdle()

        assertEquals(WorktreeComposeState(worktreeComposeName, null), viewModel.composeStates[worktreePath])
        assertFalse(viewModel.isComposeUnavailable)
    }

    @Test
    fun `starting the services shows progress and then the new state`() = runTest(dispatcher) {
        var status: String? = null
        val actions = mutableListOf<Pair<String, ComposeAction>>()
        val gate = CompletableDeferred<Unit>()
        val compose = composeSection(status = { status }, run = { path, action ->
            actions += path to action
            gate.await()
            status = "running(2)"
            Result.success(worktreeComposeName)
        })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = compose))
        advanceUntilIdle()

        viewModel.startComposeServices(worktree)
        viewModel.startComposeServices(worktree)
        advanceUntilIdle()
        assertEquals(ProjectWorktrees.ComposeActivity.STARTING, viewModel.composeActivities[worktreePath])

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<Pair<String, ComposeAction>>(worktreePath to ComposeAction.Up()), actions)
        assertTrue(viewModel.composeActivities.isEmpty())
        assertEquals("running(2)", viewModel.composeStates[worktreePath]?.status)
    }

    @Test
    fun `building the services runs a build`() = runTest(dispatcher) {
        val actions = mutableListOf<ComposeAction>()
        val compose = composeSection(run = { _, action -> actions += action; Result.success(worktreeComposeName) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = compose))
        advanceUntilIdle()

        viewModel.buildComposeServices(worktree)
        advanceUntilIdle()

        assertEquals(listOf<ComposeAction>(ComposeAction.Build()), actions)
    }

    @Test
    fun `a failed start is shown as an action error with its reason`() = runTest(dispatcher) {
        val compose = composeSection(run = { _, _ -> Result.failure(WorktreeError.ComposeCommandFailed(worktreeComposeName, "port is already allocated")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = compose))
        advanceUntilIdle()

        viewModel.startComposeServices(worktree)
        advanceUntilIdle()

        assertEquals("port is already allocated", (viewModel.actionErrors[worktreePath] as WorktreeError.ComposeCommandFailed).detail)
        assertTrue(viewModel.composeActivities.isEmpty())
    }

    @Test
    fun `without docker the compose section is unavailable`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = composeSection(unavailable = true)))
        advanceUntilIdle()

        assertTrue(viewModel.isComposeUnavailable)
        assertTrue(viewModel.composeStates.isEmpty())
    }

    @Test
    fun `a repository without the compose provider reads no compose state`() = runTest(dispatcher) {
        var reads = 0
        val compose = composeSection(declared = false).copy(readStates = { reads++; Result.success(emptyMap()) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(compose = compose))
        advanceUntilIdle()

        assertFalse(viewModel.isComposeRepository)
        assertEquals(0, reads)
    }

    @Test
    fun `cleanup takes down the compose projects of the removed worktrees`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(listOf(ComposeProject(worktreeComposeName, "running(1)", emptyList())))
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.project.isComposeRepository)
        assertTrue(board.cleanupDownComposeProjects)
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf(worktreeComposeName to false), source.downs)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, takenDownComposeProjects = 1), board.cleanupSuccess)
    }

    private val composeImage get() = ComposeResource(ComposeResourceKind.IMAGE, "sha256:aa", "$worktreeComposeName-api", worktreeComposeName)
    private val composeVolume get() = ComposeResource(ComposeResourceKind.VOLUME, "${worktreeComposeName}_db", "${worktreeComposeName}_db", worktreeComposeName)

    @Test
    fun `cleanup removes built images and keeps volumes by default`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(resources = listOf(composeImage, composeVolume))
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        assertFalse(board.cleanupRemoveComposeVolumes)
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf(composeImage), source.removals)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, removedComposeResources = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup removes volumes when that option is checked`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(resources = listOf(composeImage, composeVolume))
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupRemoveComposeVolumes = true
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf(composeImage, composeVolume), source.removals)
    }

    @Test
    fun `unchecking the compose option unchecks the volumes option`() = runTest(dispatcher) {
        val board = board(composeRepository(mergedResponder(), FakeComposeProjectSource()))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupRemoveComposeVolumes = true
        board.cleanupDownComposeProjects = false

        assertFalse(board.cleanupRemoveComposeVolumes)
    }

    @Test
    fun `an image in use is reported in the cleanup issues`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(resources = listOf(composeImage), failingRemovals = mapOf("sha256:aa" to "in use"))
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals("in use", board.project.cleanupIssues?.composeResources?.failures?.single()?.detail)
    }

    @Test
    fun `cleanup leaves compose projects alone when the option is unchecked`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(listOf(ComposeProject(worktreeComposeName, "running(1)", emptyList())))
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupDownComposeProjects = false
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(source.downs.isEmpty())
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
    }

    @Test
    fun `a repository without the compose provider shows no option and never runs docker`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(listOf(ComposeProject(worktreeComposeName, "running(1)", emptyList())))
        val board = board(composeRepository(mergedResponder(), source, declared = false))
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertFalse(board.project.isComposeRepository)
        assertTrue(source.listings.isEmpty() && source.downs.isEmpty())
    }

    @Test
    fun `cleanup still removes the worktrees when docker is unavailable and reports the kept projects`() = runTest(dispatcher) {
        val source = FakeComposeProjectSource(unavailable = "daemon not reachable")
        val board = board(composeRepository(mergedResponder(), source))
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
        assertEquals("daemon not reachable", board.project.cleanupIssues?.composeUnavailable)
    }

    @Test
    fun `cleanup stops the idle maven daemons of the removed worktrees`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeMavenDaemonSource(
            listOf(
                mavenDaemon(20, idle, workingDirectory = "$worktreePath/service"),
                mavenDaemon(21, idle, workingDirectory = config.baseRepositoryPath),
            ),
        )
        val board = board(mavenRepository(mergedResponder(executed), source))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.project.isMavenRepository)
        assertTrue(board.cleanupStopMavenDaemons)
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("kill", "20")))
        assertFalse(executed.contains(listOf("kill", "21")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, stoppedDaemons = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup keeps a busy maven daemon of a removed worktree and reports it`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeMavenDaemonSource(listOf(mavenDaemon(20, idle, workingDirectory = worktreePath)))
        val merged = mergedResponder(executed)
        val board = board(
            mavenRepository(
                { command ->
                    if (command.take(3) == listOf("git", "worktree", "remove")) {
                        source.daemons = listOf(mavenDaemon(20, DaemonState.Busy, workingDirectory = worktreePath))
                    }
                    merged(command)
                },
                source,
            ),
        )
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertFalse(executed.contains(listOf("kill", "20")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, keptDaemons = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup without the maven option or provider stops no maven daemon`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeMavenDaemonSource(listOf(mavenDaemon(20, idle, workingDirectory = worktreePath)))
        val board = board(mavenRepository(mergedResponder(executed), source))
        advanceUntilIdle()
        board.openCleanup()
        board.cleanupStopMavenDaemons = false
        board.confirmCleanup()
        advanceUntilIdle()

        val undeclaredExecuted = mutableListOf<List<String>>()
        val undeclaredSource = FakeMavenDaemonSource(listOf(mavenDaemon(20, idle, workingDirectory = worktreePath)))
        val undeclared = board(mavenRepository(mergedResponder(undeclaredExecuted), undeclaredSource, declared = false))
        advanceUntilIdle()
        assertFalse(undeclared.project.isMavenRepository)
        undeclared.openCleanup()
        undeclared.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.none { it.first() == "kill" })
        assertTrue(undeclaredExecuted.none { it.first() == "kill" })
        assertEquals(0, undeclaredSource.listings)
    }

    @Test
    fun `cleanup keeps daemons that became busy and reports them`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val merged = mergedResponder(executed)
        val board = board(
            gradleRepository(
                { command ->
                    if (command.take(3) == listOf("git", "worktree", "remove")) {
                        source.daemons = listOf(gradleDaemon(10, DaemonState.Busy, lastBuildDirectory = worktreePath))
                    }
                    merged(command)
                },
                source,
            ),
        )
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertFalse(executed.contains(listOf("kill", "10")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, keptDaemons = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup without the option stops no daemon`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val board = board(gradleRepository(mergedResponder(executed), source))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupStopGradleDaemons = false
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.none { it.first() == "kill" })
        assertEquals(WorktreeSuccess.CleanedUp(1), board.cleanupSuccess)
    }

    @Test
    fun `reserved ports are shown per worktree`() = runTest(dispatcher) {
        portsByWorktree = mapOf(worktreePath to mapOf("PORT" to 20001, "port:api" to 20002))
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()

        assertEquals(mapOf(worktreePath to mapOf("PORT" to 20001, "port:api" to 20002)), viewModel.ports)
        assertEquals("PORT 20001 · api 20002", portsLabel(viewModel.ports.getValue(worktreePath)))
    }

    @Test
    fun `opening the screen reserves no port`() = runTest(dispatcher) {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command == listCommand -> success(command, porcelainOutput)
                "--git-dir" in command ->
                    success(command, "/home/dev/projects/main-repo/.git/worktrees/TASK-123\n/home/dev/projects/main-repo/.git\n")
                else -> success(command)
            }
        }
        val store = FakeEnvironmentStateStore()
        val resolve = ResolveWorktreeEnvironmentUseCase(
            executor,
            FakeRepositoryProvisioningSource(
                byRepository = mapOf(
                    config.baseRepositoryPath to RepositoryProvisioning(
                        environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}"))),
                    ),
                ),
            ),
            store,
            FakePortProbe(),
        )
        val viewModel = projectWorktrees(
            useCasesWith(executor).copy(
                readPorts = { worktree -> resolve(config.baseRepositoryPath, worktree.path, reserve = false).map { it.ports } },
            ),
        )
        advanceUntilIdle()

        assertEquals(emptyMap(), viewModel.ports)
        assertEquals(0, store.writes)
    }

    private class RecordingEmulators(
        val declared: Boolean = true,
        initial: Map<String, WorktreeEmulator> = emptyMap(),
    ) {
        var states = initial
        var reads = 0
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val stoppedSerials = mutableListOf<String>()

        fun useCases() = EmulatorUseCases(
            declared = { declared },
            readStates = { reads++; Result.success(states) },
            start = { path ->
                started += path
                val emulator = WorktreeEmulator("emulator-5554", EmulatorState.RUNNING)
                states = states + (path to emulator)
                Result.success(DeviceStart(emulator, ResourceAcquisition(false, emptyList())))
            },
            stop = { path ->
                stopped += path
                states = states + (path to WorktreeEmulator("emulator-5554", EmulatorState.STOPPED))
                Result.success(states[path])
            },
            stopSerials = { serials -> stoppedSerials += serials; Result.success(serials.map { WorktreeEmulator(it, EmulatorState.STOPPED) }) },
        )
    }

    @Test
    fun `repositories without the emulator provider never read emulators`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(declared = false)
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        assertFalse(viewModel.isEmulatorRepository)
        assertEquals(0, emulators.reads)
        assertEquals(emptyMap(), viewModel.emulators)
    }

    @Test
    fun `emulator states are loaded and can be started and stopped from the row`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5554", EmulatorState.STOPPED)))
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        assertTrue(viewModel.isEmulatorRepository)
        assertEquals(EmulatorState.STOPPED, viewModel.emulators[worktreePath]?.state)

        viewModel.startEmulator(worktree)
        assertTrue(worktreePath in viewModel.startingEmulators)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), emulators.started)
        assertEquals(EmulatorState.RUNNING, viewModel.emulators[worktreePath]?.state)
        assertTrue(viewModel.startingEmulators.isEmpty())

        viewModel.stopEmulator(worktree)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), emulators.stopped)
        assertEquals(EmulatorState.STOPPED, viewModel.emulators[worktreePath]?.state)
    }

    @Test
    fun `stopping an emulator twice sends one stop and shows progress meanwhile`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5554", EmulatorState.RUNNING)))
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        viewModel.stopEmulator(worktree)
        viewModel.stopEmulator(worktree)
        assertTrue(worktreePath in viewModel.stoppingEmulators)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), emulators.stopped)
        assertTrue(viewModel.stoppingEmulators.isEmpty())
    }

    @Test
    fun `stopping a simulator twice sends one stop and shows progress meanwhile`() = runTest(dispatcher) {
        val simulators = RecordingSimulators()
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        viewModel.stopSimulator(worktree)
        viewModel.stopSimulator(worktree)
        assertTrue(worktreePath in viewModel.stoppingSimulators)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), simulators.stopped)
        assertTrue(viewModel.stoppingSimulators.isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail shows the compose section and starts the services from it`() = runComposeUiTest {
        val actions = mutableListOf<ComposeAction>()
        val compose = composeSection(run = { _, action -> actions += action; Result.success(worktreeComposeName) })
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(compose = compose))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitUntil { onAllNodesWithText("Not started").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(worktreeComposeName).assertIsDisplayed()
        onNodeWithText("Not started").assertIsDisplayed()
        onNodeWithText("Build").assertIsDisplayed()
        onNodeWithText("Take down").assertDoesNotExist()
        onNodeWithText("Start").performClick()
        waitUntil { actions.isNotEmpty() }

        assertEquals(listOf<ComposeAction>(ComposeAction.Up()), actions)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the compose section says when docker is unavailable and offers no action`() = runComposeUiTest {
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(compose = composeSection(unavailable = true)))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitUntil { onAllNodesWithText("Docker isn't available").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Build").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail shows its sections in the inspector's fixed order`() = runComposeUiTest {
        val compose = composeSection(run = { _, _ -> Result.success(worktreeComposeName) })
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(compose = compose))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitUntil { onAllNodesWithText("Not started").fetchSemanticsNodes().isNotEmpty() }
        val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }

        assertEquals(listOf("STATUS", "AGENT", "RESOURCES", "PROVISIONED FILES"), headings.filter { it == it.uppercase() })
    }

    @Test
    fun `reading agents records when they were read`() = runTest(dispatcher) {
        val readAt = Instant.parse("2026-10-16T12:00:00Z")
        val viewModel = ProjectWorktrees(
            useCases(listingResponder()),
            CoroutineScope(SupervisorJob() + dispatcher),
            ioDispatcher = dispatcher,
            clock = { readAt },
        ).also { it.refresh() }
        advanceUntilIdle()

        assertEquals(readAt, viewModel.agentsReadAt)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `copying a path from the detail confirms it`() = runComposeUiTest {
        val holder = projectWorktrees(useCases(listingResponder()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        onNodeWithContentDescription("Copy path").performClick()

        onNodeWithContentDescription("Copied").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail picks the worktree's agent once, in the open with menu`() = runComposeUiTest {
        val holder = projectWorktrees(withAgents())
        setContent { MaterialTheme { WorktreeDetailPane(worktree, multiAgentConfig, holder) } }

        onNodeWithText("Open in Herdr").assertIsDisplayed()
        onNodeWithContentDescription("Open with…", substring = true).performClick()
        onNodeWithText("With agent").assertIsDisplayed()
        onNode(hasText("Claude") and isSelected()).assertExists()

        onNodeWithText("Codex").performClick()
        waitForIdle()

        assertEquals(codex, holder.agentFor(worktree))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail of a single-agent project shows no agent choice`() = runComposeUiTest {
        val holder = projectWorktrees(withAgents(projectConfig = config))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        onNodeWithContentDescription("Copy launch command", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Open with…", substring = true).performClick()
        onNodeWithText("With agent").assertDoesNotExist()
        onNodeWithContentDescription("Choose agent").assertDoesNotExist()
    }

    @Test
    fun `cleanup stops the running emulators of removed worktrees`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5556", EmulatorState.RUNNING)))
        val board = board(useCases(mergedResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.cleanupStopEmulators)
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("emulator-5556"), emulators.stoppedSerials)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, stoppedEmulators = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup without the emulator option stops none`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5556", EmulatorState.RUNNING)))
        val board = board(useCases(mergedResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupStopEmulators = false
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(emulators.stoppedSerials.isEmpty())
    }

    private class RecordingSimulators(
        val declared: Boolean = true,
        initial: Map<String, WorktreeSimulator> = emptyMap(),
        var orphans: List<SimulatorDevice> = emptyList(),
    ) {
        var states = initial
        var reads = 0
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        fun useCases() = SimulatorUseCases(
            declared = { declared },
            readStates = { reads++; Result.success(states) },
            start = { path ->
                started += path
                val simulator = WorktreeSimulator("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED)
                states = states + (path to simulator)
                Result.success(DeviceStart(simulator, ResourceAcquisition(false, emptyList())))
            },
            stop = { path ->
                stopped += path
                states = states.mapValues { (key, value) -> if (key == path) value.copy(state = SimulatorState.SHUTDOWN) else value }
                Result.success(states[path])
            },
            orphans = { Result.success(orphans) },
            delete = { udids -> deleted += udids; Result.success(udids) },
        )
    }

    @Test
    fun `repositories without the iOS simulator provider never read simulators`() = runTest(dispatcher) {
        val simulators = RecordingSimulators(declared = false)
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        assertFalse(viewModel.isSimulatorRepository)
        assertEquals(0, simulators.reads)
    }

    @Test
    fun `simulators are loaded and can be started and stopped from the row`() = runTest(dispatcher) {
        val simulators = RecordingSimulators()
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()
        assertTrue(viewModel.isSimulatorRepository)

        viewModel.startSimulator(worktree)
        assertTrue(worktreePath in viewModel.startingSimulators)
        advanceUntilIdle()
        assertEquals(SimulatorState.BOOTED, viewModel.simulators[worktreePath]?.state)
        assertTrue(viewModel.startingSimulators.isEmpty())

        viewModel.stopSimulator(worktree)
        advanceUntilIdle()
        assertEquals(listOf(worktreePath), simulators.stopped)
        assertEquals(SimulatorState.SHUTDOWN, viewModel.simulators[worktreePath]?.state)
    }

    @Test
    fun `cleanup deletes the simulators of removed worktrees and orphans`() = runTest(dispatcher) {
        val simulators = RecordingSimulators(
            initial = mapOf(worktreePath to WorktreeSimulator("SIM-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN)),
            orphans = listOf(SimulatorDevice("SIM-ORPHAN", "ShogunAi · main-repo · TASK-0", SimulatorState.SHUTDOWN)),
        )
        val board = board(useCases(mergedResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.cleanupDeleteSimulators)
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("SIM-1", "SIM-ORPHAN"), simulators.deleted)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, deletedSimulators = 2), board.cleanupSuccess)
    }

    @Test
    fun `cleanup looks for orphans again after removing, catching simulators it couldn't read before`() = runTest(dispatcher) {
        val simulators = RecordingSimulators()
        val board = board(useCases(mergedResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()
        board.openCleanup()
        simulators.orphans = listOf(SimulatorDevice("SIM-GONE", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED))

        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("SIM-GONE"), simulators.deleted)
    }

    @Test
    fun `orphaned simulators alone enable the cleanup`() = runTest(dispatcher) {
        val simulators = RecordingSimulators(orphans = listOf(SimulatorDevice("SIM-ORPHAN", "ShogunAi · main-repo · TASK-0", SimulatorState.SHUTDOWN)))
        val board = board(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        assertTrue(board.project.cleanupCandidates.isEmpty())
        assertTrue(board.project.canCleanUp)
        board.openCleanup()
        assertTrue(board.isCleanupDialogOpen)
    }

    @Test
    fun `repositories without the xcode provider never read derived data`() = runTest(dispatcher) {
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(existing = listOf(FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj"))),
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(xcode = xcodeUseCases(source)))
        advanceUntilIdle()

        assertFalse(viewModel.isXcodeRepository)
        assertEquals(0, source.listings)
    }

    @Test
    fun `the xcode provider is ignored when the source isn't supported`() = runTest(dispatcher) {
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(
                supported = false,
                existing = listOf(FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj")),
            ),
        )
        val viewModel = projectWorktrees(xcodeRepository(listingResponder(), source))
        advanceUntilIdle()

        assertFalse(viewModel.isXcodeRepository)
        assertFalse(viewModel.canCleanUp)
        assertEquals(0, source.listings)
    }

    @Test
    fun `xcode repositories load their orphans for the cleanup`() = runTest(dispatcher) {
        val project = worktreeProject()
        val worktreeFolder = FakeDerivedDataSource.derivedData("TASK-123", project, 2_000_000_000L)
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 500_000_000L)
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(
                existing = listOf(worktreeFolder, orphan),
                existingProjects = setOf(project),
                cacheBytes = 300_000_000L,
            ),
        )
        val viewModel = projectWorktrees(
            xcodeRepository(listingResponder(), source, provider = XcodeProvider(compilationCache = true)),
        )
        advanceUntilIdle()

        assertTrue(viewModel.isXcodeRepository)
        assertEquals(listOf(orphan.path), viewModel.derivedDataOrphans.map { it.path })
        assertTrue(viewModel.hasCompilationCache)
        assertEquals(1, source.listings)
    }

    @Test
    fun `xcode repositories measure only the orphans`() = runTest(dispatcher) {
        val project = worktreeProject()
        val worktreeFolder = FakeDerivedDataSource.derivedData("TASK-123", project, null)
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", null)
        val fake = FakeDerivedDataSource(existing = listOf(worktreeFolder, orphan), existingProjects = setOf(project))
        fake.sizes[orphan.path] = 500_000_000L
        val viewModel = projectWorktrees(xcodeRepository(listingResponder(), CountingDerivedDataSource(fake)))
        advanceUntilIdle()

        assertEquals(listOf(orphan.path), fake.measured)
        assertEquals(listOf(500_000_000L), viewModel.derivedDataOrphans.map { it.sizeBytes })
    }

    @Test
    fun `an auto-refresh check without changes doesn't reload derived data`() = runTest(dispatcher) {
        val source = CountingDerivedDataSource(FakeDerivedDataSource())
        val board = board(xcodeRepository(listingResponder(), source))
        advanceUntilIdle()
        assertEquals(1, source.listings)

        board.startWatching()
        tick()
        tick()
        board.stopWatching()

        assertEquals(1, source.listings)
    }

    @Test
    fun `cleanup deletes the removed worktrees' derived data and the orphans`() = runTest(dispatcher) {
        val project = worktreeProject()
        val worktreeFolder = FakeDerivedDataSource.derivedData("TASK-123", project, 2_000_000_000L)
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(existing = listOf(worktreeFolder, orphan), existingProjects = setOf(project)),
        )
        val board = board(xcodeRepository(mergedResponder(), source.source))
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.cleanupDeleteDerivedData)
        source.source.existingProjects = emptySet()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(setOf(worktreeFolder.path, orphan.path), source.source.deleted.toSet())
        assertEquals(
            WorktreeSuccess.CleanedUp(removedCount = 1, deletedDerivedDataFolders = 2, derivedDataBytesFreed = 2_700_000_000L),
            board.cleanupSuccess,
        )
    }

    @Test
    fun `cleanup without the option deletes no derived data`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val board = board(xcodeRepository(mergedResponder(), source.source))
        advanceUntilIdle()

        board.openCleanup()
        board.cleanupDeleteDerivedData = false
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(source.source.deleted.isEmpty())
        assertEquals(WorktreeSuccess.CleanedUp(1), board.cleanupSuccess)
    }

    @Test
    fun `a failing derived data deletion doesn't fail the cleanup`() = runTest(dispatcher) {
        val board = board(
            useCasesWith(FakeShellCommandExecutor(mergedResponder()))
                .copy(xcode = xcodeUseCases(ThrowingDerivedDataSource(IOException("disk error")), XcodeProvider())),
        )
        advanceUntilIdle()

        assertTrue(board.project.isXcodeRepository)
        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
    }

    @Test
    fun `orphaned derived data alone enables the cleanup`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val board = board(xcodeRepository(listingResponder(), source.source))
        advanceUntilIdle()

        assertTrue(board.project.cleanupCandidates.isEmpty())
        assertTrue(board.project.canCleanUp)
        board.openCleanup()
        assertTrue(board.isCleanupDialogOpen)
    }

    @Test
    fun `cleanup without the xcode provider deletes no derived data`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val board = board(useCases(mergedResponder()).copy(xcode = xcodeUseCases(source)))
        advanceUntilIdle()

        board.openCleanup()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(0, source.listings)
        assertTrue(source.source.deleted.isEmpty())
        assertFalse(board.project.isXcodeRepository)
    }

    @Test
    fun `Herdr's worktrees folder is loaded with the list to classify origins`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(worktreesRoot = { "/home/dev/.herdr/worktrees" })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        assertEquals("/home/dev/.herdr/worktrees", viewModel.herdrWorktreesRoot)
    }

    @Test
    fun `opening in Herdr passes the worktree and reports the opening`() = runTest(dispatcher) {
        val calls = mutableListOf<Worktree>()
        val herdr = HerdrUseCases.NONE.copy(open = { target, _ -> calls += target; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        val target = viewModel.worktrees.first { it.path == worktreePath }

        viewModel.openInHerdr(target)
        assertTrue(worktreePath in viewModel.openingInHerdr)
        advanceUntilIdle()

        assertEquals(target, calls.single())
        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, HerdrOutcome.OPENED), viewModel.success)
        assertTrue(viewModel.openingInHerdr.isEmpty())
    }

    @Test
    fun `opening in Herdr focuses the pane of the agent that needs the user`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        var opened = 0
        val herdr = HerdrUseCases.NONE.copy(
            open = { _, _ -> opened++; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) },
            focusAgent = { pane -> focused += pane; Result.success(Unit) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(
            mapOf(
                worktreePath to listOf(
                    RunningAgent("codex", AgentState.WORKING, "w1:p1"),
                    RunningAgent("claude", AgentState.BLOCKED, "w1:p2"),
                ),
            ),
        )

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(listOf("w1:p2"), focused)
        assertEquals(0, opened)
        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, HerdrOutcome.FOCUSED), viewModel.success)
    }

    @Test
    fun `focusing the agent focuses the pane of the most urgent agent in Herdr`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        val used = mutableListOf<String>()
        val herdr = HerdrUseCases.NONE.copy(focusAgent = { pane -> focused += pane; Result.success(Unit) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr), onUsed = { used += it })
        advanceUntilIdle()
        viewModel.applyAgents(
            mapOf(
                worktreePath to listOf(
                    RunningAgent("codex", AgentState.DONE, "w1:p1", AgentStateSource.HERDR),
                    RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR),
                ),
            ),
        )

        assertTrue(viewModel.focusAgent(viewModel.worktrees.first { it.path == worktreePath }))
        assertTrue(worktreePath in viewModel.openingInHerdr)
        advanceUntilIdle()

        assertEquals(listOf("w1:p2"), focused)
        assertEquals(listOf(worktreePath), used)
        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, HerdrOutcome.FOCUSED), viewModel.success)
        assertTrue(viewModel.openingInHerdr.isEmpty())
    }

    @Test
    fun `focusing the agent while Herdr is opening the worktree does nothing more`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        val herdr = HerdrUseCases.NONE.copy(focusAgent = { pane -> focused += pane; Result.success(Unit) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(mapOf(worktreePath to listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR))))
        val target = viewModel.worktrees.first { it.path == worktreePath }

        viewModel.focusAgent(target)
        assertTrue(viewModel.focusAgent(target))
        advanceUntilIdle()

        assertEquals(listOf("w1:p2"), focused)
    }

    @Test
    fun `focusing the agent records Herdr's error for the worktree`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(focusAgent = { Result.failure(WorktreeError.HerdrNotInstalled) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(mapOf(worktreePath to listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR))))

        viewModel.focusAgent(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.HerdrNotInstalled>(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.success)
    }

    @Test
    fun `focusing an agent in Orca opens the worktree in Orca`() = runTest(dispatcher) {
        val calls = mutableListOf<Worktree>()
        val orca = OrcaUseCases.NONE.copy(open = { target, _ -> calls += target; Result.success(OrcaOpening(OrcaOutcome.FOCUSED, "term_9", "codex")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = orca))
        advanceUntilIdle()
        viewModel.applyAgents(mapOf(worktreePath to listOf(RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA))))
        val target = viewModel.worktrees.first { it.path == worktreePath }

        assertTrue(viewModel.focusAgent(target))
        advanceUntilIdle()

        assertEquals(target, calls.single())
        assertEquals(WorktreeSuccess.OpenedInOrca(worktreePath, OrcaOutcome.FOCUSED), viewModel.success)
    }

    @Test
    fun `focusing an agent without a known runner does nothing`() = runTest(dispatcher) {
        var opened = 0
        val herdr = HerdrUseCases.NONE.copy(
            open = { _, _ -> opened++; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) },
            focusAgent = { opened++; Result.success(Unit) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(mapOf(worktreePath to listOf(RunningAgent("claude"))))

        assertFalse(viewModel.focusAgent(viewModel.worktrees.first { it.path == worktreePath }))
        advanceUntilIdle()

        assertEquals(0, opened)
        assertNull(viewModel.success)
    }

    @Test
    fun `Enter focuses the selected worktree's agent`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        val herdr = HerdrUseCases.NONE.copy(focusAgent = { pane -> focused += pane; Result.success(Unit) })
        val board = board(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        assertFalse(board.focusSelectedAgent())

        board.selectPath(worktreePath)
        assertFalse(board.focusSelectedAgent())

        board.project.applyAgents(mapOf(worktreePath to listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR))))
        assertTrue(board.focusSelectedAgent())
        advanceUntilIdle()

        assertEquals(listOf("w1:p2"), focused)
    }

    @Test
    fun `opening in Herdr opens the workspace when the agents have no pane`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        var opened = 0
        val herdr = HerdrUseCases.NONE.copy(
            open = { _, _ -> opened++; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) },
            focusAgent = { pane -> focused += pane; Result.success(Unit) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(mapOf(worktreePath to listOf(RunningAgent("claude"))))

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertTrue(focused.isEmpty())
        assertEquals(1, opened)
    }

    @Test
    fun `an already open worktree is reported as focused`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(open = { _, _ -> Result.success(HerdrOpening(HerdrOutcome.FOCUSED, "w1:p1", "claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, HerdrOutcome.FOCUSED), viewModel.success)
    }

    @Test
    fun `an agent started in an already open worktree is reported as started`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(open = { _, _ -> Result.success(HerdrOpening(HerdrOutcome.STARTED_AGENT, "w1:p2", "Claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, HerdrOutcome.STARTED_AGENT), viewModel.success)
    }

    @Test
    fun `an agent that doesn't start is recorded for the worktree`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(open = { _, _ -> Result.failure(WorktreeError.HerdrAgentNotStarted("w1:p2", "claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.HerdrAgentNotStarted>(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.success)
    }

    @Test
    fun `a Herdr failure is recorded for the worktree`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertIs<WorktreeError.HerdrNotInstalled>(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.success)
        assertTrue(viewModel.openingInHerdr.isEmpty())
    }

    @Test
    fun `the selection survives a reload that keeps the worktree`() = runTest(dispatcher) {
        var listCalls = 0
        val board = board(useCases { command ->
            if (command == listCommand) {
                listCalls++
                success(command, porcelainOutput)
            } else {
                success(command)
            }
        })
        advanceUntilIdle()

        board.selectPath(worktreePath)
        board.project.refresh()
        advanceUntilIdle()

        assertEquals(2, listCalls)
        assertEquals(worktreePath, board.selectedWorktree?.worktree?.path)
    }

    @Test
    fun `the selection is cleared when the selected worktree disappears`() = runTest(dispatcher) {
        var listCalls = 0
        val gonePorcelain = """
            worktree ${config.baseRepositoryPath}
            HEAD aaa000
            branch refs/heads/main
        """.trimIndent()
        val board = board(useCases { command ->
            if (command == listCommand) {
                listCalls++
                success(command, if (listCalls == 1) porcelainOutput else gonePorcelain)
            } else {
                success(command)
            }
        })
        advanceUntilIdle()

        board.selectPath(worktreePath)
        board.project.refresh()
        advanceUntilIdle()

        assertNull(board.selectedWorktree?.worktree?.path)
        assertEquals(config.baseRepositoryPath, board.project.worktrees.single().path)
    }

    @Test
    fun `arrow navigation moves between worktrees and stops at the ends`() = runTest(dispatcher) {
        val board = board(useCases(mergedResponder()))
        advanceUntilIdle()
        val first = config.baseRepositoryPath
        val third = "/home/dev/projects/TASK-456"

        board.selectNext()
        assertEquals(first, board.selectedWorktree?.worktree?.path)

        board.selectNext()
        assertEquals(worktreePath, board.selectedWorktree?.worktree?.path)

        board.selectNext()
        assertEquals(third, board.selectedWorktree?.worktree?.path)

        board.selectNext()
        assertEquals(third, board.selectedWorktree?.worktree?.path)

        board.selectPrevious()
        assertEquals(worktreePath, board.selectedWorktree?.worktree?.path)

        board.selectPrevious()
        assertEquals(first, board.selectedWorktree?.worktree?.path)

        board.selectPrevious()
        assertEquals(first, board.selectedWorktree?.worktree?.path)
    }

    @Test
    fun `dismissing an action error only clears that worktree's error`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        viewModel.dismissActionError(worktreePath)
        assertNull(viewModel.actionErrors[worktreePath])
    }

    @Test
    fun `a worktree without an emulator exposes why it was released`() = runTest(dispatcher) {
        val record = ReleaseRecord(1_000L, "main-repo · TASK-9", "emulator-5554")
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(emptyMap()) },
            start = { Result.failure(IOException("unused")) },
            stop = { Result.success(null) },
            stopSerials = { Result.success(emptyList()) },
            readRelease = { path -> record.takeIf { path == worktreePath } },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators))
        advanceUntilIdle()

        assertEquals(mapOf(worktreePath to record), viewModel.emulatorReleases)
    }

    @Test
    fun `a worktree that holds an emulator has no release note`() = runTest(dispatcher) {
        val record = ReleaseRecord(1_000L, "main-repo · TASK-9", "emulator-5554")
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(mapOf(worktreePath to WorktreeEmulator("emulator-5554", EmulatorState.STOPPED))) },
            start = { Result.failure(IOException("unused")) },
            stop = { Result.success(null) },
            stopSerials = { Result.success(emptyList()) },
            readRelease = { record },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators))
        advanceUntilIdle()

        assertTrue(viewModel.emulatorReleases.isEmpty())
    }

    @Test
    fun `a failed emulator start records an action error without failing the view`() = runTest(dispatcher) {
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(emptyMap()) },
            start = { Result.failure(IOException("no emulator")) },
            stop = { Result.success(WorktreeEmulator("emulator-5554", EmulatorState.STOPPED)) },
            stopSerials = { Result.success(emptyList()) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators))
        advanceUntilIdle()

        viewModel.startEmulator(worktree)
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertIs<IOException>(viewModel.actionErrors[worktreePath])
        assertTrue(viewModel.startingEmulators.isEmpty())
    }

    @Test
    fun `a start that released another emulator keeps a notice until dismissed`() = runTest(dispatcher) {
        val release = PoolRelease(ResourceKind.EMULATOR, "/home/dev/other", PoolReleaseReason.MAKE_ROOM, "api · TASK-1")
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(emptyMap()) },
            start = {
                Result.success(
                    DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(true, listOf(release))),
                )
            },
            stop = { Result.success(WorktreeEmulator("emulator-5554", EmulatorState.STOPPED)) },
            stopSerials = { Result.success(emptyList()) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators))
        advanceUntilIdle()

        viewModel.startEmulator(worktree)
        advanceUntilIdle()

        assertEquals(
            listOf(ProjectWorktrees.ReleaseNotice(ResourceKind.EMULATOR, "api · TASK-1", worktreePath.substringAfterLast('/'))),
            viewModel.releaseNotices[worktreePath],
        )

        viewModel.dismissReleaseNotices(worktreePath)
        assertNull(viewModel.releaseNotices[worktreePath])
    }

    @Test
    fun `retrying a failed emulator start repeats it and clears the error on success`() = runTest(dispatcher) {
        var failing = true
        var starts = 0
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(emptyMap()) },
            start = {
                starts++
                if (failing) {
                    Result.failure(IOException("no emulator"))
                } else {
                    Result.success(DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(false, emptyList())))
                }
            },
            stop = { Result.success(WorktreeEmulator("emulator-5554", EmulatorState.STOPPED)) },
            stopSerials = { Result.success(emptyList()) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(emulator = emulators))
        advanceUntilIdle()
        viewModel.startEmulator(worktree)
        advanceUntilIdle()
        assertIs<IOException>(viewModel.actionErrors[worktreePath])

        failing = false
        viewModel.actionRetry(worktreePath)?.invoke()
        advanceUntilIdle()

        assertEquals(2, starts)
        assertNull(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.actionRetry(worktreePath))
    }

    @Test
    fun `a failure without a retry offers none`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.HerdrNotInstalled>(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.actionRetry(worktreePath))
    }

    @Test
    fun `copying the launch command reports the worktree as used`() = runTest(dispatcher) {
        val used = mutableListOf<String>()
        val viewModel = projectWorktrees(useCases(listingResponder()), used::add)
        advanceUntilIdle()

        viewModel.copyLaunchCommand(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), used)
    }

    @Test
    fun `a success on another worktree keeps the error of this one`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()
        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        val failure = viewModel.actionErrors[worktreePath]

        viewModel.copyLaunchCommand(Worktree(path = "/home/dev/projects/TASK-456", branch = "feature/TASK-456"))
        advanceUntilIdle()

        assertEquals(failure, viewModel.actionErrors[worktreePath])
    }

    @Test
    fun `a success on a worktree keeps the view's error`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases { command ->
            if (command.take(3) == repairCommandPrefix) failure(command, stderr = "fatal: not a git repository") else listingResponder()(command)
        })
        advanceUntilIdle()
        viewModel.repairAfterMove(previousWorktreesRoot = "/home/dev/old-projects")
        advanceUntilIdle()
        val failure = viewModel.error
        assertTrue(failure != null)

        viewModel.copyLaunchCommand(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(failure, viewModel.error)
    }

    @Test
    fun `copying the selected launch command requires a selection`() = runTest(dispatcher) {
        val board = board(useCases(listingResponder()))
        advanceUntilIdle()

        board.copySelectedLaunchCommand()
        assertTrue(clipboard.writtenText.isEmpty())

        board.selectPath(worktreePath)
        board.copySelectedLaunchCommand()
        advanceUntilIdle()

        assertEquals(1, clipboard.writtenText.size)
        assertTrue(clipboard.writtenText.single().startsWith("cd '$worktreePath' && "))
    }

    @Test
    fun `deleting the selected worktree only opens the dialog for non-main worktrees`() = runTest(dispatcher) {
        val board = board(useCases(mergedResponder()))
        advanceUntilIdle()

        board.selectPath(config.baseRepositoryPath)
        board.deleteSelectedWorktree()
        assertNull(board.project.worktreePendingRemoval)

        board.selectPath(worktreePath)
        board.deleteSelectedWorktree()
        assertEquals(worktreePath, board.project.worktreePendingRemoval?.path)
    }

    private fun withOrca(
        responder: (List<String>) -> CommandResult,
        orca: FakeOrcaClient,
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        val close = CloseOrcaTerminalsUseCase(orca)
        return useCasesWith(executor).copy(
            remove = RemoveWorktreeUseCase(config, executor, closeOrcaTerminals = close),
            cleanUp = CleanUpWorktreesUseCase(config, executor, closeOrcaTerminals = close),
            orca = OrcaUseCases.NONE.copy(withTerminals = close::withTerminals),
        )
    }

    private fun orcaWithTerminals(vararg paths: String, closeFailures: Map<String, Throwable> = emptyMap()) =
        FakeOrcaClient(terminals = paths.associateWith { listOf(OrcaTerminal("term_1", it)) }, closeFailures = closeFailures)

    @Test
    fun `opening in Orca passes the worktree and reports a started agent`() = runTest(dispatcher) {
        val calls = mutableListOf<Worktree>()
        val orca = OrcaUseCases.NONE.copy(open = { target, _ -> calls += target; Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", "Claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = orca))
        advanceUntilIdle()
        val target = viewModel.worktrees.first { it.path == worktreePath }

        viewModel.openInOrca(target)
        assertTrue(worktreePath in viewModel.openingInOrca)
        advanceUntilIdle()

        assertEquals(target, calls.single())
        assertEquals(WorktreeSuccess.OpenedInOrca(worktreePath, OrcaOutcome.STARTED_AGENT), viewModel.success)
        assertTrue(viewModel.openingInOrca.isEmpty())
    }

    @Test
    fun `opening in Orca reports a focused live agent`() = runTest(dispatcher) {
        val orca = OrcaUseCases.NONE.copy(open = { _, _ -> Result.success(OrcaOpening(OrcaOutcome.FOCUSED, "term_9", "claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = orca))
        advanceUntilIdle()

        viewModel.openInOrca(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.OpenedInOrca(worktreePath, OrcaOutcome.FOCUSED), viewModel.success)
    }

    @Test
    fun `an agent that doesn't start in Orca is recorded for the worktree`() = runTest(dispatcher) {
        val orca = OrcaUseCases.NONE.copy(open = { _, _ -> Result.failure(WorktreeError.OrcaAgentNotStarted("term_1", "claude")) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = orca))
        advanceUntilIdle()

        viewModel.openInOrca(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.OrcaAgentNotStarted>(viewModel.actionErrors[worktreePath])
        assertNull(viewModel.success)
    }

    @Test
    fun `Orca not installed is recorded for the worktree`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(useCases(listingResponder()))
        advanceUntilIdle()

        viewModel.openInOrca(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.OrcaNotInstalled>(viewModel.actionErrors[worktreePath])
        assertTrue(viewModel.openingInOrca.isEmpty())
    }

    @Test
    fun `the agent picker opens in Orca with the picked agent`() = runTest(dispatcher) {
        val used = mutableListOf<AgentLaunchConfig>()
        val orca = OrcaUseCases.NONE.copy(open = { _, agent -> used += agent; Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", agent.displayName)) })
        val holder = projectWorktrees(withAgents().copy(orca = orca))
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.perform(target, ActionId.Open(OpenTarget.ORCA), agent = "Codex")
        advanceUntilIdle()

        assertEquals(listOf(codex), used)
        assertEquals(codex, holder.agentFor(target))
    }

    @Test
    fun `the ports Orca sees are read with the agents and follow each check`() = runTest(dispatcher) {
        var observed = mapOf(worktreePath to listOf(ObservedPort(8765, "Python", "http://127.0.0.1:8765")))
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = OrcaUseCases.NONE.copy(ports = { observed })))
        advanceUntilIdle()

        assertEquals(listOf(ObservedPort(8765, "Python", "http://127.0.0.1:8765")), viewModel.observedPorts[worktreePath])

        observed = mapOf(worktreePath to listOf(ObservedPort(10011, "java")))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(ObservedPort(10011, "java")), viewModel.observedPorts[worktreePath])
    }

    @Test
    fun `Orca's origins are read with the agents`() = runTest(dispatcher) {
        val origins = OrcaOrigins(setOf(worktreePath), "/home/dev/orca/workspaces") { it }
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(orca = OrcaUseCases.NONE.copy(origins = { origins })))
        advanceUntilIdle()

        assertEquals(origins, viewModel.orcaOrigins)
    }

    @Test
    fun `the remove dialog says when the worktree has Orca terminals`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(withOrca(listingResponder(), orcaWithTerminals(worktreePath)))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertTrue(viewModel.pendingRemovalHasOrcaTerminals)
    }

    @Test
    fun `the remove dialog shows no Orca notice when Orca isn't running`() = runTest(dispatcher) {
        val orca = orcaWithTerminals(worktreePath).apply { running = false }
        val viewModel = projectWorktrees(withOrca(listingResponder(), orca))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertFalse(viewModel.pendingRemovalHasOrcaTerminals)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertEquals(0, orca.starts)
    }

    @Test
    fun `removing a worktree with Orca terminals closes them`() = runTest(dispatcher) {
        val orca = orcaWithTerminals(worktreePath)
        val viewModel = projectWorktrees(withOrca(listingResponder(), orca))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), orca.closedWorktrees)
        assertTrue(viewModel.orcaCloseFailures.isEmpty())
    }

    @Test
    fun `a failing Orca close is shown as a warning after the removal`() = runTest(dispatcher) {
        val failure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable")
        val viewModel = projectWorktrees(withOrca(listingResponder(), orcaWithTerminals(worktreePath, closeFailures = mapOf(worktreePath to failure))))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertEquals(failure, viewModel.orcaCloseFailures.single().error)
        viewModel.dismissOrcaCloseFailures()
        assertTrue(viewModel.orcaCloseFailures.isEmpty())
    }

    @Test
    fun `the cleanup dialog marks the candidates with Orca terminals and cleanup closes them`() = runTest(dispatcher) {
        val failure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable")
        val orca = orcaWithTerminals(worktreePath, closeFailures = mapOf(worktreePath to failure))
        val board = board(withOrca(mergedResponder(), orca))
        advanceUntilIdle()

        board.openCleanup()
        advanceUntilIdle()
        assertEquals(setOf(worktreePath), board.project.orcaTerminalPaths)
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
        assertEquals(listOf(worktreePath), board.project.orcaCloseFailures.map { it.path })
    }

    private fun withHerdr(
        responder: (List<String>) -> CommandResult,
        herdr: FakeHerdrClient,
    ): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
        val close = CloseHerdrWorkspacesUseCase(config, herdr)
        return useCasesWith(executor).copy(
            remove = RemoveWorktreeUseCase(config, executor, close),
            cleanUp = CleanUpWorktreesUseCase(config, executor, close),
            herdr = HerdrUseCases.NONE.copy(openWorkspaces = close::openWorkspaces),
        )
    }

    @Test
    fun `the remove dialog says when the worktree is open in Herdr`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(withHerdr(listingResponder(), FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertTrue(viewModel.pendingRemovalOpenInHerdr)
    }

    @Test
    fun `the remove dialog shows no Herdr notice for a worktree that is not open`() = runTest(dispatcher) {
        val viewModel = projectWorktrees(withHerdr(listingResponder(), FakeHerdrClient()))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertFalse(viewModel.pendingRemovalOpenInHerdr)
    }

    @Test
    fun `the remove dialog works while the Herdr lookup is pending or failed`() = runTest(dispatcher) {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"), listFailure = WorktreeError.HerdrNotInstalled)
        val viewModel = projectWorktrees(withHerdr(listingResponder(), herdr))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        assertFalse(viewModel.pendingRemovalOpenInHerdr)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertFalse(viewModel.pendingRemovalOpenInHerdr)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertTrue(viewModel.herdrCloseFailures.isEmpty())
    }

    @Test
    fun `removing a worktree open in Herdr closes its workspace`() = runTest(dispatcher) {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val viewModel = projectWorktrees(withHerdr(listingResponder(), herdr))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertEquals(listOf("w2"), herdr.closedWorkspaces)
        assertTrue(viewModel.herdrCloseFailures.isEmpty())
    }

    @Test
    fun `a failing Herdr close is shown as a warning after the removal`() = runTest(dispatcher) {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"), closeFailures = mapOf("w2" to failure))
        val viewModel = projectWorktrees(withHerdr(listingResponder(), herdr))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertEquals(listOf(worktreePath), viewModel.herdrCloseFailures.map { it.path })
        assertEquals(failure, viewModel.herdrCloseFailures.single().error)
        viewModel.dismissHerdrCloseFailures()
        assertTrue(viewModel.herdrCloseFailures.isEmpty())
    }

    @Test
    fun `the cleanup dialog marks the candidates open in Herdr`() = runTest(dispatcher) {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val board = board(withHerdr(mergedResponder(), herdr))
        advanceUntilIdle()

        board.openCleanup()
        advanceUntilIdle()

        assertEquals(setOf(worktreePath), board.project.openInHerdrPaths)
    }

    @Test
    fun `cleanup closes the Herdr workspaces and reports failures with the success`() = runTest(dispatcher) {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"), closeFailures = mapOf("w2" to failure))
        val board = board(withHerdr(mergedResponder(), herdr))
        advanceUntilIdle()

        board.openCleanup()
        advanceUntilIdle()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
        assertEquals(listOf(worktreePath), board.project.herdrCloseFailures.map { it.path })
    }

    private class RecordingHeadlessApps(running: Map<String, HeadlessAppProcess> = emptyMap()) {
        var apps = running
        val stopped = mutableListOf<String>()

        fun useCases() = HeadlessAppUseCases(
            declared = { true },
            read = { path -> Result.success(apps[path] ?: HeadlessAppProcess(HeadlessAppState.STOPPED, null, "$path.log")) },
            stop = { path ->
                stopped += path
                apps = apps - path
                Result.success(HeadlessAppStop(wasRunning = true, pid = 4242))
            },
        )
    }

    private fun runningApp() = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/state/launch.log")

    private fun failingHeadlessStop(): StopHeadlessAppUseCase {
        val files = FakeFileManager()
        val pidFile = "$worktreePath/desktopApp/build/run/main/main.pid"
        files.writtenProperties[pidFile] = mapOf("pid" to "4242")
        val launcher = FakeHeadlessAppLauncher(files, pidFile)
        launcher.alive += 4242L
        launcher.failStop = IllegalStateException("operation not permitted")
        val provisioning = FakeRepositoryProvisioningSource(
            mapOf(worktreePath to RepositoryProvisioning(providers = RepositoryProviders(desktopApp = DesktopAppProvider(":desktopApp")))),
        )
        val worktrees = HeadlessAppWorktrees(provisioning, { "${config.baseRepositoryPath}/.git/worktrees/TASK-123" }, launcher, FakeAppWindowService(), files)
        return StopHeadlessAppUseCase(worktrees, launcher)
    }

    @Test
    fun `a failed headless app stop is a notice after deleting the worktree`() = runTest(dispatcher) {
        val executor = FakeShellCommandExecutor(listingResponder())
        val viewModel = projectWorktrees(useCasesWith(executor).copy(remove = RemoveWorktreeUseCase(config, executor, stopHeadlessApp = failingHeadlessStop())))
        advanceUntilIdle()

        viewModel.requestRemoval(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertEquals(listOf(worktreePath), viewModel.headlessStopFailures.map { it.path })
        viewModel.dismissHeadlessStopFailures()
        assertTrue(viewModel.headlessStopFailures.isEmpty())
    }

    @Test
    fun `cleanup reports the headless apps it couldn't stop`() = runTest(dispatcher) {
        val executor = FakeShellCommandExecutor(mergedResponder())
        val board = board(useCasesWith(executor).copy(cleanUp = CleanUpWorktreesUseCase(config, executor, stopHeadlessApp = failingHeadlessStop())))
        advanceUntilIdle()

        board.openCleanup()
        advanceUntilIdle()
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), board.project.headlessStopFailures.map { it.path })
    }

    @Test
    fun `the headless apps load with the worktrees and stop from the holder`() = runTest(dispatcher) {
        val apps = RecordingHeadlessApps(mapOf(worktreePath to runningApp()))
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(headlessApp = apps.useCases()))
        advanceUntilIdle()

        assertTrue(viewModel.isHeadlessAppRepository)
        assertEquals(HeadlessAppState.RUNNING, viewModel.headlessApps[worktreePath]?.state)
        viewModel.stopHeadlessApp(worktree)
        viewModel.stopHeadlessApp(worktree)
        assertTrue(worktreePath in viewModel.stoppingHeadlessApps)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), apps.stopped)
        assertEquals(HeadlessAppState.STOPPED, viewModel.headlessApps[worktreePath]?.state)
        assertTrue(viewModel.stoppingHeadlessApps.isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail shows a running headless app with its process and log and stops it`() = runComposeUiTest {
        val apps = RecordingHeadlessApps(mapOf(worktreePath to runningApp()))
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(headlessApp = apps.useCases()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitUntil { onAllNodesWithText("Running (process 4242)").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Log: /home/dev/state/launch.log").assertIsDisplayed()
        onNodeWithText("Stop").performClick()
        waitUntil { onAllNodesWithText("Stopped").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf(worktreePath), apps.stopped)
        onNodeWithText("Stop").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail shows a crashed headless app as stopped without Stop`() = runComposeUiTest {
        val holder = loadedProjectWorktrees(useCases(listingResponder()).copy(headlessApp = RecordingHeadlessApps().useCases()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitUntil { onAllNodesWithText("Stopped").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Background app").assertIsDisplayed()
        onNodeWithText("Stop").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail shows nothing about a headless app without the provider`() = runComposeUiTest {
        val holder = loadedProjectWorktrees(useCases(listingResponder()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        waitForIdle()
        onNodeWithText("Background app").assertDoesNotExist()
    }

    private fun desktopTurnWatch(state: () -> DesktopTurnState, released: MutableList<Unit> = mutableListOf()) = DesktopTurnWatch(
        read = { Result.success(state()) },
        forceRelease = {
            released += Unit
            Result.success(DesktopTurnRelease.NobodyHolds)
        },
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail of the desktop holder offers to release it after confirming`() = runComposeUiTest {
        val acquiredAt = Instant.parse("2026-01-05T14:00:00Z")
        val expiresAt = acquiredAt.plusSeconds(600)
        var turn = DesktopTurnState(DesktopTurn("${config.baseRepositoryPath}/.git/worktrees/TASK-123", worktreePath, acquiredAt.toEpochMilli(), expiresAt.toEpochMilli()), emptyList())
        val released = mutableListOf<Unit>()
        val watch = desktopTurnWatch({ turn }, released)
        runBlocking { watch.refresh() }
        val holder = loadedProjectWorktrees(useCases(listingResponder()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder, desktopTurn = watch) } }

        onNodeWithText("Has the desktop").assertIsDisplayed()
        onNodeWithText("Since ${acquiredAt.toLocalClockTime()} until ${expiresAt.toLocalClockTime()}").assertIsDisplayed()
        onNodeWithText("Release").performClick()
        onNodeWithText("Release the desktop?").assertIsDisplayed()
        turn = DesktopTurnState(null, emptyList())
        onAllNodesWithText("Release")[1].performClick()
        waitUntil { released.isNotEmpty() }
        waitUntil { onAllNodesWithText("Has the desktop").fetchSemanticsNodes().isEmpty() }

        onNodeWithText("Desktop turn").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the detail of a waiter shows its place and who holds the desktop`() = runComposeUiTest {
        val since = Instant.parse("2026-01-05T14:03:00Z")
        val other = DesktopTurn("/home/dev/projects/api/.git/worktrees/TASK-1", "/home/dev/projects/api-worktrees/TASK-1", 0, Long.MAX_VALUE)
        val tickets = listOf(
            WaitTicket("a", "/home/dev/projects/api/.git/worktrees/TASK-9", "/home/dev/projects/api-worktrees/TASK-9", ProcessIdentity(1, 1), 0),
            WaitTicket("b", "${config.baseRepositoryPath}/.git/worktrees/TASK-123", worktreePath, ProcessIdentity(2, 1), since.toEpochMilli()),
        )
        val watch = desktopTurnWatch({ DesktopTurnState(other, tickets) })
        runBlocking { watch.refresh() }
        val holder = loadedProjectWorktrees(useCases(listingResponder()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder, desktopTurn = watch) } }

        onNodeWithText("Waiting for the desktop, #2 since ${since.toLocalClockTime()}, behind api · TASK-1").assertIsDisplayed()
        onNodeWithText("Release").assertDoesNotExist()
    }

    private class ToolCalls {
        val terminals = mutableListOf<Pair<String, TerminalId>>()
        val editors = mutableListOf<Pair<String, EditorId>>()
        val folders = mutableListOf<String>()
        val claudeDesktop = mutableListOf<String>()
        var failure: Throwable? = null
    }

    private suspend fun withTools(
        detected: DetectedTools,
        chosenTerminal: TerminalId? = null,
        chosenEditor: EditorId? = null,
        editorOverride: EditorId? = null,
        calls: ToolCalls = ToolCalls(),
    ): Pair<ProjectWorktrees, FakeToolPreferenceRepository> {
        val repository = FakeToolPreferenceRepository(chosenTerminal, chosenEditor)
        val preferences = ToolsPreferences(repository, FakeToolDetector(detected)).also { it.refresh() }
        val tools = ToolUseCases(
            preferences = preferences,
            editorOverride = editorOverride,
            openInTerminal = { path, terminal, _ -> calls.terminals += path to terminal; calls.failure?.let { Result.failure(it) } ?: Result.success(Unit) },
            openInEditor = { path, editor -> calls.editors += path to editor; calls.failure?.let { Result.failure(it) } ?: Result.success(Unit) },
            openFolder = { path -> calls.folders += path; calls.failure?.let { Result.failure(it) } ?: Result.success(Unit) },
            openInClaudeDesktop = { path -> calls.claudeDesktop += path; calls.failure?.let { Result.failure(it) } ?: Result.success(Unit) },
        )
        val holder = projectWorktrees(useCases(listingResponder()).copy(tools = tools))
        return holder to repository
    }

    private val installed = DetectedTools(
        terminals = listOf(TerminalId.TERMINAL, TerminalId.ITERM2),
        editors = listOf(EditorId.VS_CODE, EditorId.ANDROID_STUDIO),
    )

    @Test
    fun `the open actions name the chosen terminal and the default IDE`() = runTest(dispatcher) {
        val (holder) = withTools(installed, chosenTerminal = TerminalId.ITERM2)
        advanceUntilIdle()

        val actions = holder.toolOpens(holder.worktrees.first { it.path == worktreePath })

        assertEquals(
            listOf(
                OpenTarget.TERMINAL to "Open in iTerm2",
                OpenTarget.EDITOR to "Open in VS Code",
                OpenTarget.FOLDER to "Open folder",
            ),
            actions,
        )
    }

    @Test
    fun `Claude Desktop is offered after the IDE and before the folder when detected`() = runTest(dispatcher) {
        val (holder) = withTools(installed.copy(claudeDesktop = true), chosenTerminal = TerminalId.ITERM2)
        advanceUntilIdle()

        val actions = holder.toolOpens(holder.worktrees.first { it.path == worktreePath })

        assertEquals(
            listOf(OpenTarget.TERMINAL, OpenTarget.EDITOR, OpenTarget.CLAUDE_DESKTOP, OpenTarget.FOLDER),
            actions.map { it.first },
        )
        assertEquals("Open in Claude Desktop", actions.first { it.first == OpenTarget.CLAUDE_DESKTOP }.second)
    }

    @Test
    fun `Claude Desktop isn't offered when it isn't detected or the folder is missing`() = runTest(dispatcher) {
        val (missingApp) = withTools(installed)
        val (prunable) = withTools(installed.copy(claudeDesktop = true))
        advanceUntilIdle()

        val offered = missingApp.toolOpens(missingApp.worktrees.first { it.path == worktreePath })

        assertTrue(offered.none { it.first == OpenTarget.CLAUDE_DESKTOP })
        val missingFolder = prunable.actionCatalog(Worktree(path = worktreePath, branch = null, isPrunable = true), EnglishStrings)
        assertTrue(missingFolder.opens.none { it.isAvailable })
    }

    @Test
    fun `opening in Claude Desktop calls the launcher with the worktree path and records a failure`() = runTest(dispatcher) {
        val calls = ToolCalls()
        val (holder) = withTools(installed.copy(claudeDesktop = true), calls = calls)
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.perform(target, ActionId.Open(OpenTarget.CLAUDE_DESKTOP))
        advanceUntilIdle()
        calls.failure = WorktreeError.ClaudeDesktopLaunchFailed("boom")
        holder.perform(target, ActionId.Open(OpenTarget.CLAUDE_DESKTOP))
        advanceUntilIdle()

        assertEquals(listOf(worktreePath, worktreePath), calls.claudeDesktop)
        assertIs<WorktreeError.ClaudeDesktopLaunchFailed>(holder.actionErrors[worktreePath])
    }

    @Test
    fun `a project's IDE override replaces the default and an uninstalled one falls back`() = runTest(dispatcher) {
        val (overridden) = withTools(installed, chosenEditor = EditorId.VS_CODE, editorOverride = EditorId.ANDROID_STUDIO)
        val (uninstalled) = withTools(installed, chosenEditor = EditorId.VS_CODE, editorOverride = EditorId.INTELLIJ_IDEA)
        advanceUntilIdle()

        fun labels(holder: ProjectWorktrees) =
            holder.toolOpens(holder.worktrees.first { it.path == worktreePath }).map { it.second }

        assertTrue("Open in Android Studio" in labels(overridden))
        assertTrue("Open in VS Code" in labels(uninstalled))
    }

    @Test
    fun `with nothing detected only the folder action is offered`() = runTest(dispatcher) {
        val (holder) = withTools(DetectedTools())
        advanceUntilIdle()

        val actions = holder.toolOpens(holder.worktrees.first { it.path == worktreePath })

        assertEquals(listOf(OpenTarget.FOLDER), actions.map { it.first })
    }

    @Test
    fun `a worktree whose folder is missing offers its open actions disabled with the reason`() = runTest(dispatcher) {
        val (holder) = withTools(installed, chosenTerminal = TerminalId.TERMINAL)
        advanceUntilIdle()

        val catalog = holder.actionCatalog(Worktree(path = worktreePath, branch = null, isPrunable = true), EnglishStrings)
        assertTrue(catalog.opens.isNotEmpty())
        assertTrue(catalog.opens.all { it.disabledReason == "The worktree's folder is missing" })
    }

    @Test
    fun `opening in the chosen terminal, the IDE and the folder calls each launcher with the worktree path`() = runTest(dispatcher) {
        val calls = ToolCalls()
        val (holder) = withTools(installed, chosenTerminal = TerminalId.TERMINAL, chosenEditor = EditorId.ANDROID_STUDIO, calls = calls)
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.openInTerminal(target)
        holder.openInEditor(target)
        holder.openFolder(target)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath to TerminalId.TERMINAL), calls.terminals)
        assertEquals(listOf(worktreePath to EditorId.ANDROID_STUDIO), calls.editors)
        assertEquals(listOf(worktreePath), calls.folders)
    }

    @Test
    fun `the first use asks for a terminal, remembers the choice and opens it`() = runTest(dispatcher) {
        val calls = ToolCalls()
        val (holder, repository) = withTools(installed, calls = calls)
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.openInTerminal(target)
        advanceUntilIdle()

        assertEquals(target, holder.terminalChoiceFor)
        assertTrue(calls.terminals.isEmpty())

        holder.chooseTerminal(TerminalId.ITERM2)
        advanceUntilIdle()

        assertNull(holder.terminalChoiceFor)
        assertEquals(TerminalId.ITERM2, repository.terminal)
        assertEquals(listOf(worktreePath to TerminalId.ITERM2), calls.terminals)
    }

    @Test
    fun `dismissing the terminal choice opens nothing`() = runTest(dispatcher) {
        val calls = ToolCalls()
        val (holder) = withTools(installed, calls = calls)
        advanceUntilIdle()

        holder.openInTerminal(holder.worktrees.first { it.path == worktreePath })
        holder.dismissTerminalChoice()
        advanceUntilIdle()

        assertNull(holder.terminalChoiceFor)
        assertTrue(calls.terminals.isEmpty())
    }

    @Test
    fun `a failed launch is shown as the worktree's action error and a later success clears it`() = runTest(dispatcher) {
        val calls = ToolCalls()
        val (holder) = withTools(installed, chosenTerminal = TerminalId.TERMINAL, calls = calls)
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }
        val failure = WorktreeError.TerminalLaunchFailed(TerminalId.TERMINAL, "boom")
        calls.failure = failure

        holder.openInTerminal(target)
        advanceUntilIdle()
        assertEquals(failure, holder.actionErrors[worktreePath])

        calls.failure = null
        holder.openInTerminal(target)
        advanceUntilIdle()
        assertNull(holder.actionErrors[worktreePath])
    }

    private fun provisioningCase(
        drift: MutableList<ProvisionDrift>,
        provisioned: MutableList<String> = mutableListOf(),
        environment: WorktreeEnvironment = WorktreeEnvironment(),
    ): ProjectWorktrees {
        val base = useCases { command -> if (command == listCommand) success(command, porcelainOutput) else success(command) }
        return projectWorktrees(
            base.copy(
                provisioning = ProvisioningUseCases(
                    check = { Result.success(drift.toList()) },
                    provision = { path ->
                        provisioned += path
                        drift.clear()
                        Result.success(ProvisionResult(emptyList()))
                    },
                ),
                readEnvironment = { Result.success(environment) },
            ),
        )
    }

    private fun ProjectWorktrees.linked(): Worktree = worktrees.first { it.path == worktreePath }

    @Test
    fun `a never provisioned worktree lists its missing files and can be provisioned`() = runTest(dispatcher) {
        val drift = mutableListOf(ProvisionDrift(".env", ProvisionDriftState.MISSING), ProvisionDrift("local.properties", ProvisionDriftState.MISSING))
        val provisioned = mutableListOf<String>()
        val viewModel = provisioningCase(drift, provisioned)
        advanceUntilIdle()
        val worktree = viewModel.linked()

        viewModel.loadDetail(worktree)
        assertEquals(ProvisioningState.Checking, viewModel.provisioning[worktreePath])
        advanceUntilIdle()
        assertEquals(ProvisioningState.Checked(drift.toList()), viewModel.provisioning[worktreePath])

        viewModel.provision(worktree)
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), provisioned)
        assertEquals(ProvisioningState.Checked(emptyList()), viewModel.provisioning[worktreePath])
        assertTrue(viewModel.provisioningInProgress.isEmpty())
    }

    @Test
    fun `a stale copy is listed as differing`() = runTest(dispatcher) {
        val drift = mutableListOf(ProvisionDrift(".env", ProvisionDriftState.DIFFERS))
        val viewModel = provisioningCase(drift)
        advanceUntilIdle()

        viewModel.loadDetail(viewModel.linked())
        advanceUntilIdle()

        assertEquals(ProvisioningState.Checked(listOf(ProvisionDrift(".env", ProvisionDriftState.DIFFERS))), viewModel.provisioning[worktreePath])
    }

    @Test
    fun `an up to date worktree has no drift`() = runTest(dispatcher) {
        val viewModel = provisioningCase(mutableListOf())
        advanceUntilIdle()

        viewModel.loadDetail(viewModel.linked())
        advanceUntilIdle()

        assertEquals(ProvisioningState.Checked(emptyList()), viewModel.provisioning[worktreePath])
    }

    @Test
    fun `the main worktree is never checked for provisioning`() = runTest(dispatcher) {
        val viewModel = provisioningCase(mutableListOf(ProvisionDrift(".env", ProvisionDriftState.MISSING)))
        advanceUntilIdle()

        viewModel.loadDetail(Worktree(path = config.baseRepositoryPath, branch = "main", isMain = true))
        advanceUntilIdle()

        assertTrue(viewModel.provisioning.isEmpty())
    }

    @Test
    fun `the detail loads the environment variables and copies them in dotenv format`() = runTest(dispatcher) {
        val environment = WorktreeEnvironment(
            variables = listOf("PORT" to "20001", "APP_SUFFIX" to "-TASK-123"),
            ports = mapOf("port" to 20001),
        )
        val viewModel = provisioningCase(mutableListOf(), environment = environment)
        advanceUntilIdle()

        viewModel.loadDetail(viewModel.linked())
        advanceUntilIdle()

        assertEquals(environment, viewModel.environments[worktreePath])
        assertEquals("PORT=20001\nAPP_SUFFIX=-TASK-123\n", environmentAsDotenv(environment))
    }

    @Test
    fun `the detail names variables awaiting approval without values and leaves them out of the copy`() = runTest(dispatcher) {
        val environment = WorktreeEnvironment(
            variables = listOf("ANDROID_SERIAL" to "emulator-5554"),
            awaitingApproval = listOf("PORT", "APP_SUFFIX"),
            declarationApproved = false,
        )
        val viewModel = provisioningCase(mutableListOf(), environment = environment)
        advanceUntilIdle()

        viewModel.loadDetail(viewModel.linked())
        advanceUntilIdle()

        assertEquals(listOf("PORT", "APP_SUFFIX"), viewModel.environments[worktreePath]?.awaitingApproval)
        assertEquals("ANDROID_SERIAL=emulator-5554\n", environmentAsDotenv(environment))
        assertEquals(
            "Awaiting approval: PORT, APP_SUFFIX. Approve them in project settings or with shogun trust.",
            EnglishStrings.variablesAwaitingApproval(environment.awaitingApproval),
        )
    }

    @Test
    fun `removing a worktree releases the resources planned before the removal`() = runTest(dispatcher) {
        val steps = mutableListOf<String>()
        val base = useCases { command ->
            if (command == listCommand) {
                success(command, porcelainOutput)
            } else {
                if (command.take(3) == listOf("git", "worktree", "remove")) steps += "removed"
                success(command)
            }
        }
        val viewModel = projectWorktrees(
            base.copy(
                releaseResources = ReleaseResourcesUseCases(
                    plan = { _, _ ->
                        steps += "planned"
                        WorktreeResourcePlan(emulatorSerial = "emulator-5554")
                    },
                    release = { plan ->
                        steps += "released ${plan.emulatorSerial}"
                        ReleasedResources(stoppedEmulators = listOfNotNull(plan.emulatorSerial))
                    },
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.remove(viewModel.linked(), branchToDelete = null)
        advanceUntilIdle()

        assertEquals(listOf("planned", "removed", "released emulator-5554"), steps)

    }

    private val codex = AgentLaunchConfig(name = "Codex", agentCommand = "codex", useHeadroom = false)
    private val multiAgentConfig = config.copy(additionalAgents = listOf(codex))

    private fun withAgents(choices: AgentChoices = AgentChoices(), projectConfig: ProjectConfig = multiAgentConfig) =
        useCases(listingResponder()).copy(agents = AgentUseCases(projectConfig, choices))

    @Test
    fun `launching with another agent runs it and remembers it for the next launch`() = runTest(dispatcher) {
        val holder = projectWorktrees(withAgents())
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.copyLaunchCommand(target, picked = codex)
        advanceUntilIdle()
        holder.copyLaunchCommand(target)
        advanceUntilIdle()

        assertEquals(listOf("cd '$worktreePath' && codex", "cd '$worktreePath' && codex"), clipboard.writtenText)
        assertEquals(codex, holder.agentFor(target))
        assertEquals("Codex", holder.actionCatalog(target, EnglishStrings).context.agent)
    }

    @Test
    fun `setting the agent from the detail changes the launch without launching`() = runTest(dispatcher) {
        val holder = projectWorktrees(withAgents())
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.setAgent(target, codex)
        holder.copyLaunchCommand(target)
        advanceUntilIdle()

        assertEquals(listOf("cd '$worktreePath' && codex"), clipboard.writtenText)
    }

    @Test
    fun `choosing the default agent clears the remembered one`() = runTest(dispatcher) {
        val choices = AgentChoices()
        val holder = projectWorktrees(withAgents(choices))
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.setAgent(target, codex)
        holder.setAgent(target, config.agentLaunchConfig)

        assertEquals(emptyMap(), choices.chosen)
    }

    @Test
    fun `a removed agent falls back to the default one`() = runTest(dispatcher) {
        val choices = AgentChoices(mapOf(worktreePath to "Codex"))
        val holder = projectWorktrees(withAgents(choices, projectConfig = config))
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        holder.copyLaunchCommand(target)
        advanceUntilIdle()

        assertEquals(listOf("cd '$worktreePath' && claude"), clipboard.writtenText)
        assertEquals(emptyMap(), choices.chosen)
    }

    @Test
    fun `a single agent shows no choice`() = runTest(dispatcher) {
        val holder = projectWorktrees(withAgents(projectConfig = config))
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        assertFalse(holder.hasAgentChoice)
        assertTrue(holder.actionCatalog(target, EnglishStrings).agentChoices.isEmpty())
    }

    @Test
    fun `the palette offers launches with the other agents`() = runTest(dispatcher) {
        val holder = projectWorktrees(withAgents())
        advanceUntilIdle()
        val target = holder.worktrees.first { it.path == worktreePath }

        val withOtherAgent = holder.actionCatalog(target, EnglishStrings).paletteEntries(EnglishStrings).filter { it.agent != null }

        assertTrue(withOtherAgent.isNotEmpty())
        assertTrue(withOtherAgent.all { it.agent == "Codex" })
        assertTrue(withOtherAgent.any { it.title == "Copy launch command with Codex" })
    }

    @Test
    fun `opening in Herdr with a picked agent passes it on`() = runTest(dispatcher) {
        val used = mutableListOf<AgentLaunchConfig>()
        val herdr = HerdrUseCases.NONE.copy(open = { _, agent -> used += agent; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) })
        val holder = projectWorktrees(withAgents().copy(herdr = herdr))
        advanceUntilIdle()

        holder.openInHerdr(holder.worktrees.first { it.path == worktreePath }, codex)
        advanceUntilIdle()

        assertEquals(listOf(codex), used)
    }

    private fun ProjectWorktrees.toolOpens(worktree: Worktree): List<Pair<OpenTarget, String>> =
        actionCatalog(worktree, EnglishStrings).opens
            .map { (it.id as ActionId.Open).target to it.label }
            .filter { it.first != OpenTarget.HERDR && it.first != OpenTarget.ORCA }
}
