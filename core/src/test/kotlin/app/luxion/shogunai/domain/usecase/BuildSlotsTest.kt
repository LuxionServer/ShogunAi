package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildSlotsTest {

    private fun machine(gigabytes: Long?, cores: Int) = object : MachineCapacity {
        override fun totalMemoryBytes(): Long? = gigabytes?.let { it * BYTES_PER_GIGABYTE }
        override fun cores(): Int = cores
    }

    @Test
    fun `fixed profiles give fixed slots`() {
        assertEquals(1, buildSlotsFor(CapacityProfile.LIGHT, machine(64, 16)))
        assertEquals(2, buildSlotsFor(CapacityProfile.BALANCED, machine(64, 16)))
        assertNull(buildSlotsFor(CapacityProfile.PER_WORKTREE, machine(64, 16)))
    }

    @Test
    fun `automatic slots follow memory and cores`() {
        assertEquals(1, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(16, 8)))
        assertEquals(2, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(32, 12)))
        assertEquals(4, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(64, 16)))
        assertEquals(2, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(128, 8)))
        assertEquals(4, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(256, 64)))
        assertEquals(1, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(8, 2)))
        assertEquals(1, buildSlotsFor(CapacityProfile.AUTOMATIC, machine(null, 16)))
    }

    @Test
    fun `custom uses the automatic slots`() {
        assertEquals(4, buildSlotsFor(CapacityProfile.CUSTOM, machine(64, 16)))
    }

    @Test
    fun `the machine's profile decides its slots`() {
        val repository = FakeResourceLimitsRepository(ResourceSettings(profile = CapacityProfile.BALANCED))

        assertEquals(2, ResolveResourceLimitsUseCase(repository, machine(64, 16)).buildSlots())
    }

    @Test
    fun `unreadable settings use the automatic slots`() {
        val repository = FakeResourceLimitsRepository().apply { loadFailure = IllegalStateException("unreadable") }

        assertEquals(4, ResolveResourceLimitsUseCase(repository, machine(64, 16)).buildSlots())
    }
}
