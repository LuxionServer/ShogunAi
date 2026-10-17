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
}

fun WorktreeError.herdrDetail(): String = (this as? WorktreeError.HerdrCommandFailed)?.detail ?: message.orEmpty()
