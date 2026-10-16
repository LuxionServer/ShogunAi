package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.emulatorSerial

class FakeAndroidSdk(
    var avds: List<String> = listOf("Medium_Phone_API_35"),
    var states: MutableMap<String, EmulatorState> = mutableMapOf(),
    var missing: Boolean = false,
    private val bootAfterPolls: Int = 1,
) : AndroidSdk {

    data class Started(val avd: String, val consolePort: Int, val noWindow: Boolean)

    val started = mutableListOf<Started>()
    val stopped = mutableListOf<String>()
    var locates = 0
        private set
    var stateQueries = 0
        private set
    private var pollsSinceStart = 0

    private val location = SdkLocation("/home/dev/Android/sdk", "/home/dev/Android/sdk/emulator/emulator", "/home/dev/Android/sdk/platform-tools/adb")

    override fun locate(repositoryPath: String): SdkLocation {
        locates++
        if (missing) throw WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME", "/home/dev/Android/sdk"))
        return location
    }

    override suspend fun listAvds(sdk: SdkLocation): List<String> = avds

    override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> {
        stateQueries++
        val booting = states.filterValues { it == EmulatorState.BOOTING }.keys
        if (booting.isNotEmpty() && ++pollsSinceStart > bootAfterPolls) {
            booting.forEach { states[it] = EmulatorState.RUNNING }
        }
        return states.toMap()
    }

    override fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean) {
        started += Started(avd, consolePort, noWindow)
        states[emulatorSerial(consolePort)] = EmulatorState.BOOTING
        pollsSinceStart = 0
    }

    override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
        stopped += serial
        states.remove(serial)
    }
}
