package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SimulatorSettings(
    val device: String? = null,
    val runtime: String? = null,
)

data class SimulatorDeviceType(val identifier: String, val name: String, val productFamily: String)

data class SimulatorRuntime(
    val identifier: String,
    val name: String,
    val version: String,
    val platform: String,
    val supportedDeviceTypes: List<SimulatorDeviceType>,
)

data class SimulatorChoice(val runtime: SimulatorRuntime, val deviceType: SimulatorDeviceType)

enum class SimulatorState { SHUTDOWN, BOOTING, BOOTED }

data class SimulatorDevice(val udid: String, val name: String, val state: SimulatorState)

data class WorktreeSimulator(val udid: String, val name: String, val state: SimulatorState)

const val IOS_SIMULATOR_KEY = "ios-simulator"
const val IOS_SIMULATOR_UDID = "IOS_SIMULATOR_UDID"
const val SIMULATOR_NAME_PREFIX = "ShogunAi · "

fun worktreeSimulatorName(repositoryFolder: String, worktreeFolder: String): String =
    "$SIMULATOR_NAME_PREFIX$repositoryFolder · $worktreeFolder"

fun chooseSimulator(
    requested: SimulatorSettings,
    settings: SimulatorSettings,
    preferred: IosSimulatorProvider?,
    runtimes: List<SimulatorRuntime>,
): SimulatorChoice? {
    val iosRuntimes = runtimes.filter { it.platform.equals("iOS", ignoreCase = true) }
    val runtime = listOfNotNull(requested.runtime, settings.runtime, preferred?.runtime)
        .firstNotNullOfOrNull { wanted -> iosRuntimes.firstOrNull { it.name.equals(wanted, ignoreCase = true) } }
        ?: iosRuntimes.maxWithOrNull(compareBy(versionComparator) { it.version })
        ?: return null
    val candidates = runtime.supportedDeviceTypes.filter { it.productFamily == "iPhone" || it.productFamily == "iPad" }
    val deviceType = listOfNotNull(requested.device, settings.device, preferred?.device)
        .firstNotNullOfOrNull { wanted -> candidates.firstOrNull { it.name.equals(wanted, ignoreCase = true) } }
        ?: newestIphone(candidates)
        ?: candidates.firstOrNull()
        ?: return null
    return SimulatorChoice(runtime, deviceType)
}

private val IPHONE_MODEL_NUMBER = Regex("""^iPhone (\d+)""")

private fun newestIphone(candidates: List<SimulatorDeviceType>): SimulatorDeviceType? =
    candidates.filter { it.productFamily == "iPhone" }
        .minWithOrNull(
            compareByDescending<SimulatorDeviceType> { IPHONE_MODEL_NUMBER.find(it.name)?.let { match -> match.groupValues[1].toInt() } ?: -1 }
                .thenBy { it.name.length },
        )

private val versionComparator = Comparator<String> { first, second ->
    val left = first.split('.').map { it.toIntOrNull() ?: 0 }
    val right = second.split('.').map { it.toIntOrNull() ?: 0 }
    (0 until maxOf(left.size, right.size))
        .map { (left.getOrNull(it) ?: 0).compareTo(right.getOrNull(it) ?: 0) }
        .firstOrNull { it != 0 } ?: 0
}
