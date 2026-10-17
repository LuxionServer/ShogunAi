package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.HerdrWorktreeOpening

interface HerdrClient {

    suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening

    suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String

    suspend fun closePane(paneId: String)

    suspend fun runInPane(paneId: String, command: String)

    suspend fun openWorktreeWorkspaces(repository: String): Map<String, String>

    suspend fun closeWorkspace(workspaceId: String)
}
