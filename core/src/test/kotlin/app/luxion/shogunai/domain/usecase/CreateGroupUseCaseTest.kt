package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateGroupUseCaseTest {

    private val world = MultirepoTestWorld()
    private val create = CreateGroupUseCase(world.git, world.fileManager, FakeRepositoryProvisioningSource())

    @Test
    fun `each repository gets today's worktree in its project folder from its own base`() = runTest {
        val creation = create(listOf(world.member("app"), world.member("docs")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertEquals("feature/TASK-12", creation.branch)
        assertEquals(listOf(GroupRepositoryOutcome.CREATED, GroupRepositoryOutcome.CREATED), creation.repositories.map { it.outcome })
        assertEquals(listOf("${world.worktreesRoot}/app/TASK-12", "${world.worktreesRoot}/docs/TASK-12"), creation.repositories.map { it.path })
        assertTrue(world.git.ranIn(world.appBase, "git", "worktree", "add", "--no-track", "${world.worktreesRoot}/app/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
        assertTrue(world.git.ranIn(world.docsBase, "git", "worktree", "add", "--no-track", "${world.worktreesRoot}/docs/TASK-12", "-b", "feature/TASK-12", "origin/main"))
        assertTrue(world.git.ranIn(world.appBase, "git", "config", "branch.feature/TASK-12.gh-merge-base", "develop"))
        assertTrue(world.git.ranIn(world.docsBase, "git", "config", "branch.feature/TASK-12.gh-merge-base", "main"))
        assertFalse(creation.failed)
    }

    @Test
    fun `fix creates fix branches and fetch fetches each repository first`() = runTest {
        val creation = create(listOf(world.member("api")), "TASK-3", BranchType.FIX, fetch = true).getOrThrow()

        assertEquals("fix/TASK-3", creation.branch)
        assertTrue(world.git.ranIn(world.apiBase, "git", "fetch", "--prune", "origin"))
    }

    @Test
    fun `a repository already on the branch anywhere is left as it is`() = runTest {
        with(world.git) { world.api.worktree("/home/dev/old/api-task-12", "feature/TASK-12") }

        val creation = create(listOf(world.member("app"), world.member("api")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertEquals(GroupRepositoryResult("api", GroupRepositoryOutcome.EXISTING, "/home/dev/old/api-task-12"), creation.repositories[1])
        assertFalse(world.git.calls.any { it.second == world.apiBase && it.first.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `an existing branch without a worktree is checked out without moving it`() = runTest {
        world.api.branches += "feature/TASK-12"

        create(listOf(world.member("api")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertTrue(world.git.ranIn(world.apiBase, "git", "worktree", "add", "${world.worktreesRoot}/api/TASK-12", "feature/TASK-12"))
        assertFalse(world.git.calls.any { it.second == world.apiBase && it.first.take(2) == listOf("git", "config") })
    }

    @Test
    fun `a failing repository doesn't stop the others`() = runTest {
        world.api.failAdd = "fatal: something went wrong"

        val creation = create(listOf(world.member("app"), world.member("api"), world.member("docs")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertEquals(listOf(GroupRepositoryOutcome.CREATED, GroupRepositoryOutcome.FAILED, GroupRepositoryOutcome.CREATED), creation.repositories.map { it.outcome })
        assertTrue(creation.failed)
    }

    @Test
    fun `a repository without a base branch fails alone`() = runTest {
        world.docs.remoteBranches.clear()
        world.docs.branches.clear()

        val creation = create(listOf(world.member("docs"), world.member("app")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertIs<WorktreeError.BaseBranchNotFound>(creation.repositories[0].error)
        assertEquals(GroupRepositoryOutcome.CREATED, creation.repositories[1].outcome)
    }

    @Test
    fun `an invalid id creates nothing`() = runTest {
        assertIs<WorktreeError.InvalidTaskId>(create(world.members(), "bad..id", BranchType.FEATURE).exceptionOrNull())
        assertFalse(world.git.calls.any { it.first.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `a failing post-creation command fails its repository and keeps the worktree's path`() = runTest {
        val failingInstall = object : ShellCommandExecutor by world.git {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult =
                if (command.first() == "npm") CommandResult(command, 1, "", "npm ERR! missing script") else world.git.execute(command, workingDirectory)
        }
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(world.appBase to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install")))))
        val create = CreateGroupUseCase(failingInstall, world.fileManager, source, trust = FakeRepositoryTrust(approvesEverything = true))

        val result = create(listOf(world.member("app")), "TASK-12", BranchType.FEATURE).getOrThrow().repositories.single()

        assertEquals(GroupRepositoryOutcome.FAILED, result.outcome)
        assertEquals("${world.worktreesRoot}/app/TASK-12", result.path)
        assertIs<WorktreeError.PostCreateCommandFailed>(result.error)
    }
    @Test
    fun `a repository whose project has no worktrees folder fails alone`() = runTest {
        val app = world.member("app")
        val withoutFolder = app.copy(config = app.config.copy(worktreesRoot = " "))

        val creation = create(listOf(withoutFolder, world.member("docs")), "TASK-12", BranchType.FEATURE).getOrThrow()

        assertIs<WorktreeError.InvalidRepositoryConfig>(creation.repositories[0].error)
        assertEquals(GroupRepositoryOutcome.CREATED, creation.repositories[1].outcome)
    }
}
