package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

class DevicectlPhysicalDevices(
    private val executor: ShellCommandExecutor,
    private val osName: String = System.getProperty("os.name", ""),
    private val tempFile: () -> Path = { Files.createTempFile("shogun-devicectl", ".json") },
) : PhysicalDevices {

    override suspend fun list(): PhysicalDeviceListing {
        if (!osName.startsWith("Mac", ignoreCase = true)) return PhysicalDeviceListing()
        val file = runCatching(tempFile).getOrNull() ?: return PhysicalDeviceListing()
        try {
            val result = executor.executeWithin(
                listOf("xcrun", "devicectl", "list", "devices", "--json-output", file.toString()),
                CommandTimeouts.LISTING,
            )
            if (!result.isSuccess) return PhysicalDeviceListing()
            return PhysicalDeviceListing(parseDevicectlDevices(Files.readString(file)))
        } catch (error: java.io.IOException) {
            return PhysicalDeviceListing()
        } finally {
            Files.deleteIfExists(file)
        }
    }
}

internal fun parseDevicectlDevices(text: String): List<PhysicalDevice> {
    val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val devices = root.at("result", "devices")?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
    return devices.mapNotNull { (it as? JsonObject)?.toPhysicalDevice() }
}

private fun JsonObject.toPhysicalDevice(): PhysicalDevice? {
    val reality = text("hardwareProperties", "reality") ?: text("properties", "hardware", "reality")
    if (reality != "physical") return null
    val udid = text("hardwareProperties", "udid") ?: text("properties", "hardware", "udid") ?: return null
    val transport = text("connectionProperties", "transportType") ?: text("properties", "connection", "transportType") ?: return null
    val tunnel = text("connectionProperties", "tunnelState") ?: text("properties", "connection", "state")
    if (tunnel == null || tunnel == UNAVAILABLE) return null
    val paired = (text("connectionProperties", "pairingState") ?: text("properties", "connection", "pairingState")) == PAIRED
    val developerMode = text("deviceProperties", "developerModeStatus")
        ?: at("properties", "state", "developerModeStatus")?.let { (it as? JsonObject)?.keys.orEmpty().firstOrNull() }
    return PhysicalDevice(
        platform = DevicePlatform.IOS,
        identity = udid,
        transport = udid,
        connection = if (transport == WIRED) DeviceConnection.USB else DeviceConnection.NETWORK,
        usability = when {
            !paired -> DeviceUsability.UNPAIRED
            developerMode != ENABLED -> DeviceUsability.DEVELOPER_MODE_OFF
            else -> DeviceUsability.USABLE
        },
        name = text("deviceProperties", "name") ?: text("properties", "state", "name"),
        manufacturer = APPLE,
        model = text("hardwareProperties", "productType") ?: text("properties", "hardware", "productType"),
        marketingName = text("hardwareProperties", "marketingName") ?: text("properties", "hardware", "marketingName"),
        osVersion = text("deviceProperties", "osVersionNumber") ?: text("properties", "software", "osVersionNumber", "stringValue"),
    )
}

private fun JsonObject.at(vararg path: String): JsonElement? =
    path.fold(this as JsonElement?) { element, key -> (element as? JsonObject)?.get(key) }

private fun JsonObject.text(vararg path: String): String? = (at(*path) as? JsonPrimitive)?.takeIf { it.isString }?.content

private const val UNAVAILABLE = "unavailable"
private const val PAIRED = "paired"
private const val ENABLED = "enabled"
private const val WIRED = "wired"
private const val APPLE = "Apple"
