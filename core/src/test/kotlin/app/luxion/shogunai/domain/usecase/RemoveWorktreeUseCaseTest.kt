package app.luxion.shogunai.domain.usecase

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

class RemoveWorktreeUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )
    private val worktreePath = "/home/dev/projects/TASK-123"

    @Test
    fun `removes worktree without deleting branch by default`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertEquals(listOf(listOf("git", "worktree", "remove", worktreePath)), executor.executedCommands)
    }

    @Test
    fun `deletes branch when requested`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        useCase(worktreePath, branchToDelete = "feature/TASK-123").getOrThrow()

        assertTrue(executor.executed("git", "worktree", "remove", worktreePath))
        assertTrue(executor.executed("git", "branch", "-d", "feature/TASK-123"))
    }

    @Test
    fun `adds force flag when requested`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        useCase(worktreePath, force = true).getOrThrow()

        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }

    @Test
    fun `fails when git remove fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "is dirty") }
        val useCase = RemoveWorktreeUseCase(config, executor)

        val error = useCase(worktreePath).exceptionOrNull()

        val gitError = assertIs<WorktreeError.GitCommandFailed>(error)
        assertEquals(1, gitError.exitCode)
    }

    @Test
    fun `does not attempt branch deletion when remove fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        useCase(worktreePath, branchToDelete = "feature/TASK-123")

        assertEquals(1, executor.executedCommands.size)
    }
}
