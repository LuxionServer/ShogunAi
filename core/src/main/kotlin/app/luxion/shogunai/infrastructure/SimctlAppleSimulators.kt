package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class SimctlAppleSimulators(
    private val executor: ShellCommandExecutor,
    private val osName: String = System.getProperty("os.name").orEmpty(),
) : AppleSimulators {

    override fun isSupported(): Boolean = osName.startsWith("Mac", ignoreCase = true)

    override suspend fun runtimes(): List<SimulatorRuntime> = parseRuntimes(simctl("list", "-j", "runtimes").stdout)

    override suspend fun devices(): List<SimulatorDevice> = parseDevices(simctl("list", "-j", "devices").stdout)

    override suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String =
        simctl("create", name, deviceTypeIdentifier, runtimeIdentifier).stdout.trim()

    override suspend fun boot(udid: String) {
        simctl("boot", udid, toleratedState = "Booted")
    }

    override suspend fun show(udid: String) {
        val simulatorApp = listOf("open", "-b", SIMULATOR_BUNDLE_ID, "--args", "-CurrentDeviceUDID", udid)
        if (executor.execute(simulatorApp).isSuccess) return
        val deviceHub = listOf("open", "-b", DEVICE_HUB_BUNDLE_ID)
        val result = executor.execute(deviceHub)
        if (!result.isSuccess) throw WorktreeError.AppleToolFailed(deviceHub, result.exitCode, result.stderr.trim())
    }

    override suspend fun shutdown(udid: String) {
        simctl("shutdown", udid, toleratedState = "Shutdown")
    }

    override suspend fun delete(udid: String) {
        simctl("delete", udid)
    }

    private suspend fun simctl(vararg arguments: String, toleratedState: String? = null): CommandResult {
        val command = listOf("xcrun", "simctl") + arguments
        val result = executor.execute(command)
        val tolerated = toleratedState != null && result.stderr.contains("current state: $toleratedState")
        if (!result.isSuccess && !tolerated) {
            throw WorktreeError.AppleToolFailed(command, result.exitCode, result.stderr.trim())
        }
        return result
    }

    private companion object {
        const val SIMULATOR_BUNDLE_ID = "com.apple.iphonesimulator"
        const val DEVICE_HUB_BUNDLE_ID = "com.apple.dt.Devices"
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

internal fun parseRuntimes(json: String): List<SimulatorRuntime> {
    val runtimes = lenientJson.parseToJsonElement(json).objectField("runtimes") as? JsonArray ?: return emptyList()
    return runtimes.mapNotNull { element ->
        val runtime = element as? JsonObject ?: return@mapNotNull null
        if ((runtime["isAvailable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
        SimulatorRuntime(
            identifier = runtime.string("identifier") ?: return@mapNotNull null,
            name = runtime.string("name") ?: return@mapNotNull null,
            version = runtime.string("version").orEmpty(),
            platform = runtime.string("platform").orEmpty(),
            supportedDeviceTypes = (runtime["supportedDeviceTypes"] as? JsonArray).orEmpty().mapNotNull { type ->
                val deviceType = type as? JsonObject ?: return@mapNotNull null
                SimulatorDeviceType(
                    identifier = deviceType.string("identifier") ?: return@mapNotNull null,
                    name = deviceType.string("name") ?: return@mapNotNull null,
                    productFamily = deviceType.string("productFamily").orEmpty(),
                )
            },
        )
    }
}

internal fun parseDevices(json: String): List<SimulatorDevice> {
    val byRuntime = lenientJson.parseToJsonElement(json).objectField("devices") as? JsonObject ?: return emptyList()
    return byRuntime.values.flatMap { devices ->
        (devices as? JsonArray).orEmpty().mapNotNull { element ->
            val device = element as? JsonObject ?: return@mapNotNull null
            SimulatorDevice(
                udid = device.string("udid") ?: return@mapNotNull null,
                name = device.string("name").orEmpty(),
                state = when (device.string("state")) {
                    "Booted" -> SimulatorState.BOOTED
                    "Shutdown" -> SimulatorState.SHUTDOWN
                    else -> SimulatorState.BOOTING
                },
            )
        }
    }
}

private fun JsonElement.objectField(name: String) = (this as? JsonObject)?.get(name)

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
