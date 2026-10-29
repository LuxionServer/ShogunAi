package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaStatus
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.WorktreeError

class FakeOrcaClient(
    private val installed: Boolean = true,
    var running: Boolean = true,
    private val startsWhenOpened: Boolean = true,
    repos: List<OrcaRepo> = emptyList(),
    private val visibleWorktrees: Map<String, List<OrcaWorktree>> = emptyMap(),
    private val worktreesShownWhenExternalVisible: Map<String, List<OrcaWorktree>> = emptyMap(),
    private val psRows: List<OrcaWorktree> = emptyList(),
    terminals: Map<String, List<OrcaTerminal>> = emptyMap(),
    agents: Map<String, String> = emptyMap(),
    private val startsAgents: Boolean = true,
    private val agentDetectedAfterChecks: Int = 0,
    private val failure: Throwable? = null,
    private val statusFailure: Throwable? = null,
    private val psFailure: Throwable? = null,
    private val terminalsFailure: Throwable? = null,
    private val closeFailures: Map<String, Throwable> = emptyMap(),
    private val folderPaths: Map<String, String> = emptyMap(),
    private val attachFailure: Throwable? = null,
) : OrcaClient {

    data class Created(val worktreePath: String, val title: String, val command: String, val focus: Boolean)

    private val knownRepos = repos.toMutableList()
    private val terminalsByPath = terminals.mapValues { it.value.toMutableList() }.toMutableMap()
    private val agentsByHandle = agents.toMutableMap()
    private val startingAgents = mutableMapOf<String, Pair<String, Int>>()

    var statusChecks = 0
        private set
    var starts = 0
        private set
    var psReads = 0
        private set
    val addedRepos = mutableListOf<String>()
    val shownExternal = mutableListOf<String>()
    val created = mutableListOf<Created>()
    val switched = mutableListOf<String>()
    val closedWorktrees = mutableListOf<String>()
    val attachedEmulators = mutableListOf<Pair<String, String>>()
    val events = mutableListOf<String>()
    val terminalLookups = mutableListOf<String>()
    var raised = 0
        private set

    fun startedClaudeSessions(): List<ClaudeSession> =
        created.mapIndexedNotNull { index, terminal ->
            val name = SESSION_NAME.find(terminal.command)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            ClaudeSession(STARTED_SESSION_PID + index, terminal.worktreePath, name, startedAt = index.toLong())
        }

    override fun isInstalled(): Boolean = installed

    override suspend fun status(): OrcaStatus {
        requireInstalled()
        statusChecks++
        statusFailure?.let { throw it }
        return OrcaStatus(running, "1.4.243")
    }

    override suspend fun start() {
        requireInstalled()
        starts++
        if (!startsWhenOpened) throw WorktreeError.OrcaNotStarted
        running = true
    }

    override suspend fun bringToFront() {
        requireInstalled()
        raised++
    }

    override suspend fun repos(): List<OrcaRepo> {
        requireReady()
        return knownRepos.toList()
    }

    override suspend fun addRepo(path: String): OrcaRepo {
        requireReady()
        addedRepos += path
        return OrcaRepo("repo${knownRepos.size + 1}", path).also { knownRepos += it }
    }

    override suspend fun showExternalWorktrees(repoId: String) {
        requireReady()
        shownExternal += repoId
    }

    override suspend fun worktrees(repoId: String): List<OrcaWorktree> {
        requireReady()
        val external = if (repoId in shownExternal) worktreesShownWhenExternalVisible[repoId].orEmpty() else emptyList()
        return visibleWorktrees[repoId].orEmpty() + external
    }

    override suspend fun agentsByWorktree(): List<OrcaWorktree> {
        requireInstalled()
        psReads++
        psFailure?.let { throw it }
        return psRows
    }

    override suspend fun terminals(workspace: OrcaWorkspace): List<OrcaTerminal> {
        requireReady()
        val worktreePath = keyOf(workspace)
        terminalLookups += worktreePath
        terminalsFailure?.let { throw it }
        return terminalsByPath[worktreePath].orEmpty().toList()
    }

    override suspend fun terminalAgent(handle: String): String? {
        requireReady()
        startingAgents[handle]?.let { (agent, remaining) ->
            if (remaining <= 1) {
                startingAgents -= handle
                agentsByHandle[handle] = agent
            } else {
                startingAgents[handle] = agent to remaining - 1
            }
        }
        return agentsByHandle[handle]
    }

    override suspend fun createTerminal(workspace: OrcaWorkspace, title: String, command: String, focus: Boolean): OrcaTerminal {
        requireReady()
        val worktreePath = keyOf(workspace)
        created += Created(worktreePath, title, command, focus)
        events += "create:$worktreePath"
        val terminal = OrcaTerminal("term_${created.size}", worktreePath, title)
        terminalsByPath.getOrPut(worktreePath) { mutableListOf() } += terminal
        if (startsAgents) {
            val agent = agentName(command)
            if (agentDetectedAfterChecks > 0) startingAgents[terminal.handle] = agent to agentDetectedAfterChecks else agentsByHandle[terminal.handle] = agent
        }
        return terminal
    }

    override suspend fun switchTo(handle: String) {
        requireReady()
        switched += handle
        events += "switch:$handle"
    }

    override suspend fun closeTerminals(workspace: OrcaWorkspace) {
        requireReady()
        val worktreePath = keyOf(workspace)
        closeFailures[worktreePath]?.let { throw it }
        closedWorktrees += worktreePath
        terminalsByPath -= worktreePath
    }

    override suspend fun attachEmulator(workspace: OrcaWorkspace, device: String) {
        requireReady()
        attachFailure?.let { throw it }
        val worktreePath = keyOf(workspace)
        attachedEmulators += worktreePath to device
        events += "attach:$worktreePath:$device"
    }

    private fun keyOf(workspace: OrcaWorkspace): String = when (workspace) {
        is OrcaWorkspace.Worktree -> workspace.path
        is OrcaWorkspace.Folder -> folderPaths[workspace.id] ?: "folder:${workspace.id}"
    }

    private fun requireInstalled() {
        if (!installed) throw WorktreeError.OrcaNotInstalled
    }

    private fun requireReady() {
        requireInstalled()
        failure?.let { throw it }
    }

    private fun agentName(command: String): String =
        command.replace(SESSION_NAME, "").trim().split(' ').last { !it.contains('=') && it != "env" }.substringAfterLast('/')

    private companion object {
        val SESSION_NAME = Regex(" -n '?([^' ]+)'?$")
    }
}
