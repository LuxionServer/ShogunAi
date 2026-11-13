package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PendingDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.runSuspendCatching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AdbPhysicalDevices(
    private val sdk: AndroidSdk,
    private val repositoryPath: () -> String,
) : PhysicalDevices {

    private val mutex = Mutex()
    private val identities = mutableMapOf<String, Identity>()

    override suspend fun list(): PhysicalDeviceListing = mutex.withLock {
        val location = runSuspendCatching { sdk.locate(repositoryPath()) }.getOrNull() ?: return@withLock PhysicalDeviceListing()
        val attached = runSuspendCatching { sdk.attachedDevices(location) }.getOrDefault(emptyMap())
            .filterKeys { !it.startsWith(EMULATOR_PREFIX) }
        identities.keys.retainAll(attached.filterValues { it == ONLINE }.keys)
        val devices = mutableListOf<PhysicalDevice>()
        val pending = mutableListOf<PendingDevice>()
        attached.forEach { (serial, state) ->
            if (state != ONLINE) {
                pending += PendingDevice(DevicePlatform.ANDROID, serial, usabilityOf(state))
                return@forEach
            }
            when (val identity = identityOf(location, serial)) {
                null -> pending += PendingDevice(DevicePlatform.ANDROID, serial, DeviceUsability.CONNECTING)
                Identity.Emulator -> Unit
                is Identity.Device -> devices += identity.toDevice(serial)
            }
        }
        PhysicalDeviceListing(devices, pending)
    }

    private suspend fun identityOf(location: SdkLocation, serial: String): Identity? {
        identities[serial]?.let { return it }
        val properties = sdk.deviceProperties(location, serial) ?: return null
        val identity = when {
            properties[QEMU] == "1" -> Identity.Emulator
            else -> properties[SERIAL_NUMBER]?.takeIf { it.isNotBlank() }?.let { Identity.Device(it, properties) } ?: return null
        }
        identities[serial] = identity
        return identity
    }

    private fun Identity.Device.toDevice(serial: String): PhysicalDevice {
        val model = properties[MODEL].orNullIfBlank()
        val marketingName = MARKETING_NAMES.firstNotNullOfOrNull { properties[it].orNullIfBlank() } ?: model
        return PhysicalDevice(
            platform = DevicePlatform.ANDROID,
            identity = serialNumber,
            transport = serial,
            connection = connectionOf(serial),
            name = marketingName,
            manufacturer = properties[MANUFACTURER].orNullIfBlank(),
            model = model,
            marketingName = marketingName,
            osVersion = properties[ANDROID_VERSION].orNullIfBlank(),
        )
    }

    private sealed interface Identity {
        data object Emulator : Identity

        data class Device(val serialNumber: String, val properties: Map<String, String>) : Identity
    }

    private companion object {
        const val EMULATOR_PREFIX = "emulator-"
        const val ONLINE = "device"
        const val QEMU = "ro.kernel.qemu"
        const val SERIAL_NUMBER = "ro.serialno"
        const val MANUFACTURER = "ro.product.manufacturer"
        const val MODEL = "ro.product.model"
        const val ANDROID_VERSION = "ro.build.version.release"
    }
}

private val MARKETING_NAMES = listOf("ro.product.marketname", "ro.config.marketing_name")

internal fun connectionOf(serial: String): DeviceConnection =
    if (':' in serial || serial.contains("._adb-tls-connect.")) DeviceConnection.NETWORK else DeviceConnection.USB

private fun usabilityOf(state: String): DeviceUsability = when (state) {
    "unauthorized" -> DeviceUsability.UNAUTHORIZED
    "offline" -> DeviceUsability.OFFLINE
    else -> DeviceUsability.CONNECTING
}

private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
