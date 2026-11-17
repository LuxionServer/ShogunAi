package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.AppInstanceLock
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.io.NotificationSetup
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.runSuspendCatching
import app.luxion.shogunai.domain.usecase.PendingAgentNotifications
import app.luxion.shogunai.domain.usecase.ReadAgentActivityUseCase

class AgentNotificationHook(
    private val setup: NotificationSetup?,
    private val sessions: AgentSessionStore,
    private val herdr: HerdrClient,
    private val projects: ProjectRepository,
    private val appLock: AppInstanceLock,
    private val isClaudeAlive: (Long) -> Boolean,
    private val now: () -> Long,
    private val sameDirectory: (String, String) -> Boolean,
) {

    fun before(owner: Pair<String, Worktree>?, sessionId: String): (AgentState?) -> Unit {
        if (owner == null) return {}
        val previous = sessions.list(owner.second.path).firstOrNull { it.sessionId == sessionId }?.state
        return { state -> schedule(previous, state, owner, sessionId) }
    }

    private fun schedule(previous: AgentState?, state: AgentState?, owner: Pair<String, Worktree>, sessionId: String) {
        val setup = setup ?: return
        val pending = pendingNotifications(setup)
        if (!pending.shouldSchedule(previous, state)) return
        val (base, worktree) = owner
        val project = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, base) } ?: return
        val name = worktree.branch ?: worktree.path.replace('\\', '/').substringAfterLast('/')
        pending.schedule(sessionId, NotifiedWorktree(project.id, project.name, worktree.path, name))
        setup.startInBackground(listOf("hook", "notify-pending"))
    }

    suspend fun notifyPending(): Int {
        val setup = setup ?: return EXIT_OK
        val pending = pendingNotifications(setup)
        repeat(MAX_ROUNDS) {
            val next = runSuspendCatching { pending.notifyDue() }.getOrNull() ?: return EXIT_OK
            setup.pause((next - now()).coerceAtLeast(MIN_PAUSE_MILLIS))
        }
        return EXIT_OK
    }

    private fun pendingNotifications(setup: NotificationSetup): PendingAgentNotifications =
        PendingAgentNotifications(
            store = setup.pending,
            sessions = sessions,
            sessionState = ReadAgentActivityUseCase(sessions, isAlive = isClaudeAlive)::stateOf,
            herdrAgents = herdr::listAgents,
            runners = setup.runners,
            notifier = setup.notifier,
            images = setup.images,
            preferences = setup.preferences::load,
            language = setup.language::load,
            appRunning = appLock::isHeldByAnyInstance,
            now = now,
        )

    private companion object {
        const val EXIT_OK = 0
        const val MAX_ROUNDS = 30
        const val MIN_PAUSE_MILLIS = 250L
    }
}
