package app.luxion.shogunai.domain.io

interface EnvironmentStateStore {

    fun readPorts(adminDirectory: String): Map<String, Int>

    fun writePorts(adminDirectory: String, ports: Map<String, Int>)

    fun reservedPortsOfRepository(commonDirectory: String): Set<Int>

    fun <T> withLock(commonDirectory: String, block: () -> T): T
}

interface PortProbe {

    fun isFree(port: Int): Boolean
}
