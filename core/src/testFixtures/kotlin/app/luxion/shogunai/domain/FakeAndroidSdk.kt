package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.EmulatorProcess
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.emulatorSerial

class FakeAndroidSdk(
    var avds: List<String> = listOf("Medium_Phone_API_35"),
    var states: MutableMap<String, EmulatorState> = mutableMapOf(),
    var missing: Boolean = false,
    var stopFailure: Throwable? = null,
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

    class FakeEmulatorProcess(var alive: Boolean = true, var log: String = "") : EmulatorProcess {
        override val isAlive: Boolean get() = alive

        override fun lastLogLines(count: Int): String = log.lines().takeLast(count).joinToString("\n")
    }

    var process = FakeEmulatorProcess()

    var lockProbe: () -> Boolean = { false }
    val callsUnderLock = mutableListOf<String>()
    var afterListing: (suspend () -> Unit)? = null
    var onStop: (suspend (String) -> Unit)? = null

    private fun observe(call: String) {
        if (lockProbe()) callsUnderLock += call
    }

    private val location = SdkLocation("/home/dev/Android/sdk", "/home/dev/Android/sdk/emulator/emulator", "/home/dev/Android/sdk/platform-tools/adb")

    override fun locate(repositoryPath: String): SdkLocation {
        locates++
        if (missing) throw WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME", "/home/dev/Android/sdk"))
        return location
    }

    override suspend fun listAvds(sdk: SdkLocation): List<String> = avds

    var physical: MutableMap<String, String> = mutableMapOf()
    var properties: MutableMap<String, Map<String, String>> = mutableMapOf()
    val propertyQueries = mutableListOf<String>()
    var attachedQueries = 0
        private set

    override suspend fun attachedDevices(sdk: SdkLocation): Map<String, String> {
        attachedQueries++
        return states.mapValues { if (it.value == EmulatorState.RUNNING) "device" else "offline" } + physical
    }

    override suspend fun emulatorStates(sdk: SdkLocation, attached: Map<String, String>): Map<String, EmulatorState> = deviceStates(sdk)

    override suspend fun deviceProperties(sdk: SdkLocation, serial: String): Map<String, String>? {
        propertyQueries += serial
        return properties[serial]
    }

    override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> {
        stateQueries++
        observe("deviceStates")
        val booting = states.filterValues { it == EmulatorState.BOOTING }.keys
        if (booting.isNotEmpty() && ++pollsSinceStart > bootAfterPolls) {
            booting.forEach { states[it] = EmulatorState.RUNNING }
        }
        val listed = states.toMap()
        afterListing?.also { afterListing = null }?.invoke()
        return listed
    }

    override fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess {
        started += Started(avd, consolePort, noWindow)
        states[emulatorSerial(consolePort)] = EmulatorState.BOOTING
        pollsSinceStart = 0
        return process
    }

    override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
        observe("stopEmulator")
        onStop?.also { onStop = null }?.invoke(serial)
        stopFailure?.let { throw it }
        stopped += serial
        states.remove(serial)
    }
}
