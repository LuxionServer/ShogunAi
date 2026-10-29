package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentRunnersTest {

    private val world = MultirepoTestWorld()
    private val appTask = "${world.worktreesRoot}/app/TASK-1"
    private val apiTask = "${world.worktreesRoot}/api/TASK-1"
    private val samePath = { first: String, second: String -> first.removePrefix("/private") == second.removePrefix("/private") }
    private val multirepo = Multirepo(world.folder, world.declaration, approved = true, localAgent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))

    init {
        with(world.git) {
            world.app.worktree(appTask, "feature/TASK-1")
            world.api.worktree(apiTask, "feature/TASK-1")
        }
    }

    private val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(
        world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true),
    )

    private suspend fun group() = DetectGroupsUseCase(world.git)(world.members()).groups.single()

    private fun herdr(client: FakeHerdrClient) = HerdrAgentRunner(client, resolveEnvironment)

    private fun orca(client: FakeOrcaClient) = OrcaAgentRunner(client, resolveEnvironment, usesPowerShell = false, samePath = samePath)

    @Test
    fun `every Herdr and Orca outcome maps to the runner's outcome`() {
        assertEquals(listOf(RunnerOutcome.OPENED, RunnerOutcome.STARTED_AGENT, RunnerOutcome.FOCUSED), HerdrOutcome.entries.map { it.toRunnerOutcome() })
        assertEquals(listOf(RunnerOutcome.STARTED_AGENT, RunnerOutcome.FOCUSED), OrcaOutcome.entries.map { it.toRunnerOutcome() })
    }

    @Test
    fun `a worktree opened in Herdr reports its pane as the handle`() = runTest {
        val opening = herdr(FakeHerdrClient()).openWorktree(world.appProject.config, Worktree(appTask, "feature/TASK-1")).getOrThrow()

        assertEquals(RunnerId.HERDR, opening.runner)
        assertEquals(RunnerOutcome.OPENED, opening.outcome)
        assertEquals("w1:p1", opening.handle)
    }

    @Test
    fun `a worktree opened in Orca reports its terminal as the handle`() = runTest {
        val opening = orca(FakeOrcaClient()).openWorktree(world.appProject.config, Worktree(appTask, "feature/TASK-1")).getOrThrow()

        assertEquals(RunnerId.ORCA, opening.runner)
        assertEquals(RunnerOutcome.STARTED_AGENT, opening.outcome)
        assertEquals("term_1", opening.handle)
    }

    @Test
    fun `a worktree opened in Orca reports the simulator it shows`() = runTest {
        val client = FakeOrcaClient()
        val runner = OrcaAgentRunner(client, resolveEnvironment, usesPowerShell = false, samePath = samePath, runningSimulator = { _, _ -> "SIM-UDID-1" })

        val opening = runner.openWorktree(world.appProject.config, Worktree(appTask, "feature/TASK-1")).getOrThrow()

        assertEquals("SIM-UDID-1", opening.simulator)
        assertFalse(opening.devicesSkipped)
        assertEquals(listOf(appTask to "SIM-UDID-1"), client.attachedEmulators)
    }

    @Test
    fun `a worktree opened in Orca with both devices running reports them skipped`() = runTest {
        val client = FakeOrcaClient()
        val runner = OrcaAgentRunner(
            client,
            resolveEnvironment,
            usesPowerShell = false,
            samePath = samePath,
            runningEmulator = { _, _ -> "emulator-5556" },
            runningSimulator = { _, _ -> "SIM-UDID-1" },
        )

        val opening = runner.openWorktree(world.appProject.config, Worktree(appTask, "feature/TASK-1")).getOrThrow()

        assertTrue(opening.devicesSkipped)
        assertTrue(client.attachedEmulators.isEmpty())
    }

    @Test
    fun `a group opens in Herdr member by member and keeps each result`() = runTest {
        val client = FakeHerdrClient(
            alreadyOpen = setOf(appTask),
            panes = listOf(HerdrPane("w1:p1", "w1", appTask, agent = "claude", agentStatus = "idle")),
            openFailures = mapOf(apiTask to WorktreeError.HerdrNotInstalled),
        )

        val openings = herdr(client).openGroup(group()).getOrThrow()

        assertEquals(listOf("app", "api"), openings.map { it.alias })
        assertEquals(RunnerOpening(RunnerId.HERDR, RunnerOutcome.FOCUSED, "w1:p1", "claude"), openings[0].result.getOrThrow())
        assertIs<WorktreeError.HerdrNotInstalled>(openings[1].result.exceptionOrNull())
    }

    @Test
    fun `a group opens in Orca with a live agent focused`() = runTest {
        val client = FakeOrcaClient(terminals = mapOf(appTask to listOf(OrcaTerminal("term_app", appTask))), agents = mapOf("term_app" to "claude"))

        val openings = orca(client).openGroup(group()).getOrThrow()

        assertEquals(RunnerOpening(RunnerId.ORCA, RunnerOutcome.FOCUSED, "term_app", "claude"), openings[0].result.getOrThrow())
        assertEquals(RunnerOutcome.STARTED_AGENT, openings[1].result.getOrThrow().outcome)
    }

    @Test
    fun `a group in Orca fails once when Orca isn't installed`() = runTest {
        val runner = orca(FakeOrcaClient(installed = false))

        assertFalse(runner.isInstalled())
        assertIs<WorktreeError.OrcaNotInstalled>(runner.openGroup(group()).exceptionOrNull())
    }

    @Test
    fun `the multirepo agent opens through either runner`() = runTest {
        assertEquals(
            RunnerOpening(RunnerId.HERDR, RunnerOutcome.OPENED, "plain1:p1", multirepo.agent.displayName),
            herdr(FakeHerdrClient()).openMultirepoAgent(multirepo).getOrThrow(),
        )
        val inOrca = orca(FakeOrcaClient()).openMultirepoAgent(multirepo.copy(folderIsRepository = true)).getOrThrow()
        assertEquals(RunnerId.ORCA, inOrca.runner)
        assertEquals(RunnerOutcome.STARTED_AGENT, inOrca.outcome)
    }

    @Test
    fun `a failure passes through unchanged`() = runTest {
        val result = herdr(FakeHerdrClient(failure = WorktreeError.HerdrNotInstalled)).openWorktree(world.appProject.config, Worktree(appTask, "feature/TASK-1"))

        assertIs<WorktreeError.HerdrNotInstalled>(result.exceptionOrNull())
    }
}
