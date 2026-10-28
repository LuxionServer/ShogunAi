package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LockedWorktreeFolderTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val path = "/home/dev/projects/TASK-123"
    private val locked = "error: failed to delete '$path': Permission denied"
    private val notAWorkingTree = "fatal: '$path' is not a working tree"
    private val tooLong = "error: failed to delete '$path': Filename too long"
    private val removeCommand = listOf("git", "worktree", "remove", path)

    private fun executor(vararg removals: CommandResult.() -> CommandResult, registered: Boolean = false): FakeShellCommandExecutor {
        var attempt = 0
        return FakeShellCommandExecutor { command ->
            when {
                command == removeCommand -> removals.getOrElse(attempt++) { removals.last() }(success(command))
                command == listOf("git", "worktree", "list", "--porcelain") -> success(
                    command,
                    "worktree ${config.baseRepositoryPath}\nHEAD abc\nbranch refs/heads/develop\n\n" +
                        if (registered) "worktree $path\nHEAD abc\nbranch refs/heads/feature/TASK-123\n\n" else "",
                )
                else -> success(command)
            }
        }
    }

    private val ok: CommandResult.() -> CommandResult = { this }
    private fun fails(stderr: String): CommandResult.() -> CommandResult = { failure(command, exitCode = 255, stderr = stderr) }

    private fun removal(executor: FakeShellCommandExecutor, herdr: FakeHerdrClient?, files: FakeFileManager? = null) =
        RemoveWorktreeUseCase(config, executor, herdr?.let { CloseHerdrWorkspacesUseCase(config, it) }, files)

    @Test
    fun `a locked folder closes the Herdr workspace and retries once`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2"))
        val executor = executor(fails(locked), ok)

        val result = removal(executor, herdr)(path).getOrThrow()

        assertEquals(listOf("w2"), herdr.closedWorkspaces)
        assertEquals(2, executor.executedCommands.count { it == removeCommand })
        assertEquals(null, result.herdrFailure)
    }

    @Test
    fun `panes in another workspace inside the worktree are closed one by one`() = runTest {
        val herdr = FakeHerdrClient(
            openWorkspaces = mapOf(path to "w2"),
            panes = listOf(
                HerdrPane("w1:p3", "w1", "$path/src"),
                HerdrPane("w1:p4", "w1", "/home/dev/projects/other"),
                HerdrPane("w2:p1", "w2", path),
                HerdrPane("w1:p5", "w1", "$path-copy"),
            ),
        )

        removal(executor(fails(locked), ok), herdr)(path).getOrThrow()

        assertEquals(listOf("w1:p3"), herdr.closed)
    }

    @Test
    fun `a failure other than a lock closes nothing in Herdr`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2"), panes = listOf(HerdrPane("w1:p3", "w1", path)))
        val executor = executor(fails("fatal: '$path' contains modified or untracked files, use --force to delete it"))

        val failure = removal(executor, herdr)(path).exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(failure)
        assertTrue(herdr.closedWorkspaces.isEmpty())
        assertTrue(herdr.closed.isEmpty())
        assertEquals(1, executor.executedCommands.count { it == removeCommand })
    }

    @Test
    fun `still locked after the retry reports the folder in use`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2"))

        val failure = removal(executor(fails(locked), fails(locked), registered = true), herdr)(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeFolderInUse>(failure)
        assertEquals(false, failure.unregistered)
        assertTrue(failure.errorOutput.contains("Permission denied"))
    }

    @Test
    fun `a lock without Herdr reports the folder in use`() = runTest {
        val failure = removal(executor(fails(locked), fails(locked), registered = true), null)(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeFolderInUse>(failure)
    }

    @Test
    fun `an unregistered worktree whose folder is released is finished by deleting the leftover`() = runTest {
        val files = FakeFileManager(existing = listOf(path))
        val executor = executor(fails(locked), fails(notAWorkingTree))

        removal(executor, FakeHerdrClient(openWorkspaces = mapOf(path to "w2")), files)(path).getOrThrow()

        assertEquals(listOf(path), files.deletedRecursively)
        assertTrue(executor.executed("git", "worktree", "prune"))
    }

    @Test
    fun `a later removal of an unregistered worktree with its folder left succeeds`() = runTest {
        val files = FakeFileManager(existing = listOf(path))
        val executor = executor(fails(notAWorkingTree))

        removal(executor, null, files)(path).getOrThrow()

        assertEquals(listOf(path), files.deletedRecursively)
    }

    @Test
    fun `an unregistered worktree whose folder is gone succeeds`() = runTest {
        val files = FakeFileManager()

        removal(executor(fails(notAWorkingTree)), null, files)(path).getOrThrow()

        assertTrue(files.deletedRecursively.isEmpty())
    }

    @Test
    fun `a leftover folder that stays locked reports an unregistered worktree`() = runTest {
        val files = FakeFileManager(existing = listOf(path)).apply { lockedFolders = setOf(path) }

        val failure = removal(executor(fails(notAWorkingTree)), null, files)(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeFolderInUse>(failure)
        assertEquals(true, failure.unregistered)
    }

    @Test
    fun `a path that is a repository is never deleted`() = runTest {
        val withGit = FakeFileManager(existing = listOf(path), directories = setOf("$path/.git"))

        assertFailsWith<WorktreeError.GitCommandFailed> {
            removal(executor(fails(notAWorkingTree)), null, withGit)(path).getOrThrow()
        }
        assertTrue(withGit.deletedRecursively.isEmpty())

        val base = config.baseRepositoryPath
        val baseFiles = FakeFileManager(existing = listOf(base))
        val baseCommand = listOf("git", "worktree", "remove", base)
        val baseExecutor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: '$base' is not a working tree") }
        assertFailsWith<WorktreeError.GitCommandFailed> { removal(baseExecutor, null, baseFiles)(base).getOrThrow() }
        assertTrue(baseFiles.deletedRecursively.isEmpty())
        assertTrue(baseExecutor.executed(*baseCommand.toTypedArray()))
    }

    @Test
    fun `cleanup recovers a locked worktree and reports the one that stays locked`() = runTest {
        val other = Worktree(path = "/home/dev/projects/TASK-9", branch = "feature/TASK-9")
        val third = Worktree(path = "/home/dev/projects/TASK-5", branch = "feature/TASK-5")
        val lockedFirst = Worktree(path = path, branch = "feature/TASK-123")
        val attempts = mutableMapOf<String, Int>()
        val executor = FakeShellCommandExecutor { command ->
            val target = command.last()
            if (command.take(3) == listOf("git", "worktree", "remove")) {
                val attempt = attempts.merge(target, 1, Int::plus)!!
                when {
                    target == path && attempt == 1 -> failure(command, 255, locked)
                    target == other.path -> failure(command, 255, "error: failed to delete '$target': Permission denied")
                    else -> success(command)
                }
            } else {
                success(command)
            }
        }
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2", other.path to "w3"))
        val useCase = CleanUpWorktreesUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr), null, FakeFileManager())

        val report = useCase(listOf(lockedFirst, other, third), mainBranch = null, deleteBranches = false).getOrThrow()

        assertEquals(listOf(path, third.path), report.removed)
        assertEquals(listOf(other.path), report.failures.map { it.path })
        assertIs<WorktreeError.WorktreeFolderInUse>(report.failures.single().error)
        assertEquals(listOf("w2", "w3"), herdr.closedWorkspaces)
        assertTrue(report.herdrFailures.isEmpty())
    }

    @Test
    fun `a path too long for Git is finished by deleting the leftover`() = runTest {
        val files = FakeFileManager(existing = listOf(path))
        val executor = executor(fails(tooLong))

        removal(executor, null, files)(path).getOrThrow()

        assertEquals(listOf(path), files.deletedRecursively)
        assertTrue(executor.executed("git", "worktree", "prune"))
        assertEquals(1, executor.executedCommands.count { it == removeCommand })
    }

    @Test
    fun `a path too long after closing Herdr is finished by deleting the leftover`() = runTest {
        val files = FakeFileManager(existing = listOf(path))
        val executor = executor(fails(locked), fails(tooLong))

        removal(executor, FakeHerdrClient(openWorkspaces = mapOf(path to "w2")), files)(path).getOrThrow()

        assertEquals(listOf(path), files.deletedRecursively)
    }

    @Test
    fun `a path too long in a repository folder deletes nothing`() = runTest {
        val withGit = FakeFileManager(existing = listOf(path), directories = setOf("$path/.git"))

        val failure = removal(executor(fails(tooLong)), null, withGit)(path).exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(failure)
        assertTrue(failure.errorOutput.contains("Filename too long"))
        assertTrue(withGit.deletedRecursively.isEmpty())
    }

    @Test
    fun `a path too long whose folder can't be deleted reports an unregistered worktree in use`() = runTest {
        val files = FakeFileManager(existing = listOf(path)).apply { lockedFolders = setOf(path) }

        val failure = removal(executor(fails(tooLong)), null, files)(path).exceptionOrNull()

        assertIs<WorktreeError.WorktreeFolderInUse>(failure)
        assertEquals(true, failure.unregistered)
    }

    @Test
    fun `cleanup reports a worktree with a path too long for Git as removed`() = runTest {
        val other = Worktree(path = "/home/dev/projects/TASK-9", branch = "feature/TASK-9")
        val third = Worktree(path = "/home/dev/projects/TASK-5", branch = "feature/TASK-5")
        val deep = Worktree(path = path, branch = "feature/TASK-123")
        val executor = FakeShellCommandExecutor { command ->
            if (command == removeCommand) failure(command, 255, tooLong) else success(command)
        }
        val files = FakeFileManager(existing = listOf(path, other.path, third.path))
        val useCase = CleanUpWorktreesUseCase(config, executor, null, null, files)

        val report = useCase(listOf(other, deep, third), mainBranch = null, deleteBranches = false).getOrThrow()

        assertEquals(listOf(other.path, path, third.path), report.removed)
        assertTrue(report.failures.isEmpty())
        assertEquals(listOf(path), files.deletedRecursively)
    }
}
