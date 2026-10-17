package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe

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
