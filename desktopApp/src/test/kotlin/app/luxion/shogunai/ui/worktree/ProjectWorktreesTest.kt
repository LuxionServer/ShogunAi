package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.domain.model.HerdrAgentState
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.PostCreateDecision
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
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Project
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun projectWorktrees(useCases: WorktreeUseCases): ProjectWorktrees =
        ProjectWorktrees(useCases, CoroutineScope(SupervisorJob() + dispatcher), ioDispatcher = dispatcher).also { it.refresh() }

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
        approval: PostCreateApproval = PostCreateApproval.SKIP,
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
            remove = RemoveWorktreeUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false),
            repair = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath))),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor),
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
        override fun projectExists(workspacePath: String): Boolean = false
        override fun isOnUnavailableVolume(workspacePath: String): Boolean = false
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

    private fun existingBase() =
        FakeFileManager(existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) })

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))

    private fun listingResponder(executed: MutableList<List<String>> = mutableListOf()): (List<String>) -> CommandResult = { command ->
        executed += command
        if (command == listCommand) success(command, porcelainOutput) else success(command)
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
        val trust = FakeRepositoryTrust(mapOf(config.baseRepositoryPath to listOf(listOf("npm", "install"))))
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

    private fun approvalScenario(approval: DialogPostCreateApproval, trust: FakeRepositoryTrust): Pair<WorktreeBoard, FakeShellCommandExecutor> {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                config.baseRepositoryPath to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"))),
            ),
        )
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
        val approval = DialogPostCreateApproval()
        val trust = FakeRepositoryTrust()
        val (board, executor) = approvalScenario(approval, trust)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")

        board.confirmCreate()
        advanceUntilIdle()

        assertEquals(listOf(listOf("npm", "install")), board.approval.pending?.commands)
        assertTrue(board.project.isCreating)
        assertFalse(executor.executedCommands.any { it.first() == "npm" })

        board.approval.resolve(PostCreateDecision.RUN)
        advanceUntilIdle()

        assertNull(board.approval.pending)
        assertTrue(executor.executedCommands.any { it == listOf("npm", "install") })
        assertTrue(trust.isApproved(config.baseRepositoryPath, listOf(listOf("npm", "install"))))
        assertEquals(WorktreeSuccess.Created("${config.worktreesRoot}/TASK-456", commandsSkipped = false), board.project.success)
    }

    @Test
    fun `skipping the approval creates the worktree without running the commands and says so`() = runTest(dispatcher) {
        val approval = DialogPostCreateApproval()
        val trust = FakeRepositoryTrust()
        val (board, executor) = approvalScenario(approval, trust)
        advanceUntilIdle()
        board.openCreateDialog()
        board.updateTaskId("TASK-456")
        board.confirmCreate()
        advanceUntilIdle()

        board.approval.resolve(PostCreateDecision.SKIP)
        advanceUntilIdle()

        assertFalse(executor.executedCommands.any { it.first() == "npm" })
        assertFalse(trust.isApproved(config.baseRepositoryPath, listOf(listOf("npm", "install"))))
        assertEquals(WorktreeSuccess.Created("${config.worktreesRoot}/TASK-456", commandsSkipped = true), board.project.success)
    }

    @Test
    fun `approved commands are not asked again`() = runTest(dispatcher) {
        val approval = DialogPostCreateApproval()
        val trust = FakeRepositoryTrust(mapOf(config.baseRepositoryPath to listOf(listOf("npm", "install"))))
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
        assertTrue(executedCommands.none { it == listOf("git", "branch", "-d", "--", worktree.branch) })
    }

    @Test
    fun `confirmPendingRemoval deletes the branch when the checkbox is checked`() = runTest(dispatcher) {
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
        viewModel.setPendingRemovalDeleteBranch(true)
        viewModel.confirmPendingRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-d", "--", worktree.branch) })
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
                    else -> success(command)
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
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-d", "--", worktree.branch) })
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

    private val addPrefix = listOf("git", "worktree", "add", "/home/dev/projects/TASK-456", "-b", "feature/TASK-456")

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
                command.take(2) == listOf("git", "rev-parse") && command.last() == "refs/remotes/origin/develop" -> success(command)
                command.take(2) == listOf("git", "rev-parse") && command.last() == "origin/develop^{commit}" ->
                    success(command, stdout = "fetched1\n")
                command.take(2) == listOf("git", "rev-parse") && command.last() == "develop^{commit}" ->
                    success(command, stdout = "local11\n")
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
    fun `the new branch starts at the main branch by default`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario()
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()
        board.updateTaskId("TASK-456")

        assertEquals(StartOption.Branch("origin/develop"), board.effectiveStartFrom)
        assertEquals(StartOption.Branch("origin/develop"), board.startOptions.first())
        assertEquals(StartOption.Current, board.startOptions[1])

        board.confirmCreate()
        advanceUntilIdle()

        assertTrue(executor.executed(*(addPrefix + "fetched1").toTypedArray()), executor.executedCommands.toString())
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

        assertTrue(executor.executed(*addPrefix.toTypedArray()), executor.executedCommands.toString())
    }

    @Test
    fun `without a main branch the current branch is the default`() = runTest(dispatcher) {
        val (board, executor) = startPointScenario(mainBranch = null)
        advanceUntilIdle()
        board.openCreateDialog()
        advanceUntilIdle()

        assertEquals(StartOption.Current, board.effectiveStartFrom)
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

        statusGate?.complete(Unit)
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

        statusGate?.complete(Unit)
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
                    command.first() == "lsof" -> failure(command, exitCode = 127, stderr = "lsof: not found")
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
        var lsof = 0
    }

    private fun countingResponder(counters: Counters): (List<String>) -> CommandResult = { command ->
        when {
            command == listCommand -> {
                counters.lists++
                success(command, porcelainOutput)
            }
            command.first() == "lsof" -> {
                counters.lsof++
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
    fun `without changes only agents are detected again`() = runTest(dispatcher) {
        val counters = Counters()
        val board = board(useCasesWithFiles(watchedFiles(), countingResponder(counters)))
        advanceUntilIdle()
        val lsofAfterLoad = counters.lsof
        board.startWatching()

        tick()
        tick()
        board.stopWatching()

        assertEquals(1, counters.lists)
        assertEquals(lsofAfterLoad + 2, counters.lsof)
    }

    @Test
    fun `no checks run after stopWatching`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val board = board(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        val lsofAfterLoad = counters.lsof
        board.startWatching()
        board.stopWatching()

        files.stamps["$gitDir/HEAD"] = 2
        tick()
        tick()

        assertEquals(1, counters.lists)
        assertEquals(lsofAfterLoad, counters.lsof)
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
        val lsofAfterLoad = counters.lsof
        board.startWatching()

        board.project.remove(worktree, branchToDelete = null)
        runCurrent()
        files.stamps["$gitDir/HEAD"] = 2
        tick()
        tick()

        assertTrue(board.project.isRemoving)
        assertEquals(1, counters.lists)
        assertEquals(lsofAfterLoad, counters.lsof)

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
                Result.success(emulator)
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
    fun `copying a path from the detail confirms it`() = runComposeUiTest {
        val holder = projectWorktrees(useCases(listingResponder()))
        setContent { MaterialTheme { WorktreeDetailPane(worktree, config, holder) } }

        onNodeWithContentDescription("Copy path").performClick()

        onNodeWithContentDescription("Copied").assertIsDisplayed()
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
                Result.success(simulator)
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
        val herdr = HerdrUseCases.NONE.copy(open = { target -> calls += target; Result.success(HerdrOpening.OPENED) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        val target = viewModel.worktrees.first { it.path == worktreePath }

        viewModel.openInHerdr(target)
        assertTrue(worktreePath in viewModel.openingInHerdr)
        advanceUntilIdle()

        assertEquals(target, calls.single())
        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, focused = false), viewModel.success)
        assertTrue(viewModel.openingInHerdr.isEmpty())
    }

    @Test
    fun `opening in Herdr focuses the pane of the agent that needs the user`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        var opened = 0
        val herdr = HerdrUseCases.NONE.copy(
            open = { opened++; Result.success(HerdrOpening.OPENED) },
            focusAgent = { pane -> focused += pane; Result.success(Unit) },
        )
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()
        viewModel.applyAgents(
            mapOf(
                worktreePath to listOf(
                    RunningAgent("codex", HerdrAgentState.WORKING, "w1:p1"),
                    RunningAgent("claude", HerdrAgentState.BLOCKED, "w1:p2"),
                ),
            ),
        )

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(listOf("w1:p2"), focused)
        assertEquals(0, opened)
        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, focused = true), viewModel.success)
    }

    @Test
    fun `opening in Herdr opens the workspace when the agents have no pane`() = runTest(dispatcher) {
        val focused = mutableListOf<String>()
        var opened = 0
        val herdr = HerdrUseCases.NONE.copy(
            open = { opened++; Result.success(HerdrOpening.OPENED) },
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
        val herdr = HerdrUseCases.NONE.copy(open = { Result.success(HerdrOpening.FOCUSED) })
        val viewModel = projectWorktrees(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, focused = true), viewModel.success)
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
        assertEquals(config.baseRepositoryPath, board.project.orderedWorktrees.single().path)
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
    fun `retrying a failed emulator start repeats it and clears the error on success`() = runTest(dispatcher) {
        var failing = true
        var starts = 0
        val emulators = EmulatorUseCases(
            declared = { true },
            readStates = { Result.success(emptyMap()) },
            start = {
                starts++
                if (failing) Result.failure(IOException("no emulator")) else Result.success(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING))
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
}
