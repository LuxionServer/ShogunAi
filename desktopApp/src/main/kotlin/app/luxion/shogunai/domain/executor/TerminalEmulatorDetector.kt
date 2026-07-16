package app.luxion.shogunai.domain.executor

import app.luxion.shogunai.domain.model.TerminalEmulator

/** Domain port to detect which terminal emulators are installed. */
interface TerminalEmulatorDetector {

    /** Emulators available on the current system, in priority order. */
    suspend fun detectAvailable(): List<TerminalEmulator>
}
