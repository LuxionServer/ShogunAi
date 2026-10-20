package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MachineCapacity
import java.lang.management.ManagementFactory

class JvmMachineCapacity : MachineCapacity {

    override fun totalMemoryBytes(): Long? =
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
            ?.totalMemorySize
            ?.takeIf { it > 0 }

    override fun cores(): Int = Runtime.getRuntime().availableProcessors()
}
