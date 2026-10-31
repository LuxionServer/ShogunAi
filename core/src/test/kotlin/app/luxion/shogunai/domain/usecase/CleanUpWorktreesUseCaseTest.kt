package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeComposeProjectRegistry
import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanUpWorktreesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val mainBranch = MainBranch("develop", "origin/develop")

    private val first = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val second = Worktree(path = "/home/dev/projects/TASK-2", branch = "feature/TASK-2")
    private val missing = Worktree(path = "/home/dev/projects/TASK-3", branch = "feature/TASK-3", isPrunable = true)

    private fun executor(overrides: Map<List<String>, (List<String>) -> CommandResult> = emptyMap()) =
        FakeShellCommandExecutor { command -> overrides[command]?.invoke(command) ?: success(command) }

    private fun status(merged: Boolean, changes: Int = 0) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = null,
        mainBranch = if (merged) Divergence(ahead = 0, behind = 2) else Divergence(ahead = 1, behind = 0),
        lastCommitAt = null,
    )

    @Test
    fun `keeps a worktree with a running agent out of the candidates and says which agent`() {
        val statuses = mapOf(first.path to status(merged = true), second.path to status(merged = true))
        val agents = mapOf(first.path to listOf(RunningAgent("claude")))

        val selection = selectCleanup(listOf(first, second, missing), statuses, agents)

        assertEquals(listOf(second, missing), selection.candidates)
        assertEquals(mapOf(first to listOf(RunningAgent("claude"))), selection.keptForRunningAgent)
    }

    @Test
    fun `keeps a worktree whose agent only Herdr reports`() {
        val statuses = mapOf(first.path to status(merged = true))
        val herdrAgent = RunningAgent("claude", AgentState.BLOCKED, "w1:p1")

        val selection = selectCleanup(listOf(first), statuses, mapOf(first.path to listOf(herdrAgent)))

        assertEquals(emptyList(), selection.candidates)
        assertEquals(mapOf(first to listOf(herdrAgent)), selection.keptForRunningAgent)
    }

    @Test
    fun `selects every qualifying worktree when running agents are unknown`() {
        val statuses = mapOf(first.path to status(merged = true))

        assertEquals(listOf(first), selectCleanup(listOf(first), statuses, runningAgents = null).candidates)
    }

    @Test
    fun `selects merged clean worktrees and missing ones, never the main, locked or dirty`() {
        val main = Worktree(path = config.baseRepositoryPath, branch = "develop", isMain = true)
        val locked = Worktree(path = "/home/dev/projects/TASK-4", branch = "feature/TASK-4", isLocked = true)
        val dirty = Worktree(path = "/home/dev/projects/TASK-5", branch = "feature/TASK-5")
        val unmerged = Worktree(path = "/home/dev/projects/TASK-6", branch = "feature/TASK-6")
        val loading = Worktree(path = "/home/dev/projects/TASK-7", branch = "feature/TASK-7")
        val lockedMissing = missing.copy(path = "/home/dev/projects/TASK-8", isLocked = true)
        val statuses = mapOf(
            main.path to status(merged = true),
            first.path to status(merged = true),
            locked.path to status(merged = true),
            dirty.path to status(merged = true, changes = 2),
            unmerged.path to status(merged = false),
        )

        val candidates = selectCleanupCandidates(
            listOf(main, first, locked, dirty, unmerged, loading, missing, lockedMissing),
            statuses,
        )

        assertEquals(listOf(first, missing), candidates)
    }

    @Test
    fun `never selects a worktree that has not started`() {
        val notStarted = WorktreeStatus(
            uncommittedChanges = 0,
            upstream = null,
            mainBranch = Divergence(ahead = 0, behind = 2),
            lastCommitAt = null,
            neverCommitted = true,
        )

        assertEquals(emptyList(), selectCleanupCandidates(listOf(first), mapOf(first.path to notStarted)))
    }

    @Test
    fun `selects a clean worktree merged into its recorded base but not into the main branch`() {
        val mergedIntoEpic = WorktreeStatus(
            uncommittedChanges = 0,
            upstream = null,
            mainBranch = Divergence(ahead = 4, behind = 0),
            lastCommitAt = null,
            base = Divergence(ahead = 0, behind = 1),
            baseName = "epic/redesign",
        )

        assertEquals(listOf(first), selectCleanupCandidates(listOf(first), mapOf(first.path to mergedIntoEpic)))
    }

    @Test
    fun `deletes a branch contained in its recorded base but not in the main branch`() = runTest {
        val executor = executor(
            mapOf(
                listOf("git", "config", "--get", "branch.feature/TASK-1.gh-merge-base") to { c -> success(c, "epic/redesign\n") },
                listOf("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/develop") to { c -> failure(c, stderr = "") },
            ),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = true).getOrThrow()

        assertTrue(executor.executed("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/epic/redesign"))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-1"))
        assertTrue(report.keptBranches.isEmpty())
    }

    @Test
    fun `removes every worktree and prunes the missing ones`() = runTest {
        val executor = executor()

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, second, missing), mainBranch, deleteBranches = false)
            .getOrThrow()

        assertEquals(listOf(first.path, second.path, missing.path), report.removed)
        assertTrue(report.failures.isEmpty())
        assertTrue(executor.executed("git", "worktree", "remove", first.path))
        assertTrue(executor.executed("git", "worktree", "prune"))
        assertTrue(executor.executedCommands.none { it.getOrNull(1) == "branch" })
    }

    @Test
    fun `a failing prune reports every missing worktree`() = runTest {
        val executor = executor(mapOf(listOf("git", "worktree", "prune") to { command -> failure(command, exitCode = 1, stderr = "fatal: locked") }))

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, missing), mainBranch, deleteBranches = false).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertEquals(listOf(missing.path), report.failures.map { it.path })
        assertIs<WorktreeError.GitCommandFailed>(report.failures.single().error)
    }

    private fun desktopTurns(store: FakeDesktopTurnStore, queue: FakeDeviceWaitQueue) = ReleaseDesktopTurnUseCase(
        DesktopTurnLedger(store, FakeEnvironmentStateStore(), queue, FakeProcessLiveness(), { path -> "/home/dev/projects/main-repo/.git/worktrees/${path.substringAfterLast('/')}" }),
    )

    @Test
    fun `cleaning up releases the desktop turn and the waits of removed worktrees`() = runTest {
        val admin1 = "/home/dev/projects/main-repo/.git/worktrees/TASK-1"
        val admin2 = "/home/dev/projects/main-repo/.git/worktrees/TASK-2"
        val store = FakeDesktopTurnStore(DesktopTurn(admin1, first.path, 0, Long.MAX_VALUE))
        val queue = FakeDeviceWaitQueue()
        queue.write(WaitQueueName.DESKTOP, listOf(WaitTicket("ticket", admin2, second.path, ProcessIdentity(3000, 1), 0)))

        CleanUpWorktreesUseCase(config, executor(), releaseDesktopTurn = desktopTurns(store, queue))(listOf(first, second), mainBranch, deleteBranches = false)
            .getOrThrow()

        assertEquals(null, store.turn)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `does not prune when nothing is missing`() = runTest {
        val executor = executor()

        CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = false).getOrThrow()

        assertTrue(executor.executedCommands.none { it == listOf("git", "worktree", "prune") })
    }

    @Test
    fun `deletes a branch still contained in the main branch with -D`() = runTest {
        val executor = executor()

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = true).getOrThrow()

        assertTrue(executor.executed("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/develop"))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-1"))
        assertTrue(report.keptBranches.isEmpty())
    }

    @Test
    fun `keeps a branch that is no longer contained in the main branch`() = runTest {
        val executor = executor(
            mapOf(listOf("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/develop") to { c -> failure(c, stderr = "") }),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = true).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertEquals(listOf("feature/TASK-1"), report.keptBranches)
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `branches of pruned worktrees go through the same deletion check`() = runTest {
        val executor = executor(
            mapOf(listOf("git", "merge-base", "--is-ancestor", "feature/TASK-3", "origin/develop") to { c -> failure(c, stderr = "") }),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, missing), mainBranch, deleteBranches = true)
            .getOrThrow()

        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-1"))
        assertEquals(listOf("feature/TASK-3"), report.keptBranches)
    }

    @Test
    fun `keeps branches when there is no main branch`() = runTest {
        val report = CleanUpWorktreesUseCase(config, executor())(listOf(first), null, deleteBranches = true).getOrThrow()

        assertEquals(listOf("feature/TASK-1"), report.keptBranches)
    }

    @Test
    fun `a failing removal is reported and the rest continue`() = runTest {
        val executor = executor(
            mapOf(
                listOf("git", "worktree", "remove", first.path) to { c ->
                    failure(c, exitCode = 128, stderr = "fatal: contains modified or untracked files, use --force to delete it")
                },
            ),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, second), mainBranch, deleteBranches = true)
            .getOrThrow()

        assertEquals(listOf(second.path), report.removed)
        assertEquals(listOf(first.path), report.failures.map { it.path })
        assertEquals(128, assertIs<WorktreeError.GitCommandFailed>(report.failures.single().error).exitCode)
        assertTrue(executor.executedCommands.none { it == listOf("git", "branch", "-D", "--", "feature/TASK-1") })
    }

    @Test
    fun `closes the Herdr workspaces of removed and pruned worktrees`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3", missing.path to "w4"))

        val report = CleanUpWorktreesUseCase(config, executor(), CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second, missing),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf("w2", "w3", "w4"), herdr.closedWorkspaces)
        assertTrue(report.herdrFailures.isEmpty())
    }

    @Test
    fun `keeps the workspace of a worktree whose removal failed`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3"))
        val executor = executor(mapOf(listOf("git", "worktree", "remove", first.path) to { failure(it) }))

        CleanUpWorktreesUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }

    @Test
    fun `a failing close is reported next to the removal`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3"), closeFailures = mapOf("w2" to failure))

        val report = CleanUpWorktreesUseCase(config, executor(), CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(first.path, second.path), report.removed)
        assertEquals(listOf(first.path), report.herdrFailures.map { it.path })
        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }
    private val locked = "error: failed to delete '${first.path}': Permission denied"

    private fun lockedOnce(): FakeShellCommandExecutor {
        var attempts = 0
        return FakeShellCommandExecutor { command ->
            if (command == listOf("git", "worktree", "remove", first.path) && attempts++ == 0) failure(command, stderr = locked) else success(command)
        }
    }

    @Test
    fun `holders closed while removing a locked folder report their failures once`() = runTest {
        val orcaFailure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable")
        val herdrFailure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "busy")
        val orca = FakeOrcaClient(terminals = mapOf(first.path to listOf(OrcaTerminal("term_1", first.path))), closeFailures = mapOf(first.path to orcaFailure))
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2"), closeFailures = mapOf("w2" to herdrFailure))

        val report = CleanUpWorktreesUseCase(
            config,
            lockedOnce(),
            CloseHerdrWorkspacesUseCase(config, herdr),
            closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca),
        )(listOf(first), mainBranch, deleteBranches = false).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertEquals(listOf(CleanupFailure(first.path, orcaFailure)), report.orcaFailures)
        assertTrue(CleanupFailure(first.path, herdrFailure) in report.herdrFailures)
    }

    @Test
    fun `a locked folder's Orca terminals are closed once`() = runTest {
        val orca = FakeOrcaClient(terminals = mapOf(first.path to listOf(OrcaTerminal("term_1", first.path))))

        val report = CleanUpWorktreesUseCase(config, lockedOnce(), closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca))(
            listOf(first),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertEquals(listOf(first.path), orca.closedWorktrees)
        assertTrue(report.orcaFailures.isEmpty())
    }

    @Test
    fun `a locked folder without terminals or workspaces closes nothing in Orca`() = runTest {
        val orca = FakeOrcaClient()

        val report = CleanUpWorktreesUseCase(config, lockedOnce(), closeOrcaTerminals = CloseOrcaTerminalsUseCase(orca))(
            listOf(first),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertTrue(orca.closedWorktrees.isEmpty())
    }

    @Test
    fun `a headless app that fails to stop is reported and the worktree is still removed`() = runTest {
        val files = FakeFileManager()
        val pidFile = "${first.path}/desktopApp/build/run/main/main.pid"
        files.writtenProperties[pidFile] = mapOf("pid" to "4242")
        val launcher = FakeHeadlessAppLauncher(files, pidFile)
        launcher.alive += 4242L
        launcher.failStop = IllegalStateException("operation not permitted")
        val provisioning = FakeRepositoryProvisioningSource(
            mapOf(first.path to RepositoryProvisioning(providers = RepositoryProviders(desktopApp = DesktopAppProvider(":desktopApp")))),
        )
        val worktrees = HeadlessAppWorktrees(provisioning, { "${config.baseRepositoryPath}/.git/worktrees/TASK-1" }, launcher, FakeAppWindowService(), files)

        val report = CleanUpWorktreesUseCase(config, executor(), stopHeadlessApp = StopHeadlessAppUseCase(worktrees, launcher))(
            listOf(first),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertIs<WorktreeError.HeadlessAppStopFailed>(report.headlessAppFailures.single().error)
    }

    private suspend fun composeCleanup(source: ComposeProjectSource?, registry: ComposeProjectRegistry? = null, executor: FakeShellCommandExecutor = executor()) =
        CleanUpWorktreesUseCase(config, executor, composeProjects = source, composeRegistry = registry)(
            listOf(first),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
        ).getOrThrow()

    @Test
    fun `compose projects are only taken down with a source and removed worktrees`() = runTest {
        val source = FakeComposeProjectSource()
        val failingRemoval = executor(mapOf(listOf("git", "worktree", "remove", first.path) to { command -> failure(command) }))

        val withoutSource = composeCleanup(null)
        val nothingRemoved = composeCleanup(source, executor = failingRemoval)

        assertNull(withoutSource.composeDown)
        assertNull(withoutSource.composeUnavailable)
        assertNull(nothingRemoved.composeDown)
        assertTrue(source.listings.isEmpty())
    }

    @Test
    fun `docker being unavailable is reported with its reason or the error's message`() = runTest {
        fun failingWith(error: Exception) = object : ComposeProjectSource by FakeComposeProjectSource() {
            override suspend fun list(): List<ComposeProject> = throw error
        }

        assertEquals("daemon stopped", composeCleanup(FakeComposeProjectSource(unavailable = "daemon stopped")).composeUnavailable)
        assertEquals("Docker isn't available", composeCleanup(failingWith(WorktreeError.DockerUnavailable(null))).composeUnavailable)
        assertEquals("socket closed", composeCleanup(failingWith(IllegalStateException("socket closed"))).composeUnavailable)
        assertEquals("", composeCleanup(failingWith(IllegalStateException())).composeUnavailable)
    }

    @Test
    fun `projects recorded for removed worktrees are taken down and an unreadable registry is skipped`() = runTest {
        val recorded = ComposeProject("custom-name", "running(1)", emptyList())
        val source = FakeComposeProjectSource(projects = listOf(recorded))
        val registry = FakeComposeProjectRegistry(
            mapOf(config.baseRepositoryPath to listOf(ComposeRegistryEntry("custom-name", first.path), ComposeRegistryEntry("other", second.path))),
        )
        val unreadable = object : ComposeProjectRegistry by FakeComposeProjectRegistry() {
            override fun entries(repositoryPath: String): List<ComposeRegistryEntry> = throw IllegalStateException("unreadable")
        }

        composeCleanup(source, registry)
        assertEquals(listOf("custom-name" to false), source.downs)
        assertTrue(composeCleanup(FakeComposeProjectSource(projects = listOf(recorded)), unreadable).composeDown!!.failures.isEmpty())
    }
}
