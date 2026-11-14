package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolAcquisition
import app.luxion.shogunai.domain.model.CustomPoolHolder
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolRelease
import app.luxion.shogunai.domain.model.CustomPoolState
import app.luxion.shogunai.domain.model.CustomPoolWaitProgress
import app.luxion.shogunai.domain.model.parsePoolTemplate
import app.luxion.shogunai.domain.model.toLocalClockTime
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomPoolTextTest {

    private val pool = CustomPool("db", 2, listOf(parsePoolTemplate("up")))
    private val lease = CustomPoolLease("/r/.git", "/r", "db", 1, "/r/.git/worktrees/TASK-1", "/w/TASK-1", 0L, 0L)

    @Test
    fun `an acquisition taken from another worktree says so`() {
        val acquisition = CustomPoolAcquisition("db", 1, Instant.EPOCH, renewed = false, takenFrom = "r · TASK-2", waitedSeconds = null, variables = emptyList())

        assertEquals(listOf("acquired slot 1 of pool db, lease until ${Instant.EPOCH.toLocalClockTime()}, taken from r · TASK-2"), poolAcquisitionLines(acquisition))
    }

    @Test
    fun `progress without holders names the slots and no expiry`() {
        assertEquals(
            "waiting for pool db: 2nd of 2 in line for 2 slots; no holders",
            poolWaitProgressLine(CustomPoolWaitProgress("db", 2, 2, 2, emptyList(), null)),
        )
    }

    @Test
    fun `a holder whose agent works says so`() {
        val state = CustomPoolState(pool, listOf(CustomPoolHolder(lease, Instant.EPOCH, AgentState.WORKING, running = false)), emptyList(), emptyList())

        assertEquals(listOf("pool db 1/2", "  slot 1 r · TASK-1 agent working"), customPoolStatusLines(listOf(state)))
    }

    @Test
    fun `releasing nothing reads as not released in json`() {
        assertEquals("""{"pool":"db","slot":null,"released":false}""", poolReleaseJson(CustomPoolRelease("db", null)).toString())
    }
}
