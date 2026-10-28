package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeComposeProject
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MavenAndComposeMachineTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private fun machineTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            scope.coroutineContext[Job]?.cancelChildren()
        }
    }

    private val project = Project(
        id = "main-repo",
        name = "main-repo",
        config = ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects/worktrees", emptyList()),
    )
    private val mainWorktree = Worktree("/home/dev/projects/main-repo", "develop", isMain = true)
    private val taskWorktree = Worktree("/home/dev/projects/worktrees/TASK-1", "feature/TASK-1")

    private fun declaring(vararg resources: MachineResource) =
        MachineProject(project, listOf(mainWorktree, taskWorktree), resources.toSet())

    private class Calls {
        var mavenDaemons = emptyList<MavenDaemonProcess>()
        var mavenListings = 0
        var composeProjects: Result<List<WorktreeComposeProject>> = Result.success(emptyList())
        val takenDown = mutableListOf<List<String>>()
        var leftovers = emptyList<ComposeResource>()
        val removals = mutableListOf<Pair<List<ComposeResource>, Boolean>>()
    }

    private fun useCases(calls: Calls) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.success(app.luxion.shogunai.domain.model.DaemonStopReport()) },
        listDerivedData = { Result.success(DerivedDataListing(emptyList(), null)) },
        measureDerivedData = { _, _ -> emptyFlow() },
        measureCompilationCache = { Result.success(null) },
        deleteOrphanedDerivedData = { Result.success(DerivedDataDeletion()) },
        readEmulatorStates = { _, _ -> Result.success(emptyMap()) },
        readRunningEmulators = { Result.success(emptyMap()) },
        startEmulator = { _, _ -> Result.failure(IllegalStateException()) },
        readPool = { _, _, _ -> Result.failure(IllegalStateException()) },
        trimGradleDaemons = { Result.success(GradleTrim(limit = null)) },
        resourceSettings = { ResourceSettings() },
        stopEmulator = { _, _ -> Result.success(null) },
        stopEmulatorSerials = { _, _ -> Result.success(emptyList()) },
        readSimulatorStates = { _, _ -> Result.success(emptyMap()) },
        startSimulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopSimulator = { _, _ -> Result.success(null) },
        readSimulatorOrphans = { Result.success(emptyList()) },
        deleteSimulators = { Result.success(it) },
        listMavenDaemons = { calls.mavenListings++; Result.success(calls.mavenDaemons) },
        listComposeProjects = { _, _, _ -> calls.composeProjects },
        takeDownComposeProjects = { names -> calls.takenDown += names; ComposeDownReport(takenDown = names) },
        listComposeLeftovers = { _, _ -> Result.success(calls.leftovers) },
        removeComposeLeftovers = { _, resources, removeVolumes ->
            calls.removals += resources to removeVolumes
            val (volumes, others) = resources.partition { it.kind == ComposeResourceKind.VOLUME }
            val removed = others + if (removeVolumes) volumes else emptyList()
            calls.leftovers = calls.leftovers - removed.toSet()
            ComposeLeftoversReport(removed = removed, kept = if (removeVolumes) emptyList() else volumes)
        },
    )

    private fun machine(calls: Calls, vararg resources: MachineResource, existing: Set<String> = emptySet()) = MachineResources(
        scope = scope,
        useCases = useCases(calls),
        projects = { listOf(declaring(*resources)) },
        pathExists = { it in existing },
        ioDispatcher = dispatcher,
    )

    private fun composeProject(name: String, owner: ComposeOwner) =
        WorktreeComposeProject(ComposeProject(name, "running(1)", emptyList()), owner)

    @Test
    fun `maven and compose entries only show for projects that declare them`() = machineTest {
        val calls = Calls()

        assertTrue(machine(calls).visibleResources.isEmpty())
        assertEquals(
            listOf(MachineResource.MAVEN_DAEMONS, MachineResource.COMPOSE_PROJECTS),
            machine(calls, MachineResource.MAVEN_DAEMONS, MachineResource.COMPOSE_PROJECTS).visibleResources,
        )
        assertEquals(0, calls.mavenListings)
    }

    @Test
    fun `maven daemons are attributed to worktrees and have no stop action`() = machineTest {
        val calls = Calls().apply {
            mavenDaemons = listOf(
                MavenDaemonProcess(1, 100, "${taskWorktree.path}/service"),
                MavenDaemonProcess(2, 200, "/home/dev/other"),
                MavenDaemonProcess(3, 300, null),
            )
        }
        val machine = machine(calls, MachineResource.MAVEN_DAEMONS, existing = setOf("/home/dev/other", "${taskWorktree.path}/service"))

        machine.show(MachineResource.MAVEN_DAEMONS)
        runCurrent()

        val owners = assertIs<MachineLoad.Loaded<List<MachineMavenDaemon>>>(machine.mavenDaemons).value.map { it.owner }
        assertEquals(listOf(ProjectOwner.Owned("main-repo", taskWorktree.path), ProjectOwner.Outside, null), owners)
    }

    @Test
    fun `the maven summary counts daemons and memory`() = machineTest {
        val calls = Calls().apply { mavenDaemons = listOf(MavenDaemonProcess(1, 1_048_576, null), MavenDaemonProcess(2, 1_048_576, null)) }
        val machine = machine(calls, MachineResource.MAVEN_DAEMONS)

        machine.refreshSummaries()

        val summary = machine.summaries.getValue(MachineResource.MAVEN_DAEMONS)
        assertEquals(2, summary.count)
        assertEquals("2 · 2.0 MB", summary.text(MachineResource.MAVEN_DAEMONS, EnglishStrings))
    }

    @Test
    fun `compose projects are listed with their worktree or as belonging to a removed one`() = machineTest {
        val calls = Calls().apply {
            composeProjects = Result.success(
                listOf(
                    composeProject("main-repo-task-1", ComposeOwner.Worktree(taskWorktree.path)),
                    composeProject("main-repo-task-9", ComposeOwner.RemovedWorktree),
                ),
            )
        }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)

        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        assertEquals(2, assertIs<MachineLoad.Loaded<List<MachineComposeProject>>>(machine.composeProjectList).value.size)
        assertEquals(listOf("main-repo-task-9"), machine.removedComposeProjectNames)
    }

    @Test
    fun `taking down the projects of removed worktrees reports and reloads`() = machineTest {
        val calls = Calls().apply {
            composeProjects = Result.success(listOf(composeProject("main-repo-task-9", ComposeOwner.RemovedWorktree)))
        }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)
        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        machine.takeDownRemovedComposeProjects()
        runCurrent()

        assertEquals(listOf(listOf("main-repo-task-9")), calls.takenDown)
        assertEquals(listOf("main-repo-task-9"), machine.composeDownReport?.takenDown)
        assertFalse(machine.isTakingDownCompose)
    }

    @Test
    fun `docker unavailable is reported with its reason and nothing is taken down`() = machineTest {
        val calls = Calls().apply { composeProjects = Result.failure(WorktreeError.DockerUnavailable("daemon not reachable")) }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)

        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        assertEquals(MachineLoad.Unavailable, machine.composeProjectList)
        assertEquals("daemon not reachable", machine.composeUnavailableReason)
        machine.takeDownRemovedComposeProjects()
        runCurrent()
        assertTrue(calls.takenDown.isEmpty())
        assertNull(machine.composeDownReport)
    }

    private val leftoverNetwork = ComposeResource(ComposeResourceKind.NETWORK, "n1", "main-repo-task-9_default", "main-repo-task-9")
    private val leftoverVolume = ComposeResource(ComposeResourceKind.VOLUME, "main-repo-task-9_db", "main-repo-task-9_db", "main-repo-task-9")

    @Test
    fun `leftovers of removed worktrees are listed after the projects`() = machineTest {
        val calls = Calls().apply { leftovers = listOf(leftoverNetwork, leftoverVolume) }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)

        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        assertEquals(listOf(MachineComposeLeftover("main-repo", leftoverNetwork), MachineComposeLeftover("main-repo", leftoverVolume)), machine.composeLeftovers)
    }

    @Test
    fun `removing leftovers keeps volumes and reloads`() = machineTest {
        val calls = Calls().apply { leftovers = listOf(leftoverNetwork, leftoverVolume) }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)
        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        machine.removeComposeLeftovers(removeVolumes = false)
        runCurrent()

        assertEquals(false, calls.removals.single().second)
        assertEquals(listOf(leftoverNetwork), machine.composeLeftoversReport?.removed)
        assertEquals(listOf(MachineComposeLeftover("main-repo", leftoverVolume)), machine.composeLeftovers)
        assertFalse(machine.isRemovingComposeLeftovers)
    }

    @Test
    fun `removing leftovers with volumes removes them too`() = machineTest {
        val calls = Calls().apply { leftovers = listOf(leftoverNetwork, leftoverVolume) }
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)
        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        machine.removeComposeLeftovers(removeVolumes = true)
        runCurrent()

        assertEquals(true, calls.removals.single().second)
        assertTrue(machine.composeLeftovers.isEmpty())
    }

    @Test
    fun `nothing left means no leftovers and no removal`() = machineTest {
        val calls = Calls()
        val machine = machine(calls, MachineResource.COMPOSE_PROJECTS)
        machine.show(MachineResource.COMPOSE_PROJECTS)
        runCurrent()

        machine.removeComposeLeftovers(removeVolumes = false)
        runCurrent()

        assertTrue(machine.composeLeftovers.isEmpty())
        assertTrue(calls.removals.isEmpty())
    }
}
