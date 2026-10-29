package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.io.PowerSource
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveRefreshCadenceUseCaseTest {

    private class FakePowerSource(var onBattery: Boolean?) : PowerSource {
        var reads = 0

        override suspend fun isOnBattery(): Boolean? {
            reads++
            return onBattery
        }
    }

    private var clock = 0L

    private fun resolver(
        profile: CapacityProfile,
        gigabytes: Long? = 24,
        power: PowerSource = PowerSource.UNKNOWN,
    ) = ResolveRefreshCadenceUseCase(
        FakeResourceLimitsRepository(ResourceSettings(profile = profile)),
        FakeMachineCapacity(gigabytes?.let { it * 1024 * 1024 * 1024 }, 8),
        power,
        now = { clock },
    )

    @Test
    fun `explicit profiles have their own cadence and custom refreshes as balanced`() = runTest {
        assertEquals(RefreshCadence.LIGHT, resolver(CapacityProfile.LIGHT)())
        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.BALANCED)())
        assertEquals(RefreshCadence.PER_WORKTREE, resolver(CapacityProfile.PER_WORKTREE)())
        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.CUSTOM)())
    }

    @Test
    fun `automatic is balanced from 16 GB and light below`() = runTest {
        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.AUTOMATIC, gigabytes = 24)())
        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.AUTOMATIC, gigabytes = 16)())
        assertEquals(RefreshCadence.LIGHT, resolver(CapacityProfile.AUTOMATIC, gigabytes = 8)())
    }

    @Test
    fun `automatic is light when the memory can't be read`() = runTest {
        assertEquals(RefreshCadence.LIGHT, resolver(CapacityProfile.AUTOMATIC, gigabytes = null)())
    }

    @Test
    fun `automatic is light on battery and returns on AC power`() = runTest {
        val power = FakePowerSource(onBattery = true)
        val resolve = resolver(CapacityProfile.AUTOMATIC, power = power)

        assertEquals(RefreshCadence.LIGHT, resolve())

        power.onBattery = false
        clock += 60_000
        assertEquals(RefreshCadence.BALANCED, resolve())
    }

    @Test
    fun `an unknown power source counts as AC power`() = runTest {
        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.AUTOMATIC, power = FakePowerSource(onBattery = null))())
    }

    @Test
    fun `a failing power source counts as AC power`() = runTest {
        val failing = object : PowerSource {
            override suspend fun isOnBattery(): Boolean? = error("pmset failed")
        }

        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.AUTOMATIC, power = failing)())
    }

    @Test
    fun `the power source is read at most once a minute`() = runTest {
        val power = FakePowerSource(onBattery = true)
        val resolve = resolver(CapacityProfile.AUTOMATIC, power = power)

        resolve()
        clock += 30_000
        power.onBattery = false
        assertEquals(RefreshCadence.LIGHT, resolve())
        clock += 30_000
        assertEquals(RefreshCadence.BALANCED, resolve())

        assertEquals(2, power.reads)
    }

    @Test
    fun `explicit profiles never read the power source`() = runTest {
        val power = FakePowerSource(onBattery = true)

        assertEquals(RefreshCadence.BALANCED, resolver(CapacityProfile.BALANCED, power = power)())
        assertEquals(0, power.reads)
    }
}
