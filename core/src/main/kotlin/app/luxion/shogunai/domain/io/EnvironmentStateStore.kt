package app.luxion.shogunai.domain.io

interface EnvironmentStateStore {

    fun readPorts(adminDirectory: String): Map<String, Int>

    fun writePorts(adminDirectory: String, ports: Map<String, Int>)

    fun readResources(adminDirectory: String): Map<String, String>

    fun writeResources(adminDirectory: String, resources: Map<String, String>)

    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>

    fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String>

    fun <T> withLock(block: () -> T): T
}

interface PortProbe {

    fun isFree(port: Int): Boolean
}
