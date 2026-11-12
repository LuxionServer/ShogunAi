package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceAcquisition
import app.luxion.shogunai.domain.model.DeviceBlock
import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceRelease
import app.luxion.shogunai.domain.model.DeviceRequest
import app.luxion.shogunai.domain.model.DeviceSnapshot
import app.luxion.shogunai.domain.model.HeldDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.PhysicalDeviceWait
import app.luxion.shogunai.domain.model.PhysicalDeviceWaitProgress
import app.luxion.shogunai.domain.model.PhysicalDevicesState
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.SHOGUN_ANDROID_DEVICE_SERIAL
import app.luxion.shogunai.domain.model.SHOGUN_IOS_DEVICE_UDID
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.poolDisplayName
import app.luxion.shogunai.domain.model.releaseRank
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class PhysicalDeviceLedger(
    private val sources: List<PhysicalDevices>,
    private val inventory: PhysicalDeviceInventoryStore,
    private val leases: PhysicalDeviceLeaseStore,
    private val stateStore: EnvironmentStateStore,
    waitQueue: DeviceWaitQueue,
    liveness: ProcessLiveness,
    private val adminDirectoryOf: suspend (worktreePath: String) -> String,
    private val leaseDuration: () -> Duration,
    private val agentActivity: (worktreePath: String) -> AgentState? = { null },
    private val clock: () -> Instant = Instant::now,
    sleep: suspend (Duration) -> Unit,
) {
    internal val inLine = WaitInLine(stateStore, waitQueue, liveness, clock, sleep, CONNECTION_POLL)

    internal fun now(): Instant = clock()

    internal fun duration(): Duration = leaseDuration()

    internal fun agentOf(lease: PhysicalDeviceLease): AgentState? = agentActivity(lease.worktreePath)

    internal suspend fun adminOf(worktreePath: String): String = normalizePath(adminDirectoryOf(worktreePath))

    internal suspend fun listing(): PhysicalDeviceListing =
        sources.fold(PhysicalDeviceListing()) { all, source -> all + runSuspendCatching { source.list() }.getOrDefault(PhysicalDeviceListing()) }

    internal fun record(listing: PhysicalDeviceListing): DeviceSnapshot {
        val now = clock().toEpochMilli()
        val recorded = inventory.read()
        val seen = listing.devices.associateBy { it.id }
        val updated = recorded.map { device -> seen[device.id].let { if (it == null) device else device.refreshedBy(it, now) } } +
            listing.devices.filter { device -> recorded.none { it.id == device.id } }.distinctBy { it.id }.map { it.recordedAt(now) }
        if (updated != recorded) inventory.write(updated)
        return DeviceSnapshot(updated, seen, listing.pending)
    }

    internal fun readInventory(): List<RecordedDevice> = inventory.read()

    internal fun writeInventory(devices: List<RecordedDevice>) = inventory.write(devices)

    internal fun readLeases(): DeviceLeases = leases.read()

    internal fun writeLeases(updated: DeviceLeases) = leases.write(updated)

    internal fun held(lease: PhysicalDeviceLease, device: RecordedDevice, connected: PhysicalDevice?) =
        HeldDevice(device, lease, connected, lease.expiresAt(leaseDuration()), agentOf(lease))

    internal fun state(snapshot: DeviceSnapshot, current: DeviceLeases): PhysicalDevicesState {
        val devices = snapshot.inventory.map { device ->
            val connected = snapshot.connected[device.id]
            val lease = current.leases.firstOrNull { it.deviceId == device.id }
            DeviceOverview(device, connected, lease?.let { held(it, device, connected) })
        }
        val waiting = DevicePlatform.entries.associateWith { inLine.liveQueue(it.queueName) }
        return PhysicalDevicesState(devices, snapshot.pending, waiting, current.losses, leaseDuration())
    }

    internal suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }

    suspend fun connectedAndroidSerial(worktreePath: String): String? {
        val android = leases.read().leases.filter { it.platform == DevicePlatform.ANDROID }
        if (android.isEmpty()) return null
        val admin = adminOf(worktreePath)
        val lease = android.firstOrNull { normalizePath(it.adminDirectory) == admin } ?: return null
        return listing().devices.firstOrNull { it.id == lease.deviceId }?.transport
    }

    suspend fun variablesFor(worktreePath: String): List<Pair<String, String>> {
        if (leases.read().leases.isEmpty()) return emptyList()
        val admin = runSuspendCatching { adminOf(worktreePath) }.getOrNull() ?: return emptyList()
        val mine = leases.read().leases.filter { normalizePath(it.adminDirectory) == admin }
        if (mine.isEmpty()) return emptyList()
        val connected = if (mine.any { it.platform == DevicePlatform.ANDROID }) listing().devices.associateBy { it.id } else emptyMap()
        return mine.sortedBy { it.platform }.map { lease ->
            when (lease.platform) {
                DevicePlatform.ANDROID -> SHOGUN_ANDROID_DEVICE_SERIAL to (connected[lease.deviceId]?.transport ?: lease.deviceId.substringAfter(':'))
                DevicePlatform.IOS -> SHOGUN_IOS_DEVICE_UDID to lease.deviceId.substringAfter(':')
            }
        }
    }
}

private val CONNECTION_POLL: Duration = Duration.ofSeconds(5)

private fun RecordedDevice.refreshedBy(device: PhysicalDevice, now: Long) = copy(
    name = device.name ?: name,
    manufacturer = device.manufacturer ?: manufacturer,
    model = device.model ?: model,
    marketingName = device.marketingName ?: marketingName,
    osVersion = device.osVersion ?: osVersion,
    lastSeen = now,
)

private fun PhysicalDevice.recordedAt(now: Long) =
    RecordedDevice(id, platform, null, name, manufacturer, model, marketingName, osVersion, lent = true, lastSeen = now)

class ReadPhysicalDevicesUseCase(private val ledger: PhysicalDeviceLedger) {

    suspend operator fun invoke(): Result<PhysicalDevicesState> = runSuspendCatching {
        val listing = ledger.listing()
        ledger.locked { ledger.state(ledger.record(listing), ledger.readLeases()) }
    }

    fun hasRecorded(): Boolean = ledger.readInventory().isNotEmpty()

    suspend fun ifAnyHeld(): PhysicalDevicesState? {
        val busy = runSuspendCatching {
            ledger.locked { ledger.readLeases().leases.isNotEmpty() || DevicePlatform.entries.any { ledger.inLine.liveQueue(it.queueName).isNotEmpty() } }
        }.getOrDefault(false)
        return if (busy) invoke().getOrNull() else null
    }
}

class AcquirePhysicalDeviceUseCase(private val ledger: PhysicalDeviceLedger) {

    suspend operator fun invoke(
        worktreePath: String,
        request: DeviceRequest,
        wait: PhysicalDeviceWait? = null,
    ): Result<DeviceAcquisition> = runSuspendCatching {
        val admin = ledger.adminOf(worktreePath)
        val worktree = normalizePath(worktreePath)
        val listing = ledger.listing()
        val platformRequest = request.forPlatform(ledger.locked { platformFor(request, ledger.record(listing)) })
        if (wait == null) return@runSuspendCatching attempt(admin, worktree, platformRequest, ticketId = null, listing = listing).getOrThrow()
        val platform = platformRequest.platform!!
        var first: PhysicalDeviceListing? = listing
        val waited = ledger.inLine.await(
            queue = platform.queueName,
            adminDirectory = admin,
            worktreePath = worktree,
            timeout = wait.timeout,
            attempt = { ticketId -> attempt(admin, worktree, platformRequest, ticketId, first ?: ledger.listing()).also { first = null } },
            blockedBy = { it as? WorktreeError.PhysicalDeviceUnavailable },
            onWaiting = { line ->
                wait.onProgress(PhysicalDeviceWaitProgress(platform, line.position, line.waiting, line.blocked.blocks))
                line.blocked.blocks.filterIsInstance<DeviceBlock.Held>().map { it.device }
                    .filter { it.isConnected && it.agentState != AgentState.WORKING }
                    .map { it.expiresAt }
                    .minOrNull()
            },
            timedOut = { waitedFor, blocked -> WorktreeError.PhysicalDeviceWaitTimedOut(waitedFor, blocked) },
            request = platformRequest.encode(),
        )
        waited.value.copy(waitedSeconds = waited.waitedSeconds)
    }

    private fun platformFor(request: DeviceRequest, snapshot: DeviceSnapshot): DevicePlatform {
        val matching = request.matching(snapshot.inventory)
        if (matching.isEmpty()) throw WorktreeError.NoRecordedDeviceMatches(request)
        request.platform?.let { return it }
        val acquirable = matching.filter(snapshot::isAcquirable)
        val platforms = (if (request.isUnfiltered) acquirable.ifEmpty { matching } else matching).map { it.platform }.toSet()
        return platforms.singleOrNull() ?: throw WorktreeError.PhysicalDevicePlatformRequired
    }

    private suspend fun attempt(
        admin: String,
        worktree: String,
        request: DeviceRequest,
        ticketId: String?,
        listing: PhysicalDeviceListing,
    ): Result<DeviceAcquisition> = runSuspendCatching {
        ledger.locked {
            val snapshot = ledger.record(listing)
            val current = ledger.readLeases()
            val platform = request.platform!!
            val now = ledger.now()
            val queue = ledger.inLine.liveQueue(platform.queueName)
            val matching = request.matching(snapshot.inventory)
            val leases = current.leases.filter { lease -> snapshot.inventory.any { it.id == lease.deviceId } }
            val mine = leases.firstOrNull { it.platform == platform && normalizePath(it.adminDirectory) == admin }
            if (mine != null) return@locked keep(mine, matching, snapshot, current.copy(leases = leases), now)
            val ahead = queue.takeWhile { it.id != ticketId }.othersThan(admin)
            val claimedAhead = { device: RecordedDevice ->
                ahead.any { ticket -> DeviceRequest.decode(ticket.request).let { it == null || device in it.matching(snapshot.inventory) } }
            }
            val candidates = matching.filter { snapshot.isAcquirable(it) && !claimedAhead(it) }.associateBy { it.id }
            val held = leases.mapNotNull { lease -> candidates[lease.deviceId]?.let { it to lease } }
            val free = candidates.values.firstOrNull { device -> leases.none { it.deviceId == device.id } }
            val taken = if (free == null) takeOver(held, now) else null
            val device = free ?: taken?.first ?: throw WorktreeError.PhysicalDeviceUnavailable(
                request,
                blocks(matching, leases, snapshot),
                queue.othersThan(admin).size,
            )
            val lease = PhysicalDeviceLease(device.id, platform, admin, worktree, now.toEpochMilli(), now.toEpochMilli())
            val previous = taken?.second
            val loss = previous?.let {
                DeviceLoss(it.adminDirectory, it.worktreePath, platform, device.id, device.displayName, now.toEpochMilli(), DeviceLossReason.TAKEN, poolDisplayName(admin, worktree))
            }
            ledger.writeLeases(
                DeviceLeases(
                    leases = leases.filterNot { it == previous } + lease,
                    losses = current.losses.filterNot { it.platform == platform && normalizePath(it.adminDirectory) == admin } + listOfNotNull(loss),
                ),
            )
            DeviceAcquisition(ledger.held(lease, device, snapshot.connected[device.id]), renewed = false, waitedSeconds = null, takenFrom = previous?.displayName)
        }
    }

    private fun keep(
        mine: PhysicalDeviceLease,
        matching: List<RecordedDevice>,
        snapshot: DeviceSnapshot,
        current: DeviceLeases,
        now: Instant,
    ): DeviceAcquisition {
        val device = snapshot.inventory.first { it.id == mine.deviceId }
        val connected = snapshot.connected[device.id]
        if (device !in matching) throw WorktreeError.PhysicalDeviceAlreadyHeld(ledger.held(mine, device, connected))
        val renewed = mine.copy(renewedAt = now.toEpochMilli())
        ledger.writeLeases(current.copy(leases = current.leases.map { if (it == mine) renewed else it }))
        return DeviceAcquisition(ledger.held(renewed, device, connected), renewed = true, waitedSeconds = null, takenFrom = null)
    }

    private fun takeOver(held: List<Pair<RecordedDevice, PhysicalDeviceLease>>, now: Instant): Pair<RecordedDevice, PhysicalDeviceLease>? =
        held.mapNotNull { (device, lease) -> rankOf(lease, now)?.let { Triple(device, lease, it) } }
            .minWithOrNull(compareBy<Triple<RecordedDevice, PhysicalDeviceLease, Int>> { it.third }.thenBy { it.second.acquiredAt })
            ?.let { it.first to it.second }

    private fun rankOf(lease: PhysicalDeviceLease, now: Instant): Int? =
        releaseRank(stopped = true, busy = false, leaseExpiresAt = lease.expiresAt(ledger.duration()), agent = ledger.agentOf(lease), now = now)

    private fun blocks(matching: List<RecordedDevice>, leases: List<PhysicalDeviceLease>, snapshot: DeviceSnapshot): List<DeviceBlock> =
        matching.mapNotNull { device ->
            val connected = snapshot.connected[device.id]
            val lease = leases.firstOrNull { it.deviceId == device.id }
            when {
                lease != null -> DeviceBlock.Held(ledger.held(lease, device, connected))
                !device.lent -> DeviceBlock.Withdrawn(device)
                connected == null -> DeviceBlock.Disconnected(device)
                connected.isUsable -> null
                else -> DeviceBlock.Unusable(device, connected.usability)
            }
        }
}

class ReleasePhysicalDeviceUseCase(private val ledger: PhysicalDeviceLedger) {

    suspend operator fun invoke(worktreePath: String, platform: DevicePlatform? = null): Result<DeviceRelease> = runSuspendCatching {
        val admin = ledger.adminOf(worktreePath)
        ledger.locked { release { it.platform.matches(platform) && normalizePath(it.adminDirectory) == admin } }
    }

    suspend fun force(deviceId: String): Result<DeviceRelease> = runSuspendCatching {
        ledger.locked { release { it.deviceId == deviceId } }
    }

    suspend fun onWorktreeRemoved(worktreePath: String) {
        val admin = runSuspendCatching { ledger.adminOf(worktreePath) }.getOrNull() ?: return
        runSuspendCatching {
            ledger.locked {
                release { normalizePath(it.adminDirectory) == admin }
                val current = ledger.readLeases()
                val kept = current.losses.filterNot { normalizePath(it.adminDirectory) == admin }
                if (kept != current.losses) ledger.writeLeases(current.copy(losses = kept))
            }
            DevicePlatform.entries.forEach { ledger.inLine.drop(it.queueName, admin) }
        }
    }

    private fun release(selected: (PhysicalDeviceLease) -> Boolean): DeviceRelease {
        val current = ledger.readLeases()
        val released = current.leases.filter(selected)
        if (released.isEmpty()) return DeviceRelease(emptyList())
        ledger.writeLeases(current.copy(leases = current.leases - released.toSet()))
        val inventory = ledger.readInventory()
        return DeviceRelease(
            released.mapNotNull { lease -> inventory.firstOrNull { it.id == lease.deviceId }?.let { ledger.held(lease, it, null) } },
        )
    }
}

private fun DevicePlatform.matches(selected: DevicePlatform?): Boolean = selected == null || selected == this

class ManagePhysicalDevicesUseCase(private val ledger: PhysicalDeviceLedger) {

    suspend fun lend(query: String): Result<RecordedDevice> = update(query) { it.copy(lent = true) }

    suspend fun withdraw(query: String): Result<RecordedDevice> = runSuspendCatching {
        ledger.locked {
            val device = find(query)
            val withdrawn = device.copy(lent = false)
            replace(device, withdrawn)
            val current = ledger.readLeases()
            val lease = current.leases.firstOrNull { it.deviceId == device.id }
            if (lease != null) {
                val loss = DeviceLoss(lease.adminDirectory, lease.worktreePath, lease.platform, device.id, device.displayName, ledger.now().toEpochMilli(), DeviceLossReason.WITHDRAWN)
                ledger.writeLeases(DeviceLeases(current.leases - lease, current.losses + loss))
            }
            withdrawn
        }
    }

    suspend fun rename(query: String, alias: String): Result<RecordedDevice> = runSuspendCatching {
        val trimmed = alias.trim()
        ledger.locked {
            val device = find(query)
            val owner = ledger.readInventory().firstOrNull { it.id != device.id && it.alias.equals(trimmed, ignoreCase = true) }
            if (owner != null) throw WorktreeError.DuplicateDeviceAlias(trimmed, owner)
            device.copy(alias = trimmed.ifEmpty { null }).also { replace(device, it) }
        }
    }

    suspend fun forget(query: String): Result<RecordedDevice> = runSuspendCatching {
        ledger.locked {
            val device = find(query)
            ledger.writeInventory(ledger.readInventory().filterNot { it.id == device.id })
            val current = ledger.readLeases()
            val kept = current.leases.filterNot { it.deviceId == device.id }
            if (kept != current.leases) ledger.writeLeases(current.copy(leases = kept))
            device
        }
    }

    private suspend fun update(query: String, change: (RecordedDevice) -> RecordedDevice): Result<RecordedDevice> = runSuspendCatching {
        ledger.locked {
            val device = find(query)
            change(device).also { replace(device, it) }
        }
    }

    private fun replace(device: RecordedDevice, updated: RecordedDevice) {
        if (updated != device) ledger.writeInventory(ledger.readInventory().map { if (it.id == device.id) updated else it })
    }

    private fun find(query: String): RecordedDevice {
        val inventory = ledger.readInventory()
        val matches = inventory.filter { it.id == query || it.id.substringAfter(':') == query }
            .ifEmpty { inventory.filter { it.alias.equals(query, ignoreCase = true) } }
            .ifEmpty { inventory.filter { it.name.equals(query, ignoreCase = true) } }
        return when (matches.size) {
            0 -> throw WorktreeError.PhysicalDeviceNotFound(query)
            1 -> matches.single()
            else -> throw WorktreeError.AmbiguousPhysicalDevice(query, matches)
        }
    }
}
