package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenWorktreeInOrcaUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = Worktree(path = "/home/dev/projects/worktrees/TASK-8", branch = "feature/TASK-8")
    private val config = ProjectConfig(
        baseRepositoryPath = repository,
        worktreesRoot = "/home/dev/projects/worktrees",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(agentCommand = "claude"),
    )
    private val known = OrcaRepo("r1", repository)
    private val visible = mapOf("r1" to listOf(OrcaWorktree(task.path, createdByOrca = false)))

    private val gitExecutor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "$common/worktrees/TASK-8\n$common\n")
    }

    private fun provisioning(vararg variables: Pair<String, String>) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            ),
        ),
    )

    private fun useCase(
        orca: FakeOrcaClient,
        source: FakeRepositoryProvisioningSource = provisioning(),
        registry: FakeClaudeSessionRegistry? = null,
        usesPowerShell: Boolean = false,
        samePath: (String, String) -> Boolean = { first, second -> first == second },
    ) = OpenWorktreeInOrcaUseCase(
        config,
        orca,
        ResolveWorktreeEnvironmentUseCase(gitExecutor, source, FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true)),
        ClaudeSessionNaming(registry, isClaudeProcessAlive = { true }),
        usesPowerShell = usesPowerShell,
        samePath = samePath,
    )

    @Test
    fun `opens a focused terminal with the environment and the named agent`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        val opening = useCase(orca, provisioning("PORT" to "\${port}", "APP_SUFFIX" to "-\${worktree}"))(task).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", config.agentLaunchConfig.displayName), opening)
        assertEquals(
            FakeOrcaClient.Created(task.path, "main-repo-task-8", "env 'PORT=20000' 'APP_SUFFIX=-TASK-8' claude -n main-repo-task-8", focus = true),
            orca.created.single(),
        )
        assertEquals(1, orca.raised)
    }

    @Test
    fun `without an environment the terminal runs just the agent`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca)(task).getOrThrow()

        assertEquals("claude -n main-repo-task-8", orca.created.single().command)
    }

    @Test
    fun `the project's command runs instead of Orca's launcher settings`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca)(task, AgentLaunchConfig(agentCommand = "claude", useHeadroom = true)).getOrThrow()

        assertEquals("headroom wrap claude -n main-repo-task-8", orca.created.single().command)
    }

    @Test
    fun `a non-Claude agent isn't named and the terminal takes its name`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        val opening = useCase(orca)(task, AgentLaunchConfig(agentCommand = "codex")).getOrThrow()

        assertEquals(FakeOrcaClient.Created(task.path, "Codex", "codex", focus = true), orca.created.single())
        assertNull(opening.sessionName)
    }

    @Test
    fun `values with spaces and quotes are quoted for a POSIX shell`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca, provisioning("GREETING" to "it's a b"))(task).getOrThrow()

        assertEquals("env 'GREETING=it'\\''s a b' claude -n main-repo-task-8", orca.created.single().command)
    }

    @Test
    fun `on Windows the variables are set the PowerShell way`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca, provisioning("GREETING" to "it's a b", "PORT" to "\${port}"), usesPowerShell = true)(task).getOrThrow()

        assertEquals("\$env:GREETING = 'it''s a b'; \$env:PORT = '20000'; claude -n main-repo-task-8", orca.created.single().command)
    }

    @Test
    fun `an unknown repository is registered first`() = runTest {
        val orca = FakeOrcaClient(worktreesShownWhenExternalVisible = mapOf("repo1" to listOf(OrcaWorktree(task.path, false))))

        useCase(orca)(task).getOrThrow()

        assertEquals(listOf(repository), orca.addedRepos)
        assertEquals(listOf("repo1"), orca.shownExternal)
    }

    @Test
    fun `hidden external worktrees are shown for that repository only`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(OrcaRepo("other", "/home/dev/projects/other"), known))

        useCase(orca)(task).getOrThrow()

        assertTrue(orca.addedRepos.isEmpty())
        assertEquals(listOf("r1"), orca.shownExternal)
    }

    @Test
    fun `an already visible worktree changes no setting`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca)(task).getOrThrow()

        assertTrue(orca.shownExternal.isEmpty())
        assertTrue(orca.addedRepos.isEmpty())
    }

    @Test
    fun `paths are matched canonically, so tmp and private tmp are the same`() = runTest {
        val tmpRepository = "/tmp/main-repo"
        val tmpConfig = config.copy(baseRepositoryPath = tmpRepository)
        val tmpTask = Worktree(path = "/tmp/worktrees/TASK-8", branch = "feature/TASK-8")
        val orca = FakeOrcaClient(
            repos = listOf(OrcaRepo("r1", "/private/tmp/main-repo")),
            visibleWorktrees = mapOf("r1" to listOf(OrcaWorktree("/private/tmp/worktrees/TASK-8", false))),
        )
        val canonical = { path: String -> path.replace(Regex("^/tmp/"), "/private/tmp/") }

        OpenWorktreeInOrcaUseCase(
            tmpConfig,
            orca,
            ResolveWorktreeEnvironmentUseCase(gitExecutor, provisioning(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true)),
            usesPowerShell = false,
            samePath = { first, second -> canonical(first) == canonical(second) },
        )(tmpTask, sessionName = "main-repo-task-8").getOrThrow()

        assertTrue(orca.addedRepos.isEmpty())
        assertTrue(orca.shownExternal.isEmpty())
    }

    @Test
    fun `a live agent is focused without creating or typing anything`() = runTest {
        val orca = FakeOrcaClient(
            repos = listOf(known),
            visibleWorktrees = visible,
            terminals = mapOf(task.path to listOf(OrcaTerminal("term_shell", task.path), OrcaTerminal("term_agent", task.path))),
            agents = mapOf("term_agent" to "claude"),
        )

        val opening = useCase(orca)(task).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.FOCUSED, "term_agent", "claude"), opening)
        assertEquals(listOf("term_agent"), orca.switched)
        assertTrue(orca.created.isEmpty())
        assertEquals(1, orca.raised)
    }

    @Test
    fun `an agent that finished its turn still counts as live`() = runTest {
        val orca = FakeOrcaClient(
            repos = listOf(known),
            visibleWorktrees = visible,
            terminals = mapOf(task.path to listOf(OrcaTerminal("term_done", task.path))),
            agents = mapOf("term_done" to "claude"),
        )

        assertEquals(OrcaOutcome.FOCUSED, useCase(orca)(task).getOrThrow().outcome)
    }

    @Test
    fun `terminals without an agent are kept and a new one starts the agent`() = runTest {
        val orca = FakeOrcaClient(
            repos = listOf(known),
            visibleWorktrees = visible,
            terminals = mapOf(task.path to listOf(OrcaTerminal("term_shell", task.path))),
        )

        val opening = useCase(orca, provisioning("PORT" to "\${port}"))(task).getOrThrow()

        assertEquals(OrcaOutcome.STARTED_AGENT, opening.outcome)
        assertEquals("env 'PORT=20000' claude -n main-repo-task-8", orca.created.single().command)
        assertTrue(orca.closedWorktrees.isEmpty())
        assertTrue(orca.switched.isEmpty())
    }

    @Test
    fun `Orca is started when it isn't running`() = runTest {
        val orca = FakeOrcaClient(running = false, repos = listOf(known), visibleWorktrees = visible)

        val opening = useCase(orca)(task).getOrThrow()

        assertEquals(1, orca.starts)
        assertEquals(OrcaOutcome.STARTED_AGENT, opening.outcome)
    }

    @Test
    fun `a running Orca isn't started again`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        useCase(orca)(task).getOrThrow()

        assertEquals(0, orca.starts)
    }

    @Test
    fun `Orca that doesn't come up fails saying it didn't start`() = runTest {
        val orca = FakeOrcaClient(running = false, startsWhenOpened = false)

        assertIs<WorktreeError.OrcaNotStarted>(useCase(orca)(task).exceptionOrNull())
        assertTrue(orca.created.isEmpty())
    }

    @Test
    fun `Orca not installed fails without touching the worktree`() = runTest {
        val orca = FakeOrcaClient(installed = false)

        assertIs<WorktreeError.OrcaNotInstalled>(useCase(orca)(task).exceptionOrNull())
    }

    @Test
    fun `a failing Orca command is reported with Orca's message`() = runTest {
        val orca = FakeOrcaClient(failure = WorktreeError.OrcaCommandFailed(listOf("orca", "repo", "list", "--json"), "runtime_unavailable"))

        val error = assertIs<WorktreeError.OrcaCommandFailed>(useCase(orca)(task).exceptionOrNull())
        assertEquals("runtime_unavailable", error.detail)
    }

    @Test
    fun `an agent detected after a few checks succeeds`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible, agentDetectedAfterChecks = 4)

        assertEquals(OrcaOutcome.STARTED_AGENT, useCase(orca)(task).getOrThrow().outcome)
    }

    @Test
    fun `an agent never detected fails naming the terminal and the command, leaving it open`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible, startsAgents = false)

        val error = assertIs<WorktreeError.OrcaAgentNotStarted>(useCase(orca)(task).exceptionOrNull())

        assertEquals("term_1", error.terminalHandle)
        assertEquals("claude -n main-repo-task-8", error.command)
        assertTrue(orca.closedWorktrees.isEmpty())
    }

    @Test
    fun `the started session's name is reported once it appears`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        val opening = useCase(orca, registry = FakeClaudeSessionRegistry(started = orca::startedClaudeSessions))(task).getOrThrow()

        assertEquals("main-repo-task-8", opening.sessionName)
    }

    @Test
    fun `a name already taken by a live session gets a suffix`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, "/elsewhere", "main-repo-task-8")), started = orca::startedClaudeSessions)

        val opening = useCase(orca, registry = registry)(task).getOrThrow()

        assertEquals("claude -n main-repo-task-8-2", orca.created.single().command)
        assertEquals("main-repo-task-8-2", opening.sessionName)
    }

    @Test
    fun `a name never confirmed is null and the open still succeeds`() = runTest {
        val orca = FakeOrcaClient(repos = listOf(known), visibleWorktrees = visible)

        val opening = useCase(orca, registry = FakeClaudeSessionRegistry())(task).getOrThrow()

        assertEquals(OrcaOutcome.STARTED_AGENT, opening.outcome)
        assertNull(opening.sessionName)
    }

    @Test
    fun `a focused agent reports its live session's name`() = runTest {
        val orca = FakeOrcaClient(
            repos = listOf(known),
            visibleWorktrees = visible,
            terminals = mapOf(task.path to listOf(OrcaTerminal("term_agent", task.path))),
            agents = mapOf("term_agent" to "claude"),
        )
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, task.path, "main-repo-task-8")))

        val opening = useCase(orca, registry = registry)(task).getOrThrow()

        assertEquals(OrcaOpening(OrcaOutcome.FOCUSED, "term_agent", "claude", "main-repo-task-8"), opening)
    }

    @Test
    fun `focusing waits for nothing`() = runTest {
        val orca = FakeOrcaClient(
            repos = listOf(known),
            visibleWorktrees = visible,
            terminals = mapOf(task.path to listOf(OrcaTerminal("term_agent", task.path))),
            agents = mapOf("term_agent" to "codex"),
        )
        val registry = FakeClaudeSessionRegistry()

        val opening = useCase(orca, registry = registry)(task).getOrThrow()

        assertNull(opening.sessionName)
        assertEquals(0, registry.listings)
        assertTrue(orca.created.isEmpty())
    }
}
