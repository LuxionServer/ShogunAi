package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation

interface AndroidSdk {

    fun locate(repositoryPath: String): SdkLocation

    suspend fun listAvds(sdk: SdkLocation): List<String>

    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>

    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess

    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}

interface EmulatorProcess {

    val isAlive: Boolean

    fun lastLogLines(count: Int): String
}
