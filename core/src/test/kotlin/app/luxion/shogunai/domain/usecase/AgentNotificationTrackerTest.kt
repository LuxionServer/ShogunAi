package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentKey
import app.luxion.shogunai.domain.model.AgentRunner
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.NotificationItem
import app.luxion.shogunai.domain.model.NotificationPolicy
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.ObservedAgent
import app.luxion.shogunai.domain.model.RunnerNotifications
import app.luxion.shogunai.domain.model.TrackerState
import app.luxion.shogunai.domain.model.TrackerStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentNotificationTrackerTest {
    private val login = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")
    private val search = NotifiedWorktree("web", "web", "/home/dev/projects/TASK-2", "feature/search")
    private val scope = setOf(login.worktreePath, search.worktreePath)
    private val bothOn = NotificationPolicy(NotificationPreferences(blocked = true, done = true))
    private val grace = AgentNotificationTracker.GRACE_MILLIS

    private fun agent(
        state: AgentState?,
        worktree: NotifiedWorktree = login,
        name: String = "claude",
        runner: AgentRunner? = null,
        focused: Boolean = false,
    ) = ObservedAgent(worktree, name, name.replaceFirstChar { it.uppercase() }, state, runner, focused)

    private fun step(
        state: TrackerState,
        now: Long,
        vararg agents: ObservedAgent,
        policy: NotificationPolicy = bothOn,
        scope: Set<String> = this.scope,
    ): TrackerStep = AgentNotificationTracker.advance(state, agents.toList(), scope, now, policy)

    private fun working(vararg agents: ObservedAgent = arrayOf(agent(AgentState.WORKING))): TrackerState =
        step(TrackerState(), 0, *agents).state

    @Test
    fun `an agent seen for the first time only records its state`() {
        val first = step(TrackerState(), 0, agent(AgentState.BLOCKED))
        val later = step(first.state, grace * 2, agent(AgentState.BLOCKED))

        assertNull(first.notification)
        assertNull(later.notification)
        assertEquals(mapOf(AgentKey(login.worktreePath, "claude") to AgentState.BLOCKED), later.state.known)
    }

    @Test
    fun `working to blocked waits for the grace period and then notifies once`() {
        val changed = step(working(), 1_000, agent(AgentState.BLOCKED))
        val early = step(changed.state, 1_000 + grace - 1, agent(AgentState.BLOCKED))
        val confirmed = step(early.state, 1_000 + grace, agent(AgentState.BLOCKED))
        val again = step(confirmed.state, 1_000 + grace * 3, agent(AgentState.BLOCKED))

        assertNull(changed.notification)
        assertEquals(1_000 + grace, changed.nextCheckAtMillis)
        assertNull(early.notification)
        assertEquals(1_000 + grace, early.nextCheckAtMillis)
        assertEquals(listOf(NotificationItem(login, "Claude", AgentState.BLOCKED)), confirmed.notification?.items)
        assertNull(confirmed.nextCheckAtMillis)
        assertNull(again.notification)
    }

    @Test
    fun `an agent working again within the grace period is dropped`() {
        val changed = step(working(), 1_000, agent(AgentState.DONE))
        val resumed = step(changed.state, 3_000, agent(AgentState.WORKING))
        val later = step(resumed.state, 1_000 + grace * 2, agent(AgentState.WORKING))

        assertNull(resumed.notification)
        assertNull(resumed.nextCheckAtMillis)
        assertNull(later.notification)
    }

    @Test
    fun `an agent that vanishes within the grace period is dropped`() {
        val changed = step(working(), 1_000, agent(AgentState.BLOCKED))
        val gone = step(changed.state, 1_000 + grace)

        assertNull(gone.notification)
        assertTrue(gone.state.pendingSince.isEmpty())
        assertTrue(gone.state.known.isEmpty())
    }

    @Test
    fun `blocked to done while pending keeps the original time and notifies the latest state`() {
        val changed = step(working(), 1_000, agent(AgentState.BLOCKED))
        val done = step(changed.state, 4_000, agent(AgentState.DONE))
        val confirmed = step(done.state, 1_000 + grace, agent(AgentState.DONE))

        assertEquals(1_000 + grace, done.nextCheckAtMillis)
        assertEquals(listOf(NotificationItem(login, "Claude", AgentState.DONE)), confirmed.notification?.items)
    }

    @Test
    fun `changes that don't start from working never notify`() {
        listOf(
            AgentState.BLOCKED to AgentState.DONE,
            AgentState.DONE to AgentState.BLOCKED,
            AgentState.IDLE to AgentState.DONE,
            AgentState.WORKING to AgentState.IDLE,
        ).forEach { (from, to) ->
            val start = step(TrackerState(), 0, agent(from)).state
            val changed = step(start, 1_000, agent(to))
            val later = step(changed.state, 1_000 + grace, agent(to))

            assertNull(changed.nextCheckAtMillis, "$from to $to")
            assertNull(later.notification, "$from to $to")
        }
    }

    @Test
    fun `an agent without a known state is forgotten`() {
        val unknown = step(working(), 1_000, agent(null))
        val blocked = step(unknown.state, 2_000, agent(AgentState.BLOCKED))

        assertTrue(unknown.state.known.isEmpty())
        assertNull(blocked.nextCheckAtMillis)
    }

    @Test
    fun `a focused agent is confirmed silently`() {
        val changed = step(working(), 1_000, agent(AgentState.BLOCKED, focused = true))
        val confirmed = step(changed.state, 1_000 + grace, agent(AgentState.BLOCKED, focused = true))

        assertNull(confirmed.notification)
        assertTrue(confirmed.state.pendingSince.isEmpty())
    }

    @Test
    fun `the switches decide which states notify`() {
        val defaults = NotificationPolicy()
        val blocked = step(working(), 1_000, agent(AgentState.BLOCKED), policy = defaults)
        val done = step(working(), 1_000, agent(AgentState.DONE), policy = defaults)

        assertEquals(1, step(blocked.state, 1_000 + grace, agent(AgentState.BLOCKED), policy = defaults).notification?.items?.size)
        assertNull(step(done.state, 1_000 + grace, agent(AgentState.DONE), policy = defaults).notification)
    }

    @Test
    fun `a runner that notifies a state by itself silences that state only`() {
        val orcaNotifiesDone = bothOn.copy(runners = RunnerNotifications(orca = setOf(AgentState.DONE)))
        val herdrNotifiesBoth = bothOn.copy(runners = RunnerNotifications(herdr = RunnerNotifications.STOPPED_STATES))
        fun confirm(to: AgentState, runner: AgentRunner?, policy: NotificationPolicy) =
            step(step(working(), 1_000, agent(to, runner = runner)).state, 1_000 + grace, agent(to, runner = runner), policy = policy).notification

        assertNull(confirm(AgentState.DONE, AgentRunner.ORCA, orcaNotifiesDone))
        assertEquals(1, confirm(AgentState.BLOCKED, AgentRunner.ORCA, orcaNotifiesDone)?.items?.size)
        assertNull(confirm(AgentState.BLOCKED, AgentRunner.HERDR, herdrNotifiesBoth))
        assertEquals(1, confirm(AgentState.BLOCKED, null, herdrNotifiesBoth)?.items?.size)
    }

    @Test
    fun `changes confirmed together make one notification with blocked agents first`() {
        val start = working(agent(AgentState.WORKING), agent(AgentState.WORKING, search), agent(AgentState.WORKING, search, "codex"))
        val changed = step(start, 1_000, agent(AgentState.DONE), agent(AgentState.BLOCKED, search), agent(AgentState.BLOCKED, search, "codex"))
        val confirmed = step(changed.state, 1_000 + grace, agent(AgentState.DONE), agent(AgentState.BLOCKED, search), agent(AgentState.BLOCKED, search, "codex"))

        val notification = confirmed.notification!!
        assertEquals(
            listOf(
                NotificationItem(search, "Claude", AgentState.BLOCKED),
                NotificationItem(search, "Codex", AgentState.BLOCKED),
                NotificationItem(login, "Claude", AgentState.DONE),
            ),
            notification.items,
        )
        assertEquals(search, notification.target)
        assertEquals(2, notification.blockedCount)
        assertEquals(1, notification.doneCount)
    }

    @Test
    fun `the next check is the earliest pending one`() {
        val start = working(agent(AgentState.WORKING), agent(AgentState.WORKING, search))
        val first = step(start, 1_000, agent(AgentState.BLOCKED), agent(AgentState.WORKING, search))
        val second = step(first.state, 5_000, agent(AgentState.BLOCKED), agent(AgentState.BLOCKED, search))
        val one = step(second.state, 1_000 + grace, agent(AgentState.BLOCKED), agent(AgentState.BLOCKED, search))

        assertEquals(1_000 + grace, second.nextCheckAtMillis)
        assertEquals(listOf(login), one.notification?.items?.map { it.worktree })
        assertEquals(5_000 + grace, one.nextCheckAtMillis)
    }

    @Test
    fun `worktrees outside the observed scope keep what was known and pending`() {
        val start = working(agent(AgentState.WORKING), agent(AgentState.WORKING, search))
        val changed = step(start, 1_000, agent(AgentState.BLOCKED), agent(AgentState.WORKING, search))
        val partial = step(changed.state, 2_000, agent(AgentState.BLOCKED, search), scope = setOf(search.worktreePath))
        val confirmed = step(partial.state, 1_000 + grace, agent(AgentState.BLOCKED), agent(AgentState.BLOCKED, search))

        assertEquals(AgentState.BLOCKED, partial.state.known[AgentKey(search.worktreePath, "claude")])
        assertEquals(1_000L, partial.state.pendingSince[AgentKey(login.worktreePath, "claude")])
        assertEquals(listOf(login), confirmed.notification?.items?.map { it.worktree })
        assertEquals(2_000L, confirmed.state.pendingSince[AgentKey(search.worktreePath, "claude")])
    }

    @Test
    fun `agents reported outside the scope are ignored`() {
        val changed = step(working(), 1_000, agent(AgentState.BLOCKED), scope = setOf(search.worktreePath))

        assertEquals(AgentState.WORKING, changed.state.known[AgentKey(login.worktreePath, "claude")])
        assertNull(changed.nextCheckAtMillis)
    }
}
