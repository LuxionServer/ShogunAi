package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveMainBranchUseCaseTest {

    private fun config(mainBranch: String? = null) = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
        mainBranch = mainBranch,
    )

    private fun executor(existingRefs: Set<String>, remoteHead: String? = null) = FakeShellCommandExecutor { command ->
        when (command.getOrNull(1)) {
            "symbolic-ref" ->
                if (remoteHead != null) success(command, stdout = "$remoteHead\n") else failure(command, exitCode = 1, stderr = "")
            "rev-parse" ->
                if (command.last() in existingRefs) success(command, stdout = "abc123\n") else failure(command, exitCode = 1, stderr = "")
            else -> success(command)
        }
    }

    private suspend fun resolve(config: ProjectConfig, executor: FakeShellCommandExecutor): MainBranch? =
        ResolveMainBranchUseCase(config, executor)().getOrThrow()

    @Test
    fun `a configured branch is compared through its origin counterpart`() = runTest {
        val executor = executor(existingRefs = setOf("refs/remotes/origin/develop", "refs/heads/develop"))

        assertEquals(MainBranch("develop", "origin/develop"), resolve(config("develop"), executor))
    }

    @Test
    fun `a configured branch without origin counterpart is compared locally`() = runTest {
        val executor = executor(existingRefs = setOf("refs/heads/develop"))

        assertEquals(MainBranch("develop", "develop"), resolve(config("develop"), executor))
    }

    @Test
    fun `a configured branch takes precedence over the remote default branch`() = runTest {
        val executor = executor(existingRefs = setOf("refs/remotes/origin/develop"), remoteHead = "origin/main")

        assertEquals(MainBranch("develop", "origin/develop"), resolve(config("develop"), executor))
        assertEquals(0, executor.executedCommands.count { it.getOrNull(1) == "symbolic-ref" })
    }

    @Test
    fun `without configuration the remote default branch is used`() = runTest {
        val executor = executor(existingRefs = setOf("refs/remotes/origin/main"), remoteHead = "origin/main")

        assertEquals(MainBranch("main", "origin/main"), resolve(config(), executor))
    }

    @Test
    fun `without remote default branch a local main is used`() = runTest {
        val executor = executor(existingRefs = setOf("refs/heads/main", "refs/heads/master"))

        assertEquals(MainBranch("main", "main"), resolve(config(), executor))
    }

    @Test
    fun `without remote default branch nor local main a local master is used`() = runTest {
        val executor = executor(existingRefs = setOf("refs/heads/master"))

        assertEquals(MainBranch("master", "master"), resolve(config(), executor))
    }

    @Test
    fun `nothing is resolved when no candidate branch exists`() = runTest {
        assertNull(resolve(config(), executor(existingRefs = emptySet())))
    }

    @Test
    fun `nothing is resolved when the configured branch does not exist`() = runTest {
        assertNull(resolve(config("develop"), executor(existingRefs = setOf("refs/heads/main"))))
    }
}
