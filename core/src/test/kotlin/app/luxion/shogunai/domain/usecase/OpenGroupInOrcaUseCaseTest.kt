package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.OrcaAgent
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenGroupInOrcaUseCaseTest {

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

    private suspend fun openGroup(orca: FakeOrcaClient) = OpenGroupInOrcaUseCase(orca, resolveEnvironment, usesPowerShell = false)(group())

    private fun openAgent(orca: FakeOrcaClient) =
        OpenMultirepoAgentInOrcaUseCase(orca, samePath = samePath)

    @Test
    fun `a group opens each worktree under its own repository with its group session name`() = runTest {
        val orca = FakeOrcaClient()

        val openings = openGroup(orca).getOrThrow()

        assertEquals(listOf("app", "api"), openings.map { it.alias })
        assertEquals(listOf(appTask, apiTask), openings.map { it.path })
        assertTrue(openings.all { it.result.getOrThrow().outcome == OrcaOutcome.STARTED_AGENT })
        assertEquals(listOf(world.appBase, world.apiBase), orca.addedRepos)
        assertEquals(listOf(appTask, apiTask), orca.created.map { it.worktreePath })
        assertTrue(orca.created.all { it.focus })
        assertTrue(orca.created[0].command.endsWith("-n app-task-1"), orca.created[0].command)
        assertTrue(orca.created[1].command.endsWith("-n api-task-1"), orca.created[1].command)
    }

    @Test
    fun `a member with a running emulator gets it shown by its own repository's lookup`() = runTest {
        val orca = FakeOrcaClient()
        val lookups = mutableListOf<Pair<String, String>>()
        val open = OpenGroupInOrcaUseCase(orca, resolveEnvironment, usesPowerShell = false) { config, path ->
            lookups += config.baseRepositoryPath to path
            "emulator-5556".takeIf { config.baseRepositoryPath == world.appBase }
        }

        val openings = open(group()).getOrThrow()

        assertEquals(listOf(world.appBase to appTask, world.apiBase to apiTask), lookups)
        assertEquals(listOf(appTask to "emulator-5556"), orca.attachedEmulators)
        assertEquals(listOf("emulator-5556", null), openings.map { it.result.getOrThrow().emulator })
    }

    @Test
    fun `a worktree with a live agent is focused and the other gets its agent started`() = runTest {
        val orca = FakeOrcaClient(terminals = mapOf(appTask to listOf(OrcaTerminal("term_app", appTask))), agents = mapOf("term_app" to "claude"))

        val openings = openGroup(orca).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.FOCUSED, "term_app", "claude"), openings[0].result.getOrThrow())
        assertEquals(OrcaOutcome.STARTED_AGENT, openings[1].result.getOrThrow().outcome)
        assertEquals(listOf("term_app"), orca.switched)
        assertEquals(listOf(apiTask), orca.created.map { it.worktreePath })
    }

    @Test
    fun `repositories Orca already knows from its group aren't registered again`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(OrcaRepo("r-app", world.appBase), OrcaRepo("r-api", world.apiBase)))

        assertTrue(openGroup(orca).getOrThrow().all { it.result.isSuccess })
        assertEquals(emptyList(), orca.addedRepos)
    }

    @Test
    fun `a worktree whose agent doesn't start fails while the other still opens`() = runTest {
        val orca = FakeOrcaClient(
            terminals = mapOf(appTask to listOf(OrcaTerminal("term_app", appTask))),
            agents = mapOf("term_app" to "claude"),
            startsAgents = false,
        )

        val openings = openGroup(orca).getOrThrow()

        assertEquals(OrcaOutcome.FOCUSED, openings[0].result.getOrThrow().outcome)
        assertIs<WorktreeError.OrcaAgentNotStarted>(openings[1].result.exceptionOrNull())
    }

    @Test
    fun `without Orca the group fails once and opens nothing`() = runTest {
        val orca = FakeOrcaClient(installed = false)

        assertIs<WorktreeError.OrcaNotInstalled>(openGroup(orca).exceptionOrNull())
        assertEquals(0, orca.statusChecks)
    }

    @Test
    fun `the multirepo agent starts in the folder workspace without an agent`() = runTest {
        val orca = FakeOrcaClient(
            psRows = listOf(OrcaWorktree(world.folder, false, folderId = "f1")),
            folderPaths = mapOf("f1" to world.folder),
        )

        val opening = openAgent(orca)(multirepo).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", multirepo.agent.displayName), opening)
        assertEquals(listOf(FakeOrcaClient.Created(world.folder, "product", "claude -n product", true)), orca.created)
        assertEquals(emptyList(), orca.addedRepos)
    }

    @Test
    fun `a live agent in the folder's workspace is focused without typing anything`() = runTest {
        val orca = FakeOrcaClient(
            psRows = listOf(OrcaWorktree(world.folder, false, listOf(OrcaAgent("claude", AgentState.DONE)), folderId = "f1")),
            folderPaths = mapOf("f1" to world.folder),
            terminals = mapOf(world.folder to listOf(OrcaTerminal("term_shell", ""), OrcaTerminal("term_agent", ""))),
            agents = mapOf("term_agent" to "claude"),
        )

        val opening = openAgent(orca)(multirepo).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.FOCUSED, "term_agent", "claude"), opening)
        assertEquals(listOf("term_agent"), orca.switched)
        assertTrue(orca.created.isEmpty())
    }

    @Test
    fun `among several workspaces in the folder the one with a live agent is used, otherwise the first`() = runTest {
        val withAgent = FakeOrcaClient(
            psRows = listOf(
                OrcaWorktree(world.folder, false, folderId = "f1"),
                OrcaWorktree(world.folder, false, listOf(OrcaAgent("claude", AgentState.WORKING)), folderId = "f2"),
            ),
            terminals = mapOf("folder:f2" to listOf(OrcaTerminal("term_f2", ""))),
            agents = mapOf("term_f2" to "claude"),
        )
        assertEquals("term_f2", openAgent(withAgent)(multirepo).getOrThrow().terminalHandle)

        val withoutAgent = FakeOrcaClient(
            psRows = listOf(OrcaWorktree(world.folder, false, folderId = "f1"), OrcaWorktree(world.folder, false)),
        )
        openAgent(withoutAgent)(multirepo).getOrThrow()
        assertEquals(listOf("folder:f1"), withoutAgent.created.map { it.worktreePath })
    }

    @Test
    fun `a folder opened as a folder or a Git main worktree is used by its path, matching canonical paths`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree("/private${world.folder}", false)))

        openAgent(orca)(multirepo).getOrThrow()

        assertEquals(listOf("/private${world.folder}"), orca.created.map { it.worktreePath })
    }

    @Test
    fun `a Git multirepo folder Orca doesn't know is registered and its main worktree hosts the agent`() = runTest {
        val orca = FakeOrcaClient()

        openAgent(orca)(multirepo.copy(folderIsRepository = true)).getOrThrow()

        assertEquals(listOf(world.folder), orca.addedRepos)
        assertEquals(listOf(world.folder), orca.created.map { it.worktreePath })
    }

    @Test
    fun `a non Git folder without a workspace in Orca fails and registers nothing`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree(world.appBase, false)))

        val error = openAgent(orca)(multirepo).exceptionOrNull()

        assertIs<WorktreeError.OrcaMultirepoFolderNotOpen>(error)
        assertEquals(world.folder, error.folder)
        assertTrue(orca.addedRepos.isEmpty() && orca.created.isEmpty())
    }

    @Test
    fun `the multirepo agent that doesn't start fails naming its terminal and command`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree(world.folder, false, folderId = "f1")), startsAgents = false)

        val error = openAgent(orca)(multirepo).exceptionOrNull()

        assertIs<WorktreeError.OrcaAgentNotStarted>(error)
        assertEquals("term_1", error.terminalHandle)
        assertEquals("claude -n product", error.command)
    }

    @Test
    fun `a closed Orca is started before looking for the folder's workspace`() = runTest {
        val orca = FakeOrcaClient(running = false, psRows = listOf(OrcaWorktree(world.folder, false, folderId = "f1")))

        openAgent(orca)(multirepo).getOrThrow()

        assertEquals(1, orca.starts)
        assertEquals(1, orca.psReads)
    }
}
