package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolveRepositoryUseCaseTest {

    private fun resolveWith(stdout: String) = ResolveRepositoryUseCase(FakeShellCommandExecutor { success(it, stdout = stdout) })

    @Test
    fun `resolves the main worktree from the main worktree or a subfolder`() = runTest {
        val repository = resolveWith("/home/dev/projects/main-repo/.git\n")("/home/dev/projects/main-repo/src").getOrThrow()

        assertEquals("/home/dev/projects/main-repo", repository)
    }

    @Test
    fun `resolves the main worktree from a linked worktree`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "/home/dev/projects/main-repo/.git") }

        val repository = ResolveRepositoryUseCase(executor)("/home/dev/projects/TASK-123").getOrThrow()

        assertEquals("/home/dev/projects/main-repo", repository)
        assertEquals(
            listOf("git", "rev-parse", "--path-format=absolute", "--git-common-dir"),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `fails outside a repository`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: not a git repository") }

        val error = ResolveRepositoryUseCase(executor)("/home/dev").exceptionOrNull()

        assertIs<WorktreeError.RepositoryNotFound>(error)
        assertEquals("/home/dev", error.path)
    }

    @Test
    fun `fails for a bare repository`() = runTest {
        val error = resolveWith("/home/dev/projects/main-repo.git")("/home/dev/projects/main-repo.git").exceptionOrNull()

        assertIs<WorktreeError.RepositoryNotFound>(error)
    }
}
