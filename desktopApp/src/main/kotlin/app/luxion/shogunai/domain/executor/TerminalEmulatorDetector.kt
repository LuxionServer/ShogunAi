package app.luxion.shogunai.domain.executor

import app.luxion.shogunai.domain.model.TerminalEmulator

/** Puerto de dominio para detectar qué emuladores de terminal están instalados. */
interface TerminalEmulatorDetector {

    /** Emuladores disponibles en el sistema actual, en orden de prioridad. */
    suspend fun detectAvailable(): List<TerminalEmulator>
}
