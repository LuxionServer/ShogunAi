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

    override fun reservedPortsOnMachine(commonDirectory: String): Set<Int> =
        portsByAdminDirectory.values.flatMap { it.values }.toSet()

    override fun <T> withLock(block: () -> T): T {
        locks++
        return block()
    }
}

class FakePortProbe(private val busy: Set<Int> = emptySet()) : PortProbe {
    override fun isFree(port: Int): Boolean = port !in busy
}
