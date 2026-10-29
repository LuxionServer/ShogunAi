package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.HerdrPaneProcesses
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening

interface HerdrClient {

    fun isInstalled(): Boolean = true

    suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening

    suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String

    suspend fun closePane(paneId: String)

    suspend fun runInPane(paneId: String, command: String)

    suspend fun openWorktreeWorkspaces(repository: String): Map<String, String>

    suspend fun closeWorkspace(workspaceId: String)

    suspend fun listAgents(): List<HerdrAgent>

    suspend fun focusAgent(paneId: String)

    suspend fun createWorkspace(cwd: String, label: String): HerdrWorktreeOpening

    suspend fun listWorkspaces(): List<HerdrWorkspace>

    suspend fun listPanes(workspaceId: String? = null): List<HerdrPane>

    suspend fun focusWorkspace(workspaceId: String)

    suspend fun paneProcesses(paneId: String): HerdrPaneProcesses?
}
