package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentTool
import app.luxion.shogunai.domain.model.OrcaAgent
import app.luxion.shogunai.domain.model.OrcaPort
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaStatus
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CliOrcaClientTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/orca/$name")) { name }.readText().trim()

    private val orca = "/opt/homebrew/bin/orca"
    private val repository = "/projects/main-repo"
    private val external = "/projects/worktrees/TASK-1"
    private val created = "/Users/user/orca/workspaces/main-repo/TASK-2"
    private val shogunai = "/projects/main-repo-worktrees/TASK-9"
    private val repoId = "0355427a-217f-4a07-9072-67d11e704c23"
    private val folderId = "2f00a2db-d2c3-42f0-8f9a-6271686535cc"
    private val folder = OrcaWorkspace.Folder(folderId)

    private fun client(
        executor: ShellCommandExecutor,
        files: Set<String> = setOf(orca),
        osName: String = "Mac OS X",
        onPath: Map<String, String> = emptyMap(),
    ) = CliOrcaClient(executor, userHome = "/home/dev", osName = osName, isFile = { it in files }, findOnPath = { onPath[it] })

    private fun answering(name: String) = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture(name)) }

    @Test
    fun `status reports a reachable runtime and its version`() = runTest {
        val executor = answering("status.json")

        assertEquals(OrcaStatus(running = true, version = "1.4.243"), client(executor).status())
        assertEquals(listOf(orca, "status", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `status reports a runtime that isn't reachable as not running`() = runTest {
        assertEquals(OrcaStatus(running = false, version = null), client(answering("status-not-running.json")).status())
    }

    @Test
    fun `an Orca older than the minimum version fails naming that version`() = runTest {
        val error = assertFailsWith<WorktreeError.OrcaCommandFailed> { client(answering("status-old.json")).status() }

        assertTrue("1.4.243" in error.detail)
        assertTrue("1.4.200" in error.detail)
    }

    @Test
    fun `versions are compared part by part`() {
        assertTrue(CliOrcaClient.isOlder("1.4.99", "1.4.243"))
        assertTrue(CliOrcaClient.isOlder("1.3.900", "1.4.243"))
        assertFalse(CliOrcaClient.isOlder("1.4.243", "1.4.243"))
        assertFalse(CliOrcaClient.isOlder("1.10.0", "1.4.243"))
        assertFalse(CliOrcaClient.isOlder("2.0.0-beta", "1.4.243"))
    }

    @Test
    fun `start opens Orca and waits up to thirty seconds`() = runTest {
        val executor = answering("open.json")

        client(executor).start()

        val command = listOf(orca, "open", "--json")
        assertEquals(command, executor.executedCommands.single())
        assertEquals(30.seconds, executor.timeouts[command])
    }

    @Test
    fun `start that times out reports that Orca didn't start`() = runTest {
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult =
                throw CommandExecutionException.TimedOut(command, 30.seconds)
        }

        assertFailsWith<WorktreeError.OrcaNotStarted> { client(executor).start() }
    }

    @Test
    fun `start that fails reports that Orca didn't start`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }

        assertFailsWith<WorktreeError.OrcaNotStarted> { client(executor).start() }
    }

    @Test
    fun `repositories are listed with their id, path and visibility`() = runTest {
        val executor = answering("repo-list.json")

        assertEquals(listOf(OrcaRepo(repoId, repository, "show")), client(executor).repos())
        assertEquals(listOf(orca, "repo", "list", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `a repository is added by its path`() = runTest {
        val executor = answering("repo-add.json")

        assertEquals(OrcaRepo(repoId, repository, "hide"), client(executor).addRepo(repository))
        assertEquals(listOf(orca, "repo", "add", "--path", repository, "--json"), executor.executedCommands.single())
    }

    @Test
    fun `external worktrees are shown for one repository by id`() = runTest {
        val executor = answering("repo-set.json")

        client(executor).showExternalWorktrees(repoId)

        assertEquals(
            listOf(orca, "repo", "set", "--repo", "id:$repoId", "--external-worktree-visibility", "show", "--json"),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `worktrees are listed telling Orca's from external ones`() = runTest {
        val executor = answering("worktree-list.json")

        val worktrees = client(executor).worktrees(repoId)

        assertEquals(
            listOf(
                OrcaWorktree(repository, createdByOrca = false),
                OrcaWorktree(external, createdByOrca = false),
                OrcaWorktree(created, createdByOrca = true),
            ),
            worktrees,
        )
        assertEquals(listOf(orca, "worktree", "list", "--repo", "id:$repoId", "--limit", "1000", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `hidden external worktrees aren't listed`() = runTest {
        assertEquals(listOf(OrcaWorktree(repository, false)), client(answering("worktree-list-hidden.json")).worktrees(repoId))
    }

    @Test
    fun `one ps call reports every worktree's agents and their state`() = runTest {
        val executor = answering("worktree-ps-working.json")

        val worktrees = client(executor).agentsByWorktree().associateBy { it.path }

        assertEquals(listOf(orca, "worktree", "ps", "--limit", "1000", "--json"), executor.executedCommands.single())
        assertEquals(OrcaWorktree(shogunai, false, listOf(OrcaAgent("claude", AgentState.WORKING, AgentActivity(prompt = COUNT_PROMPT)))), worktrees[shogunai])
        assertEquals(OrcaWorktree(created, true), worktrees[created])
        assertEquals(OrcaWorktree(repository, false), worktrees[repository])
        assertEquals(OrcaWorktree(external, false), worktrees[external])
    }

    @Test
    fun `a finished turn is reported as done`() = runTest {
        val row = client(answering("worktree-ps-done.json")).agentsByWorktree().single { it.path == shogunai }

        assertEquals(
            listOf(OrcaAgent("claude", AgentState.DONE, AgentActivity(prompt = COUNT_PROMPT, lastMessage = "9\n20\n21\n22\n23\n24\n25\n26\n27\n28\n29\n30\n\ndone"))),
            row.agents,
        )
    }

    @Test
    fun `an agent waiting for permission reports the tool and its input`() = runTest {
        val row = client(answering("worktree-ps-tool.json")).agentsByWorktree().single { it.path == external }

        val waitingOn = AgentTool("Bash", "curl -s https://example.com -o page.html")
        assertEquals(
            listOf(OrcaAgent("claude", AgentState.BLOCKED, AgentActivity(waitingOn, "Use the Bash tool to run: curl -s https://example.com -o page.html"))),
            row.agents,
        )
    }

    @Test
    fun `a finished agent's last tool isn't reported as waited on`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture("worktree-ps-tool.json").replace("\"waiting\"", "\"done\"")) }

        val agent = client(executor).agentsByWorktree().single { it.path == external }.agents.single()

        assertEquals(AgentState.DONE, agent.state)
        assertNull(agent.activity?.waitingOn)
    }

    @Test
    fun `an agent with blank details has no activity`() = runTest {
        val agent = client(answering("worktree-ps.json")).agentsByWorktree().single { it.path == external }.agents.single()

        assertNull(agent.activity)
    }

    @Test
    fun `one ports call reports the ports Orca ties to workspaces`() = runTest {
        val executor = answering("ports-all.json")

        val ports = client(executor).ports()

        assertEquals(listOf(orca, "ports", "--all", "--json"), executor.executedCommands.single())
        assertEquals(
            listOf(
                OrcaPort(8765, external, "Python", "http://127.0.0.1:8765"),
                OrcaPort(10004, "/projects/other-worktree", "java"),
                OrcaPort(10011, external, "java"),
                OrcaPort(61610, "/projects/other-worktree", "java"),
            ),
            ports,
        )
    }

    @Test
    fun `a ps recorded with a waiting agent in a ShogunAi worktree shows it as external`() = runTest {
        val row = client(answering("worktree-ps.json")).agentsByWorktree().single { it.path == external }

        assertFalse(row.createdByOrca)
        assertEquals(listOf(OrcaAgent("claude", AgentState.BLOCKED)), row.agents)
    }

    @Test
    fun `terminals are listed by worktree path`() = runTest {
        val executor = answering("terminal-list-agent.json")

        val terminals = client(executor).terminals(OrcaWorkspace.Worktree(external))

        assertEquals(listOf(OrcaTerminal("term_44a32dcb-6aa6-478f-a1f0-0731154c76d2", external, "main-repo-task-1")), terminals)
        assertEquals(listOf(orca, "terminal", "list", "--worktree", "path:$external", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `a terminal's agent comes from its identity`() = runTest {
        val executor = answering("terminal-show-agent.json")

        assertEquals("claude", client(executor).terminalAgent("term_1"))
        assertEquals(listOf(orca, "terminal", "show", "--terminal", "term_1", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `a plain shell has no agent`() = runTest {
        assertNull(client(answering("terminal-show-shell.json")).terminalAgent("term_1"))
    }

    @Test
    fun `a terminal is created with its title, command and focus`() = runTest {
        val executor = answering("terminal-create.json")

        val terminal = client(executor).createTerminal(OrcaWorkspace.Worktree(external), "main-repo-task-1", "env 'PORT=4100' claude -n main-repo-task-1", focus = true)

        assertEquals(OrcaTerminal("term_44a32dcb-6aa6-478f-a1f0-0731154c76d2", external, "main-repo-task-1"), terminal)
        assertEquals(
            listOf(
                orca, "terminal", "create", "--worktree", "path:$external", "--title", "main-repo-task-1",
                "--command", "env 'PORT=4100' claude -n main-repo-task-1", "--focus", "--json",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `switch and close pass their arguments`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, fixture(if ("switch" in command) "terminal-switch.json" else "terminal-close.json"))
        }

        client(executor).switchTo("term_1")
        client(executor).closeTerminals(OrcaWorkspace.Worktree(external))

        assertEquals(
            listOf(
                listOf(orca, "terminal", "switch", "--terminal", "term_1", "--json"),
                listOf(orca, "terminal", "close", "--worktree", "path:$external", "--all", "--json"),
            ),
            executor.executedCommands,
        )
    }

    @Test
    fun `ps lists a folder workspace with its folder id and agents`() = runTest {
        val rows = client(answering("worktree-ps-folder-agent.json")).agentsByWorktree().associateBy { it.path }

        val workspace = rows.getValue("/projects/product")
        assertEquals(folderId, workspace.folderId)
        assertEquals(folder, workspace.workspace)
        assertEquals(listOf(OrcaAgent("claude", AgentState.BLOCKED)), workspace.agents)
        assertEquals(OrcaWorktree("/projects/plainmulti", false), rows["/projects/plainmulti"])
        assertEquals(OrcaWorktree("/projects/gitmulti", false), rows["/projects/gitmulti"])
        assertEquals(OrcaWorkspace.Worktree("/projects/product/product-app"), rows.getValue("/projects/product/product-app").workspace)
    }

    @Test
    fun `a folder workspace without an agent is listed without agents`() = runTest {
        val workspace = client(answering("worktree-ps-folder.json")).agentsByWorktree().single { it.folderId != null }

        assertEquals("/projects/product", workspace.path)
        assertEquals(emptyList(), workspace.agents)
    }

    @Test
    fun `an unknown workspace kind is a worktree with a path`() = runTest {
        val row = """{"ok": true, "result": {"worktrees": [{"workspaceKind": "remote-box", "worktreeId": "folder:x", "path": "/projects/box", "agents": []}]}}"""
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, row) }

        assertEquals(listOf(OrcaWorktree("/projects/box", false)), client(executor).agentsByWorktree())
    }

    @Test
    fun `a folder workspace's terminals are listed by its folder id`() = runTest {
        val executor = answering("terminal-list-folder.json")

        val terminals = client(executor).terminals(folder)

        assertEquals(listOf("term_34d22a3f-c516-4008-92c8-3c01d8be4382", "term_f0d9d85e-d490-4984-9e25-64ce0aba0ebb"), terminals.map { it.handle })
        assertEquals(listOf("Terminal 1", "product"), terminals.map { it.title })
        assertEquals(listOf("", ""), terminals.map { it.worktreePath })
        assertEquals(listOf(orca, "terminal", "list", "--worktree", "id:folder:$folderId", "--json"), executor.executedCommands.single())
    }

    @Test
    fun `a terminal is created in a folder workspace by its folder id`() = runTest {
        val executor = answering("terminal-create-folder.json")

        val terminal = client(executor).createTerminal(folder, "product", "claude -n product", focus = true)

        assertEquals("term_f0d9d85e-d490-4984-9e25-64ce0aba0ebb", terminal.handle)
        assertEquals(
            listOf(orca, "terminal", "create", "--worktree", "id:folder:$folderId", "--title", "product", "--command", "claude -n product", "--focus", "--json"),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `a folder workspace's terminals are closed one by one by handle`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, fixture(if ("list" in command) "terminal-list-folder.json" else "terminal-close-handle.json"))
        }

        client(executor).closeTerminals(folder)

        assertEquals(
            listOf(
                listOf(orca, "terminal", "list", "--worktree", "id:folder:$folderId", "--json"),
                listOf(orca, "terminal", "close", "--terminal", "term_34d22a3f-c516-4008-92c8-3c01d8be4382", "--json"),
                listOf(orca, "terminal", "close", "--terminal", "term_f0d9d85e-d490-4984-9e25-64ce0aba0ebb", "--json"),
            ),
            executor.executedCommands,
        )
    }

    @Test
    fun `Orca rejects a folder that isn't a Git repository`() = runTest {
        val executor = FakeShellCommandExecutor { CommandResult(it, 1, fixture("repo-add-not-git.json"), "") }

        val error = assertFailsWith<WorktreeError.OrcaCommandFailed> { client(executor).addRepo("/projects/product") }

        assertEquals("Not a valid git repository: /projects/product", error.detail)
    }

    @Test
    fun `an emulator is attached to a worktree by serial and focused`() = runTest {
        val executor = answering("emulator-attach.json")

        client(executor).attachEmulator(OrcaWorkspace.Worktree(shogunai), "emulator-5556")

        assertEquals(
            listOf(orca, "emulator", "attach", "emulator-5556", "--worktree", "path:$shogunai", "--focus", "--json"),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `attaching an emulator that isn't running fails with Orca's message`() = runTest {
        val executor = FakeShellCommandExecutor { CommandResult(it, 1, fixture("emulator-attach-not-found.json"), "") }

        val error = assertFailsWith<WorktreeError.OrcaCommandFailed> {
            client(executor).attachEmulator(OrcaWorkspace.Worktree(shogunai), "emulator-5590")
        }

        assertTrue("emulator-5590 not found" in error.detail)
    }

    @Test
    fun `a failed response becomes a command failure with Orca's message`() = runTest {
        val executor = FakeShellCommandExecutor { CommandResult(it, 1, fixture("error.json"), "") }

        val error = assertFailsWith<WorktreeError.OrcaCommandFailed> { client(executor).repos() }

        assertEquals("repo_not_found", error.detail)
        assertEquals(listOf(orca, "repo", "list", "--json"), error.command)
    }

    @Test
    fun `an ok false response fails even with exit code zero`() = runTest {
        val error = assertFailsWith<WorktreeError.OrcaCommandFailed> { client(answering("error.json")).repos() }

        assertEquals("repo_not_found", error.detail)
    }

    @Test
    fun `an unexpected response is a command failure`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, """{"ok": true, "result": {}}""") }

        assertFailsWith<WorktreeError.OrcaCommandFailed> { client(executor).repos() }
    }

    @Test
    fun `a missing binary means Orca isn't installed`() = runTest {
        val executor = FakeShellCommandExecutor()
        val client = client(executor, files = emptySet())

        assertFalse(client.isInstalled())
        assertFailsWith<WorktreeError.OrcaNotInstalled> { client.status() }
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `macOS falls back to the app bundle's binary`() = runTest {
        val bundle = "/Applications/Orca.app/Contents/Resources/bin/orca"
        val executor = answering("status.json")

        client(executor, files = setOf(bundle)).status()

        assertEquals(bundle, executor.executedCommands.single().first())
    }

    @Test
    fun `the user's local bin comes first`() {
        val local = "/home/dev/.local/bin/orca"

        assertEquals(local, client(FakeShellCommandExecutor(), files = setOf(local, orca)).executable())
    }

    @Test
    fun `Linux only looks for orca-ide, never GNOME's orca`() {
        val linux = { files: Set<String>, onPath: Map<String, String> ->
            client(FakeShellCommandExecutor(), files = files, osName = "Linux", onPath = onPath)
        }

        assertFalse(linux(setOf("/usr/local/bin/orca"), mapOf("orca" to "/usr/bin/orca")).isInstalled())
        assertEquals("/usr/local/bin/orca-ide", linux(setOf("/usr/local/bin/orca-ide"), emptyMap()).executable())
        assertEquals("/usr/bin/orca-ide", linux(emptySet(), mapOf("orca-ide" to "/usr/bin/orca-ide")).executable())
    }

    @Test
    fun `Windows looks for orca on the PATH`() {
        val windows = client(FakeShellCommandExecutor(), files = emptySet(), osName = "Windows 11", onPath = mapOf("orca" to "C:\\Orca\\bin\\orca.cmd"))

        assertEquals("C:\\Orca\\bin\\orca.cmd", windows.executable())
    }

    private companion object {
        const val COUNT_PROMPT = "Count slowly from 1 to 30, one number per line, then reply with the word done."
    }
}
