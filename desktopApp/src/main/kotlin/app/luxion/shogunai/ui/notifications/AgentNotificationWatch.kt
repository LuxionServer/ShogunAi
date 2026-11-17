package app.luxion.shogunai.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.NoNotificationImages
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.io.NotificationPreferenceRepository
import app.luxion.shogunai.domain.io.NotificationServices
import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentNotificationText
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.NotificationPolicy
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.RunnerNotifications
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.TrackerState
import app.luxion.shogunai.domain.usecase.AgentNotificationTracker
import app.luxion.shogunai.domain.usecase.observedAgents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AgentObserver {
    val isWatching: Boolean
    suspend fun observe(worktrees: List<NotifiedWorktree>, detected: Map<String, List<RunningAgent>>): Long?

    companion object {
        val NONE: AgentObserver = object : AgentObserver {
            override val isWatching: Boolean = false
            override suspend fun observe(worktrees: List<NotifiedWorktree>, detected: Map<String, List<RunningAgent>>): Long? = null
        }
    }
}

class AgentNotificationWatch(
    private val repository: NotificationPreferenceRepository,
    private val runners: RunnerNotificationSource,
    private val notifier: SystemNotifier,
    private val language: () -> AppLanguage,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val images: NotificationImages = NoNotificationImages,
) : AgentObserver {
    constructor(services: NotificationServices, language: () -> AppLanguage, scope: CoroutineScope) :
        this(services.preferences, services.runners, services.notifier, language, scope, images = services.images) {
        onClick = { request -> runCatching { services.requests.send(request) } }
    }

    private val mutex = Mutex()
    private var tracker = TrackerState()
    var onClick: (AppRequest) -> Unit = {}
    var preferences by mutableStateOf(repository.load())
        private set

    override val isWatching: Boolean get() = preferences.anyOn

    fun update(preferences: NotificationPreferences) {
        repository.save(preferences)
        this.preferences = preferences
    }

    override suspend fun observe(worktrees: List<NotifiedWorktree>, detected: Map<String, List<RunningAgent>>): Long? = mutex.withLock {
        val runnerSettings = if (tracker.pendingSince.isEmpty()) RunnerNotifications() else runners.read()
        val step = AgentNotificationTracker.advance(
            state = tracker,
            observed = observedAgents(worktrees, detected),
            scope = worktrees.map { it.worktreePath }.toSet(),
            nowMillis = now(),
            policy = NotificationPolicy(preferences, runnerSettings),
        )
        tracker = step.state
        step.notification?.let(::show)
        step.nextCheckAtMillis
    }

    private fun show(notification: AgentNotification) {
        val text = AgentNotificationText.of(notification, language())
        val target = notification.target
        val request = AppRequest.openWorktree(createdAt = 0, projectId = target.projectId, worktreePath = target.worktreePath)
        scope.launch {
            notifier.show(SystemNotification(text.title, text.body, text.openLabel, request, images.imageFor(notification))) {
                onClick(request.copy(createdAt = now()))
            }
        }
    }
}
