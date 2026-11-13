package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.CapacityProfile

fun buildSlotsFor(profile: CapacityProfile, capacity: MachineCapacity): Int? = when (profile) {
    CapacityProfile.LIGHT -> LIGHT_BUILD_SLOTS
    CapacityProfile.BALANCED -> BALANCED_BUILD_SLOTS
    CapacityProfile.PER_WORKTREE -> null
    CapacityProfile.AUTOMATIC, CapacityProfile.CUSTOM -> automaticBuildSlots(capacity)
}

private fun automaticBuildSlots(capacity: MachineCapacity): Int {
    val gigabytes = capacity.totalMemoryBytes()?.let { (it / BYTES_PER_GIGABYTE).toInt() } ?: return LIGHT_BUILD_SLOTS
    return minOf(gigabytes / MEMORY_PER_BUILD_GB, capacity.cores() / CORES_PER_BUILD).coerceIn(LIGHT_BUILD_SLOTS, MAX_AUTOMATIC_BUILD_SLOTS)
}

private const val LIGHT_BUILD_SLOTS = 1
private const val BALANCED_BUILD_SLOTS = 2
private const val MEMORY_PER_BUILD_GB = 16
private const val CORES_PER_BUILD = 4
private const val MAX_AUTOMATIC_BUILD_SLOTS = 4
