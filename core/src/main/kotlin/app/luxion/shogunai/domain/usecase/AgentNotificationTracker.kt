package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.NotificationItem
import app.luxion.shogunai.domain.model.NotificationPolicy
import app.luxion.shogunai.domain.model.ObservedAgent
import app.luxion.shogunai.domain.model.RunnerNotifications
import app.luxion.shogunai.domain.model.TrackerState
import app.luxion.shogunai.domain.model.TrackerStep

object AgentNotificationTracker {
    const val GRACE_MILLIS = 8_000L

    fun advance(
        state: TrackerState,
        observed: List<ObservedAgent>,
        scope: Set<String>,
        nowMillis: Long,
        policy: NotificationPolicy,
    ): TrackerStep {
        val inScope = observed.filter { it.worktree.worktreePath in scope }.associateBy { it.key }.values
        val confirmed = mutableListOf<ObservedAgent>()
        val pending = state.pendingSince.filterKeys { it.worktreePath !in scope }.toMutableMap()
        inScope.forEach { agent ->
            val since = state.pendingSince[agent.key]
            when {
                since != null && agent.isStopped() -> if (nowMillis >= since + GRACE_MILLIS) confirmed += agent else pending[agent.key] = since
                since == null && agent.isStopped() && state.known[agent.key] == AgentState.WORKING -> pending[agent.key] = nowMillis
            }
        }
        val known = state.known.filterKeys { it.worktreePath !in scope } +
            inScope.mapNotNull { agent -> agent.state?.let { agent.key to it } }
        val items = confirmed
            .filter { agent -> notifies(agent, policy) }
            .sortedBy { if (it.state == AgentState.BLOCKED) 0 else 1 }
            .map { NotificationItem(it.worktree, it.agentName, it.state!!) }
        return TrackerStep(
            state = TrackerState(known, pending),
            notification = items.takeIf { it.isNotEmpty() }?.let(::AgentNotification),
            nextCheckAtMillis = pending.values.minOrNull()?.plus(GRACE_MILLIS),
        )
    }

    private fun ObservedAgent.isStopped(): Boolean = state in RunnerNotifications.STOPPED_STATES

    private fun notifies(agent: ObservedAgent, policy: NotificationPolicy): Boolean {
        val state = agent.state!!
        return policy.preferences.allows(state) && !agent.focused && !policy.runners.notifies(agent.runner, state)
    }
}
