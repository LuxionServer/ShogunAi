package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MachineCapacity
import java.lang.management.ManagementFactory
import java.lang.management.OperatingSystemMXBean

class JvmMachineCapacity(
    private val system: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean(),
) : MachineCapacity {

    override fun totalMemoryBytes(): Long? {
        val memory = system as? com.sun.management.OperatingSystemMXBean ?: return null
        return memory.totalMemorySize.takeIf { it > 0 }
    }

    override fun cores(): Int = Runtime.getRuntime().availableProcessors()
}
