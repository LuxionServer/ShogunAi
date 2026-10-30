package app.luxion.shogunai.domain.model

enum class ClaudeSessionStatus { BUSY, WAITING, IDLE }

data class ClaudeSession(
    val pid: Long,
    val cwd: String,
    val name: String?,
    val startedAt: Long? = null,
    val status: ClaudeSessionStatus? = null,
    val statusUpdatedAt: Long? = null,
) {
    val state: AgentState?
        get() {
            val status = status ?: return null
            return when (status) {
                ClaudeSessionStatus.BUSY -> AgentState.WORKING
                ClaudeSessionStatus.WAITING -> AgentState.BLOCKED
                ClaudeSessionStatus.IDLE -> if (ranATurn()) AgentState.DONE else AgentState.IDLE
            }
        }

    private fun ranATurn(): Boolean {
        val started = startedAt ?: return false
        val changed = statusUpdatedAt ?: return false
        return changed - started > FIRST_TURN_MARGIN_MILLIS
    }

    private companion object {
        const val FIRST_TURN_MARGIN_MILLIS = 5_000L
    }
}

data class ClaudeSessionRef(val name: String, val pid: Long)
