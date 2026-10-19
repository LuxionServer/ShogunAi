package app.luxion.shogunai.domain.usecase

import kotlin.test.assertTrue
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolveStartPointUseCaseTest {

    @Test
    fun `resolves a ref to its commit in the given directory`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "abc123\n") }

        val commit = ResolveStartPointUseCase(executor)("/home/dev/projects/TASK-123", "HEAD").getOrThrow()

        assertEquals("abc123", commit)
        assertEquals(
            listOf("git", "rev-parse", "--verify", "--quiet", "HEAD^{commit}"),
            executor.executedCommands.single(),
        )
        assertEquals("/home/dev/projects/TASK-123", executor.workingDirectories.single())
    }

    @Test
    fun `fails when the ref doesn't name a commit`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "") }

        val error = ResolveStartPointUseCase(executor)("/home/dev/projects/main-repo", "missing-branch").exceptionOrNull()

        assertEquals("missing-branch", assertIs<WorktreeError.StartPointNotFound>(error).ref)
    }

    @Test
    fun `rejects a ref that Git could read as an option`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "abc123\n") }

        val error = ResolveStartPointUseCase(executor)("/home/dev/projects/TASK-123", "--upload-pack=x").exceptionOrNull()

        assertEquals("--upload-pack=x", assertIs<WorktreeError.InvalidRef>(error).ref)
        assertTrue(executor.executedCommands.isEmpty())
    }
}
