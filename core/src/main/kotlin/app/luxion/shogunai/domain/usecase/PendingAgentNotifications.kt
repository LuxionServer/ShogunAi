package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.io.PendingNotificationStore
import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.AgentKey
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentNotificationText
import app.luxion.shogunai.domain.model.AgentRunner
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.NotificationPolicy
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.ObservedAgent
import app.luxion.shogunai.domain.model.PendingSession
import app.luxion.shogunai.domain.model.RunnerNotifications
import app.luxion.shogunai.domain.model.TrackerState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.runSuspendCatching

class PendingAgentNotifications(
    private val store: PendingNotificationStore,
    private val sessions: AgentSessionStore,
    private val sessionState: (AgentSessionRecord) -> AgentState?,
    private val herdrAgents: suspend () -> List<HerdrAgent>,
    private val runners: RunnerNotificationSource,
    private val notifier: SystemNotifier,
    private val images: NotificationImages,
    private val preferences: () -> NotificationPreferences,
    private val language: () -> AppLanguage,
    private val appRunning: () -> Boolean,
    private val now: () -> Long,
) {

    fun shouldSchedule(previous: AgentState?, next: AgentState?): Boolean =
        previous == AgentState.WORKING && next in RunnerNotifications.STOPPED_STATES && preferences().anyOn && !appRunning()

    fun schedule(sessionId: String, worktree: NotifiedWorktree) {
        store.update { pending -> (pending.filterNot { it.sessionId == sessionId } + PendingSession(sessionId, worktree, now())) to Unit }
    }

    suspend fun notifyDue(): Long? {
        if (appRunning()) return store.update { emptyList<PendingSession>() to null }
        val pending = store.update { it to it }
        val due = (pending.minOfOrNull { it.sinceMillis } ?: return null) + AgentNotificationTracker.GRACE_MILLIS
        if (now() < due) return due
        val herdr = runSuspendCatching { herdrAgents() }.getOrDefault(emptyList())
        val policy = NotificationPolicy(preferences(), runners.read())
        val observed = pending.mapNotNull { observe(it, herdr) }
        val (notification, next) = store.update { current ->
            val step = AgentNotificationTracker.advance(
                state = TrackerState(pendingSince = current.associate { it.key() to it.sinceMillis }),
                observed = observed.filter { agent -> current.any { it.key() == agent.key } },
                scope = current.map { it.worktree.worktreePath }.toSet(),
                nowMillis = now(),
                policy = policy,
            )
            current.filter { it.key() in step.state.pendingSince } to (step.notification to step.nextCheckAtMillis)
        }
        notification?.let { show(it) }
        return next
    }

    private fun observe(session: PendingSession, herdr: List<HerdrAgent>): ObservedAgent? {
        val record = sessions.list(session.worktree.worktreePath).firstOrNull { it.sessionId == session.sessionId } ?: return null
        val worktree = listOf(Worktree(path = session.worktree.worktreePath, branch = null))
        val inHerdr = herdr.filter { it.name == record.agent && owningWorktree(it.directory, worktree) != null }
        return ObservedAgent(
            worktree = session.worktree,
            agentId = session.sessionId,
            agentName = agentDisplayName(record.agent),
            state = sessionState(record),
            runner = AgentRunner.HERDR.takeIf { inHerdr.isNotEmpty() },
            focused = inHerdr.any { it.focused },
        )
    }

    private suspend fun show(notification: AgentNotification) {
        val text = AgentNotificationText.of(notification, language())
        val target = notification.target
        val click = AppRequest.openWorktree(createdAt = 0, projectId = target.projectId, worktreePath = target.worktreePath)
        notifier.show(SystemNotification(text.title, text.body, text.openLabel, click, images.imageFor(notification))) {}
    }

    private fun PendingSession.key(): AgentKey = AgentKey(worktree.worktreePath, sessionId)
}
