package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveWorktreeUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )
    private val worktreePath = "/home/dev/projects/TASK-123"

    @Test
    fun `removes worktree without deleting branch by default`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertEquals(listOf(listOf("git", "worktree", "remove", worktreePath)), executor.executedCommands)
    }

    private fun headlessApp(launcher: FakeHeadlessAppLauncher, files: FakeFileManager): StopHeadlessAppUseCase {
        val provisioning = FakeRepositoryProvisioningSource(
            mapOf(worktreePath to RepositoryProvisioning(providers = RepositoryProviders(desktopApp = DesktopAppProvider(":desktopApp")))),
        )
        val worktrees = HeadlessAppWorktrees(provisioning, { "/home/dev/projects/main-repo/.git/worktrees/TASK-123" }, launcher, FakeAppWindowService(), files)
        return StopHeadlessAppUseCase(worktrees, launcher)
    }

    private fun runningApp(): Pair<FakeHeadlessAppLauncher, FakeFileManager> {
        val files = FakeFileManager()
        val pidFile = "$worktreePath/desktopApp/build/run/main/main.pid"
        files.writtenProperties[pidFile] = mapOf("pid" to "4242")
        val launcher = FakeHeadlessAppLauncher(files, pidFile)
        launcher.alive += 4242L
        return launcher to files
    }

    @Test
    fun `stops the worktree's headless app before removing its folder`() = runTest {
        val (launcher, files) = runningApp()
        var stoppedBeforeRemoval = false
        val executor = FakeShellCommandExecutor { command ->
            if (command.take(3) == listOf("git", "worktree", "remove")) stoppedBeforeRemoval = 4242L in launcher.stopped
            success(command)
        }

        val removal = RemoveWorktreeUseCase(config, executor, stopHeadlessApp = headlessApp(launcher, files))(worktreePath).getOrThrow()

        assertTrue(stoppedBeforeRemoval)
        assertNull(removal.headlessAppFailure)
    }

    @Test
    fun `a failed headless app stop is reported and the worktree is still removed`() = runTest {
        val (launcher, files) = runningApp()
        launcher.failStop = IllegalStateException("operation not permitted")
        val executor = FakeShellCommandExecutor { success(it) }

        val removal = RemoveWorktreeUseCase(config, executor, stopHeadlessApp = headlessApp(launcher, files))(worktreePath).getOrThrow()

        assertIs<WorktreeError.HeadlessAppStopFailed>(removal.headlessAppFailure)
        assertTrue(executor.executed("git", "worktree", "remove", worktreePath))
    }

    private fun desktopTurns(store: FakeDesktopTurnStore, queue: FakeDeviceWaitQueue) = ReleaseDesktopTurnUseCase(
        DesktopTurnLedger(store, FakeEnvironmentStateStore(), queue, FakeProcessLiveness(), { path -> "/home/dev/projects/main-repo/.git/worktrees/${path.substringAfterLast('/')}" }),
    )

    @Test
    fun `removing the worktree that holds the desktop turn releases it before its folder goes`() = runTest {
        val admin = "/home/dev/projects/main-repo/.git/worktrees/TASK-123"
        val store = FakeDesktopTurnStore(DesktopTurn(admin, worktreePath, 0, Long.MAX_VALUE))
        val queue = FakeDeviceWaitQueue()
        queue.write(WaitQueueName.DESKTOP, listOf(WaitTicket("ticket", admin, worktreePath, ProcessIdentity(3000, 1), 0)))
        var releasedBeforeRemoval = false
        val executor = FakeShellCommandExecutor { command ->
            if (command.take(3) == listOf("git", "worktree", "remove")) releasedBeforeRemoval = store.turn == null
            success(command)
        }

        RemoveWorktreeUseCase(config, executor, releaseDesktopTurn = desktopTurns(store, queue))(worktreePath).getOrThrow()

        assertTrue(releasedBeforeRemoval)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `removing another worktree keeps the desktop turn`() = runTest {
        val turn = DesktopTurn("/home/dev/projects/main-repo/.git/worktrees/TASK-9", "/home/dev/projects/TASK-9", 0, Long.MAX_VALUE)
        val store = FakeDesktopTurnStore(turn)

        RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, releaseDesktopTurn = desktopTurns(store, FakeDeviceWaitQueue()))(worktreePath)
            .getOrThrow()

        assertEquals(turn, store.turn)
    }

    private fun branchExecutor(contained: Boolean = true, deleteStderr: String? = null) = FakeShellCommandExecutor { command ->
        when {
            command.take(3) == listOf("git", "merge-base", "--is-ancestor") ->
                if (contained) success(command) else failure(command, exitCode = 1, stderr = "")
            command.getOrNull(1) == "symbolic-ref" -> failure(command)
            command.take(3) == listOf("git", "branch", "-D") && deleteStderr != null ->
                failure(command, exitCode = 1, stderr = deleteStderr)
            else -> success(command)
        }
    }

    @Test
    fun `deletes a merged branch when requested`() = runTest {
        val executor = branchExecutor()

        val removal = RemoveWorktreeUseCase(config, executor)(worktreePath, branchToDelete = "feature/TASK-123").getOrThrow()

        assertTrue(executor.executed("git", "worktree", "remove", worktreePath))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
        assertEquals(BranchDeletion.Deleted, removal.branch)
    }

    @Test
    fun `keeps an unmerged branch and still succeeds`() = runTest {
        val executor = branchExecutor(contained = false)

        val removal = RemoveWorktreeUseCase(config, executor)(worktreePath, branchToDelete = "feature/TASK-123").getOrThrow()

        assertEquals(BranchDeletion.KeptNotMerged, removal.branch)
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `keeps the branch with Git's reason when its deletion fails`() = runTest {
        val executor = branchExecutor(deleteStderr = "branch is checked out\n")

        val removal = RemoveWorktreeUseCase(config, executor)(worktreePath, branchToDelete = "feature/TASK-123").getOrThrow()

        assertEquals(BranchDeletion.KeptFailed("branch is checked out"), removal.branch)
    }

    @Test
    fun `force-deletes an unmerged branch`() = runTest {
        val executor = branchExecutor(contained = false)

        val removal = RemoveWorktreeUseCase(config, executor)(
            worktreePath,
            branchToDelete = "feature/TASK-123",
            forceBranchDeletion = true,
        ).getOrThrow()

        assertEquals(BranchDeletion.Deleted, removal.branch)
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `reports no branch outcome when no deletion was requested`() = runTest {
        val removal = RemoveWorktreeUseCase(config, branchExecutor())(worktreePath).getOrThrow()

        assertEquals(null, removal.branch)
    }

    @Test
    fun `adds force flag when requested`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        useCase(worktreePath, force = true).getOrThrow()

        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }

    @Test
    fun `fails when git remove fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "is dirty") }
        val useCase = RemoveWorktreeUseCase(config, executor)

        val error = useCase(worktreePath).exceptionOrNull()

        val gitError = assertIs<WorktreeError.GitCommandFailed>(error)
        assertEquals(1, gitError.exitCode)
    }

    @Test
    fun `does not attempt branch deletion when remove fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it) }
        val useCase = RemoveWorktreeUseCase(config, executor)

        useCase(worktreePath, branchToDelete = "feature/TASK-123")

        assertEquals(1, executor.executedCommands.size)
    }

    private fun herdrUseCase(herdr: FakeHerdrClient) = CloseHerdrWorkspacesUseCase(config, herdr)

    @Test
    fun `closes the Herdr workspace after the worktree is removed`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        val removal = useCase(worktreePath).getOrThrow()

        assertEquals(listOf("w2"), herdr.closedWorkspaces)
        assertEquals(null, removal.herdrFailure)
    }

    @Test
    fun `leaves the workspace open when git fails`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { failure(it) }, herdrUseCase(herdr))

        assertTrue(useCase(worktreePath).isFailure)

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `leaves the workspace open when the worktree needs force`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"))
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "contains modified or untracked files, use --force") }

        assertTrue(RemoveWorktreeUseCase(config, executor, herdrUseCase(herdr))(worktreePath).isFailure)

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `a failing close is reported without failing the removal`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(worktreePath to "w2"), closeFailures = mapOf("w2" to failure))
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        val removal = useCase(worktreePath).getOrThrow()

        assertEquals(failure, removal.herdrFailure)
    }

    @Test
    fun `a worktree that is not open in Herdr closes nothing`() = runTest {
        val herdr = FakeHerdrClient()
        val useCase = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) }, herdrUseCase(herdr))

        useCase(worktreePath).getOrThrow()

        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `without a Herdr use case Herdr is never involved`() = runTest {
        val removal = RemoveWorktreeUseCase(config, FakeShellCommandExecutor { success(it) })(worktreePath).getOrThrow()

        assertEquals(WorktreeRemoval(), removal)
    }
}
