package app.luxion.shogunai.ui.notifications

import app.luxion.shogunai.domain.FakeNotificationPreferenceRepository
import app.luxion.shogunai.domain.FakeRunnerNotificationSource
import app.luxion.shogunai.domain.RecordingSystemNotifier
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.RunnerNotifications
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.usecase.AgentNotificationTracker
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentNotificationWatchTest {
    private val login = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")
    private val repository = FakeNotificationPreferenceRepository()
    private val runners = FakeRunnerNotificationSource()
    private val notifier = RecordingSystemNotifier()
    private var clock = 1_000L
    private var language = AppLanguage.ENGLISH

    private fun TestScope.watch() = AgentNotificationWatch(repository, runners, notifier, { language }, backgroundScope, now = { clock })

    private fun claude(state: AgentState, source: AgentStateSource? = null, pane: String? = null) =
        mapOf(login.worktreePath to listOf(RunningAgent("claude", state, herdrPaneId = pane, stateSource = source)))

    @Test
    fun `a confirmed change shows one notification whose click opens the worktree`() = runTest {
        val watch = watch()
        val clicks = mutableListOf<AppRequest>()
        watch.onClick = { clicks += it }

        assertNull(watch.observe(listOf(login), claude(AgentState.WORKING)))
        assertEquals(1_000 + AgentNotificationTracker.GRACE_MILLIS, watch.observe(listOf(login), claude(AgentState.BLOCKED)))
        clock += AgentNotificationTracker.GRACE_MILLIS
        assertNull(watch.observe(listOf(login), claude(AgentState.BLOCKED)))
        runCurrent()

        val shown = notifier.shown.single()
        assertEquals("Claude needs you", shown.title)
        assertEquals("feature/login · api", shown.body)
        assertEquals(AppRequest.openWorktree(0, "api", login.worktreePath), shown.click)
        clock = 50_000
        notifier.click()
        assertEquals(listOf(AppRequest.openWorktree(50_000, "api", login.worktreePath)), clicks)
    }

    @Test
    fun `the notification carries the image drawn for it`() = runTest {
        val images = object : NotificationImages {
            override fun imageFor(notification: AgentNotification) = "/home/dev/.shogunai/notifications/images/needs-you-c.png"
        }
        val watch = AgentNotificationWatch(repository, runners, notifier, { language }, backgroundScope, now = { clock }, images = images)

        watch.observe(listOf(login), claude(AgentState.WORKING))
        watch.observe(listOf(login), claude(AgentState.BLOCKED))
        clock += AgentNotificationTracker.GRACE_MILLIS
        watch.observe(listOf(login), claude(AgentState.BLOCKED))
        runCurrent()

        assertEquals("/home/dev/.shogunai/notifications/images/needs-you-c.png", notifier.shown.single().image)
    }

    @Test
    fun `the text follows the app's language`() = runTest {
        val watch = watch()
        language = AppLanguage.SPANISH
        repository.stored = NotificationPreferences(done = true)
        watch.update(NotificationPreferences(blocked = true, done = true))

        watch.observe(listOf(login), claude(AgentState.WORKING))
        watch.observe(listOf(login), claude(AgentState.DONE))
        clock += AgentNotificationTracker.GRACE_MILLIS
        watch.observe(listOf(login), claude(AgentState.DONE))
        runCurrent()

        assertEquals("Claude ha terminado", notifier.shown.single().title)
    }

    @Test
    fun `the runners' settings are only read while a change is pending`() = runTest {
        val watch = watch()
        runners.runners = RunnerNotifications(herdr = RunnerNotifications.STOPPED_STATES)

        watch.observe(listOf(login), claude(AgentState.WORKING, AgentStateSource.HERDR, "w1:p1"))
        watch.observe(listOf(login), claude(AgentState.BLOCKED, AgentStateSource.HERDR, "w1:p1"))
        assertEquals(0, runners.reads)
        clock += AgentNotificationTracker.GRACE_MILLIS
        watch.observe(listOf(login), claude(AgentState.BLOCKED, AgentStateSource.HERDR, "w1:p1"))
        runCurrent()

        assertEquals(1, runners.reads)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `the switches are loaded, saved and decide whether it watches`() = runTest {
        repository.stored = NotificationPreferences(blocked = false, done = false)
        val watch = watch()
        assertFalse(watch.isWatching)

        watch.update(NotificationPreferences(blocked = false, done = true))

        assertTrue(watch.isWatching)
        assertEquals(NotificationPreferences(blocked = false, done = true), repository.stored)
        assertEquals(NotificationPreferences(blocked = false, done = true), watch.preferences)
    }

    @Test
    fun `no observer watches nothing`() = runTest {
        assertFalse(AgentObserver.NONE.isWatching)
        assertNull(AgentObserver.NONE.observe(listOf(login), claude(AgentState.BLOCKED)))
    }

    @Test
    fun `the default click handler does nothing`() = runTest {
        val watch = watch()
        watch.observe(listOf(login), claude(AgentState.WORKING))
        watch.observe(listOf(login), claude(AgentState.BLOCKED))
        clock += AgentNotificationTracker.GRACE_MILLIS
        watch.observe(listOf(login), claude(AgentState.BLOCKED))
        runCurrent()

        notifier.click()
    }
}
