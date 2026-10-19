package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteMergedBranchUseCaseTest {

    private val config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/main-repo", worktreesRoot = "", secretFiles = emptyList())

    private fun executor(contained: Boolean = true) = FakeShellCommandExecutor { command ->
        when {
            command.take(3) == listOf("git", "merge-base", "--is-ancestor") ->
                if (contained) success(command) else failure(command, exitCode = 1, stderr = "")
            command.getOrNull(1) == "symbolic-ref" -> failure(command)
            else -> success(command)
        }
    }

    @Test
    fun `deletes a branch contained in the main branch`() = runTest {
        val executor = executor()

        val deleted = DeleteMergedBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertTrue(deleted)
        assertTrue(executor.executed("git", "merge-base", "--is-ancestor", "feature/TASK-123", "main"))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `keeps a branch that is not contained in the main branch even if its upstream has it`() = runTest {
        val executor = executor(contained = false)

        val deleted = DeleteMergedBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertFalse(deleted)
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `keeps the branch when the main branch can't be resolved`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it) }

        val deleted = DeleteMergedBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertFalse(deleted)
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `rejects a branch that Git could read as an option`() = runTest {
        val executor = FakeShellCommandExecutor()

        val error = DeleteMergedBranchUseCase(config, executor)("-D").exceptionOrNull()

        assertEquals("-D", (error as WorktreeError.InvalidRef).ref)
        assertTrue(executor.executedCommands.isEmpty())
    }
}
