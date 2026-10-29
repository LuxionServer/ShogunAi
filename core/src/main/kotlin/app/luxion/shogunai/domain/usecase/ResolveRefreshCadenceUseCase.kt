package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.io.PowerSource
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.domain.model.ResourceSettings

class ResolveRefreshCadenceUseCase(
    private val repository: ResourceLimitsRepository,
    private val capacity: MachineCapacity,
    private val powerSource: PowerSource = PowerSource.UNKNOWN,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var lastPowerRead: PowerRead? = null

    suspend operator fun invoke(): RefreshCadence =
        when (runCatching { repository.load() }.getOrDefault(ResourceSettings()).profile) {
            CapacityProfile.LIGHT -> RefreshCadence.LIGHT
            CapacityProfile.BALANCED, CapacityProfile.CUSTOM -> RefreshCadence.BALANCED
            CapacityProfile.PER_WORKTREE -> RefreshCadence.PER_WORKTREE
            CapacityProfile.AUTOMATIC -> automatic()
        }

    private suspend fun automatic(): RefreshCadence {
        if (isOnBattery()) return RefreshCadence.LIGHT
        val memory = capacity.totalMemoryBytes() ?: return RefreshCadence.LIGHT
        return if (memory >= BALANCED_MINIMUM_GIGABYTES * BYTES_PER_GIGABYTE) RefreshCadence.BALANCED else RefreshCadence.LIGHT
    }

    private suspend fun isOnBattery(): Boolean {
        val cached = lastPowerRead
        if (cached != null && now() - cached.readAt < POWER_READ_INTERVAL_MILLIS) return cached.onBattery
        val onBattery = runCatching { powerSource.isOnBattery() }.getOrNull() ?: false
        lastPowerRead = PowerRead(onBattery, now())
        return onBattery
    }

    private data class PowerRead(val onBattery: Boolean, val readAt: Long)

    private companion object {
        const val BALANCED_MINIMUM_GIGABYTES = 16L
        const val POWER_READ_INTERVAL_MILLIS = 60_000L
    }
}
