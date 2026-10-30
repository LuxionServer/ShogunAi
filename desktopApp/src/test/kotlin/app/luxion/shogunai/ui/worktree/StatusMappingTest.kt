package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.theme.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusMappingTest {

    @Test
    fun `state badges follow the status table`() {
        assertEquals(
            mapOf(
                StateBadgeKind.WAITING_FOR_YOU to Status.WARNING,
                StateBadgeKind.AGENT_WORKING to Status.INFO,
                StateBadgeKind.UNFINISHED to Status.NEUTRAL,
                StateBadgeKind.NEW to Status.NEUTRAL,
                StateBadgeKind.STOPPED to Status.NEUTRAL,
                StateBadgeKind.MERGED to Status.SUCCESS,
                StateBadgeKind.INACTIVE to Status.NEUTRAL,
                StateBadgeKind.FOLDER_MISSING to Status.DANGER,
                StateBadgeKind.OTHER to Status.NEUTRAL,
            ),
            StateBadgeKind.entries.associateWith { it.status },
        )
    }

    @Test
    fun `only the new state is drawn outlined`() {
        assertEquals(listOf(StateBadgeKind.NEW), StateBadgeKind.entries.filter { it.outlined })
    }

    @Test
    fun `every state badge has its own icon`() {
        assertEquals(StateBadgeKind.entries.size, StateBadgeKind.entries.map { it.icon() }.toSet().size)
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
    fun `device state labels are translated in sentence case`() {
        assertEquals(
            listOf("Stopped", "Booting", "Running"),
            listOf(EmulatorState.STOPPED, EmulatorState.BOOTING, EmulatorState.RUNNING).map { emulatorStateLabel(it, EnglishStrings) },
        )
        assertEquals(
            listOf("Shut down", "Booting", "Booted"),
            listOf(SimulatorState.SHUTDOWN, SimulatorState.BOOTING, SimulatorState.BOOTED).map { simulatorStateLabel(it, EnglishStrings) },
        )
        assertEquals(
            listOf("Parado", "Arrancando", "En marcha"),
            listOf(EmulatorState.STOPPED, EmulatorState.BOOTING, EmulatorState.RUNNING).map { emulatorStateLabel(it, SpanishStrings) },
        )
    }
}
