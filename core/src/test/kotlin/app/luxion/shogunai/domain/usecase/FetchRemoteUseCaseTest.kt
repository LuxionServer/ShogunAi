package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import app.luxion.shogunai.domain.executor.CommandExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FetchRemoteUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )

    private val fetch = listOf("git", "fetch", "--prune", "origin")

    @Test
    fun `fetches origin in the base repository with the network timeout`() = runTest {
        val executor = FakeShellCommandExecutor()

        FetchRemoteUseCase(config, executor)().getOrThrow()

        assertTrue(executor.executed("git", "fetch", "--prune", "origin"))
        assertEquals(CommandTimeouts.NETWORK, executor.timeouts[fetch])
        assertEquals(config.baseRepositoryPath, executor.workingDirectories.single())
    }

    @Test
    fun `fails with Git's error when the fetch fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "Could not resolve host") }

        val error = FetchRemoteUseCase(config, executor)().exceptionOrNull()

        val gitError = assertIs<WorktreeError.GitCommandFailed>(error)
        assertEquals("Could not resolve host", gitError.errorOutput)
    }

    @Test
    fun `reports the origin remote when there is one`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "git@example.com:team/repo.git") }

        assertTrue(FetchRemoteUseCase(config, executor).hasOrigin())
    }

    @Test
    fun `reports no origin remote when git can't find it`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 2, stderr = "error: No such remote 'origin'") }

        assertFalse(FetchRemoteUseCase(config, executor).hasOrigin())
    }
    @Test
    fun `reports no origin remote when git can't start`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, java.io.IOException("no git")) }

        assertFalse(FetchRemoteUseCase(config, executor).hasOrigin())
    }
}
