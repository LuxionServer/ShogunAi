package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

const val DEFAULT_DEVICE_WAIT_MINUTES = 30L

enum class WaitQueueName(val key: String) {
    EMULATOR("emulator"),
    IOS_SIMULATOR("ios-simulator"),
    DESKTOP("desktop"),
}

val ResourceKind.queueName: WaitQueueName
    get() = when (this) {
        ResourceKind.EMULATOR -> WaitQueueName.EMULATOR
        ResourceKind.IOS_SIMULATOR -> WaitQueueName.IOS_SIMULATOR
        ResourceKind.GRADLE_DAEMON -> throw IllegalArgumentException("Gradle daemons aren't waited for")
    }

data class ProcessIdentity(val pid: Long, val startedAt: Long?)

data class WaitTicket(
    val id: String,
    val adminDirectory: String,
    val worktreePath: String,
    val process: ProcessIdentity,
    val since: Long,
) {
    val displayName: String
        get() = poolDisplayName(adminDirectory, worktreePath)
}

data class DeviceWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (DeviceWaitProgress) -> Unit = {},
)

data class DeviceWaitProgress(
    val kind: ResourceKind,
    val position: Int,
    val waiting: Int,
    val limit: Int,
    val holders: List<PoolHolder>,
    val nextLeaseExpiry: Instant?,
)

fun poolDisplayName(adminDirectory: String?, worktreePath: String?): String {
    val repository = adminDirectory?.let { it.substringBefore("/.git").substringAfterLast('/') }.orEmpty()
    val worktree = (worktreePath ?: adminDirectory)?.let { it.trimEnd('/').substringAfterLast('/') }.orEmpty()
    return if (repository.isEmpty()) worktree else "$repository · $worktree"
}
