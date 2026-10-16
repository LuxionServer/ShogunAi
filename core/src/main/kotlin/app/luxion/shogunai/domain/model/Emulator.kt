package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EmulatorSettings(
    val avd: String? = null,
    val noWindow: Boolean = false,
)

data class SdkLocation(val root: String, val emulator: String, val adb: String)

enum class EmulatorState { STOPPED, BOOTING, RUNNING }

data class WorktreeEmulator(val serial: String, val state: EmulatorState)

const val EMULATOR_PORT_KEY = "emulator"
const val FIRST_EMULATOR_PORT = 5554
const val LAST_EMULATOR_PORT = 5584
const val ANDROID_SERIAL = "ANDROID_SERIAL"

fun emulatorSerial(consolePort: Int): String = "emulator-$consolePort"

fun chooseAvd(requested: String?, settings: EmulatorSettings, preferred: String?, available: List<String>): String? =
    requested?.takeIf { it.isNotBlank() }
        ?: settings.avd?.takeIf { it.isNotBlank() }
        ?: preferred?.takeIf { it in available }
        ?: available.firstOrNull()
