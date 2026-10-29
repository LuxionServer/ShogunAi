package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.model.OrcaAgent
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.ClaudeSessionRef
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DetectRunningAgentsUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )

    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val worktrees = listOf(main, nested, sibling)

    private val lsofOutput = """
        p100
        c2.1.301
        fcwd
        n/home/dev/projects/main-repo/.claude/worktrees/task
        p101
        czsh
        fcwd
        n/home/dev/projects/main-repo
        p102
        cnode
        fcwd
        n/home/dev/projects/TASK-1/app
        p103
        ccodex
        fcwd
        n/home/dev/projects/main-repo/src
        p104
        cclaude
        fcwd
        n/home/dev/projects/main-repo/.claude/worktrees/task
        p200
        cclaude
        fcwd
        n/home/dev/elsewhere
    """.trimIndent()

    private val psOutput = """
          100 claude
          101 -zsh
          102 /usr/local/bin/node
          103 /opt/homebrew/bin/codex
          104 /Users/dev/.local/bin/claude
    """.trimIndent()

    private fun executor(lsof: String = lsofOutput, ps: String = psOutput, failLsof: Boolean = false) =
        FakeShellCommandExecutor { command ->
            when (command.first()) {
                "lsof" -> if (failLsof) failure(command, exitCode = 1) else success(command, stdout = lsof)
                "ps" -> success(command, stdout = ps)
                else -> success(command)
            }
        }

    @Test
    fun `agents are attributed to the deepest worktree containing their working directory`() = runTest {
        val agents = DetectRunningAgentsUseCase(executor())(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
        assertEquals(null, agents[sibling.path])
    }

    @Test
    fun `only processes inside some worktree are inspected with ps`() = runTest {
        val executor = executor()

        DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        val ps = executor.executedCommands.single { it.first() == "ps" }
        assertEquals(setOf("100", "101", "102", "103", "104"), ps.last().split(",").toSet())
    }

    @Test
    fun `the project's own agent command is recognized`() = runTest {
        val custom = config.copy(agentLaunchConfig = AgentLaunchConfig(agentCommand = "/opt/tools/my-agent --fast"))
        val executor = executor(
            lsof = "p300\ncmy-agent\nfcwd\nn${sibling.path}",
            ps = "300 /opt/tools/my-agent",
        )

        val agents = DetectRunningAgentsUseCase(executor)(custom, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("my-agent")), agents[sibling.path])
    }

    @Test
    fun `no process inside a worktree skips ps`() = runTest {
        val executor = executor(lsof = "p1\nczsh\nfcwd\nn/somewhere/else")

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertTrue(agents.isEmpty())
        assertTrue(executor.executedCommands.none { it.first() == "ps" })
    }

    @Test
    fun `a failing lsof fails the detection`() = runTest {
        val error = DetectRunningAgentsUseCase(executor(failLsof = true))(config, worktrees).exceptionOrNull()

        assertIs<WorktreeError.ProcessListingFailed>(error)
    }

    @Test
    fun `one detection attributes agents across projects with each project's own agent`() = runTest {
        val api = Worktree(path = "/home/dev/projects/api", branch = "main", isMain = true)
        val executor = executor(
            lsof = "p300\ncmy-agent\nfcwd\nn${sibling.path}\np301\ncmy-agent\nfcwd\nn${api.path}\np302\ncclaude\nfcwd\nn${api.path}",
            ps = "300 /opt/tools/my-agent\n301 /opt/tools/my-agent\n302 claude",
        )

        val agents = DetectRunningAgentsUseCase(executor)(
            listOf(
                AgentSearch(listOf("/opt/tools/my-agent"), worktrees),
                AgentSearch(listOf("claude"), listOf(api)),
            ),
        ).getOrThrow()

        assertEquals(listOf(RunningAgent("my-agent")), agents[sibling.path])
        assertEquals(listOf(RunningAgent("claude")), agents[api.path])
        assertEquals(1, executor.executedCommands.count { it.first() == "lsof" })
    }

    @Test
    fun `nothing to search runs no command`() = runTest {
        val executor = executor()

        val agents = DetectRunningAgentsUseCase(executor)(emptyList()).getOrThrow()

        assertTrue(agents.isEmpty())
        assertTrue(executor.executedCommands.isEmpty())
    }

    private fun herdrAgent(directory: String, state: AgentState?, name: String = "claude", pane: String = "w1:p1") =
        HerdrAgent(name, state, directory, pane)

    @Test
    fun `a blocked agent Herdr reports enriches the process found in its worktree`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(nested.path, AgentState.BLOCKED, pane = "w2:p1")))

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, "w2:p1", AgentStateSource.HERDR)), agents[nested.path])
        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
    }

    @Test
    fun `the most urgent state wins among several panes of the same agent`() = runTest {
        val herdr = FakeHerdrClient(
            agents = listOf(
                herdrAgent(sibling.path, AgentState.WORKING, pane = "w1:p1"),
                herdrAgent(sibling.path, AgentState.BLOCKED, pane = "w1:p2"),
                herdrAgent(sibling.path, AgentState.IDLE, pane = "w1:p3"),
            ),
        )

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR)), agents[sibling.path])
    }

    @Test
    fun `herdr agents are attributed to the deepest worktree by their directory`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent("${nested.path}/src", AgentState.DONE)))

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(RunningAgent("claude", AgentState.DONE, "w1:p1", AgentStateSource.HERDR), agents.getValue(nested.path).single())
    }

    @Test
    fun `an agent only Herdr sees is added and survives an unreadable process list`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(sibling.path, AgentState.WORKING)))

        val agents = DetectRunningAgentsUseCase(executor(failLsof = true), herdr)(config, worktrees).getOrThrow()

        assertEquals(mapOf(sibling.path to listOf(RunningAgent("claude", AgentState.WORKING, "w1:p1", AgentStateSource.HERDR))), agents)
    }

    @Test
    fun `agents outside every worktree and agents without a state are handled`() = runTest {
        val herdr = FakeHerdrClient(
            agents = listOf(
                herdrAgent("/home/dev/elsewhere", AgentState.BLOCKED),
                herdrAgent(sibling.path, null, name = "aider"),
            ),
        )

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("aider", null, "w1:p1")), agents[sibling.path])
        assertEquals(setOf(nested.path, main.path, sibling.path), agents.keys)
    }

    @Test
    fun `a failing Herdr leaves detection as it was`() = runTest {
        val herdr = FakeHerdrClient(agentsFailure = WorktreeError.HerdrNotInstalled)

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
    }

    @Test
    fun `after a Herdr failure the next read waits 30 seconds`() = runTest {
        val herdr = FakeHerdrClient(agentsFailure = WorktreeError.HerdrNotInstalled)
        var clock = 1_000L
        val useCase = DetectRunningAgentsUseCase(executor(), herdr, now = { clock })

        useCase(config, worktrees)
        clock += 29_000
        useCase(config, worktrees)
        assertEquals(1, herdr.agentListings)

        clock += 1_000
        useCase(config, worktrees)
        assertEquals(2, herdr.agentListings)
    }

    @Test
    fun `without a Herdr client nothing is added`() = runTest {
        val agents = DetectRunningAgentsUseCase(executor())(config, worktrees).getOrThrow()

        assertEquals(setOf(nested.path, main.path), agents.keys)
    }

    @Test
    fun `herdr is read once per detection across projects`() = runTest {
        val herdr = FakeHerdrClient()
        val api = Worktree(path = "/home/dev/projects/api", branch = "main", isMain = true)

        DetectRunningAgentsUseCase(executor(), herdr)(
            listOf(AgentSearch(listOf("claude"), worktrees), AgentSearch(listOf("claude"), listOf(api))),
        ).getOrThrow()

        assertEquals(1, herdr.agentListings)
    }

    private fun orcaRow(path: String, vararg agents: Pair<String, AgentState?>, createdByOrca: Boolean = false) =
        OrcaWorktree(path, createdByOrca, agents.map { (name, state) -> OrcaAgent(name, state) })

    private fun orcaReader(orca: FakeOrcaClient, clock: () -> Long = { 1_000L }) =
        OrcaWorktreeReader(orca, userHome = "/home/dev", now = clock, canonical = { it })

    private fun detectWithOrca(orca: FakeOrcaClient, failLsof: Boolean = false, clock: () -> Long = { 1_000L }) =
        DetectRunningAgentsUseCase(executor(failLsof = failLsof), orca = orcaReader(orca, clock), canonical = { it })

    @Test
    fun `a waiting agent Orca reports makes the worktree's Claude blocked`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.BLOCKED)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA)), agents[nested.path])
        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
    }

    @Test
    fun `the most urgent of several Orca agents in a worktree wins`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.WORKING, "claude" to AgentState.BLOCKED)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA)), agents[nested.path])
    }

    @Test
    fun `working and done map from Orca's states`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.WORKING), orcaRow(sibling.path, "codex" to AgentState.DONE)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(AgentState.WORKING, agents.getValue(nested.path).single().state)
        assertEquals(listOf(RunningAgent("codex", AgentState.DONE, stateSource = AgentStateSource.ORCA)), agents[sibling.path])
    }

    @Test
    fun `an agent only Orca sees counts as running when the process list can't be read`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(sibling.path, "claude" to AgentState.WORKING)))

        val agents = detectWithOrca(orca, failLsof = true)(config, worktrees).getOrThrow()

        assertEquals(mapOf(sibling.path to listOf(RunningAgent("claude", AgentState.WORKING, stateSource = AgentStateSource.ORCA))), agents)
    }

    @Test
    fun `Orca rows are matched by canonical path`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow("/private${sibling.path}", "claude" to AgentState.DONE)))
        val canonical = { path: String -> if (path.startsWith("/private")) path else "/private$path" }

        val agents = DetectRunningAgentsUseCase(
            executor(failLsof = true),
            orca = OrcaWorktreeReader(orca, userHome = "/home/dev", now = { 1_000L }, canonical = canonical),
            canonical = canonical,
        )(config, worktrees).getOrThrow()

        assertEquals(AgentState.DONE, agents.getValue(sibling.path).single().state)
    }

    @Test
    fun `Orca not running shows process agents without a state and never starts Orca`() = runTest {
        val orca = FakeOrcaClient(running = false, psRows = listOf(orcaRow(nested.path, "claude" to AgentState.BLOCKED)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
        assertEquals(0, orca.starts)
        assertEquals(0, orca.psReads)
    }

    @Test
    fun `Orca closed is asked at most once every 30 seconds`() = runTest {
        val orca = FakeOrcaClient(running = false)
        var clock = 1_000L
        val useCase = detectWithOrca(orca) { clock }

        useCase(config, worktrees)
        clock += 29_000
        useCase(config, worktrees)
        assertEquals(1, orca.statusChecks)

        clock += 1_000
        useCase(config, worktrees)
        assertEquals(2, orca.statusChecks)
        assertEquals(0, orca.starts)
    }

    @Test
    fun `a failing Orca read waits 30 seconds and leaves detection as it was`() = runTest {
        val orca = FakeOrcaClient(psFailure = WorktreeError.OrcaCommandFailed(listOf("orca"), "runtime_unavailable"))
        var clock = 1_000L
        val useCase = detectWithOrca(orca) { clock }

        assertEquals(listOf(RunningAgent("claude")), useCase(config, worktrees).getOrThrow()[nested.path])
        clock += 10_000
        useCase(config, worktrees)

        assertEquals(1, orca.psReads)
    }

    @Test
    fun `Orca not installed runs nothing`() = runTest {
        val orca = FakeOrcaClient(installed = false)

        detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(0, orca.statusChecks)
        assertEquals(0, orca.psReads)
    }

    @Test
    fun `Orca is read once per detection across projects`() = runTest {
        val orca = FakeOrcaClient()
        val api = Worktree(path = "/home/dev/projects/api", branch = "main", isMain = true)

        DetectRunningAgentsUseCase(executor(), orca = orcaReader(orca), canonical = { it })(
            listOf(AgentSearch(listOf("claude"), worktrees), AgentSearch(listOf("claude"), listOf(api))),
        ).getOrThrow()

        assertEquals(1, orca.psReads)
    }

    @Test
    fun `Orca's waiting wins over a working hook record`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.WORKING))))
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.BLOCKED)))

        val agents = DetectRunningAgentsUseCase(
            executor(lsof = claudeAtNestedLsof, ps = "100 claude"),
            sessionStore = store,
            now = { 20_000L },
            orca = orcaReader(orca),
            canonical = { it },
        )(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA)), agents[nested.path])
    }

    @Test
    fun `the executable of a second agent of the project is recognized`() = runTest {
        val multi = config.copy(additionalAgents = listOf(AgentLaunchConfig(agentCommand = "antigravity --chat")))
        val executor = executor(
            lsof = "p300\ncantigravity\nfcwd\nn${sibling.path}",
            ps = "300 /opt/tools/antigravity",
        )

        val agents = DetectRunningAgentsUseCase(executor)(multi, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("antigravity")), agents[sibling.path])
    }

    private val claudeAtNestedLsof = "p100\ncclaude\nfcwd\nn${nested.path}"

    private fun session(
        id: String,
        state: AgentState,
        updatedAt: Long = 10_000L,
        pid: Long? = 100,
        transcript: String? = null,
    ) = AgentSessionRecord(id, "claude", state, updatedAt, transcript, pid)

    private fun detectWith(
        store: FakeAgentSessionStore,
        herdr: FakeHerdrClient? = null,
        transcripts: Map<String, Long> = emptyMap(),
        clock: Long = 20_000L,
    ) = DetectRunningAgentsUseCase(
        executor(lsof = claudeAtNestedLsof, ps = "100 claude"),
        herdr,
        store,
        { transcripts[it] },
        { clock },
    )

    @Test
    fun `a blocked session recorded by Claude Code's hooks gives the agent that state`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.BLOCKED))))

        val agents = detectWith(store)(config, worktrees).getOrThrow()

        assertEquals(
            listOf(RunningAgent("claude", AgentState.BLOCKED, null, AgentStateSource.CLAUDE_CODE)),
            agents[nested.path],
        )
    }

    @Test
    fun `a blocked session whose transcript was written afterwards reads as working`() = runTest {
        val store = FakeAgentSessionStore(
            mapOf(nested.path to listOf(session("s1", AgentState.BLOCKED, transcript = "/home/dev/t.jsonl"))),
        )

        val agents = detectWith(store, transcripts = mapOf("/home/dev/t.jsonl" to 15_000L))(config, worktrees).getOrThrow()

        assertEquals(AgentState.WORKING, agents.getValue(nested.path).single().state)
    }

    @Test
    fun `a done session whose transcript was only written in the same moment stays done`() = runTest {
        val store = FakeAgentSessionStore(
            mapOf(nested.path to listOf(session("s1", AgentState.DONE, transcript = "/home/dev/t.jsonl"))),
        )

        val agents = detectWith(store, transcripts = mapOf("/home/dev/t.jsonl" to 10_400L))(config, worktrees).getOrThrow()

        assertEquals(AgentState.DONE, agents.getValue(nested.path).single().state)
    }

    @Test
    fun `a record whose Claude process is gone is ignored and deleted`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.BLOCKED, pid = 999))))

        val agents = detectWith(store)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
        assertEquals(emptyList(), store.list(nested.path))
    }

    @Test
    fun `a record whose pid now belongs to another program is ignored`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.BLOCKED))))
        val useCase = DetectRunningAgentsUseCase(
            executor(lsof = "p100\ncnode\nfcwd\nn${nested.path}\np101\ncclaude\nfcwd\nn${nested.path}", ps = "100 node\n101 claude"),
            null,
            store,
            { null },
            { 20_000L },
        )

        val agents = useCase(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
    }

    @Test
    fun `a record without a pid is live while Claude runs there and it is recent`() = runTest {
        val recent = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.DONE, pid = null))))
        val old = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.DONE, updatedAt = 0, pid = null))))
        val day = 24 * 60 * 60 * 1_000L

        assertEquals(AgentState.DONE, detectWith(recent)(config, worktrees).getOrThrow().getValue(nested.path).single().state)
        assertEquals(null, detectWith(old, clock = day)(config, worktrees).getOrThrow().getValue(nested.path).single().state)
    }

    @Test
    fun `the most urgent live session of the worktree wins`() = runTest {
        val store = FakeAgentSessionStore(
            mapOf(
                nested.path to listOf(
                    session("s1", AgentState.WORKING),
                    session("s2", AgentState.DONE),
                    session("s3", AgentState.BLOCKED),
                ),
            ),
        )

        val agents = detectWith(store)(config, worktrees).getOrThrow()

        assertEquals(AgentState.BLOCKED, agents.getValue(nested.path).single().state)
    }

    @Test
    fun `Herdr's state wins over the hook record`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.DONE))))
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(nested.path, AgentState.WORKING, pane = "w2:p1")))

        val agents = detectWith(store, herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.WORKING, "w2:p1", AgentStateSource.HERDR)), agents[nested.path])
    }

    @Test
    fun `a Herdr agent without a state keeps the hook record's state`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.BLOCKED))))
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(nested.path, null, pane = "w2:p1")))

        val agents = detectWith(store, herdr)(config, worktrees).getOrThrow()

        assertEquals(
            listOf(RunningAgent("claude", AgentState.BLOCKED, "w2:p1", AgentStateSource.CLAUDE_CODE)),
            agents[nested.path],
        )
    }

    @Test
    fun `records of a worktree without a Claude process give no agent`() = runTest {
        val store = FakeAgentSessionStore(mapOf(sibling.path to listOf(session("s1", AgentState.BLOCKED, pid = null))))

        val agents = detectWith(store)(config, worktrees).getOrThrow()

        assertEquals(null, agents[sibling.path])
    }

    @Test
    fun `an agent in a repository nested in a worktree's folder isn't attributed to it`() = runTest {
        val lsof = """
            p300
            cclaude
            fcwd
            n/home/dev/projects/main-repo/product-docs/src
            p301
            cclaude
            fcwd
            n/home/dev/projects/main-repo/notes
        """.trimIndent()
        val ps = """
              300 claude
              301 claude
        """.trimIndent()
        val nestedRepository = "/home/dev/projects/main-repo/product-docs"
        val herdr = FakeHerdrClient(agents = listOf(HerdrAgent("claude", AgentState.WORKING, "$nestedRepository/app", "w1:p1")))

        val agents = DetectRunningAgentsUseCase(executor(lsof, ps), herdr, hasGitEntry = { it == nestedRepository })(config, listOf(main)).getOrThrow()

        assertEquals(mapOf(main.path to listOf(RunningAgent("claude"))), agents)
    }

    private val multirepoFolder = "/home/dev/projects/product"
    private val memberMain = Worktree(path = "$multirepoFolder/product-app", branch = "develop", isMain = true)

    private fun multirepoSearches() = listOf(
        AgentSearch.multirepoFolder(multirepoFolder, "claude"),
        AgentSearch(listOf("claude"), listOf(memberMain)),
    )

    @Test
    fun `an agent whose directory is exactly the multirepo folder is the multirepo agent`() = runTest {
        val executor = executor(lsof = "p300\nclaude\nfcwd\nn$multirepoFolder", ps = "300 claude")

        val agents = DetectRunningAgentsUseCase(executor)(multirepoSearches()).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[multirepoFolder])
        assertEquals(null, agents[memberMain.path])
    }

    @Test
    fun `an agent in a member repository or in a subfolder of the folder is not the multirepo agent`() = runTest {
        val executor = executor(
            lsof = "p300\nclaude\nfcwd\nn${memberMain.path}\np301\nclaude\nfcwd\nn$multirepoFolder/notes",
            ps = "300 claude\n301 claude",
        )

        val agents = DetectRunningAgentsUseCase(executor)(multirepoSearches()).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[memberMain.path])
        assertEquals(null, agents[multirepoFolder])
    }

    @Test
    fun `a Herdr agent in the multirepo folder reports its state as for a worktree's agent`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(multirepoFolder, AgentState.WORKING)))

        val agents = DetectRunningAgentsUseCase(executor(lsof = "p1\nczsh\nfcwd\nn/elsewhere"), herdr)(multirepoSearches()).getOrThrow()

        assertEquals(AgentState.WORKING, agents[multirepoFolder]?.single()?.state)
    }

    private val appTask = Worktree(path = "/home/dev/worktrees/app/TASK-123", branch = "feature/TASK-123")
    private val apiTask = Worktree(path = "/home/dev/worktrees/api/TASK-123", branch = "feature/TASK-123")
    private val nestedRepository = "${appTask.path}/vendor-lib"

    private val groupLsof = """
        p400
        cclaude
        fcwd
        n${appTask.path}
        p401
        cclaude
        fcwd
        n${apiTask.path}
        p402
        cclaude
        fcwd
        n${apiTask.path}/src
        p403
        cclaude
        fcwd
        n$nestedRepository
    """.trimIndent()

    private val groupPs = "400 claude\n401 claude\n402 claude\n403 claude\n500 claude"

    private suspend fun groupAgents(registry: FakeClaudeSessionRegistry) =
        DetectRunningAgentsUseCase(
            executor(groupLsof, groupPs),
            hasGitEntry = { it == nestedRepository },
            claudeSessions = registry,
        )(listOf(AgentSearch(listOf("claude"), listOf(appTask, apiTask)))).getOrThrow()

    @Test
    fun `each worktree of a group gets the Claude sessions in its folder`() = runTest {
        val registry = FakeClaudeSessionRegistry(
            listOf(
                ClaudeSession(400, appTask.path, "app-task-123"),
                ClaudeSession(401, apiTask.path, "api-task-123"),
                ClaudeSession(402, "${apiTask.path}/src", "api-task-123-2"),
                ClaudeSession(403, nestedRepository, "vendor"),
                ClaudeSession(404, "/home/dev/elsewhere", "elsewhere"),
                ClaudeSession(405, appTask.path, "app-task-123-dead"),
                ClaudeSession(400, appTask.path, null),
            ),
        )

        val agents = groupAgents(registry)

        assertEquals(listOf(RunningAgent("claude", sessions = listOf(ClaudeSessionRef("app-task-123", 400)))), agents[appTask.path])
        assertEquals(
            listOf(RunningAgent("claude", sessions = listOf(ClaudeSessionRef("api-task-123", 401), ClaudeSessionRef("api-task-123-2", 402)))),
            agents[apiTask.path],
        )
    }

    @Test
    fun `a failing registry leaves the agents without sessions`() = runTest {
        val agents = groupAgents(FakeClaudeSessionRegistry(failure = IllegalStateException("broken")))

        assertEquals(listOf(RunningAgent("claude")), agents[appTask.path])
        assertEquals(listOf(RunningAgent("claude")), agents[apiTask.path])
    }

    @Test
    fun `only a session exactly in the multirepo folder belongs to the multirepo agent`() = runTest {
        val executor = executor(
            lsof = "p300\nclaude\nfcwd\nn$multirepoFolder\np301\nclaude\nfcwd\nn${memberMain.path}",
            ps = "300 claude\n301 claude\n302 claude",
        )
        val registry = FakeClaudeSessionRegistry(
            listOf(
                ClaudeSession(300, multirepoFolder, "product"),
                ClaudeSession(301, memberMain.path, "product-app"),
                ClaudeSession(302, "$multirepoFolder/notes", "notes"),
            ),
        )

        val agents = DetectRunningAgentsUseCase(executor, claudeSessions = registry)(multirepoSearches()).getOrThrow()

        assertEquals(listOf(ClaudeSessionRef("product", 300)), agents[multirepoFolder]?.single()?.sessions)
        assertEquals(listOf(ClaudeSessionRef("product-app", 301)), agents[memberMain.path]?.single()?.sessions)
    }
}
