package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.FIRST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.LAST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.queueName
import java.time.Instant

internal class DeviceRequest(
    val kind: ResourceKind,
    val repositoryPath: String,
    val worktreePath: String,
    val directories: WorktreeGitDirectories,
    val ticketId: String?,
) {
    val key: String = if (kind == ResourceKind.EMULATOR) EMULATOR_PORT_KEY else IOS_SIMULATOR_KEY
    val admin: String get() = directories.admin
    val beneficiary: String = "${folderName(repositoryPath)} · ${folderName(worktreePath)}"
}

internal sealed interface DeviceReservation {
    data object AlreadyHeld : DeviceReservation
    data object NeedsSimulator : DeviceReservation
    data class Reserved(val victims: List<PoolHolder>, val port: Int?, val udid: String?) : DeviceReservation
}

internal class DeviceReservationPlanner(
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val readPool: ReadResourcePoolUseCase,
    private val inLine: WaitInLine,
    private val clock: () -> Instant,
) {
    fun reserve(request: DeviceRequest, listing: DeviceListing, simulator: String?): DeviceReservation {
        var usage = readPool.usage(listing)
        val own = usage.holders.firstOrNull { normalizePath(it.adminDirectory!!) == normalizePath(request.admin) }
        if (own != null && !(request.kind == ResourceKind.IOS_SIMULATOR && listing.isMissing(own.reference!!))) {
            touch(request.admin, request.key)
            return DeviceReservation.AlreadyHeld
        }
        if (own != null) usage = usage.copy(holders = usage.holders - own)
        val queue = inLine.liveQueue(request.kind.queueName)
        val others = queue.othersThan(request.admin)
        if (queue.othersAheadOf(request.ticketId, request.admin)) {
            throw WorktreeError.ResourcePoolFull(request.kind, usage.effectiveLimit ?: usage.holders.size, usage.holders, others.size)
        }
        val victims = mutableListOf<PoolHolder>()
        val limit = usage.effectiveLimit
        var remaining = usage.holders
        while (limit != null && remaining.size >= limit) {
            val victim = nextVictim(remaining) ?: throw WorktreeError.ResourcePoolFull(request.kind, limit, remaining, others.size)
            victims += victim
            remaining = remaining - victim
        }
        if (request.kind == ResourceKind.EMULATOR) {
            val port = choosePort(request, victims, remaining)
            commit(request, own, victims) { stateStore.writePorts(request.admin, stateStore.readPorts(request.admin) + (request.key to port)) }
            return DeviceReservation.Reserved(victims.toList(), port, null)
        }
        if (simulator == null) return DeviceReservation.NeedsSimulator
        commit(request, own, victims) { stateStore.writeResources(request.admin, stateStore.readResources(request.admin) + (request.key to simulator)) }
        return DeviceReservation.Reserved(victims.toList(), null, simulator)
    }

    fun undo(request: DeviceRequest, reserved: DeviceReservation.Reserved) {
        val stillOurs = if (reserved.port != null) {
            stateStore.readPorts(request.admin)[request.key] == reserved.port
        } else {
            stateStore.readResources(request.admin)[request.key] == reserved.udid
        }
        if (stillOurs) stateStore.release(request.admin, request.key)
    }

    private fun choosePort(request: DeviceRequest, victims: MutableList<PoolHolder>, holders: List<PoolHolder>): Int {
        var remaining = holders
        while (true) {
            val port = freePort(request, victims.map(::consolePortOf).toSet())
            if (port != null) return port
            val victim = nextVictim(remaining.filter { portPairFree(consolePortOf(it)) })
                ?: throw WorktreeError.NoEmulatorPort(FIRST_EMULATOR_PORT, LAST_EMULATOR_PORT)
            victims += victim
            remaining = remaining - victim
        }
    }

    private fun freePort(request: DeviceRequest, released: Set<Int>): Int? {
        val taken = stateStore.reservedPortsOnMachine(request.directories.common) - released
        val free = (FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT step 2).filter { it !in taken && portPairFree(it) }
        val previous = stateStore.readReleases(request.admin)[request.key]?.previous?.let(::portOf)
        val quarantined = recentlyReleasedPorts(request) + released
        return free.firstOrNull { it == previous } ?: free.firstOrNull { it !in quarantined } ?: free.firstOrNull()
    }

    private fun portPairFree(port: Int): Boolean = portProbe.isFree(port) && portProbe.isFree(port + 1)

    private fun commit(request: DeviceRequest, own: PoolHolder?, victims: List<PoolHolder>, record: () -> Unit) {
        if (own != null) stateStore.release(request.admin, request.key)
        victims.forEach { victim ->
            val admin = victim.adminDirectory!!
            stateStore.release(admin, request.key)
            stateStore.writeReleases(
                admin,
                stateStore.readReleases(admin) + (request.key to ReleaseRecord(clock().toEpochMilli(), request.beneficiary, victim.reference)),
            )
        }
        record()
        stateStore.writeReleases(request.admin, stateStore.readReleases(request.admin) - request.key)
        touch(request.admin, request.key)
    }

    private fun recentlyReleasedPorts(request: DeviceRequest): Set<Int> {
        val horizon = clock().toEpochMilli() - readPool.leaseDuration().toMillis()
        return stateStore.reservationsOnMachine(request.directories.common)
            .mapNotNull { it.releases[request.key] }
            .filter { it.releasedAt > horizon }
            .mapNotNull { it.previous?.let(::portOf) }
            .toSet()
    }

    private fun nextVictim(holders: List<PoolHolder>): PoolHolder? =
        holders.filter { it.releaseRank != null }
            .minWithOrNull(compareBy<PoolHolder> { it.releaseRank }.thenBy { it.lastUsed != null }.thenBy { it.lastUsed })

    private fun touch(admin: String, key: String) {
        val now = clock().toEpochMilli()
        stateStore.writeLastUsed(admin, stateStore.readLastUsed(admin) + (key to now))
        stateStore.writeLeases(admin, stateStore.readLeases(admin) + (key to now))
    }
}

private fun consolePortOf(holder: PoolHolder): Int = holder.reference!!.substringAfterLast('-').toInt()

private fun portOf(serial: String): Int? = serial.substringAfterLast('-').toIntOrNull()

private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')
