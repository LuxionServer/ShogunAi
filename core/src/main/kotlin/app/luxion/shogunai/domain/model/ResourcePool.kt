package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ResourceKind { EMULATOR, IOS_SIMULATOR, GRADLE_DAEMON }

enum class CapacityProfile { AUTOMATIC, LIGHT, BALANCED, PER_WORKTREE, CUSTOM }

@Serializable
data class ResourceLimits(
    val emulators: Int? = null,
    val simulators: Int? = null,
    val gradleDaemons: Int? = null,
) {
    fun forKind(kind: ResourceKind): Int? = when (kind) {
        ResourceKind.EMULATOR -> emulators
        ResourceKind.IOS_SIMULATOR -> simulators
        ResourceKind.GRADLE_DAEMON -> gradleDaemons
    }

    companion object {
        val UNLIMITED = ResourceLimits()
        val LIGHT = ResourceLimits(emulators = 1, simulators = 1, gradleDaemons = 2)
        val BALANCED = ResourceLimits(emulators = 2, simulators = 2, gradleDaemons = 4)
    }
}

@Serializable
data class ResourceSettings(
    val profile: CapacityProfile = CapacityProfile.AUTOMATIC,
    val custom: ResourceLimits = ResourceLimits.LIGHT,
    val derivedDataQuotaGigabytes: Int? = null,
    val leaseMinutes: Int = DEFAULT_LEASE_MINUTES,
) {
    val leaseDuration: Duration
        get() = Duration.ofMinutes(leaseMinutes.takeIf { it > 0 }?.toLong() ?: DEFAULT_LEASE_MINUTES.toLong())


    val derivedDataQuotaBytes: Long?
        get() = derivedDataQuotaGigabytes?.takeIf { it > 0 }?.let { it * BYTES_PER_GIGABYTE }
}

const val DEFAULT_LEASE_MINUTES = 20

const val BYTES_PER_GIGABYTE = 1024L * 1024 * 1024

enum class PoolHolderState { STOPPED, BOOTING, RUNNING, SHUTDOWN, BOOTED, BUSY, IDLE, UNKNOWN }

enum class HolderProtection { AGENT_WORKING, LEASED }

fun Instant.toLocalClockTime(): String = CLOCK_TIME.format(this)

private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

data class PoolHolder(
    val kind: ResourceKind,
    val worktreePath: String?,
    val adminDirectory: String?,
    val state: PoolHolderState,
    val idle: Boolean,
    val lastUsed: Instant?,
    val reference: String? = null,
    val leaseExpiresAt: Instant? = null,
    val agentState: AgentState? = null,
    val releaseRank: Int? = null,
) {
    val protection: HolderProtection?
        get() = when {
            agentState == AgentState.WORKING -> HolderProtection.AGENT_WORKING
            releaseRank == null && leaseExpiresAt != null && state != PoolHolderState.BOOTING -> HolderProtection.LEASED
            else -> null
        }

    val displayName: String
        get() = poolDisplayName(adminDirectory, worktreePath)
}

data class PoolUsage(
    val kind: ResourceKind,
    val limit: Int?,
    val effectiveLimit: Int?,
    val holders: List<PoolHolder>,
) {
    val inUse: Int get() = holders.size
    val isOverLimit: Boolean get() = effectiveLimit != null && inUse > effectiveLimit
}

data class ReleaseRecord(
    val releasedAt: Long,
    val forWorktree: String,
    val previous: String? = null,
)

data class PoolRelease(
    val kind: ResourceKind,
    val worktreePath: String?,
    val reason: PoolReleaseReason,
    val holderName: String = "",
)

enum class PoolReleaseReason { MAKE_ROOM, WITHIN_LIMIT }

const val EMULATOR_PORT_PAIRS = 16

fun effectiveLimit(kind: ResourceKind, limit: Int?): Int? = when (kind) {
    ResourceKind.EMULATOR -> minOf(limit ?: EMULATOR_PORT_PAIRS, EMULATOR_PORT_PAIRS)
    else -> limit
}
