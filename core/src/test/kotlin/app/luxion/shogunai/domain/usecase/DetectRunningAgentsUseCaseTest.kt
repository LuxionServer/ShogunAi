package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentTool
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
import app.luxion.shogunai.domain.model.ClaudeSessionStatus
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
                "lsof" -> if (failLsof) failure(command, exitCode = 127) else success(command, stdout = lsof)
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
    fun `processes are listed once by name and only agent processes are inspected with lsof`() = runTest {
        val executor = executor()

        DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        val ps = executor.executedCommands.single { it.first() == "ps" }
        assertEquals(listOf("ps", "-A", "-o", "pid=,comm="), ps)
        val lsof = executor.executedCommands.single { it.first() == "lsof" }
        assertEquals(listOf("lsof", "-a", "-d", "cwd", "-F", "pcn", "-p"), lsof.dropLast(1))
        assertEquals(setOf("100", "103", "104"), lsof.last().split(",").toSet())
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
    fun `no agent process running skips lsof`() = runTest {
        val executor = executor(ps = "1 -zsh\n2 /usr/local/bin/node")

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertTrue(agents.isEmpty())
        assertTrue(executor.executedCommands.none { it.first() == "lsof" })
    }

    @Test
    fun `an agent outside every worktree is not attributed`() = runTest {
        val executor = executor(lsof = "p100\nc2.1.301\nfcwd\nn/somewhere/else", ps = "100 claude")

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertTrue(agents.isEmpty())
    }

    @Test
    fun `a failing lsof fails the detection`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when (command.first()) {
                "lsof" -> failure(command, exitCode = 127)
                "ps" -> success(command, stdout = psOutput)
                else -> success(command)
            }
        }

        val error = DetectRunningAgentsUseCase(executor)(config, worktrees).exceptionOrNull()

        assertIs<WorktreeError.ProcessListingFailed>(error)
    }

    @Test
    fun `a failing ps fails the detection`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == "ps") failure(command, exitCode = 1) else success(command)
        }

        val error = DetectRunningAgentsUseCase(executor)(config, worktrees).exceptionOrNull()

        assertIs<WorktreeError.ProcessListingFailed>(error)
    }

    @Test
    fun `an agent that exits before lsof reads it does not fail the detection`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when (command.first()) {
                "lsof" -> failure(command, exitCode = 1).copy(stdout = "p104\nc2.1.301\nfcwd\nn${nested.path}")
                "ps" -> success(command, stdout = psOutput)
                else -> success(command)
            }
        }

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
    }

    @Test
    fun `lsof lines for processes that were not asked for are ignored`() = runTest {
        val executor = executor(lsof = "p101\nczsh\nfcwd\nn${main.path}\np103\nccodex\nfcwd\nn${main.path}", ps = "103 codex")

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertEquals(mapOf(main.path to listOf(RunningAgent("codex"))), agents)
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
    fun `a pane with a known state wins over one without`() = runTest {
        val herdr = FakeHerdrClient(
            agents = listOf(
                herdrAgent(sibling.path, null, pane = "w1:p1"),
                herdrAgent(sibling.path, AgentState.IDLE, pane = "w1:p2"),
            ),
        )

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.IDLE, "w1:p2", AgentStateSource.HERDR)), agents[sibling.path])
    }

    @Test
    fun `a Herdr that doesn't answer in time is treated as failing`() = runTest {
        val herdr = object : HerdrClient by FakeHerdrClient() {
            override suspend fun listAgents(): List<HerdrAgent> = awaitCancellation()
        }

        val agents = DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
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
    fun `an agent in Orca's folder workspace of the multirepo folder is the multirepo agent, waiting shown as blocked`() = runTest {
        val folder = "/home/dev/projects/product"
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(folder, "claude" to AgentState.BLOCKED).copy(folderId = "f1")))

        val agents = detectWithOrca(orca, failLsof = true)(listOf(AgentSearch.multirepoFolder(folder, "claude"))).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA)), agents[folder])
    }

    @Test
    fun `an Orca agent in a member repository's folder isn't the multirepo agent`() = runTest {
        val folder = "/home/dev/projects/product"
        val orca = FakeOrcaClient(psRows = listOf(orcaRow("$folder/product-app", "claude" to AgentState.WORKING)))

        val agents = detectWithOrca(orca)(listOf(AgentSearch.multirepoFolder(folder, "claude"))).getOrThrow()

        assertEquals(null, agents[folder])
    }

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
    fun `an agent waiting on a tool in Orca carries the tool, its input and its prompt`() = runTest {
        val activity = AgentActivity(AgentTool("Bash", "curl -s https://example.com -o page.html"), prompt = "Download the page")
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree(nested.path, false, listOf(OrcaAgent("claude", AgentState.BLOCKED, activity)))))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA, activity = activity)), agents[nested.path])
    }

    @Test
    fun `a done agent only Orca sees carries its prompt and last message`() = runTest {
        val activity = AgentActivity(prompt = "Save the date", lastMessage = "Saved it in now.txt")
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree(sibling.path, false, listOf(OrcaAgent("claude", AgentState.DONE, activity)))))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.DONE, stateSource = AgentStateSource.ORCA, activity = activity)), agents[sibling.path])
    }

    @Test
    fun `the details of several Orca agents of one name are those of the one shown`() = runTest {
        val waiting = AgentActivity(AgentTool("Bash"), prompt = "B")
        val orca = FakeOrcaClient(
            psRows = listOf(
                OrcaWorktree(
                    nested.path,
                    false,
                    listOf(OrcaAgent("claude", AgentState.WORKING, AgentActivity(prompt = "A")), OrcaAgent("claude", AgentState.BLOCKED, waiting)),
                ),
            ),
        )

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(waiting, agents[nested.path]?.single()?.activity)
    }

    @Test
    fun `an agent Orca doesn't report carries no details`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(sibling.path, "claude" to AgentState.WORKING)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
        assertNull(agents[nested.path]?.single()?.activity)
    }

    @Test
    fun `Orca's details are kept when Herdr's state wins`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(herdrAgent(nested.path, AgentState.BLOCKED, pane = "w2:p1")))
        val activity = AgentActivity(prompt = "Count to ten")
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree(nested.path, false, listOf(OrcaAgent("claude", AgentState.WORKING, activity)))))

        val agents = DetectRunningAgentsUseCase(executor(), herdr, orca = orcaReader(orca), canonical = { it })(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude", AgentState.BLOCKED, "w2:p1", AgentStateSource.HERDR, activity = activity)), agents[nested.path])
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

    private fun registered(pid: Long, cwd: String, status: ClaudeSessionStatus, name: String? = null, statusUpdatedAt: Long = 1_083L) =
        ClaudeSession(pid, cwd, name, startedAt = 1_000L, status = status, statusUpdatedAt = statusUpdatedAt)

    @Test
    fun `a Claude without any other state takes its registry session's waiting as blocked`() = runTest {
        val agents = groupAgents(FakeClaudeSessionRegistry(listOf(registered(400, appTask.path, ClaudeSessionStatus.WAITING, "app-task-123"))))

        assertEquals(
            listOf(RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.CLAUDE_CODE, sessions = listOf(ClaudeSessionRef("app-task-123", 400)))),
            agents[appTask.path],
        )
    }

    @Test
    fun `an idle registry session is done after a turn and idle before its first one`() = runTest {
        val agents = groupAgents(
            FakeClaudeSessionRegistry(
                listOf(
                    registered(400, appTask.path, ClaudeSessionStatus.IDLE, statusUpdatedAt = 39_000L),
                    registered(401, apiTask.path, ClaudeSessionStatus.IDLE),
                ),
            ),
        )

        assertEquals(AgentState.DONE, agents[appTask.path]?.single()?.state)
        assertEquals(AgentState.IDLE, agents[apiTask.path]?.single()?.state)
    }

    @Test
    fun `the most urgent registry state of a worktree's sessions wins`() = runTest {
        val agents = groupAgents(
            FakeClaudeSessionRegistry(
                listOf(
                    registered(401, apiTask.path, ClaudeSessionStatus.BUSY),
                    registered(402, "${apiTask.path}/src", ClaudeSessionStatus.WAITING),
                ),
            ),
        )

        assertEquals(AgentState.BLOCKED, agents[apiTask.path]?.single()?.state)
    }

    @Test
    fun `registry sessions of a nested repository or a dead process give no state`() = runTest {
        val agents = groupAgents(
            FakeClaudeSessionRegistry(
                listOf(
                    registered(403, nestedRepository, ClaudeSessionStatus.WAITING),
                    registered(405, appTask.path, ClaudeSessionStatus.WAITING),
                ),
            ),
        )

        assertEquals(listOf(RunningAgent("claude")), agents[appTask.path])
    }

    @Test
    fun `a registry session without a status gives no state`() = runTest {
        val agents = groupAgents(FakeClaudeSessionRegistry(listOf(ClaudeSession(400, appTask.path, "app-task-123"))))

        assertEquals(null, agents[appTask.path]?.single()?.state)
    }

    @Test
    fun `Orca's state wins over the registry's`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.DONE)))
        val registry = FakeClaudeSessionRegistry(listOf(registered(100, nested.path, ClaudeSessionStatus.BUSY)))

        val agents = DetectRunningAgentsUseCase(
            executor(lsof = claudeAtNestedLsof, ps = "100 claude"),
            claudeSessions = registry,
            orca = orcaReader(orca),
            canonical = { it },
        )(config, worktrees).getOrThrow()

        val claude = agents[nested.path]?.single()
        assertEquals(AgentState.DONE, claude?.state)
        assertEquals(AgentStateSource.ORCA, claude?.stateSource)
    }

    @Test
    fun `a hook record wins over the registry's state`() = runTest {
        val store = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.WORKING))))
        val registry = FakeClaudeSessionRegistry(listOf(registered(100, nested.path, ClaudeSessionStatus.IDLE, statusUpdatedAt = 39_000L)))

        val agents = DetectRunningAgentsUseCase(
            executor(lsof = claudeAtNestedLsof, ps = "100 claude"),
            sessionStore = store,
            now = { 20_000L },
            claudeSessions = registry,
        )(config, worktrees).getOrThrow()

        assertEquals(AgentState.WORKING, agents[nested.path]?.single()?.state)
    }

    @Test
    fun `an agent Orca lists without a state takes the registry's`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to null)))
        val registry = FakeClaudeSessionRegistry(listOf(registered(100, nested.path, ClaudeSessionStatus.BUSY)))

        val agents = DetectRunningAgentsUseCase(
            executor(lsof = claudeAtNestedLsof, ps = "100 claude"),
            claudeSessions = registry,
            orca = orcaReader(orca),
            canonical = { it },
        )(config, worktrees).getOrThrow()

        assertEquals(RunningAgent("claude", AgentState.WORKING, stateSource = AgentStateSource.CLAUDE_CODE), agents[nested.path]?.single())
    }
    @Test
    fun `a file's modification time is known only when the file exists`() {
        val file = kotlin.io.path.createTempFile("transcript", ".jsonl").toFile().apply { deleteOnExit() }

        assertTrue((fileModifiedAt(file.path) ?: 0) > 0)
        assertNull(fileModifiedAt("${file.path}.missing"))
    }

    @Test
    fun `lsof names before any process and ps lines without an executable are skipped`() {
        assertEquals(listOf("1" to "/a"), parseLsofWorkingDirectories("n/orphan\np1\nn/a"))
        assertEquals(listOf("1" to "claude"), parsePsExecutables("\n  1 claude\n  2\n"))
    }

    @Test
    fun `a blank agent command adds no executable to look for`() = runTest {
        val agents = DetectRunningAgentsUseCase(executor())(listOf(AgentSearch(listOf(" "), worktrees))).getOrThrow()

        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
    }

    @Test
    fun `claude sessions outside the worktrees and agents other than claude are left as they are`() = runTest {
        val registry = FakeClaudeSessionRegistry(
            listOf(
                ClaudeSession(100, main.path, "main-session", status = ClaudeSessionStatus.BUSY),
                ClaudeSession(104, "/home/dev/elsewhere", "lost"),
            ),
        )

        val agents = DetectRunningAgentsUseCase(executor(), claudeSessions = registry)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
    }

    @Test
    fun `a done session without a transcript or with an unreadable one stays done`() = runTest {
        val without = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.DONE))))
        val unreadable = FakeAgentSessionStore(mapOf(nested.path to listOf(session("s1", AgentState.DONE, transcript = "/transcripts/s1.jsonl"))))

        assertEquals(AgentState.DONE, detectWith(without)(config, worktrees).getOrThrow()[nested.path]?.single()?.state)
        assertEquals(AgentState.DONE, detectWith(unreadable)(config, worktrees).getOrThrow()[nested.path]?.single()?.state)
    }

    @Test
    fun `a cancelled herdr read cancels the detection`() = runTest {
        val herdr = object : HerdrClient by FakeHerdrClient() {
            override suspend fun listAgents(): List<HerdrAgent> = throw kotlinx.coroutines.CancellationException("stopped")
        }

        assertTrue(DetectRunningAgentsUseCase(executor(), herdr)(config, worktrees).isFailure)
    }

    @Test
    fun `an agent only orca sees without a state has no state source`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(sibling.path, "gemini" to null)))

        val agents = detectWithOrca(orca)(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("gemini")), agents[sibling.path])
    }

    @Test
    fun `orca's state replaces a less urgent herdr state and not a more urgent one`() = runTest {
        val herdr = FakeHerdrClient(agents = listOf(HerdrAgent("claude", AgentState.WORKING, nested.path, "w1:p1"), HerdrAgent("codex", AgentState.BLOCKED, main.path, "w1:p2")))
        val orca = FakeOrcaClient(psRows = listOf(orcaRow(nested.path, "claude" to AgentState.BLOCKED), orcaRow(main.path, "codex" to AgentState.WORKING)))

        val agents = DetectRunningAgentsUseCase(executor(), herdr, orca = orcaReader(orca), canonical = { it })(config, worktrees).getOrThrow()

        assertEquals(AgentStateSource.ORCA, agents[nested.path]?.single()?.stateSource)
        assertEquals(AgentState.BLOCKED, agents[main.path]?.single()?.state)
        assertEquals(AgentStateSource.HERDR, agents[main.path]?.single()?.stateSource)
    }
    @Test
    fun `a hook state of a worktree only applies to its claude`() = runTest {
        val store = FakeAgentSessionStore(mapOf(main.path to listOf(session("s1", AgentState.BLOCKED, pid = 100))))

        val agents = DetectRunningAgentsUseCase(executor(), sessionStore = store, now = { 20_000L })(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
    }
}
