package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ListLocalBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
            create = CreateWorktreeUseCase(config, executor, FakeFileManager()),
            createFromBranch = CreateWorktreeFromBranchUseCase(
                config,
                executor,
                FakeFileManager(
                    existing = listOf(config.baseRepositoryPath) + config.secretFiles.map { config.baseSecretPath(it) },
                ),
            ),
            listLocalBranches = ListLocalBranchesUseCase(config, executor),
            remove = RemoveWorktreeUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter()),
            repair = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath))),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = DetectRunningAgentsUseCase(config, executor),
            measureSize = MeasureWorktreeSizeUseCase(fileManager, dispatcher),
        )
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

        assertEquals(branch, assertIs<WorktreeError.BranchNotFound>(viewModel.error).branch)
        assertTrue(viewModel.worktrees.none { it.branch == branch })
    }

    @Test
    fun `dismissError clears the error message`() = runTest(dispatcher) {
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

    private val statusCommand = listOf("git", "status", "--porcelain")

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
}
