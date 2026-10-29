package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RunnerTest {

    @Test
    fun `nothing chosen with both installed uses Herdr`() {
        assertEquals(RunnerId.HERDR, runnerInUse(null) { true })
    }

    @Test
    fun `nothing chosen with only Orca installed uses Orca`() {
        assertEquals(RunnerId.ORCA, runnerInUse(null) { it == RunnerId.ORCA })
    }

    @Test
    fun `the chosen runner is used when it's installed`() {
        assertEquals(RunnerId.ORCA, runnerInUse(RunnerId.ORCA) { true })
    }

    @Test
    fun `an uninstalled choice falls back to the installed runner`() {
        assertEquals(RunnerId.HERDR, runnerInUse(RunnerId.ORCA) { it == RunnerId.HERDR })
    }

    @Test
    fun `with nothing installed the choice is kept, and Herdr without one`() {
        assertEquals(RunnerId.ORCA, runnerInUse(RunnerId.ORCA) { false })
        assertEquals(RunnerId.HERDR, runnerInUse(null) { false })
    }

    @Test
    fun `the runner in use comes first`() {
        assertEquals(listOf(RunnerId.ORCA, RunnerId.HERDR), runnersInUseOrder(RunnerId.ORCA))
        assertEquals(listOf(RunnerId.HERDR, RunnerId.ORCA), runnersInUseOrder(RunnerId.HERDR))
    }
}
