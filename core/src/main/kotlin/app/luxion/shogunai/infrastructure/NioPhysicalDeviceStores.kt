package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.RecordedDevice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class NioPhysicalDeviceInventoryStore(
    private val machineDirectory: Path,
) : PhysicalDeviceInventoryStore {

    override fun read(): List<RecordedDevice> =
        readJson(machineDirectory.resolve(INVENTORY_FILE_NAME), InventoryFile.serializer())?.devices.orEmpty().mapNotNull { it.toDevice() }

    override fun write(devices: List<RecordedDevice>) {
        writeJson(machineDirectory, INVENTORY_FILE_NAME, InventoryFile.serializer(), InventoryFile(devices.map { it.toEntry() }))
    }

    @Serializable
    internal data class InventoryFile(val devices: List<DeviceEntry>)

    @Serializable
    internal data class DeviceEntry(
        val id: String,
        val platform: String,
        val alias: String? = null,
        val name: String? = null,
        val manufacturer: String? = null,
        val model: String? = null,
        val marketingName: String? = null,
        val osVersion: String? = null,
        val lent: Boolean = true,
        val lastSeen: Long,
    ) {
        fun toDevice(): RecordedDevice? = DevicePlatform.fromKey(platform)?.let {
            RecordedDevice(id, it, alias, name, manufacturer, model, marketingName, osVersion, lent, lastSeen)
        }
    }

    private fun RecordedDevice.toEntry() =
        DeviceEntry(id, platform.key, alias, name, manufacturer, model, marketingName, osVersion, lent, lastSeen)

    companion object {
        const val INVENTORY_FILE_NAME = "devices.json"
    }
}

class NioPhysicalDeviceLeaseStore(
    private val machineDirectory: Path,
) : PhysicalDeviceLeaseStore {

    override fun read(): DeviceLeases {
        val file = readJson(machineDirectory.resolve(LEASE_FILE_NAME), LeaseFile.serializer()) ?: return DeviceLeases()
        return DeviceLeases(file.leases.mapNotNull { it.toLease() }, file.losses.mapNotNull { it.toLoss() })
    }

    override fun write(leases: DeviceLeases) {
        val file = machineDirectory.resolve(LEASE_FILE_NAME)
        if (leases.leases.isEmpty() && leases.losses.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        writeJson(machineDirectory, LEASE_FILE_NAME, LeaseFile.serializer(), LeaseFile(leases.leases.map { it.toEntry() }, leases.losses.map { it.toEntry() }))
    }

    @Serializable
    internal data class LeaseFile(val leases: List<LeaseEntry>, val losses: List<LossEntry>)

    @Serializable
    internal data class LeaseEntry(
        val deviceId: String,
        val platform: String,
        val adminDirectory: String,
        val worktreePath: String,
        val acquiredAt: Long,
        val renewedAt: Long,
    ) {
        fun toLease(): PhysicalDeviceLease? = DevicePlatform.fromKey(platform)?.let {
            PhysicalDeviceLease(deviceId, it, adminDirectory, worktreePath, acquiredAt, renewedAt)
        }
    }

    @Serializable
    internal data class LossEntry(
        val adminDirectory: String,
        val worktreePath: String,
        val platform: String,
        val deviceId: String,
        val deviceName: String,
        val at: Long,
        val reason: String,
        val forWorktree: String?,
    ) {
        fun toLoss(): DeviceLoss? {
            val platform = DevicePlatform.fromKey(platform) ?: return null
            val reason = DeviceLossReason.entries.firstOrNull { it.key == reason } ?: return null
            return DeviceLoss(adminDirectory, worktreePath, platform, deviceId, deviceName, at, reason, forWorktree)
        }
    }

    private fun PhysicalDeviceLease.toEntry() = LeaseEntry(deviceId, platform.key, adminDirectory, worktreePath, acquiredAt, renewedAt)

    private fun DeviceLoss.toEntry() = LossEntry(adminDirectory, worktreePath, platform.key, deviceId, deviceName, at, reason.key, forWorktree)

    companion object {
        const val LEASE_FILE_NAME = "device-leases.json"
    }
}

private val storeJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun <T> readJson(file: Path, serializer: KSerializer<T>): T? {
    if (!file.exists()) return null
    return try {
        storeJson.decodeFromString(serializer, file.readText())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun <T> writeJson(directory: Path, fileName: String, serializer: KSerializer<T>, value: T) {
    Files.createDirectories(directory)
    val tempFile = Files.createTempFile(directory, ".$fileName", ".tmp")
    Files.writeString(tempFile, storeJson.encodeToString(serializer, value) + "\n")
    Files.move(tempFile, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
