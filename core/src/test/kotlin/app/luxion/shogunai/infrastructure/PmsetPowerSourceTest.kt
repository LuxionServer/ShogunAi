package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PmsetPowerSourceTest {

    private val onBattery = """
        Now drawing from 'Battery Power'
         -InternalBattery-0 (id=1234567)	87%; discharging; 4:12 remaining present: true
    """.trimIndent()

    private val onAc = """
        Now drawing from 'AC Power'
         -InternalBattery-0 (id=1234567)	100%; charged; 0:00 remaining present: true
    """.trimIndent()

    @Test
    fun `battery power is read from the first line`() = runTest {
        val executor = FakeShellCommandExecutor { command -> success(command, onBattery) }

        assertEquals(true, PmsetPowerSource(executor).isOnBattery())
        assertEquals(listOf(listOf("pmset", "-g", "batt")), executor.executedCommands)
    }

    @Test
    fun `ac power is not battery`() = runTest {
        assertEquals(false, PmsetPowerSource(FakeShellCommandExecutor { command -> success(command, onAc) }).isOnBattery())
    }

    @Test
    fun `other sources and empty output are unknown`() {
        assertNull(parsePowerSource("Now drawing from 'UPS Power'"))
        assertNull(parsePowerSource(""))
    }

    @Test
    fun `a failing pmset is unknown`() = runTest {
        assertNull(PmsetPowerSource(FakeShellCommandExecutor { command -> failure(command) }).isOnBattery())
    }
}
