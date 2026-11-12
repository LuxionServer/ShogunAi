package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation

interface AndroidSdk {

    fun locate(repositoryPath: String): SdkLocation

    suspend fun listAvds(sdk: SdkLocation): List<String>

    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> = emulatorStates(sdk, attachedDevices(sdk))

    suspend fun attachedDevices(sdk: SdkLocation): Map<String, String>

    suspend fun emulatorStates(sdk: SdkLocation, attached: Map<String, String>): Map<String, EmulatorState>

    suspend fun deviceProperties(sdk: SdkLocation, serial: String): Map<String, String>?

    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess

    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}

interface EmulatorProcess {

    val isAlive: Boolean

    fun lastLogLines(count: Int): String
}
