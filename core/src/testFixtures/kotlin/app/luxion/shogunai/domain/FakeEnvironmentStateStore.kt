package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.WorktreeReservations
import app.luxion.shogunai.domain.model.ReleaseRecord

class FakeEnvironmentStateStore(
    initial: Map<String, Map<String, Int>> = emptyMap(),
) : EnvironmentStateStore {

    val portsByAdminDirectory = initial.toMutableMap()
    var writes = 0
        private set
    var locks = 0
        private set

    override fun readPorts(adminDirectory: String): Map<String, Int> = portsByAdminDirectory[adminDirectory].orEmpty()

    override fun writePorts(adminDirectory: String, ports: Map<String, Int>) {
        writes++
        if (ports.isEmpty()) portsByAdminDirectory.remove(adminDirectory) else portsByAdminDirectory[adminDirectory] = ports
    }

    val resourcesByAdminDirectory = mutableMapOf<String, Map<String, String>>()

    override fun readResources(adminDirectory: String): Map<String, String> = resourcesByAdminDirectory[adminDirectory].orEmpty()

    override fun writeResources(adminDirectory: String, resources: Map<String, String>) {
        writes++
        if (resources.isEmpty()) resourcesByAdminDirectory.remove(adminDirectory) else resourcesByAdminDirectory[adminDirectory] = resources
    }

    val lastUsedByAdminDirectory = mutableMapOf<String, Map<String, Long>>()

    override fun readLastUsed(adminDirectory: String): Map<String, Long> = lastUsedByAdminDirectory[adminDirectory].orEmpty()

    override fun writeLastUsed(adminDirectory: String, lastUsed: Map<String, Long>) {
        if (lastUsed.isEmpty()) lastUsedByAdminDirectory.remove(adminDirectory) else lastUsedByAdminDirectory[adminDirectory] = lastUsed
    }

    val leasesByAdminDirectory = mutableMapOf<String, Map<String, Long>>()

    override fun readLeases(adminDirectory: String): Map<String, Long> = leasesByAdminDirectory[adminDirectory].orEmpty()

    override fun writeLeases(adminDirectory: String, leases: Map<String, Long>) {
        if (leases.isEmpty()) leasesByAdminDirectory.remove(adminDirectory) else leasesByAdminDirectory[adminDirectory] = leases
    }

    val releasesByAdminDirectory = mutableMapOf<String, Map<String, ReleaseRecord>>()

    override fun readReleases(adminDirectory: String): Map<String, ReleaseRecord> = releasesByAdminDirectory[adminDirectory].orEmpty()

    override fun writeReleases(adminDirectory: String, releases: Map<String, ReleaseRecord>) {
        if (releases.isEmpty()) releasesByAdminDirectory.remove(adminDirectory) else releasesByAdminDirectory[adminDirectory] = releases
    }

    override fun reservationsOnMachine(commonDirectory: String): List<WorktreeReservations> =
        (
            portsByAdminDirectory.keys + resourcesByAdminDirectory.keys + lastUsedByAdminDirectory.keys +
                leasesByAdminDirectory.keys + releasesByAdminDirectory.keys
            ).distinct().map { admin ->
            WorktreeReservations(admin, readPorts(admin), readResources(admin), readLastUsed(admin), readLeases(admin), readReleases(admin))
        }

    override fun reservedPortsOnMachine(commonDirectory: String): Set<Int> =
        portsByAdminDirectory.values.flatMap { it.values }.toSet()

    override fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String> =
        resourcesByAdminDirectory.values.mapNotNull { it[key] }.toSet()

    override fun <T> withLock(block: () -> T): T {
        locks++
        return block()
    }
}

class FakePortProbe(private val busy: Set<Int> = emptySet()) : PortProbe {
    override fun isFree(port: Int): Boolean = port !in busy
}
