package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaStatus
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.OrcaWorktree

interface OrcaClient {

    fun isInstalled(): Boolean

    suspend fun status(): OrcaStatus

    suspend fun start()

    suspend fun bringToFront()

    suspend fun repos(): List<OrcaRepo>

    suspend fun addRepo(path: String): OrcaRepo

    suspend fun showExternalWorktrees(repoId: String)

    suspend fun worktrees(repoId: String): List<OrcaWorktree>

    suspend fun agentsByWorktree(): List<OrcaWorktree>

    suspend fun terminals(workspace: OrcaWorkspace): List<OrcaTerminal>

    suspend fun terminalAgent(handle: String): String?

    suspend fun createTerminal(workspace: OrcaWorkspace, title: String, command: String, focus: Boolean): OrcaTerminal

    suspend fun switchTo(handle: String)

    suspend fun closeTerminals(workspace: OrcaWorkspace)

    suspend fun attachEmulator(workspace: OrcaWorkspace, device: String)
}
