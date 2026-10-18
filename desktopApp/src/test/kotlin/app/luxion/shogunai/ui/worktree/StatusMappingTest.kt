package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.theme.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusMappingTest {

    @Test
    fun `row indicators follow the status table`() {
        assertEquals(
            mapOf(
                RowIndicator.FOLDER_MISSING to Status.DANGER,
                RowIndicator.AGENT_RUNNING to Status.INFO,
                RowIndicator.UNCOMMITTED_CHANGES to Status.WARNING,
                RowIndicator.MERGED to Status.SUCCESS,
                RowIndicator.INACTIVE to Status.NEUTRAL,
            ),
            RowIndicator.entries.associateWith { it.status },
        )
    }

    @Test
    fun `a running device is a success and a booting one is in progress`() {
        assertEquals(
            listOf(Status.SUCCESS, Status.INFO, Status.NEUTRAL),
            listOf(EmulatorState.RUNNING, EmulatorState.BOOTING, EmulatorState.STOPPED).map { it.status() },
        )
        assertEquals(
            listOf(Status.SUCCESS, Status.INFO, Status.NEUTRAL),
            listOf(SimulatorState.BOOTED, SimulatorState.BOOTING, SimulatorState.SHUTDOWN).map { it.status() },
        )
    }

    @Test
    fun `device state labels are translated`() {
        assertEquals(
            listOf("stopped", "booting", "running"),
            listOf(EmulatorState.STOPPED, EmulatorState.BOOTING, EmulatorState.RUNNING).map { emulatorStateLabel(it, EnglishStrings) },
        )
        assertEquals(
            listOf("shut down", "booting", "booted"),
            listOf(SimulatorState.SHUTDOWN, SimulatorState.BOOTING, SimulatorState.BOOTED).map { simulatorStateLabel(it, EnglishStrings) },
        )
    }
}
