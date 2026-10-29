package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RepairWorktreesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/moved/main-repo",
        worktreesRoot = "/home/dev/moved",
        secretFiles = emptyList(),
    )

    private val listCommand = listOf("git", "worktree", "list", "--porcelain")

    private val porcelain = """
        worktree /home/dev/moved/main-repo
        HEAD abc123
        branch refs/heads/develop

        worktree /home/dev/projects/TASK-123
        HEAD def456
        branch refs/heads/feature/TASK-123

        worktree /mnt/other/TASK-456
        HEAD 789aaa
        branch refs/heads/feature/TASK-456
    """.trimIndent()

    private fun executor(repairResult: (List<String>) -> app.luxion.shogunai.domain.executor.CommandResult = { success(it) }) =
        FakeShellCommandExecutor { command ->
            if (command == listCommand) success(command, stdout = porcelain) else repairResult(command)
        }

    @Test
    fun `passes the new path of worktrees that moved along with the worktrees root`() = runTest {
        val executor = executor()
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, "/home/dev/moved/TASK-123", "/mnt/other/TASK-456"),
        )

        RepairWorktreesUseCase(config, executor, fileManager)("/home/dev/projects").getOrThrow()

        assertTrue(executor.executed("git", "worktree", "repair", "/home/dev/moved/TASK-123"))
    }

    @Test
    fun `runs a plain repair when the worktrees did not move`() = runTest {
        val executor = executor()
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, "/home/dev/projects/TASK-123", "/mnt/other/TASK-456"),
        )

        RepairWorktreesUseCase(config, executor, fileManager)("/home/dev/projects").getOrThrow()

        assertTrue(executor.executed("git", "worktree", "repair"))
    }

    @Test
    fun `fails when the base repository does not exist`() = runTest {
        val executor = executor()

        val result = RepairWorktreesUseCase(config, executor, FakeFileManager())("/home/dev/projects")

        assertIs<WorktreeError.BaseRepositoryNotFound>(result.exceptionOrNull())
        assertEquals(emptyList(), executor.executedCommands)
    }

    @Test
    fun `surfaces a failing git worktree repair as a typed error`() = runTest {
        val executor = executor { failure(it) }
        val fileManager = FakeFileManager(existing = listOf(config.baseRepositoryPath))

        val result = RepairWorktreesUseCase(config, executor, fileManager)("/home/dev/projects")

        assertIs<WorktreeError.GitCommandFailed>(result.exceptionOrNull())
    }

    @Test
    fun `fails when the worktrees can't be listed`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: not a git repository") }

        val error = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath)))().exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(error)
    }
}
