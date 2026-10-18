package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeHerdrClient
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

    private fun herdrUseCase(herdr: FakeHerdrClient) = CloseHerdrWorkspacesUseCase(config, herdr)

    @Test
    fun `closes the Herdr workspace after the worktree is removed`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        val removal = useCase(worktreePath).getOrThrow()

        assertEquals(listOf("w2"), herdr.closedWorkspaces)
        assertEquals(null, removal.herdrFailure)
    }

    @Test
    fun `leaves the workspace open when git fails`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { failure(it) }, herdrUseCase(herdr))

        assertTrue(useCase(worktreePath).isFailure)

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `leaves the workspace open when the worktree needs force`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "contains modified or untracked files, use --force") }

        assertTrue(RemoveWorktreeUseCase(config, executor, herdrUseCase(herdr))(worktreePath).isFailure)

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `a failing close is reported without failing the removal`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"), closeFailures = mapOf("w2" to failure))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        val removal = useCase(worktreePath).getOrThrow()

        assertEquals(failure, removal.herdrFailure)
    }

    @Test
    fun `a worktree that is not open in Herdr closes nothing`() = runTest {
        val herdr = FakeHerdrClient()
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        useCase(worktreePath).getOrThrow()

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `without a Herdr use case Herdr is never involved`() = runTest {
        val removal = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) })(worktreePath).getOrThrow()

        assertEquals(WorktreeRemoval(), removal)
    }
}
