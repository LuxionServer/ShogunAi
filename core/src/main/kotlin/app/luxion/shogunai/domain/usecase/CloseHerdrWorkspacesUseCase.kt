package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlin.coroutines.cancellation.CancellationException

class CloseHerdrWorkspacesUseCase(
    private val config: ProjectConfig,
    private val herdr: HerdrClient,
) {
    suspend fun openWorkspaces(paths: Collection<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val open = try {
            herdr.openWorktreeWorkspaces(config.baseRepositoryPath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return emptyMap()
        }
        val byNormalizedPath = open.mapKeys { (path, _) -> normalizePath(path) }
        return paths.mapNotNull { path -> byNormalizedPath[normalizePath(path)]?.let { path to it } }.toMap()
    }

    suspend fun close(workspaces: Map<String, String>, removedPaths: Collection<String>): List<CleanupFailure> =
        removedPaths.mapNotNull { path ->
            val workspaceId = workspaces[path] ?: return@mapNotNull null
            try {
                herdr.closeWorkspace(workspaceId)
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: WorktreeError) {
                CleanupFailure(path, error)
            } catch (error: Exception) {
                CleanupFailure(path, WorktreeError.HerdrCommandFailed(listOf("workspace", "close", workspaceId), error.message.orEmpty()))
            }
        }

    suspend fun closeHolders(path: String, workspaceId: String?): HolderClosing {
        var failure: WorktreeError? = null
        var closedWorkspace = false
        if (workspaceId != null) {
            failure = close(mapOf(path to workspaceId), listOf(path)).firstOrNull()?.error
            closedWorkspace = failure == null
        }
        val panes = try {
            herdr.listPanes()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return HolderClosing(closedWorkspace, failure)
        }
        panes.filter { it.workspaceId != workspaceId && it.directory?.let { directory -> isInsideDirectory(directory, path) } == true }
            .forEach { pane ->
                try {
                    herdr.closePane(pane.paneId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = failure ?: (error as? WorktreeError)
                        ?: WorktreeError.HerdrCommandFailed(listOf("pane", "close", pane.paneId), error.message.orEmpty())
                }
            }
        return HolderClosing(closedWorkspace, failure)
    }
}

fun WorktreeError.herdrDetail(): String = (this as? WorktreeError.HerdrCommandFailed)?.detail ?: message.orEmpty()

data class HolderClosing(
    val closedWorkspace: Boolean = false,
    val failure: WorktreeError? = null,
    val headlessAppFailure: WorktreeError? = null,
    val closedOrcaTerminals: Boolean = false,
    val orcaFailure: WorktreeError? = null,
)
