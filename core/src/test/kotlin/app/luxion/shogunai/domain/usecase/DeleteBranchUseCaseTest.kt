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

class DeleteBranchUseCaseTest {

    private val config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/main-repo", worktreesRoot = "", secretFiles = emptyList())

    @Test
    fun `force-deletes the branch`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        val deletion = DeleteBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertEquals(BranchDeletion.Deleted, deletion)
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `reports Git's reason when the deletion fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "not found\n") }

        val deletion = DeleteBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertEquals(BranchDeletion.KeptFailed("not found"), deletion)
    }

    @Test
    fun `rejects a branch that Git could read as an option`() = runTest {
        val executor = FakeShellCommandExecutor()

        val error = DeleteBranchUseCase(config, executor)("--foo").exceptionOrNull()

        assertIs<WorktreeError.InvalidRef>(error)
        assertTrue(executor.executedCommands.isEmpty())
    }
}
