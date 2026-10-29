package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloseOrcaTerminalsUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val path = "/home/dev/projects/TASK-123"
    private val other = "/home/dev/projects/TASK-124"
    private val removeCommand = listOf("git", "worktree", "remove", path)
    private val locked = "error: failed to delete '$path': Permission denied"

    private fun openIn(vararg paths: String, running: Boolean = true, closeFailures: Map<String, Throwable> = emptyMap()) =
        FakeOrcaClient(
            running = running,
            terminals = paths.associateWith { listOf(OrcaTerminal("term_${it.substringAfterLast('-')}", it)) },
            closeFailures = closeFailures,
        )

    private fun removal(executor: FakeShellCommandExecutor, orca: FakeOrcaClient) =
        RemoveWorktreeUseCase(config, executor, closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca))

    @Test
    fun `the Orca terminals of a removed worktree are closed after the removal`() = runTest {
        val orca = openIn(path)
        val executor = FakeShellCommandExecutor { success(it) }

        val result = removal(executor, orca)(path).getOrThrow()

        assertEquals(listOf(path), orca.closedWorktrees)
        assertNull(result.orcaFailure)
        assertTrue(executor.executed(*removeCommand.toTypedArray()))
    }

    @Test
    fun `a removal that fails leaves the Orca terminals open`() = runTest {
        val orca = openIn(path)

        assertTrue(removal(FakeShellCommandExecutor { failure(it) }, orca)(path).isFailure)

        assertTrue(orca.closedWorktrees.isEmpty())
    }

    @Test
    fun `a folder locked by Orca's shells closes its terminals and retries once`() = runTest {
        val orca = openIn(path)
        var attempts = 0
        val executor = FakeShellCommandExecutor { command ->
            if (command == removeCommand && attempts++ == 0) failure(command, exitCode = 255, stderr = locked) else success(command)
        }

        val result = removal(executor, orca)(path).getOrThrow()

        assertEquals(2, attempts)
        assertEquals(listOf(path), orca.closedWorktrees)
        assertNull(result.orcaFailure)
    }

    @Test
    fun `Orca not running is left alone and never started`() = runTest {
        val orca = openIn(path, running = false)

        val result = removal(FakeShellCommandExecutor { success(it) }, orca)(path).getOrThrow()

        assertEquals(WorktreeRemoval(), result)
        assertTrue(orca.terminalLookups.isEmpty())
        assertTrue(orca.closedWorktrees.isEmpty())
        assertEquals(0, orca.starts)
    }

    @Test
    fun `Orca not installed changes nothing`() = runTest {
        val result = removal(FakeShellCommandExecutor { success(it) }, FakeOrcaClient(installed = false))(path).getOrThrow()

        assertEquals(WorktreeRemoval(), result)
    }

    @Test
    fun `a worktree without Orca terminals closes nothing`() = runTest {
        val orca = openIn(other)

        removal(FakeShellCommandExecutor { success(it) }, orca)(path).getOrThrow()

        assertTrue(orca.closedWorktrees.isEmpty())
    }

    @Test
    fun `a terminal lookup that fails counts as no terminals`() = runTest {
        val orca = FakeOrcaClient(terminalsFailure = WorktreeError.OrcaCommandFailed(listOf("orca"), "worktree_not_found"))

        val result = removal(FakeShellCommandExecutor { success(it) }, orca)(path).getOrThrow()

        assertNull(result.orcaFailure)
        assertTrue(orca.closedWorktrees.isEmpty())
    }

    @Test
    fun `a failing close is a warning and the removal still succeeds`() = runTest {
        val failure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable")
        val orca = openIn(path, closeFailures = mapOf(path to failure))

        val result = removal(FakeShellCommandExecutor { success(it) }, orca)(path).getOrThrow()

        assertEquals(failure, result.orcaFailure)
        assertEquals("runtime_unavailable", failure.orcaDetail())
    }

    @Test
    fun `cleanup closes the terminals of the worktrees it removed only`() = runTest {
        val first = Worktree(path = path, branch = "feature/TASK-123")
        val second = Worktree(path = other, branch = "feature/TASK-124")
        val orca = openIn(path, other)
        val executor = FakeShellCommandExecutor { command ->
            if (command == removeCommand) failure(command) else success(command)
        }

        val report = CleanUpWorktreesUseCase(config, executor, closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca))(
            listOf(first, second),
            MainBranch("develop", "origin/develop"),
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(other), report.removed)
        assertEquals(listOf(other), orca.closedWorktrees)
        assertTrue(report.orcaFailures.isEmpty())
    }

    @Test
    fun `cleanup reports a failing close next to the removal`() = runTest {
        val failure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable")
        val orca = openIn(path, closeFailures = mapOf(path to failure))

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor { success(it) }, closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca))(
            listOf(Worktree(path = path, branch = "feature/TASK-123")),
            MainBranch("develop", "origin/develop"),
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(path), report.removed)
        assertEquals(listOf(path), report.orcaFailures.map { it.path })
    }

    @Test
    fun `the paths with Orca terminals are looked up only while Orca runs`() = runTest {
        val running = openIn(path)
        val closed = openIn(path, running = false)

        assertEquals(setOf(path), CloseOrcaTerminalsUseCase(running).withTerminals(listOf(path, other)))
        assertEquals(emptySet(), CloseOrcaTerminalsUseCase(closed).withTerminals(listOf(path, other)))
    }

    @Test
    fun `an Orca status that fails counts as Orca not running`() = runTest {
        val orca = FakeOrcaClient(statusFailure = IllegalStateException("socket closed"), terminals = mapOf(path to listOf(OrcaTerminal("term_1", path))))

        assertEquals(emptySet(), CloseOrcaTerminalsUseCase(orca).withTerminals(listOf(path)))
    }

    @Test
    fun `a retry that fails for another reason reports the git error`() = runTest {
        var attempts = 0
        val executor = FakeShellCommandExecutor { command ->
            if (command == removeCommand) {
                attempts++
                failure(command, exitCode = 128, stderr = if (attempts == 1) locked else "fatal: unexpected failure")
            } else {
                success(command)
            }
        }

        val error = removal(executor, openIn(path))(path).exceptionOrNull()

        assertTrue(error is WorktreeError.GitCommandFailed)
        assertEquals(2, attempts)
    }
}
