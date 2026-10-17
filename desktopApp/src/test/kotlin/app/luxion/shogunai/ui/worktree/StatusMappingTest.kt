package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.ui.theme.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusMappingTest {

    @Test
    fun `worktree indicators follow the status table`() {
        assertEquals(
            mapOf(
                WorktreeIndicator.MERGED to Status.SUCCESS,
                WorktreeIndicator.UNCOMMITTED_CHANGES to Status.WARNING,
                WorktreeIndicator.INACTIVE to Status.WARNING,
                WorktreeIndicator.AGENT_RUNNING to Status.INFO,
                WorktreeIndicator.FOLDER_MISSING to Status.DANGER,
                WorktreeIndicator.LOCKED to Status.NEUTRAL,
            ),
            WorktreeIndicator.entries.associateWith { it.status() },
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
}
