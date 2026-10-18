package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.Status

fun EmulatorState.status(): Status = when (this) {
    EmulatorState.RUNNING -> Status.SUCCESS
    EmulatorState.BOOTING -> Status.INFO
    EmulatorState.STOPPED -> Status.NEUTRAL
}

fun SimulatorState.status(): Status = when (this) {
    SimulatorState.BOOTED -> Status.SUCCESS
    SimulatorState.BOOTING -> Status.INFO
    SimulatorState.SHUTDOWN -> Status.NEUTRAL
}

fun emulatorStateLabel(state: EmulatorState, strings: Strings): String = when (state) {
    EmulatorState.STOPPED -> strings.emulatorStopped
    EmulatorState.BOOTING -> strings.emulatorBooting
    EmulatorState.RUNNING -> strings.emulatorRunning
}

fun simulatorStateLabel(state: SimulatorState, strings: Strings): String = when (state) {
    SimulatorState.SHUTDOWN -> strings.simulatorShutdown
    SimulatorState.BOOTING -> strings.simulatorBooting
    SimulatorState.BOOTED -> strings.simulatorBooted
}
