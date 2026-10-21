package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.effectiveLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveResourceLimitsUseCaseTest {

    private fun automatic(gigabytes: Long?, cores: Int): ResourceLimits {
        val capacity = FakeMachineCapacity(gigabytes?.let { it * 1024 * 1024 * 1024 }, cores)
        return ResolveResourceLimitsUseCase(FakeResourceLimitsRepository(), capacity)()
    }

    @Test
    fun `16 GB with 8 cores gives one device and two daemons`() {
        assertEquals(ResourceLimits(1, 1, 2), automatic(16, 8))
    }

    @Test
    fun `32 GB with 12 cores gives three devices and four daemons`() {
        assertEquals(ResourceLimits(3, 3, 4), automatic(32, 12))
    }

    @Test
    fun `8 GB is clamped up to one device and two daemons`() {
        assertEquals(ResourceLimits(1, 1, 2), automatic(8, 8))
    }

    @Test
    fun `64 GB is clamped to four devices and eight daemons`() {
        assertEquals(ResourceLimits(4, 4, 8), automatic(64, 32))
    }

    @Test
    fun `few cores bound the devices`() {
        assertEquals(ResourceLimits(1, 1, 4), automatic(32, 4))
    }

    @Test
    fun `unknown memory falls back to Light`() {
        assertEquals(ResourceLimits.LIGHT, automatic(null, 8))
    }

    @Test
    fun `fixed profiles give their limits`() {
        val useCase = ResolveResourceLimitsUseCase(FakeResourceLimitsRepository(), FakeMachineCapacity())
        assertEquals(ResourceLimits(1, 1, 2), useCase.limitsFor(CapacityProfile.LIGHT))
        assertEquals(ResourceLimits(2, 2, 4), useCase.limitsFor(CapacityProfile.BALANCED))
        assertEquals(ResourceLimits(null, null, null), useCase.limitsFor(CapacityProfile.PER_WORKTREE))
    }

    @Test
    fun `custom gives the stored limits`() {
        val custom = ResourceLimits(emulators = 3, simulators = null, gradleDaemons = 2)
        val repository = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.CUSTOM, custom))
        assertEquals(custom, ResolveResourceLimitsUseCase(repository, FakeMachineCapacity())())
    }

    @Test
    fun `the emulator limit is capped at the 16 console port pairs`() {
        assertEquals(16, effectiveLimit(ResourceKind.EMULATOR, null))
        assertEquals(16, effectiveLimit(ResourceKind.EMULATOR, 40))
        assertEquals(3, effectiveLimit(ResourceKind.EMULATOR, 3))
        assertNull(effectiveLimit(ResourceKind.IOS_SIMULATOR, null))
    }
}
