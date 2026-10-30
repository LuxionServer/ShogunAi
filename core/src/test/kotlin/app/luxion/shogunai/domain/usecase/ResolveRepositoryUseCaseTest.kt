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
            listOf("git", "rev-parse", "--path-format=absolute", "--git-common-dir", "--is-bare-repository"),
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

    private fun layoutExecutor(
        commonDir: String,
        bare: Boolean = false,
        coreBare: Boolean = false,
        coreWorktree: String? = null,
        worktrees: String = "",
    ) =
        FakeShellCommandExecutor { command ->
            when (command.getOrNull(1)) {
                "rev-parse" -> success(command, stdout = "$commonDir\n$bare\n")
                "config" -> if ("core.worktree" in command) {
                    if (coreWorktree != null) success(command, stdout = "$coreWorktree\n") else failure(command)
                } else {
                    success(command, stdout = "$coreBare\n")
                }
                "worktree" -> success(command, stdout = worktrees)
                else -> failure(command)
            }
        }

    @Test
    fun `resolves the main worktree of a submodule`() = runTest {
        val executor = layoutExecutor(
            commonDir = "/home/dev/projects/super/.git/modules/lib",
            coreWorktree = "../../../lib",
            worktrees = "worktree /home/dev/projects/super/.git/modules/lib\nbare\n",
        )

        val repository = ResolveRepositoryUseCase(executor)("/home/dev/projects/super/lib").getOrThrow()

        assertEquals("/home/dev/projects/super/lib", repository)
    }

    @Test
    fun `resolves the main worktree when the Git folder is separate`() = runTest {
        val executor = layoutExecutor(
            commonDir = "/home/dev/git-dirs/main-repo.git",
            worktrees = "worktree /home/dev/projects/main-repo\nHEAD abc\nbranch refs/heads/main\n",
        )

        val repository = ResolveRepositoryUseCase(executor)("/home/dev/projects/main-repo/src").getOrThrow()

        assertEquals("/home/dev/projects/main-repo", repository)
    }

    @Test
    fun `fails when a separate Git folder lists no worktree`() = runTest {
        val executor = layoutExecutor(commonDir = "/home/dev/git-dirs/main-repo.git", worktrees = "")

        assertIs<WorktreeError.RepositoryNotFound>(ResolveRepositoryUseCase(executor)("/home/dev/projects/main-repo/src").exceptionOrNull())
    }

    @Test
    fun `fails for a bare repository`() = runTest {
        val executor = layoutExecutor(commonDir = "/home/dev/projects/main-repo.git", bare = true)

        val error = ResolveRepositoryUseCase(executor)("/home/dev/projects/main-repo.git").exceptionOrNull()

        assertIs<WorktreeError.BareRepositoryNotSupported>(error)
    }

    @Test
    fun `fails for a worktree of a bare repository`() = runTest {
        val executor = layoutExecutor(commonDir = "/home/dev/projects/main-repo.git", coreBare = true)

        val error = ResolveRepositoryUseCase(executor)("/home/dev/projects/TASK-1").exceptionOrNull()

        assertIs<WorktreeError.BareRepositoryNotSupported>(error)
    }
}
