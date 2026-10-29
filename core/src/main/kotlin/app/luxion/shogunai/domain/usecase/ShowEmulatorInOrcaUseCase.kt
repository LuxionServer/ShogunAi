package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.OrcaEmulatorView
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ShowEmulatorInOrcaUseCase(
    private val orca: OrcaClient,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    private val timeout: Duration = 10.seconds,
) {
    suspend operator fun invoke(worktreePath: String, serial: String): OrcaEmulatorView {
        if (!orca.isInstalled()) return OrcaEmulatorView.NotOpen
        return try {
            withTimeout(timeout) { show(worktreePath, serial) }
        } catch (error: TimeoutCancellationException) {
            OrcaEmulatorView.Failed("Orca didn't answer within ${timeout.inWholeSeconds} seconds")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            OrcaEmulatorView.Failed(message(error))
        }
    }

    private suspend fun show(worktreePath: String, serial: String): OrcaEmulatorView {
        if (!orca.status().running) return OrcaEmulatorView.NotOpen
        if (orca.agentsByWorktree().none { samePath(it.path, worktreePath) }) return OrcaEmulatorView.NotOpen
        orca.attachEmulator(OrcaWorkspace.Worktree(worktreePath), serial)
        return OrcaEmulatorView.Shown
    }

    private fun message(error: Exception): String =
        (error as? WorktreeError.OrcaCommandFailed)?.detail ?: error.message ?: error::class.simpleName.orEmpty()
}
