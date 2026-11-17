package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeRunnerNotificationSource
import app.luxion.shogunai.domain.RecordingSystemNotifier
import app.luxion.shogunai.domain.io.NoNotificationImages
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.io.PendingNotificationStore
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.PendingSession
import app.luxion.shogunai.domain.model.RunnerNotifications
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingAgentNotificationsTest {
    private val login = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")
    private val search = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-2", "feature/search")
    private val grace = AgentNotificationTracker.GRACE_MILLIS

    private val stored = mutableListOf<PendingSession>()
    private val store = object : PendingNotificationStore {
        override fun <T> update(block: (List<PendingSession>) -> Pair<List<PendingSession>, T>): T {
            val (next, result) = block(stored.toList())
            stored.clear()
            stored += next
            return result
        }
    }
    private val sessions = FakeAgentSessionStore()
    private val notifier = RecordingSystemNotifier()
    private val runners = FakeRunnerNotificationSource()
    private var herdr: List<HerdrAgent> = emptyList()
    private var preferences = NotificationPreferences()
    private var appRunning = false
    private var clock = 1_000L

    private val pending = PendingAgentNotifications(
        store = store,
        sessions = sessions,
        sessionState = { it.state },
        herdrAgents = { herdr },
        runners = runners,
        notifier = notifier,
        images = object : NotificationImages {
            override fun imageFor(notification: AgentNotification) = "/home/dev/.shogunai/notifications/images/${notification.items.size}.png"
        },
        preferences = { preferences },
        language = { AppLanguage.ENGLISH },
        appRunning = { appRunning },
        now = { clock },
    )

    private fun record(worktree: NotifiedWorktree, id: String, state: AgentState) =
        sessions.write(worktree.worktreePath, AgentSessionRecord(id, "claude", state, clock, pid = 10))

    @Test
    fun `only a change from working to blocked or done is scheduled, with a switch on and the app closed`() {
        assertTrue(pending.shouldSchedule(AgentState.WORKING, AgentState.BLOCKED))
        assertTrue(pending.shouldSchedule(AgentState.WORKING, AgentState.DONE))
        assertFalse(pending.shouldSchedule(AgentState.BLOCKED, AgentState.DONE))
        assertFalse(pending.shouldSchedule(null, AgentState.BLOCKED))
        assertFalse(pending.shouldSchedule(AgentState.WORKING, AgentState.WORKING))
        assertFalse(pending.shouldSchedule(AgentState.WORKING, null))
        preferences = NotificationPreferences(blocked = false, done = false)
        assertFalse(pending.shouldSchedule(AgentState.WORKING, AgentState.BLOCKED))
        preferences = NotificationPreferences()
        appRunning = true
        assertFalse(pending.shouldSchedule(AgentState.WORKING, AgentState.BLOCKED))
    }

    @Test
    fun `a session still blocked after the grace period is notified once, opening its worktree`() = runTest {
        record(login, "s1", AgentState.BLOCKED)
        pending.schedule("s1", login)

        assertEquals(1_000 + grace, pending.notifyDue())
        assertTrue(notifier.shown.isEmpty())
        clock += grace
        assertNull(pending.notifyDue())

        val shown = notifier.shown.single()
        assertEquals("Claude needs you", shown.title)
        assertEquals(AppRequest.openWorktree(0, "api", login.worktreePath), shown.click)
        assertEquals("/home/dev/.shogunai/notifications/images/1.png", shown.image)
        assertTrue(stored.isEmpty())
        assertNull(pending.notifyDue())
        notifier.click()
    }

    @Test
    fun `sessions due together make one notification and later ones stay pending`() = runTest {
        preferences = NotificationPreferences(blocked = true, done = true)
        record(login, "s1", AgentState.BLOCKED)
        record(search, "s2", AgentState.DONE)
        pending.schedule("s1", login)
        pending.schedule("s2", search)
        clock += 3_000
        record(login, "s3", AgentState.BLOCKED)
        pending.schedule("s3", login)
        clock = 1_000 + grace

        assertEquals(4_000 + grace, pending.notifyDue())

        assertEquals("1 agent needs you, 1 is done", notifier.shown.single().title)
        assertEquals(listOf("s3"), stored.map { it.sessionId })
    }

    @Test
    fun `the earliest pending session decides when to look again, wherever it is stored`() = runTest {
        stored += PendingSession("late", search, 5_000)
        stored += PendingSession("early", login, 2_000)

        assertEquals(2_000 + grace, pending.notifyDue())
    }

    @Test
    fun `scheduling a session again restarts its grace period`() {
        pending.schedule("s1", login)
        clock += 5_000
        pending.schedule("s1", login)

        assertEquals(listOf(PendingSession("s1", login, 6_000)), stored)
    }

    @Test
    fun `a session working again, ended or focused in Herdr is dropped silently`() = runTest {
        record(login, "s1", AgentState.WORKING)
        record(search, "s2", AgentState.BLOCKED)
        pending.schedule("s1", login)
        pending.schedule("s2", search)
        pending.schedule("gone", login)
        herdr = listOf(HerdrAgent("claude", AgentState.BLOCKED, "${search.worktreePath}/app", "w1:p1", focused = true))
        clock += grace

        assertNull(pending.notifyDue())

        assertTrue(notifier.shown.isEmpty())
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `an agent in Herdr is left to Herdr when it notifies by itself`() = runTest {
        runners.runners = RunnerNotifications(herdr = RunnerNotifications.STOPPED_STATES)
        record(login, "s1", AgentState.BLOCKED)
        pending.schedule("s1", login)
        herdr = listOf(HerdrAgent("claude", AgentState.BLOCKED, login.worktreePath, "w1:p1"), HerdrAgent("codex", AgentState.BLOCKED, search.worktreePath, "w2:p1"))
        clock += grace

        pending.notifyDue()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `an unreadable Herdr doesn't stop the notification`() = runTest {
        val failing = PendingAgentNotifications(
            store, sessions, { it.state }, { error("herdr down") }, runners, notifier, NoNotificationImages, { preferences }, { AppLanguage.ENGLISH }, { appRunning }, { clock },
        )
        record(login, "s1", AgentState.BLOCKED)
        failing.schedule("s1", login)
        clock += grace

        failing.notifyDue()

        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `when the app is running it clears what is pending and notifies nothing`() = runTest {
        record(login, "s1", AgentState.BLOCKED)
        pending.schedule("s1", login)
        appRunning = true
        clock += grace

        assertNull(pending.notifyDue())

        assertTrue(stored.isEmpty())
        assertTrue(notifier.shown.isEmpty())
    }
}
