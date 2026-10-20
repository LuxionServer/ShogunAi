package app.luxion.shogunai.domain.io

data class WorktreeReservations(
    val adminDirectory: String,
    val ports: Map<String, Int>,
    val resources: Map<String, String>,
    val lastUsed: Map<String, Long>,
)

interface EnvironmentStateStore {

    fun readPorts(adminDirectory: String): Map<String, Int>

    fun writePorts(adminDirectory: String, ports: Map<String, Int>)

    fun readResources(adminDirectory: String): Map<String, String>

    fun writeResources(adminDirectory: String, resources: Map<String, String>)

    fun readLastUsed(adminDirectory: String): Map<String, Long>

    fun writeLastUsed(adminDirectory: String, lastUsed: Map<String, Long>)

    fun reservationsOnMachine(commonDirectory: String): List<WorktreeReservations>

    fun release(adminDirectory: String, key: String) {
        writePorts(adminDirectory, readPorts(adminDirectory) - key)
        writeResources(adminDirectory, readResources(adminDirectory) - key)
        writeLastUsed(adminDirectory, readLastUsed(adminDirectory) - key)
    }

    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>

    fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String>

    fun <T> withLock(block: () -> T): T
}

interface PortProbe {

    fun isFree(port: Int): Boolean
}
