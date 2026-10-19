package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.withoutLocalTwins
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListBranchesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )

    private val branches = "refs/heads/develop\nrefs/heads/feature/TASK-123\nrefs/heads/feature/TASK-456\n"

    private val porcelain = """
        worktree /home/dev/projects/main-repo
        HEAD abc123
        branch refs/heads/develop

        worktree /home/dev/projects/TASK-123
        HEAD def456
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private fun executor(
        branchesOutput: String = branches,
        porcelainOutput: String = porcelain,
        failBranches: Boolean = false,
        failWorktrees: Boolean = false,
    ) = FakeShellCommandExecutor { command ->
        when (command.getOrNull(1)) {
            "for-each-ref" ->
                if (failBranches) failure(command, stderr = "not a git repo") else success(command, stdout = branchesOutput)
            "worktree" ->
                if (failWorktrees) failure(command, stderr = "not a git repo") else success(command, stdout = porcelainOutput)
            else -> success(command)
        }
    }

    @Test
    fun `marks no branch as checked out when nothing is checked out`() = runTest {
        val useCase = ListBranchesUseCase(config, executor(porcelainOutput = ""))

        val branches = useCase().getOrThrow()

        assertEquals(
            listOf(
                BranchOption("develop", isCheckedOut = false),
                BranchOption("feature/TASK-123", isCheckedOut = false),
                BranchOption("feature/TASK-456", isCheckedOut = false),
            ),
            branches,
        )
    }

    @Test
    fun `flags branches already checked out in another worktree`() = runTest {
        val useCase = ListBranchesUseCase(config, executor())

        val branches = useCase().getOrThrow()

        assertEquals(
            listOf(
                BranchOption("develop", isCheckedOut = true),
                BranchOption("feature/TASK-123", isCheckedOut = true),
                BranchOption("feature/TASK-456", isCheckedOut = false),
            ),
            branches,
        )
    }

    @Test
    fun `flags the branch checked out in the base repository`() = runTest {
        val basePorcelain = """
            worktree /home/dev/projects/main-repo
            HEAD abc123
            branch refs/heads/develop
        """.trimIndent()
        val useCase = ListBranchesUseCase(config, executor(porcelainOutput = basePorcelain))

        val branches = useCase().getOrThrow()

        assertEquals(
            listOf(
                BranchOption("develop", isCheckedOut = true),
                BranchOption("feature/TASK-123", isCheckedOut = false),
                BranchOption("feature/TASK-456", isCheckedOut = false),
            ),
            branches,
        )
    }

    @Test
    fun `fails when listing branches fails`() = runTest {
        val useCase = ListBranchesUseCase(config, executor(failBranches = true))

        assertIs<WorktreeError.GitCommandFailed>(useCase().exceptionOrNull())
    }

    @Test
    fun `fails when listing worktrees fails`() = runTest {
        val useCase = ListBranchesUseCase(config, executor(failWorktrees = true))

        assertIs<WorktreeError.GitCommandFailed>(useCase().exceptionOrNull())
    }

    @Test
    fun `lists origin branches as remote and skips origin HEAD`() = runTest {
        val refs = "refs/heads/develop\nrefs/remotes/origin/HEAD\nrefs/remotes/origin/develop\nrefs/remotes/origin/feature/TASK-9\n"
        val useCase = ListBranchesUseCase(config, executor(branchesOutput = refs, porcelainOutput = ""))

        val branches = useCase().getOrThrow()

        assertEquals(
            listOf(
                BranchOption("develop", isCheckedOut = false),
                BranchOption("origin/develop", isCheckedOut = false, isRemote = true),
                BranchOption("origin/feature/TASK-9", isCheckedOut = false, isRemote = true),
            ),
            branches,
        )
    }

    @Test
    fun `drops remote branches that have a local twin`() = runTest {
        val refs = "refs/heads/develop\nrefs/remotes/origin/develop\nrefs/remotes/origin/feature/TASK-9\n"
        val useCase = ListBranchesUseCase(config, executor(branchesOutput = refs, porcelainOutput = ""))

        val names = useCase().getOrThrow().withoutLocalTwins().map { it.name }

        assertEquals(listOf("develop", "origin/feature/TASK-9"), names)
        assertTrue("origin/develop" !in names)
    }
}
