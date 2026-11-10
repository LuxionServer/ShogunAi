package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

enum class DevicePlatform(val key: String, val label: String) {
    ANDROID("android", "Android"),
    IOS("ios", "iOS"),
    ;

    val queueName: WaitQueueName
        get() = when (this) {
            ANDROID -> WaitQueueName.ANDROID_DEVICE
            IOS -> WaitQueueName.IOS_DEVICE
        }

    companion object {
        fun fromKey(key: String): DevicePlatform? = entries.firstOrNull { it.key == key }
    }
}

enum class DeviceConnection(val key: String, val label: String) {
    USB("usb", "USB"),
    NETWORK("network", "network"),
}

enum class DeviceUsability(val key: String, val note: String) {
    USABLE("usable", "usable"),
    UNAUTHORIZED("unauthorized", "waiting for the user to allow USB debugging on the device"),
    OFFLINE("offline", "offline"),
    CONNECTING("connecting", "connecting"),
    UNPAIRED("unpaired", "not paired with this Mac"),
    DEVELOPER_MODE_OFF("developer-mode-off", "developer mode must be turned on"),
}

fun physicalDeviceId(platform: DevicePlatform, identity: String): String = "${platform.key}:$identity"

data class PhysicalDevice(
    val platform: DevicePlatform,
    val identity: String,
    val transport: String,
    val connection: DeviceConnection,
    val usability: DeviceUsability = DeviceUsability.USABLE,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val marketingName: String? = null,
    val osVersion: String? = null,
) {
    val id: String get() = physicalDeviceId(platform, identity)

    val isUsable: Boolean get() = usability == DeviceUsability.USABLE
}

data class PendingDevice(
    val platform: DevicePlatform,
    val transport: String,
    val usability: DeviceUsability,
)

data class PhysicalDeviceListing(
    val devices: List<PhysicalDevice> = emptyList(),
    val pending: List<PendingDevice> = emptyList(),
) {
    operator fun plus(other: PhysicalDeviceListing) = PhysicalDeviceListing(devices + other.devices, pending + other.pending)
}

data class RecordedDevice(
    val id: String,
    val platform: DevicePlatform,
    val alias: String? = null,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val marketingName: String? = null,
    val osVersion: String? = null,
    val lent: Boolean = true,
    val lastSeen: Long,
) {
    val displayName: String get() = alias ?: marketingName ?: name ?: model ?: id
}

data class DeviceRequest(
    val platform: DevicePlatform? = null,
    val name: String? = null,
    val model: String? = null,
) {
    val isUnfiltered: Boolean get() = name == null && model == null

    fun forPlatform(platform: DevicePlatform) = copy(platform = platform)

    fun matching(inventory: List<RecordedDevice>): List<RecordedDevice> {
        val onPlatform = inventory.filter { platform == null || it.platform == platform }
        val named = if (name == null) {
            onPlatform
        } else {
            onPlatform.filter { it.alias.equals(name, ignoreCase = true) }
                .ifEmpty { onPlatform.filter { it.name.equals(name, ignoreCase = true) } }
        }
        return if (model == null) named else named.filter { it.matchesModel(model) }
    }

    fun encode(): String = listOfNotNull(
        platform?.let { "platform=${it.key}" },
        name?.let { "name=$it" },
        model?.let { "model=$it" },
    ).joinToString("\n")

    fun describe(): String = when {
        name != null -> name
        model != null -> "a device matching \"$model\""
        platform != null -> "an ${platform.label} device"
        else -> "a physical device"
    }

    companion object {
        fun decode(encoded: String?): DeviceRequest? {
            if (encoded == null) return null
            val fields = encoded.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return DeviceRequest(fields["platform"]?.let(DevicePlatform::fromKey), fields["name"], fields["model"])
        }
    }
}

private fun RecordedDevice.matchesModel(text: String): Boolean =
    model?.contains(text, ignoreCase = true) == true || marketingName?.contains(text, ignoreCase = true) == true

data class PhysicalDeviceLease(
    val deviceId: String,
    val platform: DevicePlatform,
    val adminDirectory: String,
    val worktreePath: String,
    val acquiredAt: Long,
    val renewedAt: Long,
) {
    val displayName: String get() = poolDisplayName(adminDirectory, worktreePath)

    fun expiresAt(duration: Duration): Instant = Instant.ofEpochMilli(renewedAt).plus(duration)
}

enum class DeviceLossReason(val key: String) { TAKEN("taken"), WITHDRAWN("withdrawn") }

data class DeviceLoss(
    val adminDirectory: String,
    val worktreePath: String,
    val platform: DevicePlatform,
    val deviceId: String,
    val deviceName: String,
    val at: Long,
    val reason: DeviceLossReason,
    val forWorktree: String? = null,
)

data class DeviceLeases(
    val leases: List<PhysicalDeviceLease> = emptyList(),
    val losses: List<DeviceLoss> = emptyList(),
)

data class DeviceSnapshot(
    val inventory: List<RecordedDevice>,
    val connected: Map<String, PhysicalDevice>,
    val pending: List<PendingDevice>,
) {
    fun isAcquirable(device: RecordedDevice): Boolean = device.lent && connected[device.id]?.isUsable == true
}

data class HeldDevice(
    val device: RecordedDevice,
    val lease: PhysicalDeviceLease,
    val connected: PhysicalDevice?,
    val expiresAt: Instant,
    val agentState: AgentState?,
) {
    val holderName: String get() = lease.displayName

    val isConnected: Boolean get() = connected != null
}

data class DeviceOverview(
    val device: RecordedDevice,
    val connected: PhysicalDevice?,
    val holder: HeldDevice?,
)

data class PhysicalDevicesState(
    val devices: List<DeviceOverview>,
    val pending: List<PendingDevice>,
    val waiting: Map<DevicePlatform, List<WaitTicket>>,
    val losses: List<DeviceLoss>,
    val leaseDuration: Duration,
) {
    fun heldBy(adminDirectory: String, platform: DevicePlatform): HeldDevice? =
        devices.mapNotNull { it.holder }.firstOrNull { it.lease.platform == platform && it.lease.adminDirectory.sameAdmin(adminDirectory) }

    fun waitingOf(adminDirectory: String, platform: DevicePlatform): Pair<Int, WaitTicket>? {
        val queue = waiting[platform].orEmpty()
        val index = queue.indexOfFirst { it.adminDirectory.sameAdmin(adminDirectory) }
        return if (index < 0) null else (index + 1) to queue[index]
    }

    fun lossOf(adminDirectory: String, platform: DevicePlatform): DeviceLoss? =
        losses.lastOrNull { it.platform == platform && it.adminDirectory.sameAdmin(adminDirectory) }
}

private fun String.sameAdmin(other: String): Boolean = replace('\\', '/').trimEnd('/') == other.replace('\\', '/').trimEnd('/')

data class DeviceAcquisition(
    val held: HeldDevice,
    val renewed: Boolean,
    val waitedSeconds: Long?,
    val takenFrom: String?,
)

data class DeviceRelease(
    val released: List<HeldDevice>,
)

sealed interface DeviceBlock {
    data class Held(val device: HeldDevice) : DeviceBlock

    data class Disconnected(val device: RecordedDevice) : DeviceBlock

    data class Withdrawn(val device: RecordedDevice) : DeviceBlock

    data class Unusable(val device: RecordedDevice, val usability: DeviceUsability) : DeviceBlock
}

data class PhysicalDeviceWaitProgress(
    val platform: DevicePlatform,
    val position: Int,
    val waiting: Int,
    val blocks: List<DeviceBlock>,
)

data class PhysicalDeviceWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (PhysicalDeviceWaitProgress) -> Unit = {},
)

const val SHOGUN_ANDROID_DEVICE_SERIAL = "SHOGUN_ANDROID_DEVICE_SERIAL"

const val SHOGUN_IOS_DEVICE_UDID = "SHOGUN_IOS_DEVICE_UDID"
