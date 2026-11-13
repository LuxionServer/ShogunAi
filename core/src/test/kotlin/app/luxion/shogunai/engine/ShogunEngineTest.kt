package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryApproval
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.io.EnginePorts
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.CustomPoolFailure
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.WorktreeEnvironmentCache
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShogunEngineTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val admin = "$common/worktrees/TASK-123"
    private val config = ProjectConfig(repository, "/home/dev/projects/worktrees", emptyList())

    private val gitExecutor = object : ScriptedShellCommandExecutor() {
        val commands = mutableListOf<List<String>>()

        override suspend fun respond(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "$admin\n$common\n").also { commands += command }
    }

    private fun provisioning(providers: RepositoryProviders) = FakeRepositoryProvisioningSource(
        mapOf(repository to RepositoryProvisioning(providers = providers), task to RepositoryProvisioning(providers = providers)),
    )

    @Test
    fun `the engine tells a Mac host from the DerivedData source`() {
        assertTrue(ShogunEngine(fakeEnginePorts(derivedData = FakeDerivedDataSource(supported = true))).isMac)
        assertFalse(ShogunEngine(fakeEnginePorts(derivedData = FakeDerivedDataSource(supported = false))).isMac)
    }

    @Test
    fun `the default options pause for real and keep no environment cache`() = runTest {
        val options = EngineOptions()

        options.pauseWhileWaiting(Duration.ZERO)
        options.pauseWhileStartingApp(0)

        assertNull(options.environmentCache)
        assertTrue(Duration.between(options.clock(), Instant.now()).abs() < Duration.ofMinutes(1))
    }

    @Test
    fun `resolving an environment uses the trusted repository's providers and the given cache`() = runTest {
        val cache = WorktreeEnvironmentCache { "gitdir: $admin" }
        val engine = ShogunEngine(
            fakeEnginePorts(
                executor = gitExecutor,
                provisioningSource = provisioning(RepositoryProviders(gradle = GradleProvider(daemonMaxHeap = "2g"))),
                trust = FakeRepositoryTrust(approvesEverything = true),
            ),
            EngineOptions(environmentCache = cache),
        )

        engine.resolveEnvironment(repository, task, reserve = false).getOrThrow()
        engine.resolveEnvironment(repository, task, reserve = false).getOrThrow()

        assertEquals(1, gitExecutor.commands.count { it.take(2) == listOf("git", "rev-parse") })
    }

    @Test
    fun `the headless app of a worktree is looked up under its Git admin directory`() = runTest {
        val engine = ShogunEngine(
            fakeEnginePorts(
                executor = gitExecutor,
                provisioningSource = provisioning(RepositoryProviders(desktopApp = DesktopAppProvider(":desktopApp"))),
            ),
        )

        assertTrue(engine.readHeadlessApp(task).isSuccess)
        assertTrue(gitExecutor.commands.isNotEmpty())
    }

    @Test
    fun `the desktop turn and physical devices start empty`() = runTest {
        val engine = ShogunEngine(fakeEnginePorts(executor = gitExecutor))

        assertTrue(engine.readDesktopTurn().isSuccess)
        assertTrue(engine.readPhysicalDevices().isSuccess)
    }

    @Test
    fun `custom pools resolve the worktree's repository and start empty`() = runTest {
        val engine = ShogunEngine(fakeEnginePorts(executor = gitExecutor))

        assertIs<CustomPoolFailure>(engine.acquireCustomPool(task, "db").exceptionOrNull())
        assertEquals(emptyList(), engine.customPoolLedger.variablesFor(task))
        assertEquals(emptyList(), engine.readCustomPools(repository).getOrThrow())
        assertEquals(emptyList(), engine.releaseCustomPool.onWorktreeRemoved(task))
    }

    @Test
    fun `a project's use cases check its base repository before listing`() = runTest {
        val listed = ShogunEngine(fakeEnginePorts(fileManager = FakeFileManager())).project(config).listWorktrees()

        assertIs<WorktreeError.BaseRepositoryNotFound>(listed.exceptionOrNull())
    }

    @Test
    fun `a project's creation asks the given approval`() = runTest {
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)
        val project = ShogunEngine(fakeEnginePorts()).project(config)

        assertSame(config, project.config)
        project.create(approval)
        project.createFromBranch(approval)
        project.provision(approval)
        ShogunEngine(fakeEnginePorts()).createGroup(approval)
    }
}
