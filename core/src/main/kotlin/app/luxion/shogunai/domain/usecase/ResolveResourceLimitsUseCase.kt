package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import java.time.Duration

class ResolveResourceLimitsUseCase(
    private val repository: ResourceLimitsRepository,
    private val capacity: MachineCapacity,
) {
    operator fun invoke(): ResourceLimits = limitsFor(runCatching { repository.load() }.getOrDefault(ResourceSettings()))

    fun leaseDuration(): Duration = runCatching { repository.load() }.getOrDefault(ResourceSettings()).leaseDuration

    fun desktopTurnDuration(): Duration = runCatching { repository.load() }.getOrDefault(ResourceSettings()).desktopTurnDuration

    fun limitsFor(settings: ResourceSettings): ResourceLimits = limitsFor(settings.profile, settings.custom)

    fun limitsFor(profile: CapacityProfile, custom: ResourceLimits = ResourceLimits.LIGHT): ResourceLimits = when (profile) {
        CapacityProfile.AUTOMATIC -> automatic()
        CapacityProfile.LIGHT -> ResourceLimits.LIGHT
        CapacityProfile.BALANCED -> ResourceLimits.BALANCED
        CapacityProfile.PER_WORKTREE -> ResourceLimits.UNLIMITED
        CapacityProfile.CUSTOM -> custom
    }

    private fun automatic(): ResourceLimits {
        val gigabytes = capacity.totalMemoryBytes()?.let { (it / BYTES_PER_GIGABYTE).toInt() } ?: return ResourceLimits.LIGHT
        val cores = capacity.cores()
        val devices = minOf((gigabytes - DEVICE_MEMORY_RESERVE_GB) / MEMORY_PER_UNIT_GB, cores / CORES_PER_DEVICE)
            .coerceIn(1, MAX_AUTOMATIC_DEVICES)
        val daemons = (gigabytes / MEMORY_PER_UNIT_GB).coerceIn(MIN_AUTOMATIC_DAEMONS, MAX_AUTOMATIC_DAEMONS)
        return ResourceLimits(emulators = devices, simulators = devices, gradleDaemons = daemons)
    }

    private companion object {
        const val DEVICE_MEMORY_RESERVE_GB = 8
        const val MEMORY_PER_UNIT_GB = 8
        const val CORES_PER_DEVICE = 4
        const val MAX_AUTOMATIC_DEVICES = 4
        const val MIN_AUTOMATIC_DAEMONS = 2
        const val MAX_AUTOMATIC_DAEMONS = 8
    }
}
