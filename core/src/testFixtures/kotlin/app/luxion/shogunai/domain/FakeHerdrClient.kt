package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.HerdrPaneProcesses
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening

class FakeHerdrClient(
    private val alreadyOpen: Set<String> = emptySet(),
    private val failure: Throwable? = null,
    private val runFailure: Throwable? = null,
    private val openWorkspaces: Map<String, String> = emptyMap(),
    private val listFailure: Throwable? = null,
    private val closeFailures: Map<String, Throwable> = emptyMap(),
    private val agents: List<HerdrAgent> = emptyList(),
    private val agentsFailure: Throwable? = null,
    private val workspaces: List<HerdrWorkspace> = emptyList(),
    private val panes: List<HerdrPane> = emptyList(),
    private val openFailures: Map<String, Throwable> = emptyMap(),
    private val startsAgents: Boolean = true,
    private val agentDetectedAfterListings: Int = 0,
    private val busyPanes: Set<String> = emptySet(),
    private val unknownProcessPanes: Set<String> = emptySet(),
) : HerdrClient {

    data class Split(val paneId: String, val cwd: String, val environment: List<Pair<String, String>>)

    val opened = mutableListOf<Triple<String, String, String>>()
    val splits = mutableListOf<Split>()
    val closed = mutableListOf<String>()
    val closedWorkspaces = mutableListOf<String>()
    var listings = 0
        private set
    var agentListings = 0
        private set
    val focusedAgents = mutableListOf<String>()
    val ran = mutableListOf<Pair<String, String>>()
    val paneListings = mutableListOf<String?>()
    val processReads = mutableListOf<String>()
    private val createdPanes = mutableListOf<HerdrPane>()
    private val startingAgents = mutableMapOf<String, Pair<String, Int>>()
    private val workspacesByPath = mutableMapOf<String, String>()

    fun startedClaudeSessions(): List<ClaudeSession> =
        ran.mapIndexedNotNull { index, (paneId, command) ->
            val name = SESSION_NAME_ARGUMENT.find(command)?.value?.substringAfterLast(' ') ?: return@mapIndexedNotNull null
            val directory = (createdPanes + panes).firstOrNull { it.paneId == paneId }?.directory ?: return@mapIndexedNotNull null
            ClaudeSession(STARTED_SESSION_PID + index, directory, name, startedAt = index.toLong())
        }

    fun quitAgent(paneId: String) {
        val pane = createdPanes.indexOfFirst { it.paneId == paneId }
        if (pane >= 0) createdPanes[pane] = createdPanes[pane].copy(agent = null, agentStatus = HerdrPane.UNKNOWN_AGENT_STATUS)
    }

    private fun addPane(paneId: String, cwd: String) {
        if (panes.none { it.paneId == paneId } && createdPanes.none { it.paneId == paneId }) {
            createdPanes += HerdrPane(paneId, paneId.substringBefore(':'), cwd)
        }
    }

    override suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening {
        failure?.let { throw it }
        openFailures[path]?.let { throw it }
        opened += Triple(repository, path, label)
        val reopened = path in workspacesByPath
        val workspaceId = workspacesByPath.getOrPut(path) { "w${workspacesByPath.size + 1}" }
        addPane("$workspaceId:p1", path)
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = "$workspaceId:p1", alreadyOpen = reopened || path in alreadyOpen)
    }

    override suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String {
        splits += Split(paneId, cwd, environment)
        return (paneId.substringBefore(':') + ":p${splits.size + 1}").also { addPane(it, cwd) }
    }

    override suspend fun closePane(paneId: String) {
        closed += paneId
        createdPanes.removeAll { it.paneId == paneId }
    }

    override suspend fun runInPane(paneId: String, command: String) {
        runFailure?.let { throw it }
        ran += paneId to command
        if (startsAgents) {
            val agent = command.trim().replace(SESSION_NAME_ARGUMENT, "").substringAfterLast(' ')
            if (agentDetectedAfterListings > 0) {
                startingAgents[paneId] = agent to agentDetectedAfterListings
            } else {
                markLive(paneId, agent)
            }
        }
    }

    private fun markLive(paneId: String, agent: String) {
        val pane = createdPanes.indexOfFirst { it.paneId == paneId }
        if (pane >= 0) {
            createdPanes[pane] = createdPanes[pane].copy(agent = agent, agentStatus = "idle")
        } else {
            val known = panes.firstOrNull { it.paneId == paneId } ?: HerdrPane(paneId, paneId.substringBefore(':'), null)
            createdPanes += known.copy(agent = agent, agentStatus = "idle")
        }
    }

    override suspend fun openWorktreeWorkspaces(repository: String): Map<String, String> {
        listings++
        listFailure?.let { throw it }
        return openWorkspaces
    }

    override suspend fun closeWorkspace(workspaceId: String) {
        closeFailures[workspaceId]?.let { throw it }
        closedWorkspaces += workspaceId
    }

    override suspend fun listAgents(): List<HerdrAgent> {
        agentListings++
        agentsFailure?.let { throw it }
        return agents
    }

    override suspend fun focusAgent(paneId: String) {
        focusedAgents += paneId
    }

    val createdWorkspaces = mutableListOf<Pair<String, String>>()
    val focusedWorkspaces = mutableListOf<String>()

    override suspend fun createWorkspace(cwd: String, label: String): HerdrWorktreeOpening {
        failure?.let { throw it }
        createdWorkspaces += cwd to label
        val workspaceId = "plain${createdWorkspaces.size}"
        addPane("$workspaceId:p1", cwd)
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = "$workspaceId:p1", alreadyOpen = false)
    }

    override suspend fun listWorkspaces(): List<HerdrWorkspace> {
        failure?.let { throw it }
        return workspaces
    }

    override suspend fun listPanes(workspaceId: String?): List<HerdrPane> {
        failure?.let { throw it }
        paneListings += workspaceId
        startingAgents.toMap().forEach { (paneId, starting) ->
            val (agent, remaining) = starting
            if (remaining <= 1) {
                startingAgents -= paneId
                markLive(paneId, agent)
            } else {
                startingAgents[paneId] = agent to remaining - 1
            }
        }
        val live = createdPanes.associateBy { it.paneId }
        val all = panes.map { live[it.paneId] ?: it } + createdPanes.filter { created -> panes.none { it.paneId == created.paneId } }
        return all.filter { workspaceId == null || it.workspaceId == workspaceId }
    }

    override suspend fun focusWorkspace(workspaceId: String) {
        focusedWorkspaces += workspaceId
    }

    override suspend fun paneProcesses(paneId: String): HerdrPaneProcesses? {
        processReads += paneId
        return when (paneId) {
            in unknownProcessPanes -> null
            in busyPanes -> HerdrPaneProcesses(SHELL_PID, SHELL_PID + 1)
            else -> HerdrPaneProcesses(SHELL_PID, SHELL_PID)
        }
    }
}

private val SESSION_NAME_ARGUMENT = Regex(" -n \\S+$")

const val STARTED_SESSION_PID = 9000L

private const val SHELL_PID = 4000L
