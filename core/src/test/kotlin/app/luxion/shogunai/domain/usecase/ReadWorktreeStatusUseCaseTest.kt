package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReadWorktreeStatusUseCaseTest {

    private val worktree = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val mainBranch = MainBranch("develop", "origin/develop")

    private val statusCommand = listOf("git", "--no-optional-locks", "status", "--porcelain")
    private val upstreamCommand = listOf("git", "rev-list", "--left-right", "--count", "@{upstream}...HEAD")
    private val mainBranchCommand = listOf("git", "rev-list", "--left-right", "--count", "origin/develop...HEAD")
    private val lastCommitCommand = listOf("git", "log", "-1", "--format=%ct")

    private val reflogCommand = listOf("git", "reflog", "show", "--format=%H", "refs/heads/feature/TASK-123")
    private val headCommand = listOf("git", "rev-parse", "HEAD")
    private val mergedMainBranch = mapOf<List<String>, (List<String>) -> CommandResult>(
        mainBranchCommand to { command -> success(command, stdout = "3\t0\n") },
        upstreamCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: no upstream configured") },
        headCommand to { command -> success(command, stdout = "abc123\n") },
    )

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
        val status = ReadWorktreeStatusUseCase(executor(), FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertEquals(3, status.uncommittedChanges)
        assertEquals(Divergence(ahead = 2, behind = 0), status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
        assertEquals(Instant.ofEpochSecond(1760000000), status.lastCommitAt)
    }

    @Test
    fun `reads state with a limit and reports a timeout as a failed read`() = runTest {
        val fake = executor()
        ReadWorktreeStatusUseCase(fake, FakeFileManager())(worktree, mainBranch).getOrThrow()
        assertEquals(CommandTimeouts.GIT_READ, fake.timeouts[statusCommand])

        val hung = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult =
                throw CommandExecutionException.TimedOut(command, CommandTimeouts.GIT_READ)
        }
        val failure = ReadWorktreeStatusUseCase(hung, FakeFileManager())(worktree, mainBranch).exceptionOrNull()

        assertIs<CommandExecutionException.TimedOut>(failure)
    }

    @Test
    fun `a clean worktree has no uncommitted changes`() = runTest {
        val executor = executor(mapOf(statusCommand to { command -> success(command, stdout = "") }))

        assertEquals(0, ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow().uncommittedChanges)
    }

    @Test
    fun `a branch without upstream has no upstream divergence`() = runTest {
        val executor = executor(
            mapOf(upstreamCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: no upstream configured") }),
        )

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertNull(status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
    }

    @Test
    fun `a detached HEAD is compared without upstream`() = runTest {
        val executor = executor()

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree.copy(branch = null), mainBranch).getOrThrow()

        assertNull(status.upstream)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
        assertEquals(0, executor.executedCommands.count { it == upstreamCommand })
    }

    @Test
    fun `a worktree on the main branch has no main branch comparison`() = runTest {
        val executor = executor()

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree.copy(branch = "develop"), mainBranch).getOrThrow()

        assertNull(status.mainBranch)
        assertEquals(0, executor.executedCommands.count { it == mainBranchCommand })
    }

    @Test
    fun `without a main branch there is no main branch comparison`() = runTest {
        assertNull(ReadWorktreeStatusUseCase(executor(), FakeFileManager())(worktree, null).getOrThrow().mainBranch)
    }

    @Test
    fun `a branch without commits has no last commit date`() = runTest {
        val executor = executor(
            mapOf(lastCommitCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: bad default revision 'HEAD'") }),
        )

        assertNull(ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow().lastCommitAt)
    }

    @Test
    fun `the status fails when git status fails`() = runTest {
        val executor = executor(
            mapOf(statusCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: cannot change to directory") }),
        )

        val error = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).exceptionOrNull()

        assertEquals(128, assertIs<WorktreeError.GitCommandFailed>(error).exitCode)
    }

    @Test
    fun `parses left-right counts as behind then ahead`() {
        assertEquals(Divergence(ahead = 7, behind = 3), parseLeftRightCount("3\t7\n"))
        assertNull(parseLeftRightCount("garbage"))
    }

    @Test
    fun `a merged-looking branch whose only reflog entry is its tip never had commits`() = runTest {
        val executor = executor(mergedMainBranch + (reflogCommand to { command -> success(command, stdout = "abc123\n") }))

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertTrue(status.neverCommitted)
        assertFalse(status.isMerged)
    }

    @Test
    fun `a fast-forward merged branch has several reflog entries and stays merged`() = runTest {
        val executor = executor(mergedMainBranch + (reflogCommand to { command -> success(command, stdout = "abc123\ndef456\n") }))

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertFalse(status.neverCommitted)
        assertTrue(status.isMerged)
    }

    @Test
    fun `a branch with an upstream stays merged without reading the reflog`() = runTest {
        val executor = executor(
            mergedMainBranch + mapOf(
                upstreamCommand to { command -> success(command, stdout = "0\t0\n") },
                reflogCommand to { command -> success(command, stdout = "abc123\n") },
            ),
        )

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertTrue(status.isMerged)
        assertEquals(0, executor.executedCommands.count { it == reflogCommand })
    }

    @Test
    fun `an unreadable reflog keeps the merged reading`() = runTest {
        val executor = executor(
            mergedMainBranch + (reflogCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: bad ref") }),
        )

        assertTrue(ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow().isMerged)
    }

    @Test
    fun `a branch that doesn't read as merged never reads the reflog`() = runTest {
        val executor = executor()

        ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertEquals(0, executor.executedCommands.count { it == reflogCommand })
    }

    private val epic = MainBranch("epic/redesign", "origin/epic/redesign")
    private val epicCommand = listOf("git", "rev-list", "--left-right", "--count", "origin/epic/redesign...HEAD")

    @Test
    fun `reads the divergence from the base besides the main branch`() = runTest {
        val executor = executor(mapOf(epicCommand to { command -> success(command, stdout = "1\t3\n") }))

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch, epic).getOrThrow()

        assertEquals(Divergence(ahead = 3, behind = 1), status.base)
        assertEquals("epic/redesign", status.baseName)
        assertEquals(Divergence(ahead = 4, behind = 1), status.mainBranch)
        assertFalse(status.isMerged)
    }

    @Test
    fun `without a recorded base the base is the main branch, read once`() = runTest {
        val executor = executor()

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertEquals(status.mainBranch, status.base)
        assertEquals("develop", status.baseName)
        assertEquals(1, executor.executedCommands.count { it == mainBranchCommand })
    }

    @Test
    fun `a branch merged into its base is merged although the main branch lacks it`() = runTest {
        val executor = executor(
            mapOf(
                epicCommand to { command -> success(command, stdout = "2\t0\n") },
                mainBranchCommand to { command -> success(command, stdout = "0\t7\n") },
                reflogCommand to { command -> success(command, stdout = "abc123\ndef456\n") },
            ),
        )

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch, epic).getOrThrow()

        assertTrue(status.isMerged)
        assertTrue(status.isMergedIntoBase)
        assertFalse(status.isMergedIntoMainBranch)
    }

    @Test
    fun `a branch merged into the main branch is merged whatever its base says`() = runTest {
        val executor = executor(
            mapOf(
                epicCommand to { command -> success(command, stdout = "0\t2\n") },
                mainBranchCommand to { command -> success(command, stdout = "3\t0\n") },
            ),
        )

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch, epic).getOrThrow()

        assertTrue(status.isMerged)
        assertFalse(status.isMergedIntoBase)
        assertTrue(status.isMergedIntoMainBranch)
    }

    @Test
    fun `a branch without commits stays not started when its base moves`() = runTest {
        val executor = executor(
            mapOf(
                epicCommand to { command -> success(command, stdout = "2\t0\n") },
                mainBranchCommand to { command -> success(command, stdout = "0\t5\n") },
                upstreamCommand to { command -> failure(command, exitCode = 128, stderr = "fatal: no upstream configured") },
                headCommand to { command -> success(command, stdout = "abc123\n") },
                reflogCommand to { command -> success(command, stdout = "abc123\n") },
            ),
        )

        val status = ReadWorktreeStatusUseCase(executor, FakeFileManager())(worktree, mainBranch, epic).getOrThrow()

        assertTrue(status.isNotStarted)
        assertFalse(status.isMerged)
        assertFalse(status.hasOwnCommits)
    }

    @Test
    fun `a worktree on its base has no base comparison`() = runTest {
        val epicWorktree = Worktree(path = "/home/dev/projects/redesign", branch = "epic/redesign")

        val status = ReadWorktreeStatusUseCase(executor(), FakeFileManager())(epicWorktree, mainBranch, epic).getOrThrow()

        assertNull(status.base)
        assertNull(status.baseName)
    }

    @Test
    fun `a linked worktree is created when its administrative folder was written`() = runTest {
        val files = FakeFileManager()
        files.writtenTexts["${worktree.path}/.git"] = "gitdir: /repo/.git/worktrees/TASK-123\n"
        files.modifiedAt["/repo/.git/worktrees/TASK-123/commondir"] = 1750000000000

        val status = ReadWorktreeStatusUseCase(executor(), files)(worktree, mainBranch).getOrThrow()

        assertEquals(Instant.ofEpochMilli(1750000000000), status.createdAt)
    }

    @Test
    fun `the main worktree has no creation time`() = runTest {
        val files = FakeFileManager()
        files.writtenTexts["${worktree.path}/.git"] = "gitdir: /repo/.git/worktrees/TASK-123\n"
        files.modifiedAt["/repo/.git/worktrees/TASK-123/commondir"] = 1750000000000

        val status = ReadWorktreeStatusUseCase(executor(), files)(worktree.copy(isMain = true), mainBranch).getOrThrow()

        assertNull(status.createdAt)
    }

    @Test
    fun `an unreadable git file leaves the creation time empty`() = runTest {
        val status = ReadWorktreeStatusUseCase(executor(), FakeFileManager())(worktree, mainBranch).getOrThrow()

        assertNull(status.createdAt)
    }
}
