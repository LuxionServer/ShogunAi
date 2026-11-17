package app.luxion.shogunai.domain.model

data class NotifiedWorktree(
    val projectId: String,
    val projectName: String,
    val worktreePath: String,
    val worktreeName: String,
)

enum class AgentRunner { HERDR, ORCA }

data class AgentKey(val worktreePath: String, val agentId: String)

data class ObservedAgent(
    val worktree: NotifiedWorktree,
    val agentId: String,
    val agentName: String,
    val state: AgentState?,
    val runner: AgentRunner? = null,
    val focused: Boolean = false,
) {
    val key: AgentKey get() = AgentKey(worktree.worktreePath, agentId)
}

data class NotificationItem(val worktree: NotifiedWorktree, val agentName: String, val state: AgentState)

data class AgentNotification(val items: List<NotificationItem>) {
    val target: NotifiedWorktree get() = items.first().worktree
    val blockedCount: Int get() = items.count { it.state == AgentState.BLOCKED }
    val doneCount: Int get() = items.size - blockedCount
}

data class NotificationPreferences(val blocked: Boolean = true, val done: Boolean = false) {
    val anyOn: Boolean get() = blocked || done

    fun allows(state: AgentState): Boolean = when (state) {
        AgentState.BLOCKED -> blocked
        AgentState.DONE -> done
        else -> false
    }
}

data class RunnerNotifications(
    val herdr: Set<AgentState> = emptySet(),
    val orca: Set<AgentState> = emptySet(),
) {
    fun notifies(runner: AgentRunner?, state: AgentState): Boolean =
        (runner == AgentRunner.HERDR && state in herdr) || (runner == AgentRunner.ORCA && state in orca)

    companion object {
        val STOPPED_STATES: Set<AgentState> = setOf(AgentState.BLOCKED, AgentState.DONE)
    }
}

data class NotificationPolicy(
    val preferences: NotificationPreferences = NotificationPreferences(),
    val runners: RunnerNotifications = RunnerNotifications(),
)

data class TrackerState(
    val known: Map<AgentKey, AgentState> = emptyMap(),
    val pendingSince: Map<AgentKey, Long> = emptyMap(),
)

data class PendingSession(val sessionId: String, val worktree: NotifiedWorktree, val sinceMillis: Long)

data class TrackerStep(val state: TrackerState, val notification: AgentNotification?, val nextCheckAtMillis: Long?)
