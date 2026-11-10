package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergedChangesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val branch = "feature/TASK-123"
    private val rebaseCount = listOf("git", "rev-list", "--cherry-pick", "--right-only", "--count", "develop...$branch")
    private val forkPoint = listOf("git", "merge-base", "develop", branch)
    private val squashCommit = listOf("git", "commit-tree", "$branch^{tree}", "-p", "fork123", "-m", "squash")
    private val squashCount = listOf("git", "rev-list", "--cherry-pick", "--right-only", "--count", "develop...squash456")

    private fun executor(overrides: Map<List<String>, (List<String>) -> CommandResult>) = FakeShellCommandExecutor { command ->
        overrides[command]?.invoke(command) ?: when (command) {
            rebaseCount -> success(command, "2\n")
            forkPoint -> success(command, "fork123\n")
            squashCommit -> success(command, "squash456\n")
            squashCount -> success(command, "0\n")
            else -> failure(command)
        }
    }

    private suspend fun holds(executor: FakeShellCommandExecutor) = holdsChangesOf(executor, repository, branch, "develop")

    @Test
    fun `every commit having an equivalent in the target is a rebase merge, without building a squash commit`() = runTest {
        val executor = executor(mapOf(rebaseCount to { c -> success(c, "0\n") }))

        assertTrue(holds(executor))
        assertEquals(listOf(rebaseCount), executor.executedCommands)
        assertEquals(listOf<String?>(repository), executor.workingDirectories)
    }

    @Test
    fun `the whole change having an equivalent in the target is a squash merge`() = runTest {
        val executor = executor(emptyMap())

        assertTrue(holds(executor))
        assertEquals(listOf(rebaseCount, forkPoint, squashCommit, squashCount), executor.executedCommands)
        assertEquals(SQUASH_COMMIT_ENVIRONMENT, executor.environments[squashCommit])
        assertEquals("@0 +0000", SQUASH_COMMIT_ENVIRONMENT["GIT_COMMITTER_DATE"])
    }

    @Test
    fun `a whole change without an equivalent isn't merged`() = runTest {
        assertFalse(holds(executor(mapOf(squashCount to { c -> success(c, "1\n") }))))
    }

    @Test
    fun `failed or unreadable counts read as not merged`() = runTest {
        assertTrue(holds(executor(mapOf(rebaseCount to { c -> failure(c) }))))
        assertTrue(holds(executor(mapOf(rebaseCount to { c -> success(c, "lots\n") }))))
        assertFalse(holds(executor(mapOf(squashCount to { c -> failure(c) }))))
        assertFalse(holds(executor(mapOf(squashCount to { c -> success(c, "") }))))
    }

    @Test
    fun `a missing fork point or squash commit reads as not merged`() = runTest {
        assertFalse(holds(executor(mapOf(forkPoint to { c -> failure(c) }))))
        assertFalse(holds(executor(mapOf(forkPoint to { c -> success(c, "\n") }))))
        assertFalse(holds(executor(mapOf(squashCommit to { c -> failure(c) }))))
        assertFalse(holds(executor(mapOf(squashCommit to { c -> success(c, " ") }))))
    }
}
