package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

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
)

enum class PoolHolderState { STOPPED, BOOTING, RUNNING, SHUTDOWN, BOOTED, BUSY, IDLE, UNKNOWN }

data class PoolHolder(
    val kind: ResourceKind,
    val worktreePath: String?,
    val adminDirectory: String?,
    val state: PoolHolderState,
    val idle: Boolean,
    val lastUsed: Instant?,
    val reference: String? = null,
) {
    val displayName: String
        get() {
            val repository = adminDirectory?.substringBefore("/.git")?.substringAfterLast('/').orEmpty()
            val worktree = (worktreePath ?: adminDirectory)?.trimEnd('/')?.substringAfterLast('/').orEmpty()
            return if (repository.isEmpty()) worktree else "$repository · $worktree"
        }
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
