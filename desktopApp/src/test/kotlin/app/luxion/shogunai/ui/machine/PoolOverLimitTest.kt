package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoolOverLimitTest {

    private fun daemon(state: PoolHolderState) = PoolHolder(
        kind = ResourceKind.GRADLE_DAEMON,
        worktreePath = null,
        adminDirectory = null,
        state = state,
        idle = state == PoolHolderState.IDLE,
        lastUsed = null,
    )

    private fun gradlePool(limit: Int, vararg states: PoolHolderState) =
        PoolUsage(ResourceKind.GRADLE_DAEMON, limit, limit, states.map(::daemon))

    @Test
    fun `busy and unknown daemons over the limit are counted`() {
        val pool = gradlePool(3, PoolHolderState.BUSY, PoolHolderState.BUSY, PoolHolderState.BUSY, PoolHolderState.BUSY, PoolHolderState.UNKNOWN)

        assertEquals(BuildsHoldingPool(busy = 4, unknown = 1), buildsHoldingPool(pool))
    }

    @Test
    fun `only busy daemons over the limit`() {
        assertEquals(BuildsHoldingPool(busy = 3, unknown = 0), buildsHoldingPool(gradlePool(2, PoolHolderState.BUSY, PoolHolderState.BUSY, PoolHolderState.BUSY)))
    }

    @Test
    fun `an idle daemon over the limit can still be trimmed`() {
        assertNull(buildsHoldingPool(gradlePool(2, PoolHolderState.BUSY, PoolHolderState.BUSY, PoolHolderState.IDLE)))
    }

    @Test
    fun `within the limit nothing is explained`() {
        assertNull(buildsHoldingPool(gradlePool(3, PoolHolderState.BUSY, PoolHolderState.BUSY)))
    }

    @Test
    fun `only the gradle pool is explained`() {
        val emulators = PoolUsage(ResourceKind.EMULATOR, 1, 1, List(2) { daemon(PoolHolderState.RUNNING).copy(kind = ResourceKind.EMULATOR) })

        assertNull(buildsHoldingPool(emulators))
    }
}
