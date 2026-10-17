package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.BranchOption
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
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListLocalBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import kotlinx.coroutines.CompletableDeferred
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

class WorktreeViewModelTest {

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
    private val forEachRefCommand = listOf("git", "for-each-ref", "--format=%(refname:short)", "refs/heads")

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

    private fun useCases(responder: (List<String>) -> CommandResult): WorktreeUseCases =
        useCasesWith(FakeShellCommandExecutor(responder))

    private fun useCasesWithFiles(fileManager: FakeFileManager, responder: (List<String>) -> CommandResult): WorktreeUseCases =
        useCasesWith(FakeShellCommandExecutor(responder), fileManager)

    private fun useCasesWith(executor: ShellCommandExecutor, fileManager: FakeFileManager = FakeFileManager()): WorktreeUseCases {
        return WorktreeUseCases(
            list = ListWorktreesUseCase(config, executor),
            create = CreateWorktreeUseCase(config, executor, FakeFileManager(), FakeRepositoryProvisioningSource()),
            createFromBranch = CreateWorktreeFromBranchUseCase(
                config,
                executor,
                FakeFileManager(
                    existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) },
                ),
                FakeRepositoryProvisioningSource(),
            ),
            listLocalBranches = ListLocalBranchesUseCase(config, executor),
            remove = RemoveWorktreeUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, clipboard),
            repair = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath))),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = DetectRunningAgentsUseCase(config, executor),
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

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))

    private fun listingResponder(executed: MutableList<List<String>> = mutableListOf()): (List<String>) -> CommandResult = { command ->
        executed += command
        if (command == listCommand) success(command, porcelainOutput) else success(command)
    }

    @Test
    fun `createFromBranch adds the worktree on success`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val branchWorktreePath = "/home/dev/projects/hotfix-security-patch"
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "branch" && command.getOrNull(2) == "--list" ->
                        success(command, stdout = "  $branch\n")
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
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "branch" && command.getOrNull(2) == "--list" -> success(command, stdout = "")
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
        val viewModel = WorktreeViewModel(useCases(listingResponder()))
        advanceUntilIdle()
        viewModel.openCreateDialog()
        viewModel.updateTaskId("TASK-456")
        viewModel.selectedBranch = "hotfix/security-patch"
        viewModel.dismissCreateDialog()

        viewModel.openCreateDialog()

        assertTrue(viewModel.isCreateDialogOpen)
        assertEquals("", viewModel.taskId)
        assertNull(viewModel.selectedBranch)
        assertNull(viewModel.createError)
    }

    @Test
    fun `a failed creation keeps the dialog open with the task id and its error`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(useCases(listingResponder()))
        advanceUntilIdle()
        viewModel.openCreateDialog()
        viewModel.updateTaskId("TASK-456")

        viewModel.confirmCreate()
        advanceUntilIdle()

        assertTrue(viewModel.isCreateDialogOpen)
        assertEquals("TASK-456", viewModel.taskId)
        assertIs<WorktreeError.BaseRepositoryNotFound>(viewModel.createError)
        assertNull(viewModel.error)
        assertNull(viewModel.success)
    }

    @Test
    fun `a successful creation closes the dialog and remembers the new worktree`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "branch" && command.getOrNull(2) == "--list" ->
                        success(command, stdout = "  $branch\n")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.openCreateDialog()

        viewModel.createFromBranch(branch)
        advanceUntilIdle()

        assertFalse(viewModel.isCreateDialogOpen)
        assertEquals("/home/dev/projects/hotfix-security-patch", viewModel.lastCreatedWorktree?.path)
        assertEquals(WorktreeSuccess.Created("/home/dev/projects/hotfix-security-patch"), viewModel.success)
    }

    @Test
    fun `dismissing the create dialog is ignored while creating`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(useCases(listingResponder()))
        advanceUntilIdle()
        viewModel.openCreateDialog()
        viewModel.updateTaskId("TASK-456")

        viewModel.confirmCreate()
        viewModel.dismissCreateDialog()

        assertTrue(viewModel.isCreating)
        assertTrue(viewModel.isCreateDialogOpen)
        advanceUntilIdle()
    }

    @Test
    fun `confirmCreate does nothing with an invalid task id`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(useCases(listingResponder(executed)))
        advanceUntilIdle()
        viewModel.openCreateDialog()
        viewModel.updateTaskId("bad..id")

        viewModel.confirmCreate()
        advanceUntilIdle()

        assertFalse(viewModel.isCreating)
        assertNull(viewModel.createError)
        assertTrue(executed.none { it.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `copying from the creation message copies the new worktree's launch command`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when {
                    command == listCommand -> success(command, porcelainOutput)
                    command.getOrNull(1) == "branch" && command.getOrNull(2) == "--list" ->
                        success(command, stdout = "  $branch\n")
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
        assertTrue(clipboard.writtenText.single().startsWith("cd \"/home/dev/projects/hotfix-security-patch\" && "))
    }

    @Test
    fun `dismissError clears the error message`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> failure(command, stderr = "not a git repository")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        assertIs<WorktreeError>(viewModel.error)

        viewModel.dismissError()

        assertNull(viewModel.error)
    }

    @Test
    fun `sets pending removal in force state when remove fails suggesting force`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
    fun `sets error when remove fails for a reason unrelated to force`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    removeCommand -> failure(command, stderr = "fatal: '$worktreePath' is not a working tree")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingRemoval)
        val error = assertIs<WorktreeError.GitCommandFailed>(viewModel.error)
        assertTrue(error.errorOutput.contains("is not a working tree"))
    }

    @Test
    fun `confirmPendingRemoval retries with force and clears pending state on success`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
        assertTrue(executedCommands.none { it == listOf("git", "branch", "-d", worktree.branch) })
    }

    @Test
    fun `confirmPendingRemoval deletes the branch when the checkbox is checked`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(
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
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-d", worktree.branch) })
    }

    @Test
    fun `force-eligible failure preserves the chosen deleteBranch value`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(
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
        assertTrue(executedCommands.any { it == listOf("git", "branch", "-d", worktree.branch) })
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
        val viewModel = WorktreeViewModel(
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
    fun `refresh surfaces a failure via error when ListWorktreesUseCase fails`() = runTest(dispatcher) {
        var listCallCount = 0
        val viewModel = WorktreeViewModel(
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
        assertNull(viewModel.error)

        viewModel.refresh()
        advanceUntilIdle()

        val error = assertIs<WorktreeError.GitCommandFailed>(viewModel.error)
        assertTrue(error.errorOutput.contains("not a git repository"))
    }

    @Test
    fun `refresh also reloads local branches when create mode is existing branch`() = runTest(dispatcher) {
        var forEachRefCallCount = 0
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> {
                        forEachRefCallCount++
                        success(command, stdout = "feature/TASK-123\nhotfix/security-patch\n")
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        viewModel.createMode = CreateMode.EXISTING_BRANCH
        viewModel.loadLocalBranches()
        advanceUntilIdle()
        assertEquals(1, forEachRefCallCount)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, forEachRefCallCount)
        assertEquals(
            listOf(
                BranchOption("feature/TASK-123", isCheckedOut = true),
                BranchOption("hotfix/security-patch", isCheckedOut = false),
            ),
            viewModel.localBranches,
        )
    }

    @Test
    fun `refresh does not reload local branches when create mode is new branch`() = runTest(dispatcher) {
        var forEachRefCallCount = 0
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> {
                        forEachRefCallCount++
                        success(command, stdout = "hotfix/security-patch\n")
                    }
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()
        assertEquals(CreateMode.NEW_BRANCH, viewModel.createMode)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(0, forEachRefCallCount)
    }

    @Test
    fun `updateTaskId normalizes whitespace to a single dash and trims`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(useCases { command -> success(command) })
        advanceUntilIdle()

        viewModel.updateTaskId("  TASK   123  ")

        assertEquals("TASK-123", viewModel.taskId)
    }

    @Test
    fun `loadLocalBranches populates localBranches flagging already checked out branches`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> success(command, stdout = "feature/TASK-123\nhotfix/security-patch\n")
                    else -> success(command)
                }
            },
        )
        advanceUntilIdle()

        viewModel.loadLocalBranches()
        advanceUntilIdle()

        assertEquals(
            listOf(
                BranchOption("feature/TASK-123", isCheckedOut = true),
                BranchOption("hotfix/security-patch", isCheckedOut = false),
            ),
            viewModel.localBranches,
        )
        assertEquals(false, viewModel.isLoadingBranches)
    }

    @Test
    fun `dismissPendingRemoval clears pending state without executing another command`() = runTest(dispatcher) {
        val executedCommands = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when (command) {
                    listCommand -> success(command, porcelainOutput)
                    forEachRefCommand -> success(command, stdout = "develop\nfeature/TASK-123\n")
                    else -> success(command)
                }
            },
        )
        viewModel.loadLocalBranches()
        advanceUntilIdle()

        viewModel.selectedBranch = "develop"
        assertTrue(viewModel.isSelectedBranchAvailable)

        viewModel.selectedBranch = "feature/TASK-123"
        assertFalse(viewModel.isSelectedBranchAvailable)

        viewModel.selectedBranch = "missing"
        assertFalse(viewModel.isSelectedBranchAvailable)

        viewModel.selectedBranch = null
        assertFalse(viewModel.isSelectedBranchAvailable)
    }

    private val statusCommand = listOf("git", "--no-optional-locks", "status", "--porcelain")

    @Test
    fun `statuses are read for every listed worktree`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(useCasesWith(executor))
        advanceUntilIdle()

        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
        assertEquals(WorktreeStatusState.Loading, viewModel.statuses[worktreePath])

        statusGate.complete(Unit)
        advanceUntilIdle()

        assertIs<WorktreeStatusState.Loaded>(viewModel.statuses[worktreePath])
    }

    @Test
    fun `a worktree whose status can't be read is unavailable without failing the list`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
    fun `a created worktree gets its status without reloading the list`() = runTest(dispatcher) {
        val branch = "hotfix/security-patch"
        var listCalls = 0
        val viewModel = WorktreeViewModel(
            useCases { command ->
                when {
                    command == listCommand -> {
                        listCalls++
                        success(command, porcelainOutput)
                    }
                    command.getOrNull(1) == "branch" && command.getOrNull(2) == "--list" ->
                        success(command, stdout = "  $branch\n")
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
        val viewModel = WorktreeViewModel(useCases(mergedResponder()))
        advanceUntilIdle()

        assertEquals(listOf(worktreePath), viewModel.cleanupCandidates.map { it.path })
    }

    @Test
    fun `cancelling the cleanup dialog runs nothing`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(useCases(mergedResponder(executed)))
        advanceUntilIdle()
        val before = executed.size

        viewModel.openCleanup()
        assertTrue(viewModel.isCleanupDialogOpen)
        assertFalse(viewModel.cleanupDeleteBranches)
        viewModel.dismissCleanup()
        advanceUntilIdle()

        assertFalse(viewModel.isCleanupDialogOpen)
        assertEquals(before, executed.size)
    }

    @Test
    fun `confirming the cleanup removes candidates, reports and reloads`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(useCases(mergedResponder(executed)))
        advanceUntilIdle()
        val listsBefore = executed.count { it == listCommand }

        viewModel.openCleanup()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("git", "worktree", "remove", worktreePath)))
        assertTrue(executed.none { it == listOf("git", "worktree", "remove", "/home/dev/projects/TASK-456") })
        assertTrue(executed.none { it.take(3) == listOf("git", "branch", "-D") })
        assertEquals(WorktreeSuccess.CleanedUp(1), viewModel.success)
        assertNull(viewModel.cleanupIssues)
        assertFalse(viewModel.isCleanupDialogOpen)
        assertEquals(listsBefore + 1, executed.count { it == listCommand })
    }

    @Test
    fun `confirming with branch deletion deletes the merged branch`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val viewModel = WorktreeViewModel(useCases(mergedResponder(executed)))
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.cleanupDeleteBranches = true
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("git", "branch", "-D", "feature/TASK-123")))
    }

    @Test
    fun `a failed removal is kept as a cleanup issue`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
            useCases(mergedResponder(removeResult = { failure(it, exitCode = 128, stderr = "use --force to delete it") })),
        )
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(0), viewModel.success)
        assertEquals(listOf(worktreePath), viewModel.cleanupIssues?.failures?.map { it.path })

        viewModel.dismissCleanupIssues()
        assertNull(viewModel.cleanupIssues)
    }

    @Test
    fun `running agents are detected for the listed worktrees`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(
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
    fun `sizes are measured for every worktree and added up, and measured again on reload`() = runTest(dispatcher) {
        val otherPath = "/home/dev/projects/TASK-456"
        val fileManager = FakeFileManager(
            existing = listOf(worktreePath, otherPath),
            sizes = mapOf(worktreePath to 1000L, otherPath to 500L),
        )
        val porcelain = "$porcelainOutput\n\nworktree $otherPath\nHEAD def456\nbranch refs/heads/feature/TASK-456"
        val viewModel = WorktreeViewModel(
            useCasesWithFiles(fileManager) { command ->
                if (command == listCommand) success(command, porcelain) else success(command)
            },
        )
        advanceUntilIdle()

        assertEquals(mapOf(worktreePath to 1000L, otherPath to 500L), viewModel.sizes)
        assertEquals(1500L, viewModel.totalSize)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(4, fileManager.sizeRequests.size)
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
        val viewModel = WorktreeViewModel(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        viewModel.startWatching()

        tick()
        assertEquals(1, counters.lists)

        files.stamps["$gitDir/refs/heads/main"] = 2
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        viewModel.stopWatching()

        assertEquals(2, counters.lists)
    }

    @Test
    fun `without changes only agents are detected again`() = runTest(dispatcher) {
        val counters = Counters()
        val viewModel = WorktreeViewModel(useCasesWithFiles(watchedFiles(), countingResponder(counters)))
        advanceUntilIdle()
        val lsofAfterLoad = counters.lsof
        viewModel.startWatching()

        tick()
        tick()
        viewModel.stopWatching()

        assertEquals(1, counters.lists)
        assertEquals(lsofAfterLoad + 2, counters.lsof)
    }

    @Test
    fun `no checks run after stopWatching`() = runTest(dispatcher) {
        val files = watchedFiles()
        val counters = Counters()
        val viewModel = WorktreeViewModel(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        val lsofAfterLoad = counters.lsof
        viewModel.startWatching()
        viewModel.stopWatching()

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
        val viewModel = WorktreeViewModel(useCasesWithFiles(files, countingResponder(counters)))
        advanceUntilIdle()
        viewModel.startWatching()

        files.stamps["$gitDir/refs/heads/main"] = 2
        viewModel.refresh()
        runCurrent()
        tick()
        tick()
        viewModel.stopWatching()

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
        val viewModel = WorktreeViewModel(useCasesWith(executor, files))
        advanceUntilIdle()
        val lsofAfterLoad = counters.lsof
        viewModel.startWatching()

        viewModel.remove(worktree, branchToDelete = null)
        runCurrent()
        files.stamps["$gitDir/HEAD"] = 2
        tick()
        tick()

        assertTrue(viewModel.isRemoving)
        assertEquals(1, counters.lists)
        assertEquals(lsofAfterLoad, counters.lsof)

        removeGate.complete(Unit)
        viewModel.stopWatching()
        runCurrent()
    }

    @Test
    fun `the resolved main branch is exposed`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(useCasesWith(executor).copy(gradle = gradleUseCases(executor, source)))
        advanceUntilIdle()

        assertFalse(viewModel.isGradleRepository)
        assertEquals(0, source.listings)
    }

    @Test
    fun `gradle repositories list daemons with their worktree and the build cache state`() = runTest(dispatcher) {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val viewModel = WorktreeViewModel(gradleRepository(listingResponder(), source, buildCacheEnabled = false))
        advanceUntilIdle()

        assertTrue(viewModel.isGradleRepository)
        assertFalse(viewModel.isGradleBuildCacheEnabled)
        val daemons = assertIs<GradleDaemonsState.Loaded>(viewModel.gradleDaemons).daemons
        assertEquals(DaemonOwner.Worktree(worktreePath), daemons.single().owner)
    }

    @Test
    fun `gradle settings are exposed only when declared`() = runTest(dispatcher) {
        val settings = GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30)
        val declared = WorktreeViewModel(gradleRepository(listingResponder(), FakeGradleDaemonSource(), provider = settings))
        val bare = WorktreeViewModel(gradleRepository(listingResponder(), FakeGradleDaemonSource()))
        advanceUntilIdle()

        assertEquals(settings, declared.gradleSettings)
        assertTrue(bare.isGradleRepository)
        assertNull(bare.gradleSettings)
    }

    @Test
    fun `memory settings are shown with their unit`() {
        assertEquals("2 GB", formatMemorySetting("2g"))
        assertEquals("1024 MB", formatMemorySetting("1024M"))
        assertEquals("512 KB", formatMemorySetting("512k"))
    }

    @Test
    fun `daemons are unavailable when processes can't be listed`() = runTest(dispatcher) {
        val source = FakeGradleDaemonSource(failure = WorktreeError.ProcessListingFailed(listOf("ps"), 1))
        val viewModel = WorktreeViewModel(gradleRepository(listingResponder(), source))
        advanceUntilIdle()

        assertEquals(GradleDaemonsState.Unavailable, viewModel.gradleDaemons)
        assertNull(viewModel.error)
    }

    @Test
    fun `stopping idle daemons keeps busy ones, reports and reloads`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle), gradleDaemon(11, DaemonState.Busy)))
        val viewModel = WorktreeViewModel(gradleRepository(listingResponder(executed), source))
        advanceUntilIdle()
        val listingsBefore = source.listings

        viewModel.stopIdleGradleDaemons()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("kill", "10")))
        assertFalse(executed.contains(listOf("kill", "11")))
        assertEquals(listOf(10L), viewModel.daemonStopReport?.stopped?.map { it.pid })
        assertEquals(listOf(11L), viewModel.daemonStopReport?.kept?.map { it.pid })
        assertEquals(listingsBefore + 2, source.listings)
        assertFalse(viewModel.isStoppingDaemons)
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
        val viewModel = WorktreeViewModel(gradleRepository(mergedResponder(executed), source))
        advanceUntilIdle()

        viewModel.openCleanup()
        assertTrue(viewModel.cleanupStopGradleDaemons)
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("kill", "10")))
        assertFalse(executed.contains(listOf("kill", "11")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, stoppedDaemons = 1), viewModel.success)
    }

    @Test
    fun `cleanup keeps daemons that became busy and reports them`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val merged = mergedResponder(executed)
        val viewModel = WorktreeViewModel(
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

        viewModel.openCleanup()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertFalse(executed.contains(listOf("kill", "10")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, keptDaemons = 1), viewModel.success)
    }

    @Test
    fun `cleanup without the option stops no daemon`() = runTest(dispatcher) {
        val executed = mutableListOf<List<String>>()
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(10, idle, lastBuildDirectory = worktreePath)))
        val viewModel = WorktreeViewModel(gradleRepository(mergedResponder(executed), source))
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.cleanupStopGradleDaemons = false
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.none { it.first() == "kill" })
        assertEquals(WorktreeSuccess.CleanedUp(1), viewModel.success)
    }

    @Test
    fun `reserved ports are shown per worktree`() = runTest(dispatcher) {
        portsByWorktree = mapOf(worktreePath to mapOf("PORT" to 20001, "port:api" to 20002))
        val viewModel = WorktreeViewModel(useCases(listingResponder()))
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
        val viewModel = WorktreeViewModel(
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
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        assertFalse(viewModel.isEmulatorRepository)
        assertEquals(0, emulators.reads)
        assertEquals(emptyMap(), viewModel.emulators)
    }

    @Test
    fun `emulator states are loaded and can be started and stopped from the row`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5554", EmulatorState.STOPPED)))
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(emulator = emulators.useCases()))
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
    fun `cleanup stops the running emulators of removed worktrees`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5556", EmulatorState.RUNNING)))
        val viewModel = WorktreeViewModel(useCases(mergedResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        viewModel.openCleanup()
        assertTrue(viewModel.cleanupStopEmulators)
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("emulator-5556"), emulators.stoppedSerials)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, stoppedEmulators = 1), viewModel.success)
    }

    @Test
    fun `cleanup without the emulator option stops none`() = runTest(dispatcher) {
        val emulators = RecordingEmulators(initial = mapOf(worktreePath to WorktreeEmulator("emulator-5556", EmulatorState.RUNNING)))
        val viewModel = WorktreeViewModel(useCases(mergedResponder()).copy(emulator = emulators.useCases()))
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.cleanupStopEmulators = false
        viewModel.confirmCleanup()
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
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        assertFalse(viewModel.isSimulatorRepository)
        assertEquals(0, simulators.reads)
    }

    @Test
    fun `simulators are loaded and can be started and stopped from the row`() = runTest(dispatcher) {
        val simulators = RecordingSimulators()
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(simulator = simulators.useCases()))
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
        val viewModel = WorktreeViewModel(useCases(mergedResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        viewModel.openCleanup()
        assertTrue(viewModel.cleanupDeleteSimulators)
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("SIM-1", "SIM-ORPHAN"), simulators.deleted)
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1, deletedSimulators = 2), viewModel.success)
    }

    @Test
    fun `cleanup looks for orphans again after removing, catching simulators it couldn't read before`() = runTest(dispatcher) {
        val simulators = RecordingSimulators()
        val viewModel = WorktreeViewModel(useCases(mergedResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()
        viewModel.openCleanup()
        simulators.orphans = listOf(SimulatorDevice("SIM-GONE", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED))

        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(listOf("SIM-GONE"), simulators.deleted)
    }

    @Test
    fun `orphaned simulators alone enable the cleanup`() = runTest(dispatcher) {
        val simulators = RecordingSimulators(orphans = listOf(SimulatorDevice("SIM-ORPHAN", "ShogunAi · main-repo · TASK-0", SimulatorState.SHUTDOWN)))
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(simulator = simulators.useCases()))
        advanceUntilIdle()

        assertTrue(viewModel.cleanupCandidates.isEmpty())
        assertTrue(viewModel.canCleanUp)
        viewModel.openCleanup()
        assertTrue(viewModel.isCleanupDialogOpen)
    }

    @Test
    fun `repositories without the xcode provider never read derived data`() = runTest(dispatcher) {
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(existing = listOf(FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj"))),
        )
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(xcode = xcodeUseCases(source)))
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
        val viewModel = WorktreeViewModel(xcodeRepository(listingResponder(), source))
        advanceUntilIdle()

        assertFalse(viewModel.isXcodeRepository)
        assertFalse(viewModel.canCleanUp)
        assertEquals(0, source.listings)
    }

    @Test
    fun `xcode repositories load worktree folders and orphans`() = runTest(dispatcher) {
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
        val viewModel = WorktreeViewModel(
            xcodeRepository(listingResponder(), source, provider = XcodeProvider(compilationCache = true)),
        )
        advanceUntilIdle()

        assertTrue(viewModel.isXcodeRepository)
        val listing = assertIs<DerivedDataState.Loaded>(viewModel.derivedData).listing
        val attributed = listing.ofWorktrees().single()
        assertEquals(worktreePath, (attributed.owner as DerivedDataOwner.Worktree).path)
        assertEquals(2_000_000_000L, attributed.folder.sizeBytes)
        assertEquals(listOf(orphan.path), listing.orphans.map { it.path })
        assertEquals(300_000_000L, listing.compilationCacheBytes)
        assertEquals(1, source.listings)
    }

    @Test
    fun `an auto-refresh check without changes doesn't reload derived data`() = runTest(dispatcher) {
        val source = CountingDerivedDataSource(FakeDerivedDataSource())
        val viewModel = WorktreeViewModel(xcodeRepository(listingResponder(), source))
        advanceUntilIdle()
        assertEquals(1, source.listings)

        viewModel.startWatching()
        tick()
        tick()
        viewModel.stopWatching()

        assertEquals(1, source.listings)
    }

    @Test
    fun `deleteOrphanedDerivedData deletes orphans, reports and reloads the section`() = runTest(dispatcher) {
        val project = worktreeProject()
        val worktreeFolder = FakeDerivedDataSource.derivedData("TASK-123", project, 2_000_000_000L)
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 500_000_000L)
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(existing = listOf(worktreeFolder, orphan), existingProjects = setOf(project)),
        )
        val viewModel = WorktreeViewModel(xcodeRepository(listingResponder(), source))
        advanceUntilIdle()
        assertTrue(viewModel.derivedDataOrphans.isNotEmpty())

        viewModel.deleteOrphanedDerivedData()
        assertTrue(viewModel.isDeletingOrphans)
        advanceUntilIdle()

        assertFalse(viewModel.isDeletingOrphans)
        assertEquals(listOf(orphan.path), source.source.deleted)
        assertEquals(3, source.listings)
        assertEquals(listOf(orphan.path), viewModel.derivedDataDeletionReport?.deleted?.map { it.path })
        assertEquals(500_000_000L, viewModel.derivedDataDeletionReport?.bytesFreed)
        assertTrue(assertIs<DerivedDataState.Loaded>(viewModel.derivedData).listing.orphans.isEmpty())

        viewModel.dismissDerivedDataDeletionReport()
        assertNull(viewModel.derivedDataDeletionReport)
    }

    @Test
    fun `cleanup deletes the removed worktrees' derived data and the orphans`() = runTest(dispatcher) {
        val project = worktreeProject()
        val worktreeFolder = FakeDerivedDataSource.derivedData("TASK-123", project, 2_000_000_000L)
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(
            FakeDerivedDataSource(existing = listOf(worktreeFolder, orphan), existingProjects = setOf(project)),
        )
        val viewModel = WorktreeViewModel(xcodeRepository(mergedResponder(), source.source))
        advanceUntilIdle()

        viewModel.openCleanup()
        assertTrue(viewModel.cleanupDeleteDerivedData)
        source.source.existingProjects = emptySet()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(setOf(worktreeFolder.path, orphan.path), source.source.deleted.toSet())
        assertEquals(
            WorktreeSuccess.CleanedUp(removedCount = 1, deletedDerivedDataFolders = 2, derivedDataBytesFreed = 2_700_000_000L),
            viewModel.success,
        )
    }

    @Test
    fun `cleanup without the option deletes no derived data`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val viewModel = WorktreeViewModel(xcodeRepository(mergedResponder(), source.source))
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.cleanupDeleteDerivedData = false
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertTrue(source.source.deleted.isEmpty())
        assertEquals(WorktreeSuccess.CleanedUp(1), viewModel.success)
    }

    @Test
    fun `a failing derived data deletion doesn't fail the cleanup`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(
            useCasesWith(FakeShellCommandExecutor(mergedResponder()))
                .copy(xcode = xcodeUseCases(ThrowingDerivedDataSource(IOException("disk error")), XcodeProvider())),
        )
        advanceUntilIdle()

        assertTrue(viewModel.isXcodeRepository)
        viewModel.openCleanup()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), viewModel.success)
    }

    @Test
    fun `orphaned derived data alone enables the cleanup`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val viewModel = WorktreeViewModel(xcodeRepository(listingResponder(), source.source))
        advanceUntilIdle()

        assertTrue(viewModel.cleanupCandidates.isEmpty())
        assertTrue(viewModel.canCleanUp)
        viewModel.openCleanup()
        assertTrue(viewModel.isCleanupDialogOpen)
    }

    @Test
    fun `cleanup without the xcode provider deletes no derived data`() = runTest(dispatcher) {
        val orphan = FakeDerivedDataSource.derivedData("Orphan-1", "/gone/App.xcodeproj", 700_000_000L)
        val source = CountingDerivedDataSource(FakeDerivedDataSource(existing = listOf(orphan)))
        val viewModel = WorktreeViewModel(useCases(mergedResponder()).copy(xcode = xcodeUseCases(source)))
        advanceUntilIdle()

        viewModel.openCleanup()
        viewModel.confirmCleanup()
        advanceUntilIdle()

        assertEquals(0, source.listings)
        assertTrue(source.source.deleted.isEmpty())
        assertFalse(viewModel.isXcodeRepository)
    }

    @Test
    fun `Herdr's worktrees folder is loaded with the list to classify origins`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(worktreesRoot = { "/home/dev/.herdr/worktrees" })
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        assertEquals("/home/dev/.herdr/worktrees", viewModel.herdrWorktreesRoot)
    }

    @Test
    fun `opening in Herdr passes the worktree and reports the opening`() = runTest(dispatcher) {
        val calls = mutableListOf<Worktree>()
        val herdr = HerdrUseCases.NONE.copy(open = { target -> calls += target; Result.success(HerdrOpening.OPENED) })
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(herdr = herdr))
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
    fun `an already open worktree is reported as focused`() = runTest(dispatcher) {
        val herdr = HerdrUseCases.NONE.copy(open = { Result.success(HerdrOpening.FOCUSED) })
        val viewModel = WorktreeViewModel(useCases(listingResponder()).copy(herdr = herdr))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.OpenedInHerdr(worktreePath, focused = true), viewModel.success)
    }

    @Test
    fun `a Herdr failure is shown as the error`() = runTest(dispatcher) {
        val viewModel = WorktreeViewModel(useCases(listingResponder()))
        advanceUntilIdle()

        viewModel.openInHerdr(viewModel.worktrees.first { it.path == worktreePath })
        advanceUntilIdle()

        assertIs<WorktreeError.HerdrNotInstalled>(viewModel.error)
        assertNull(viewModel.success)
        assertTrue(viewModel.openingInHerdr.isEmpty())
    }
}
