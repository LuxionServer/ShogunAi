package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.HerdrPane
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

    override suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening {
        failure?.let { throw it }
        openFailures[path]?.let { throw it }
        opened += Triple(repository, path, label)
        val workspaceId = "w${opened.size}"
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = "$workspaceId:p1", alreadyOpen = path in alreadyOpen)
    }

    override suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String {
        splits += Split(paneId, cwd, environment)
        return paneId.substringBefore(':') + ":p${splits.size + 1}"
    }

    override suspend fun closePane(paneId: String) {
        closed += paneId
    }

    override suspend fun runInPane(paneId: String, command: String) {
        runFailure?.let { throw it }
        ran += paneId to command
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
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = "$workspaceId:p1", alreadyOpen = false)
    }

    override suspend fun listWorkspaces(): List<HerdrWorkspace> {
        failure?.let { throw it }
        return workspaces
    }

    override suspend fun listPanes(): List<HerdrPane> {
        failure?.let { throw it }
        return panes
    }

    override suspend fun focusWorkspace(workspaceId: String) {
        focusedWorkspaces += workspaceId
    }
}
