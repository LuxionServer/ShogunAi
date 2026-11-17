package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentRunner
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunnerNotifications
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedRunnerNotificationsTest {

    @Test
    fun `the runners' settings are read once a minute`() = runTest {
        var clock = 0L
        var herdr = true
        var orcaReads = 0
        val source = CachedRunnerNotifications(
            herdrNotifiesOnDesktop = { herdr },
            orcaNotifiedStates = {
                orcaReads++
                setOf(AgentState.DONE)
            },
            now = { clock },
        )

        assertEquals(RunnerNotifications(herdr = RunnerNotifications.STOPPED_STATES, orca = setOf(AgentState.DONE)), source.read())
        herdr = false
        clock = 59_999
        assertEquals(RunnerNotifications.STOPPED_STATES, source.read().herdr)
        clock = 60_000
        assertEquals(RunnerNotifications(orca = setOf(AgentState.DONE)), source.read())
        assertEquals(2, orcaReads)
    }

    @Test
    fun `a runner notifies only the states it was set to`() {
        val runners = RunnerNotifications(herdr = setOf(AgentState.BLOCKED), orca = setOf(AgentState.DONE))

        assertEquals(true, runners.notifies(AgentRunner.HERDR, AgentState.BLOCKED))
        assertEquals(false, runners.notifies(AgentRunner.HERDR, AgentState.DONE))
        assertEquals(true, runners.notifies(AgentRunner.ORCA, AgentState.DONE))
        assertEquals(false, runners.notifies(AgentRunner.ORCA, AgentState.BLOCKED))
        assertEquals(false, runners.notifies(null, AgentState.DONE))
    }
}
