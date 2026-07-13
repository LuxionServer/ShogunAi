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
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
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
            remove = RemoveWorktreeUseCase(config, executor),
            openTerminal = OpenWorktreeTerminalUseCase(config, FakeTerminalLauncher(), FakeTerminalEmulatorDetector()),
        )
    }

    @Test
    fun `sets pending force removal when remove fails suggesting force`() = runTest(dispatcher) {
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

        assertEquals(worktree, viewModel.worktreePendingForceRemoval)
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

        assertNull(viewModel.worktreePendingForceRemoval)
        assertEquals(
            true,
            viewModel.errorMessage?.contains("is not a working tree"),
        )
    }

    @Test
    fun `confirmForceRemoval retries with force and clears pending state on success`() = runTest(dispatcher) {
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

        viewModel.confirmForceRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingForceRemoval)
        assertTrue(viewModel.worktrees.none { it.path == worktreePath })
    }

    @Test
    fun `dismissForceRemoval clears pending state without executing another command`() = runTest(dispatcher) {
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

        viewModel.dismissForceRemoval()
        advanceUntilIdle()

        assertNull(viewModel.worktreePendingForceRemoval)
        assertEquals(commandCountBeforeDismiss, executedCommands.size)
    }
}
