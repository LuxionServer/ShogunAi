package app.luxion.shogunai.infrastructure

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmMachineCapacityTest {

    @Test
    fun `reads positive memory and cores`() {
        val capacity = JvmMachineCapacity()
        assertTrue((capacity.totalMemoryBytes() ?: 0) > 0)
        assertTrue(capacity.cores() > 0)
    }
}
