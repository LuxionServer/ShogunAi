package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.FakeTerminalEmulatorDetector
import app.luxion.shogunai.domain.FakeTerminalLauncher
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ListEligibleBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.OpenWorktreeTerminalUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
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

    private fun useCases(responder: (List<String>) -> CommandResult): WorktreeUseCases {
        val executor = FakeShellCommandExecutor(responder)
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
            listEligibleBranches = ListEligibleBranchesUseCase(config, executor),
            remove = RemoveWorktreeUseCase(config, executor),
            openTerminal = OpenWorktreeTerminalUseCase(config, FakeTerminalLauncher(), FakeTerminalEmulatorDetector()),
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

        assertNull(viewModel.errorMessage)
        assertTrue(viewModel.worktrees.any { it.path == branchWorktreePath && it.branch == branch })
    }

    @Test
    fun `createFromBranch sets error message when the branch doesn't exist`() = runTest(dispatcher) {
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

        assertEquals(true, viewModel.errorMessage?.contains("Branch not found"))
        assertTrue(viewModel.worktrees.none { it.branch == branch })
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
        assertNull(viewModel.errorMessage)
        assertTrue(viewModel.worktrees.any { it.path == worktreePath })
    }

    @Test
    fun `sets error message when remove fails for a reason unrelated to force`() = runTest(dispatcher) {
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
        assertEquals(
            true,
            viewModel.errorMessage?.contains("is not a working tree"),
        )
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
    fun `refresh surfaces a failure via errorMessage when ListWorktreesUseCase fails`() = runTest(dispatcher) {
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
        assertNull(viewModel.errorMessage)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(true, viewModel.errorMessage?.contains("not a git repository"))
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
}
