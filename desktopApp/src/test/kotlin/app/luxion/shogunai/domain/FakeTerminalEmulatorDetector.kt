package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.TerminalEmulatorDetector
import app.luxion.shogunai.domain.model.TerminalEmulator

/** Doble de [TerminalEmulatorDetector] para tests: devuelve una lista fija de [available]. */
class FakeTerminalEmulatorDetector(
    private val available: List<TerminalEmulator> = emptyList(),
) : TerminalEmulatorDetector {

    override suspend fun detectAvailable(): List<TerminalEmulator> = available
}
