package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveBaseBranchUseCaseTest {

    private val directory = "/home/dev/projects/main-repo"

    private suspend fun resolve(fullRef: String?, ref: String = "anything"): String? {
        val executor = FakeShellCommandExecutor { if (fullRef == null) failure(it) else success(it, "$fullRef\n") }
        return ResolveBaseBranchUseCase(executor)(directory, ref)
    }

    @Test
    fun `a local branch is its own name`() = runTest {
        assertEquals("epic/redesign", resolve("refs/heads/epic/redesign"))
    }

    @Test
    fun `a remote branch drops the remote`() = runTest {
        assertEquals("develop", resolve("refs/remotes/origin/develop"))
        assertEquals("epic/redesign", resolve("refs/remotes/upstream/epic/redesign"))
    }

    @Test
    fun `tags, commits, a detached head and a remote's head are not branches`() = runTest {
        assertNull(resolve("refs/tags/v1.2.0"))
        assertNull(resolve(""))
        assertNull(resolve("HEAD"))
        assertNull(resolve("refs/remotes/origin/HEAD"))
    }

    @Test
    fun `an unknown ref is not a branch`() = runTest {
        assertNull(resolve(null))
    }

    @Test
    fun `asks git for the symbolic name in the given directory`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, "refs/heads/develop\n") }

        assertEquals("develop", ResolveBaseBranchUseCase(executor)(directory, "HEAD"))
        assertTrue(executor.executed("git", "rev-parse", "--verify", "--quiet", "--symbolic-full-name", "HEAD"))
        assertEquals(listOf<String?>(directory), executor.workingDirectories)
    }

    @Test
    fun `an option-like ref runs nothing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, "refs/heads/develop\n") }

        assertNull(ResolveBaseBranchUseCase(executor)(directory, "--output=x"))
        assertTrue(executor.executedCommands.isEmpty())
    }
}
