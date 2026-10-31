package app.luxion.shogunai.infrastructure

import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmMachineCapacityTest {

    @Test
    fun `reads positive memory and cores`() {
        val capacity = JvmMachineCapacity()
        assertTrue((capacity.totalMemoryBytes() ?: 0) > 0)
        assertTrue(capacity.cores() > 0)
    }

    @Test
    fun `memory is unknown without the platform bean or when it reports none`() {
        val plain = object : java.lang.management.OperatingSystemMXBean {
            override fun getObjectName() = javax.management.ObjectName("java.lang:type=OperatingSystem")
            override fun getName() = "Test"
            override fun getArch() = "arm64"
            override fun getVersion() = "1"
            override fun getAvailableProcessors() = 1
            override fun getSystemLoadAverage() = 0.0
        }
        val platform = java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val empty = object : com.sun.management.OperatingSystemMXBean by platform {
            override fun getTotalMemorySize(): Long = 0
        }

        assertNull(JvmMachineCapacity(plain).totalMemoryBytes())
        assertNull(JvmMachineCapacity(empty).totalMemoryBytes())
    }
}
