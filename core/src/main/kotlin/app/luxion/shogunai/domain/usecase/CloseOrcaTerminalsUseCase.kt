package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.WorktreeError
import kotlin.coroutines.cancellation.CancellationException

class CloseOrcaTerminalsUseCase(private val orca: OrcaClient) {

    suspend fun withTerminals(paths: Collection<String>): Set<String> {
        if (paths.isEmpty() || !orca.isInstalled()) return emptySet()
        val running = try {
            orca.status().running
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
        if (!running) return emptySet()
        return paths.filter { path ->
            try {
                orca.terminals(path).isNotEmpty()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                false
            }
        }.toSet()
    }

    suspend fun close(withTerminals: Set<String>, removedPaths: Collection<String>): List<CleanupFailure> =
        removedPaths.filter { it in withTerminals }.mapNotNull { path -> closeOne(path)?.let { CleanupFailure(path, it) } }

    suspend fun closeOne(path: String): WorktreeError? = try {
        orca.closeTerminals(path)
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: WorktreeError) {
        error
    } catch (error: Exception) {
        WorktreeError.OrcaCommandFailed(listOf("orca", "terminal", "close", "--worktree", "path:$path", "--all"), error.message.orEmpty())
    }
}

fun WorktreeError.orcaDetail(): String = (this as? WorktreeError.OrcaCommandFailed)?.detail ?: message.orEmpty()
