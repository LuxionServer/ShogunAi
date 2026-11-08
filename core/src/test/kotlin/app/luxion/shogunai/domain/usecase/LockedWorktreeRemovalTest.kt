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
import kotlin.test.assertNull

class LockedWorktreeRemovalTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val path = "/home/dev/projects/TASK-123"
    private val lockedWithReason =
        "fatal: cannot remove a locked working tree, lock reason: orca-create-preparation:v1\n" +
            "use 'remove -f -f' to override or unlock first"
    private val lockedWithoutReason =
        "fatal: cannot remove a locked working tree;\nuse 'remove -f -f' to override or unlock first"
    private val remove = listOf("git", "worktree", "remove", path)
    private val forcedRemove = listOf("git", "worktree", "remove", "--force", path)
    private val doublyForcedRemove = listOf("git", "worktree", "remove", "--force", "--force", path)

    private fun lockedExecutor(stderr: String = lockedWithReason) = FakeShellCommandExecutor { command ->
        if (command == doublyForcedRemove || command.take(3) != listOf("git", "worktree", "remove")) {
            success(command)
        } else {
            failure(command, exitCode = 128, stderr = stderr)
        }
    }

    private fun removal(executor: FakeShellCommandExecutor) = RemoveWorktreeUseCase(config, executor, null, null)

    @Test
    fun `a locked worktree without force reports the lock and its reason`() = runTest {
        val executor = lockedExecutor()

        val failure = removal(executor)(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeLocked>(failure)
        assertEquals(path, failure.path)
        assertEquals("orca-create-preparation:v1", failure.reason)
        assertEquals(remove, failure.command)
        assertEquals(128, failure.exitCode)
        assertEquals(listOf(remove), executor.executedCommands.filter { it.take(3) == remove.take(3) })
    }

    @Test
    fun `a lock without a reason reports none`() = runTest {
        val failure = removal(lockedExecutor(lockedWithoutReason))(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeLocked>(failure)
        assertNull(failure.reason)
    }

    @Test
    fun `forcing a locked worktree retries with force twice`() = runTest {
        val executor = lockedExecutor()

        removal(executor)(path, force = true).getOrThrow()

        assertEquals(
            listOf(forcedRemove, doublyForcedRemove),
            executor.executedCommands.filter { it.take(3) == remove.take(3) },
        )
    }

    @Test
    fun `forcing an unlocked worktree passes force once`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        removal(executor)(path, force = true).getOrThrow()

        assertEquals(listOf(forcedRemove), executor.executedCommands.filter { it.take(3) == remove.take(3) })
    }

    @Test
    fun `a locked worktree still refused with force twice reports the lock`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = lockedWithReason) }

        val failure = removal(executor)(path, force = true).exceptionOrNull()

        assertIs<WorktreeError.WorktreeLocked>(failure)
        assertEquals(doublyForcedRemove, failure.command)
    }
}
