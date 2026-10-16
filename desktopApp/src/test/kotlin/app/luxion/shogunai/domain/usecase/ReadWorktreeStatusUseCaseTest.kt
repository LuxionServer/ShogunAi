package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReadWorktreeStatusUseCaseTest {

    private val worktree = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val mainBranch = MainBranch("develop", "origin/develop")

    private val statusCommand = listOf("git", "status", "--porcelain")
    private val upstreamCommand = listOf("git", "rev-list", "--left-right", "--count", "@{upstream}...HEAD")
    private val mainBranchCommand = listOf("git", "rev-list", "--left-right", "--count", "origin/develop...HEAD")
    private val lastCommitCommand = listOf("git", "log", "-1", "--format=%ct")

    private fun executor(overrides: Map<List<String>, (List<String>) -> CommandResult> = emptyMap()) =
        FakeShellCommandExecutor { command ->
            overrides[command]?.invoke(command) ?: when (command) {
                statusCommand -> success(command, stdout = " M build.gradle.kts\n?? notes.md\nA  Main.kt\n")
                upstreamCommand -> success(command, stdout = "0\t2\n")
                mainBranchCommand -> success(command, stdout = "1\t4\n")
                lastCommitCommand -> success(command, stdout = "1760000000\n")
                else -> success(command)
            }
        }

    @Test
    fun `reads changes, divergences and last commit date`() = runTest {
        val status = ReadWorktreeStatusUseCase(executor())(worktree, mainBranch).getOrThrow()

        assertEquals(3, status.uncommittedChanges)
        assertEquals(Divergence(ahead = 2, behind = 0), status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
        assertEquals(Instant.ofEpochSecond(1760000000), status.lastCommitAt)
    }

    @Test
    fun `a clean worktree has no uncommitted changes`() = runTest {
        val executor = executor(mapOf(statusCommand to { command -> success(command, stdout = "") }))

        assertEquals(0, ReadWorktreeStatusUseCase(executor)(worktree, mainBranch).getOrThrow().uncommittedChanges)
    }

    @Test
    fun `a branch without upstream has no upstream divergence`() = runTest {
        val executor = executor(
            mapOf(upstreamCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: no upstream configured") }),
        )

        val status = ReadWorktreeStatusUseCase(executor)(worktree, mainBranch).getOrThrow()

        assertNull(status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
    }

    @Test
    fun `a detached HEAD is compared without upstream`() = runTest {
        val executor = executor()

        val status = ReadWorktreeStatusUseCase(executor)(worktree.copy(branch = null), mainBranch).getOrThrow()

        assertNull(status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
        assertEquals(0, executor.executedCommands.count { it == upstreamCommand })
    }

    @Test
    fun `a worktree on the main branch has no main branch comparison`() = runTest {
        val executor = executor()

        val status = ReadWorktreeStatusUseCase(executor)(worktree.copy(branch = "develop"), mainBranch).getOrThrow()

        assertNull(status.mainBranch)
        assertEquals(0, executor.executedCommands.count { it == mainBranchCommand })
    }

    @Test
    fun `without a main branch there is no main branch comparison`() = runTest {
        assertNull(ReadWorktreeStatusUseCase(executor())(worktree, null).getOrThrow().mainBranch)
    }

    @Test
    fun `a branch without commits has no last commit date`() = runTest {
        val executor = executor(
            mapOf(lastCommitCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: bad default revision 'HEAD'") }),
        )

        assertNull(ReadWorktreeStatusUseCase(executor)(worktree, mainBranch).getOrThrow().lastCommitAt)
    }

    @Test
    fun `the status fails when git status fails`() = runTest {
        val executor = executor(
            mapOf(statusCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: cannot change to directory") }),
        )

        val error = ReadWorktreeStatusUseCase(executor)(worktree, mainBranch).exceptionOrNull()

        assertEquals(128, assertIs<WorktreeError.GitCommandFailed>(error).exitCode)
    }

    @Test
    fun `parses left-right counts as behind then ahead`() {
        assertEquals(Divergence(ahead = 7, behind = 3), parseLeftRightCount("3\t7\n"))
        assertNull(parseLeftRightCount("garbage"))
    }
}
