package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import java.io.File

class ReadAgentActivityUseCase(
    private val sessionStore: AgentSessionStore,
    private val isAlive: (Long) -> Boolean = { pid -> ProcessHandle.of(pid).isPresent },
    private val transcriptModifiedAt: (String) -> Long? = { path -> File(path).lastModified().takeIf { it > 0 } },
    private val now: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(worktreePath: String): AgentState? =
        sessionStore.list(worktreePath)
            .filter(::isLive)
            .map(::effectiveState)
            .minByOrNull { PRECEDENCE.indexOf(it) }

    private fun isLive(record: AgentSessionRecord): Boolean =
        record.pid?.let(isAlive) ?: (now() - record.updatedAt < UNKNOWN_PID_MAX_AGE_MILLIS)

    private fun effectiveState(record: AgentSessionRecord): AgentState {
        val resumed = record.state in setOf(AgentState.BLOCKED, AgentState.DONE) &&
            (record.transcriptPath?.let(transcriptModifiedAt)?.let { it > record.updatedAt + TRANSCRIPT_GRACE_MILLIS } ?: false)
        return if (resumed) AgentState.WORKING else record.state
    }

    private companion object {
        const val TRANSCRIPT_GRACE_MILLIS = 1_000L
        const val UNKNOWN_PID_MAX_AGE_MILLIS = 12 * 60 * 60 * 1_000L
        val PRECEDENCE = listOf(AgentState.WORKING, AgentState.BLOCKED, AgentState.DONE, AgentState.IDLE)
    }
}
