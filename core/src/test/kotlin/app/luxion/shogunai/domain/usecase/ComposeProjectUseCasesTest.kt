package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeComposeProjectRegistry
import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeProjectUseCasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktreesRoot = "/home/dev/projects/worktrees"
    private val worktrees = listOf(
        Worktree(repository, "develop", isMain = true),
        Worktree("$worktreesRoot/TASK-123", "feature/TASK-123"),
    )

    private fun project(name: String, file: String, status: String = "running(2)") = ComposeProject(name, status, listOf(file))

    private suspend fun list(source: FakeComposeProjectSource) = ListComposeProjectsUseCase(source)(repository, worktrees, worktreesRoot)

    @Test
    fun `a project named like a worktree belongs to it`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-123", "$worktreesRoot/TASK-123/compose.yaml")))

        val listed = list(source).getOrThrow().single()

        assertEquals(ComposeOwner.Worktree("$worktreesRoot/TASK-123"), listed.owner)
        assertEquals("running(2)", listed.project.status)
    }

    @Test
    fun `a project of a removed worktree is recognised by its compose files`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml", "exited(1)")))

        assertEquals(ComposeOwner.RemovedWorktree, list(source).getOrThrow().single().owner)
    }

    @Test
    fun `an unrelated project is not listed`() = runTest {
        val source = FakeComposeProjectSource(
            listOf(
                project("other-app", "/home/dev/other/compose.yaml"),
                project("main-repo-task-9", "/home/dev/elsewhere/compose.yaml"),
                project("main-repo-extra-task-1", "/home/dev/projects/main-repo-extra/worktrees/TASK-1/compose.yaml"),
            ),
        )

        assertTrue(list(source).getOrThrow().isEmpty())
    }

    @Test
    fun `a sibling repository sharing the prefix is never an orphan`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo2-task-1", "$worktreesRoot/TASK-1/compose.yaml")))

        assertTrue(list(source).getOrThrow().isEmpty())
    }

    @Test
    fun `an unavailable docker is a failure with the reason`() = runTest {
        val result = list(FakeComposeProjectSource(unavailable = "daemon not reachable"))

        assertEquals("Docker isn't available: daemon not reachable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `takes projects down keeping volumes unless asked`() = runTest {
        val source = FakeComposeProjectSource()

        val kept = TakeDownComposeProjectsUseCase(source)(listOf("main-repo-task-9"))
        val removed = TakeDownComposeProjectsUseCase(source)(listOf("main-repo-task-9"), removeVolumes = true)

        assertEquals(listOf("main-repo-task-9"), kept.takenDown)
        assertEquals(listOf("main-repo-task-9" to false, "main-repo-task-9" to true), source.downs)
        assertTrue(removed.failures.isEmpty())
    }

    @Test
    fun `a failing project is reported and the rest are still processed`() = runTest {
        val source = FakeComposeProjectSource(failingDowns = mapOf("a" to "network in use"))

        val report = TakeDownComposeProjectsUseCase(source)(listOf("a", "b"))

        assertEquals(listOf("b"), report.takenDown)
        assertEquals("network in use", report.failures.single().detail)
    }

    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = worktreesRoot, secretFiles = emptyList())
    private val mainBranch = MainBranch("develop", "origin/develop")
    private val removed = Worktree("$worktreesRoot/TASK-9", "feature/TASK-9")

    @Test
    fun `cleanup takes down the projects of the removed worktrees`() = runTest {
        val source = FakeComposeProjectSource(
            listOf(project("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml"), project("main-repo-task-123", "x")),
        )

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
        ).getOrThrow()

        assertEquals(listOf("main-repo-task-9"), report.composeDown?.takenDown)
        assertEquals(listOf("main-repo-task-9" to false), source.downs)
    }

    @Test
    fun `cleanup without the option never touches docker`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-9", "x")))

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertNull(report.composeDown)
        assertTrue(source.listings.isEmpty() && source.downs.isEmpty())
    }

    @Test
    fun `cleanup still succeeds when docker is unavailable and says the projects were kept`() = runTest {
        val source = FakeComposeProjectSource(unavailable = "daemon not reachable")

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
        ).getOrThrow()

        assertEquals(listOf(removed.path), report.removed)
        assertEquals("daemon not reachable", report.composeUnavailable)
        assertNull(report.composeDown)
    }

    private val task9 = "$worktreesRoot/TASK-9"
    private fun resource(kind: ComposeResourceKind, name: String, project: String = "main-repo-task-9") = ComposeResource(kind, name, name, project)
    private val volume = resource(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data")
    private val network = resource(ComposeResourceKind.NETWORK, "main-repo-task-9_default")
    private val image = resource(ComposeResourceKind.IMAGE, "main-repo-task-9-api")

    private fun registryWith(vararg entries: ComposeRegistryEntry) = FakeComposeProjectRegistry(mapOf(repository to entries.toList()))

    @Test
    fun `leftovers are the resources of recorded projects whose worktree is gone`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9), ComposeRegistryEntry("main-repo-task-123", "$worktreesRoot/TASK-123"))
        val source = FakeComposeProjectSource(
            resources = listOf(volume, network, image, resource(ComposeResourceKind.VOLUME, "main-repo-task-123_db", "main-repo-task-123")),
        )

        val leftovers = ListComposeLeftoversUseCase(source, registry)(repository, worktrees).getOrThrow()

        assertEquals(listOf(volume, network, image), leftovers)
    }

    @Test
    fun `a project that was never recorded has no leftovers even with the repository's prefix`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(volume))

        assertEquals(emptyList(), ListComposeLeftoversUseCase(source, FakeComposeProjectRegistry())(repository, worktrees).getOrThrow())
    }

    @Test
    fun `a recorded name used again by an existing worktree is not a leftover`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-123", "/old/place/TASK-123"))
        val source = FakeComposeProjectSource(resources = listOf(resource(ComposeResourceKind.VOLUME, "v", "main-repo-task-123")))

        assertEquals(emptyList(), ListComposeLeftoversUseCase(source, registry)(repository, worktrees).getOrThrow())
    }

    @Test
    fun `a name recorded by another repository too is never a leftover`() = runTest {
        val registry = FakeComposeProjectRegistry(
            mapOf(
                repository to listOf(ComposeRegistryEntry("main-repo-task-9", task9)),
                "/home/dev/other/main-repo" to listOf(ComposeRegistryEntry("main-repo-task-9", "/home/dev/other/worktrees/TASK-9")),
            ),
        )

        assertEquals(emptyList(), ListComposeLeftoversUseCase(FakeComposeProjectSource(resources = listOf(volume)), registry)(repository, worktrees).getOrThrow())
    }

    @Test
    fun `a recorded project with nothing left is dropped from the registry`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))

        ListComposeLeftoversUseCase(FakeComposeProjectSource(), registry)(repository, worktrees).getOrThrow()

        assertEquals(emptyList(), registry.entries(repository))
    }

    @Test
    fun `a recorded project that still has containers is kept in the registry`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))
        val source = FakeComposeProjectSource(projects = listOf(project("main-repo-task-9", "/elsewhere/compose.yaml")))

        ListComposeLeftoversUseCase(source, registry)(repository, worktrees).getOrThrow()

        assertEquals(1, registry.entries(repository).size)
    }

    @Test
    fun `listing leftovers without docker fails as unavailable`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))

        val error = ListComposeLeftoversUseCase(FakeComposeProjectSource(unavailable = "daemon down"), registry)(repository, worktrees).exceptionOrNull()

        assertTrue(error is WorktreeError.DockerUnavailable)
    }

    @Test
    fun `a recorded project of a removed worktree is listed as removed even with files elsewhere`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-9", "/elsewhere/compose.yaml")))

        val listed = ListComposeProjectsUseCase(source, registry)(repository, worktrees, worktreesRoot).getOrThrow().single()

        assertEquals(ComposeOwner.RemovedWorktree, listed.owner)
    }

    @Test
    fun `removing leftovers keeps volumes by default, networks first`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(volume, image, network))

        val report = RemoveComposeLeftoversUseCase(source)(listOf(volume, image, network), removeVolumes = false)

        assertEquals(listOf(network, image), source.removals)
        assertEquals(listOf(network, image), report.removed)
        assertEquals(listOf(volume), report.kept)
    }

    @Test
    fun `removing leftovers with volumes removes them last`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(volume, image, network))

        val report = RemoveComposeLeftoversUseCase(source)(listOf(volume, image, network), removeVolumes = true)

        assertEquals(listOf(network, image, volume), report.removed)
        assertEquals(emptyList(), report.kept)
    }

    @Test
    fun `an image in use is reported and the rest is still removed`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(image, network), failingRemovals = mapOf(image.id to "image is being used by running container"))

        val report = RemoveComposeLeftoversUseCase(source)(listOf(image, network), removeVolumes = false)

        assertEquals(listOf(network), report.removed)
        assertEquals("image is being used by running container", report.failures.single().detail)
    }

    @Test
    fun `a project whose resources were all removed is dropped from the registry`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))

        RemoveComposeLeftoversUseCase(FakeComposeProjectSource(), registry)(listOf(network, image, volume), removeVolumes = true, repositoryPath = repository)

        assertEquals(emptyList(), registry.entries(repository))
    }

    @Test
    fun `a project whose volume was kept stays in the registry`() = runTest {
        val registry = registryWith(ComposeRegistryEntry("main-repo-task-9", task9))

        RemoveComposeLeftoversUseCase(FakeComposeProjectSource(), registry)(listOf(network, volume), removeVolumes = false, repositoryPath = repository)

        assertEquals(1, registry.entries(repository).size)
    }

    private val composeRepository = "/home/dev/projects/main-repo"
    private val taskPath = "$worktreesRoot/TASK-123"

    private fun runUseCase(
        source: FakeComposeProjectSource,
        registry: FakeComposeProjectRegistry? = FakeComposeProjectRegistry(),
        providers: RepositoryProviders = RepositoryProviders(dockerCompose = DockerComposeProvider(listOf("DB_PORT"))),
    ): RunComposeProjectUseCase {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, "$composeRepository/.git/worktrees/TASK-123\n$composeRepository/.git\n")
        }
        val resolve = ResolveWorktreeEnvironmentUseCase(
            executor,
            FakeRepositoryProvisioningSource(
                byRepository = mapOf(
                    composeRepository to RepositoryProvisioning(providers = providers),
                ),
            ),
            FakeEnvironmentStateStore(),
            FakePortProbe(),
            MachineProviderContributions(FakeAppleSimulators(), { SimulatorSettings() }, userHome = "/home/dev"),
        )
        return RunComposeProjectUseCase(source, resolve, registry)
    }

    @Test
    fun `up runs in the worktree with its project name and ports`() = runTest {
        val source = FakeComposeProjectSource()
        val registry = FakeComposeProjectRegistry()

        val project = runUseCase(source, registry)(composeRepository, taskPath, ComposeAction.Up(build = true, extraArguments = listOf("api", "--wait"))).getOrThrow()

        val run = source.runs.single()
        assertEquals("main-repo-task-123", project)
        assertEquals(taskPath, run.workingDirectory)
        assertEquals("main-repo-task-123", run.environment["COMPOSE_PROJECT_NAME"])
        assertTrue(run.environment.getValue("DB_PORT").toInt() in 20000..29999)
        assertEquals(ComposeAction.Up(true, listOf("api", "--wait")), run.action)
        assertEquals(listOf(ComposeRegistryEntry("main-repo-task-123", taskPath)), registry.entries(composeRepository))
    }

    @Test
    fun `build runs with the worktree's environment`() = runTest {
        val source = FakeComposeProjectSource()

        runUseCase(source)(composeRepository, taskPath, ComposeAction.Build()).getOrThrow()

        assertEquals(ComposeAction.Build(), source.runs.single().action)
        assertEquals("main-repo-task-123", source.runs.single().environment["COMPOSE_PROJECT_NAME"])
    }

    @Test
    fun `a failing compose command is returned as a typed error`() = runTest {
        val error = runUseCase(FakeComposeProjectSource(failingRun = "port is already allocated"))(composeRepository, taskPath, ComposeAction.Up()).exceptionOrNull()

        assertEquals("port is already allocated", (error as WorktreeError.ComposeCommandFailed).detail)
    }

    @Test
    fun `a failing registry doesn't stop the services from starting`() = runTest {
        val source = FakeComposeProjectSource()

        runUseCase(source, FakeComposeProjectRegistry(failing = true))(composeRepository, taskPath, ComposeAction.Up()).getOrThrow()

        assertEquals(1, source.runs.size)
    }

    private suspend fun cleanUp(source: FakeComposeProjectSource, removeVolumes: Boolean, registry: FakeComposeProjectRegistry? = null) =
        CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source, composeRegistry = registry)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
            removeComposeVolumes = removeVolumes,
        ).getOrThrow()

    @Test
    fun `cleanup removes the networks and built images of removed worktrees and keeps their volumes`() = runTest {
        val other = resource(ComposeResourceKind.IMAGE, "main-repo-task-123-api", "main-repo-task-123")
        val source = FakeComposeProjectSource(resources = listOf(volume, network, image, other))

        val report = cleanUp(source, removeVolumes = false)

        assertEquals(listOf(network, image), report.composeResources?.removed)
        assertEquals(listOf(volume), report.composeResources?.kept)
        assertTrue(other !in source.removals)
    }

    @Test
    fun `cleanup removes volumes when asked`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(volume, network, image))

        val report = cleanUp(source, removeVolumes = true)

        assertEquals(listOf(network, image, volume), report.composeResources?.removed)
    }

    @Test
    fun `cleanup reports an image in use without failing`() = runTest {
        val source = FakeComposeProjectSource(resources = listOf(image), failingRemovals = mapOf(image.id to "in use"))

        val report = cleanUp(source, removeVolumes = false)

        assertEquals(listOf(removed.path), report.removed)
        assertEquals("in use", report.composeResources?.failures?.single()?.detail)
    }

    @Test
    fun `cleanup also removes the resources of a declared project name recorded for the worktree`() = runTest {
        val custom = resource(ComposeResourceKind.NETWORK, "custom_default", "custom")
        val registry = registryWith(ComposeRegistryEntry("custom", removed.path))

        val report = cleanUp(FakeComposeProjectSource(resources = listOf(custom)), removeVolumes = false, registry = registry)

        assertEquals(listOf(custom), report.composeResources?.removed)
        assertEquals(emptyList(), registry.entries(repository))
    }

    @Test
    fun `cleanup without docker reports the resources as kept`() = runTest {
        val report = cleanUp(FakeComposeProjectSource(unavailable = "daemon not reachable"), removeVolumes = true)

        assertNull(report.composeResources)
        assertEquals("daemon not reachable", report.composeUnavailable)
    }
    @Test
    fun `a worktree without the compose provider runs under its own project name and without a registry`() = runTest {
        val source = FakeComposeProjectSource()

        val project = runUseCase(source, registry = null, providers = RepositoryProviders())(composeRepository, taskPath, ComposeAction.Up()).getOrThrow()

        assertEquals(worktreeComposeProjectName(composeRepository, taskPath), project)
    }

    @Test
    fun `failures without a message are reported with an empty reason`() = runTest {
        val source = object : ComposeProjectSource by FakeComposeProjectSource() {
            override suspend fun down(name: String, removeVolumes: Boolean): Result<Unit> = Result.failure(IllegalStateException())
            override suspend fun remove(resource: ComposeResource): Result<Unit> = Result.failure(IllegalStateException())
        }

        assertEquals("", TakeDownComposeProjectsUseCase(source)(listOf("main-repo-task-9")).failures.single().detail)
        assertEquals("", RemoveComposeLeftoversUseCase(source)(listOf(network), removeVolumes = false).failures.single().detail)
    }

    @Test
    fun `an unreadable registry has no leftovers`() = runTest {
        val registry = object : ComposeProjectRegistry by FakeComposeProjectRegistry() {
            override fun entries(repositoryPath: String): List<ComposeRegistryEntry> = throw IllegalStateException("unreadable")
            override fun recordedElsewhere(repositoryPath: String): Set<String> = throw IllegalStateException("unreadable")
        }

        assertEquals(emptyList(), ListComposeLeftoversUseCase(FakeComposeProjectSource(resources = listOf(volume)), registry)(repository, worktrees).getOrThrow())
    }
}
