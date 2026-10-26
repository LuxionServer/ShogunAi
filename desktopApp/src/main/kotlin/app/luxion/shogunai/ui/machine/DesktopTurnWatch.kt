package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.ui.worktree.WorktreeDesktopTurn

data class DesktopWaitPlace(val position: Int, val ticket: WaitTicket, val holder: DesktopTurn?)

class DesktopTurnWatch(
    private val read: suspend () -> Result<DesktopTurnState>,
    private val forceRelease: suspend () -> Result<DesktopTurnRelease>,
) {
    var state by mutableStateOf(DesktopTurnState(holder = null, waiting = emptyList()))
        private set
    var pendingRelease by mutableStateOf<DesktopTurn?>(null)
        private set
    var isReleasing by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set

    val isActive: Boolean
        get() = state.holder != null || state.waiting.isNotEmpty()

    suspend fun refresh() {
        read().onSuccess { state = it }
    }

    fun heldBy(worktreePath: String): DesktopTurn? = state.holder?.takeIf { samePath(it.worktreePath, worktreePath) }

    fun waitOf(worktreePath: String): DesktopWaitPlace? {
        val index = state.waiting.indexOfFirst { samePath(it.worktreePath, worktreePath) }
        if (index < 0) return null
        return DesktopWaitPlace(index + 1, state.waiting[index], state.holder)
    }

    fun rowTurn(worktreePath: String): WorktreeDesktopTurn? =
        heldBy(worktreePath)?.let { WorktreeDesktopTurn.Holding(it.expiresAt) }
            ?: waitOf(worktreePath)?.let { WorktreeDesktopTurn.Waiting(it.position) }

    fun requestRelease() {
        pendingRelease = state.holder
    }

    fun cancelRelease() {
        pendingRelease = null
    }

    suspend fun confirmRelease() {
        pendingRelease = null
        isReleasing = true
        try {
            forceRelease().onFailure { error = it }
            refresh()
        } finally {
            isReleasing = false
        }
    }

    fun dismissError() {
        error = null
    }

    private fun samePath(first: String, second: String): Boolean = first.comparablePath() == second.comparablePath()

    private fun String.comparablePath(): String = replace('\\', '/').trimEnd('/')

    companion object {
        val NONE: DesktopTurnWatch
            get() = DesktopTurnWatch(
                read = { Result.success(DesktopTurnState(holder = null, waiting = emptyList())) },
                forceRelease = { Result.success(DesktopTurnRelease.NobodyHolds) },
            )
    }
}
