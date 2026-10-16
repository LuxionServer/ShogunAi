package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteMergedBranchUseCaseTest {

    private val config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/main-repo", worktreesRoot = "", secretFiles = emptyList())

    @Test
    fun `deletes a merged branch with git branch -d`() = runTest {
        val executor = FakeShellCommandExecutor()

        val deleted = DeleteMergedBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertTrue(deleted)
        assertEquals(listOf("git", "branch", "-d", "feature/TASK-123"), executor.executedCommands.single())
    }

    @Test
    fun `reports a branch git keeps because it is not merged`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, stderr = "error: the branch 'feature/TASK-123' is not fully merged") }

        val deleted = DeleteMergedBranchUseCase(config, executor)("feature/TASK-123").getOrThrow()

        assertFalse(deleted)
    }
}
